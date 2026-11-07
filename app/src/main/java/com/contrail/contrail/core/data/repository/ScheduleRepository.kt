package com.contrail.contrail.core.data.repository

import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.data.settings.UsageTracker
import com.contrail.contrail.core.data.sync.AccountRepository
import com.contrail.contrail.core.database.CachedResponseEntity
import com.contrail.contrail.core.database.ContrailDatabase
import com.contrail.contrail.core.database.SegmentEntity
import com.contrail.contrail.core.network.schedule.AeroDataBoxSource
import com.contrail.contrail.core.network.schedule.AirportBoard
import com.contrail.contrail.core.network.schedule.FlightStatus
import com.contrail.contrail.core.network.schedule.ScheduleSource
import com.contrail.contrail.core.network.sync.SyncBackend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** How a board refresh went, for the line under the board. */
enum class BoardRefresh { Fresh, Shared, Cached, QuotaLow, Unavailable }

/**
 * Real departures and arrivals (AeroDataBox) and the airline's view of your own flights.
 *
 * The free plan is 400 units a month, so:
 * - boards are kept 15 minutes on the phone and, when you're signed in, in the shared Supabase
 *   table, so one fetch serves everyone using Contrail;
 * - boards stop when fewer than [com.contrail.contrail.core.data.settings.ScheduleUsage.RESERVE]
 *   units are left, keeping the rest for your own flights;
 * - your flights are checked at most three times each (see [checkpoints]).
 */
@Singleton
class ScheduleRepository @Inject constructor(
    private val db: ContrailDatabase,
    private val source: ScheduleSource,
    private val backend: SyncBackend,
    private val account: AccountRepository,
    private val usage: UsageTracker,
    private val json: Json,
    private val clock: Clock,
) {
    private val cache = db.cacheDao()

    val units = usage.aeroDataBox

    fun board(iata: String): Flow<AirportBoard?> = cache.observe(key(iata)).map { row ->
        row?.let { runCatching { AeroDataBoxSource.parseBoard(json, iata.uppercase(), it.body, it.fetchedAtMs) }.getOrNull() }
    }

    /**
     * With [fetch] false, only what's free: the phone's copy or one shared in the last 15 minutes.
     * With [fetch] true (you tapped Load), AeroDataBox is asked too, costing 2 units.
     */
    suspend fun refreshBoard(iata: String, fetch: Boolean = true, force: Boolean = false): BoardRefresh {
        val code = iata.uppercase()
        val now = clock.nowMs()
        val local = cache.get(key(code))
        if (!force && local != null && now - local.fetchedAtMs < FRESH_MS) return BoardRefresh.Cached
        val session = account.currentSession()
        // Someone else may have fetched it a moment ago.
        session?.let { s ->
            runCatching { backend.board(s, code) }.getOrNull()?.let { (at, body) ->
                if (now - at < FRESH_MS && (local == null || at > local.fetchedAtMs)) {
                    cache.put(CachedResponseEntity(key(code), body, at))
                    return BoardRefresh.Shared
                }
            }
        }
        if (!fetch) return if (local != null) BoardRefresh.Cached else BoardRefresh.Unavailable
        if (!usage.aeroDataBox.first().boardsAllowed) return if (local != null) BoardRefresh.QuotaLow else BoardRefresh.Unavailable
        val body = runCatching { source.board(code) }.getOrNull() ?: return if (local != null) BoardRefresh.Cached else BoardRefresh.Unavailable
        cache.put(CachedResponseEntity(key(code), body, now))
        session?.let { s -> runCatching { backend.putBoard(s, code, now, body) } }
        return BoardRefresh.Fresh
    }

    /** Asks the airline about one of your flights and saves gate, terminal, tail and times. */
    suspend fun refreshFlight(segmentId: Long): Boolean {
        val dao = db.tripDao()
        val seg = dao.segment(segmentId) ?: return false
        if (!usage.aeroDataBox.first().flightsAllowed) return false
        val date = LocalDateTime.parse(seg.departureLocal).toLocalDate()
        val found = runCatching { source.flight("${seg.airlineIata}${seg.flightNumber}", date) }.getOrNull() ?: return false
        val status = found.firstOrNull { it.departure.iata == seg.origin } ?: found.firstOrNull() ?: run {
            dao.updateSegment(seg.copy(statusCheckedAtMs = clock.nowMs()))
            return false
        }
        dao.updateSegment(apply(seg, status, clock.nowMs()))
        return true
    }

    /** Checks your flight at the moments that matter, once each: when the Live Update opens, before boarding, after landing. */
    suspend fun checkIfDue(segmentId: Long): Boolean {
        val seg = db.tripDao().segment(segmentId) ?: return false
        val now = clock.nowMs()
        val last = seg.statusCheckedAtMs ?: 0
        val due = checkpoints(seg).lastOrNull { it <= now } ?: return false
        if (last >= due) return false
        return refreshFlight(segmentId)
    }

    companion object {
        const val FRESH_MS = 15 * 60_000L
        private fun key(iata: String) = "adb:board:${iata.uppercase()}"

        fun checkpoints(s: SegmentEntity): List<Long> = listOf(
            s.departureUtcMs - 3 * 3_600_000L,
            s.departureUtcMs - 45 * 60_000L,
            s.arrivalUtcMs + 20 * 60_000L,
        )

        /** What the airline says, merged into your flight. Your own entries win where the airline is silent. */
        fun apply(seg: SegmentEntity, st: FlightStatus, nowMs: Long): SegmentEntity {
            val depRunway = st.departure.runway?.toEpochMilli()
            val arrRunway = st.arrival.runway?.toEpochMilli()
            return seg.copy(
                gate = st.departure.gate ?: seg.gate,
                departureTerminal = st.departure.terminal ?: seg.departureTerminal,
                arrivalTerminal = st.arrival.terminal ?: seg.arrivalTerminal,
                tail = seg.tail ?: st.registration?.uppercase(),
                aircraftType = seg.aircraftType,
                revisedDepartureUtcMs = st.departure.revised?.toEpochMilli() ?: seg.revisedDepartureUtcMs,
                revisedArrivalUtcMs = st.arrival.revised?.toEpochMilli() ?: seg.revisedArrivalUtcMs,
                actualDepartureUtcMs = depRunway ?: seg.actualDepartureUtcMs,
                actualArrivalUtcMs = arrRunway ?: seg.actualArrivalUtcMs,
                actualSource = if (depRunway != null || arrRunway != null) "runway" else seg.actualSource,
                liveStatus = AeroDataBoxSource.label(st.status),
                statusCheckedAtMs = nowMs,
            )
        }
    }
}

package com.contrail.contrail.core.data.rotation

import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.data.repository.AircraftDetailsRepository
import com.contrail.contrail.core.data.repository.AirportRepository
import com.contrail.contrail.core.data.settings.ApiKeys
import com.contrail.contrail.core.data.settings.ApiService
import com.contrail.contrail.core.data.settings.UsageTracker
import com.contrail.contrail.core.data.trip.FlightSegment
import com.contrail.contrail.core.database.CachedResponseEntity
import com.contrail.contrail.core.database.ContrailDatabase
import com.contrail.contrail.core.network.opensky.FlightHistorySource
import com.contrail.contrail.core.network.opensky.HistoricFlight
import com.contrail.contrail.core.network.opensky.OpenSkyException
import com.contrail.contrail.core.network.route.AdsbdbAircraftSource
import com.contrail.contrail.core.network.traffic.AircraftLookupSource
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The cockpit-visit planner's data, from free sources only:
 * - your aircraft live (adsb.fi, adsb.lol) by the tail you entered;
 * - its earlier legs (OpenSky flights by aircraft);
 * - the turnaround estimate (OpenSky arrivals and departures at your destination, last 7 days).
 * OpenSky needs a free account; until it's in Settings the history comes from [SampleRotation].
 */
@Singleton
class RotationRepository @Inject constructor(
    db: ContrailDatabase,
    private val history: FlightHistorySource,
    private val lookup: AircraftLookupSource,
    private val aircraftDb: AdsbdbAircraftSource,
    private val routes: AircraftDetailsRepository,
    private val airports: AirportRepository,
    private val keys: ApiKeys,
    private val usage: UsageTracker,
    private val json: Json,
    private val clock: Clock,
) {
    private val cache = db.cacheDao()

    suspend fun hasOpenSky(): Boolean =
        keys.get(ApiService.OpenSkyClientId) != null && keys.get(ApiService.OpenSkyClientSecret) != null

    suspend fun rotation(segment: FlightSegment): Rotation {
        val tail = segment.tail
        val live = tail?.let { runCatching { livePosition(it, segment.origin) }.getOrNull() }
        if (!hasOpenSky()) return SampleRotation.build(segment, live)
        if (usage.openSky.first().exhausted) {
            return Rotation(false, tail, live?.aircraft?.hex, live, emptyList(), null, "OpenSky credits are low today; history refreshes tomorrow.")
        }
        var error: String? = null
        val hex = live?.aircraft?.hex ?: tail?.let { t -> runCatching { hexFor(t) }.getOrNull() }
        val legs = hex?.let { h ->
            runCatching { earlierLegs(h, segment.departure) }.onFailure { error = friendly(it) }.getOrDefault(emptyList())
        }.orEmpty()
        val turn = runCatching { turnaround(segment) }.onFailure { error = error ?: friendly(it) }.getOrNull()
        return Rotation(false, tail, hex, live, legs, turn, error)
    }

    /** Registration → transponder hex, cached for good (it never changes for a tail). */
    private suspend fun hexFor(tail: String): String? {
        val key = "hex:${tail.uppercase()}"
        cache.get(key)?.let { return it.body.ifEmpty { null } }
        val rec = aircraftDb.byRegistration(tail)
        cache.put(CachedResponseEntity(key, rec?.hex.orEmpty(), clock.nowMs()))
        return rec?.hex
    }

    /** Your aircraft right now, and whether its current flight is bringing it to you. */
    suspend fun livePosition(tail: String, myOrigin: String): LivePosition? {
        val ac = lookup.byRegistration(tail).firstOrNull() ?: return null
        val callsign = ac.callsign?.trim()?.ifEmpty { null }
        var from: String? = null
        var to: String? = null
        if (callsign != null) {
            runCatching { routes.refreshRoute(callsign) }
            routes.observeRoute(callsign).first()?.let { r -> from = r.origin.iata; to = r.destination.iata }
        }
        val dir = airports.directory()
        val dest = to?.let { dir.find(it) }
        val eta = if (!ac.onGround && dest != null && (ac.groundSpeedKt ?: 0f) > 60f) {
            val metres = Geo.distanceM(ac.lat, ac.lon, dest.lat, dest.lon)
            clock.nowMs() + (metres / (ac.groundSpeedKt!! * Geo.MPS_PER_KNOT) * 1000).toLong() + 10 * 60_000L
        } else {
            null
        }
        return LivePosition(ac, callsign, from, to, isInbound = to == myOrigin && !ac.onGround, etaMs = eta)
    }

    private suspend fun earlierLegs(hex: String, before: Instant): List<EarlierLeg> {
        val end = minOf(before.epochSecond, clock.nowMs() / 1000)
        val begin = end - 2 * 86_400
        val key = "legs:$hex:${end / 3_600}"
        val flights = cache.get(key)?.let { json.decodeFromString<List<HistoricFlight>>(it.body) }
            ?: callOpenSky { history.flightsByAircraft(hex, begin, end) }.also {
                cache.put(CachedResponseEntity(key, json.encodeToString(it), clock.nowMs()))
            }
        return flights.sortedByDescending { it.lastSeen }.take(6).map { f ->
            EarlierLeg(f.cleanCallsign, airports.iataForIcao(f.from) ?: f.from, airports.iataForIcao(f.to) ?: f.to, f.firstSeen, f.lastSeen)
        }
    }

    /**
     * For each of the last 7 days: find this flight number arriving at your destination within
     * 3 hours of its scheduled time, then the same aircraft's next departure from there.
     */
    suspend fun turnaround(segment: FlightSegment): TurnaroundEstimate? {
        val callsign = segment.icaoCallsign ?: return null
        val dest = airports.directory().find(segment.destination) ?: return null
        val today = LocalDate.now(dest.zone)
        val days = (1..7).map { back ->
            val date = today.minusDays(back.toLong())
            val key = "turn:$callsign:${dest.icao}:$date"
            cache.get(key)?.let { return@map json.decodeFromString<TurnaroundDay>(it.body) }
            val day = turnaroundOn(date, callsign, dest.icao, segment, dest.zone)
            // OpenSky publishes a day's flights the next day, so only settled days are kept for good.
            if (day.seen || back >= 2) cache.put(CachedResponseEntity(key, json.encodeToString(day), clock.nowMs()))
            day
        }
        return TurnaroundEstimate(days)
    }

    private suspend fun turnaroundOn(date: LocalDate, callsign: String, destIcao: String, segment: FlightSegment, zone: java.time.ZoneId): TurnaroundDay {
        val scheduled = LocalDateTime.of(date, segment.arrivalLocal.toLocalTime()).atZone(zone).toEpochSecond()
        val arrivals = callOpenSky { history.arrivals(destIcao, scheduled - 3 * 3_600, scheduled + 3 * 3_600) }
        val mine = arrivals.firstOrNull { it.cleanCallsign == callsign } ?: return TurnaroundDay(date.toString())
        val departures = callOpenSky { history.departures(destIcao, mine.lastSeen, mine.lastSeen + 18 * 3_600) }
        val next = departures.filter { it.icao24.equals(mine.icao24, ignoreCase = true) && it.firstSeen > mine.lastSeen }
            .minByOrNull { it.firstSeen }
        return if (next == null) {
            TurnaroundDay(date.toString(), mine.icao24, mine.lastSeen, groundMinutes = null, stayed = true)
        } else {
            TurnaroundDay(
                date.toString(), mine.icao24, mine.lastSeen,
                groundMinutes = ((next.firstSeen - mine.lastSeen) / 60).toInt(),
                nextCallsign = next.cleanCallsign,
                nextTo = airports.iataForIcao(next.to) ?: next.to,
            )
        }
    }

    private suspend fun <T> callOpenSky(block: suspend () -> T): T {
        if (usage.openSky.first().exhausted) throw OpenSkyException("OpenSky credits are low today")
        return block()
    }

    /** Today's key for the OpenSky usage counter (OpenSky's day is UTC). */
    fun openSkyDayKey(): String = Instant.ofEpochMilli(clock.nowMs()).atZone(ZoneOffset.UTC).toLocalDate().toString()
}

/**
 * Realistic stand-in history while OpenSky isn't set up, so the planner still shows its shape.
 * Always labelled "sample" in the UI. Live positions are never faked.
 */
object SampleRotation {
    private val widebodies = setOf("A388", "A359", "A35K", "A333", "A332", "A339", "B789", "B788", "B78X", "B77W", "B77L", "B772", "B773", "B744", "B748", "B779")

    fun build(segment: FlightSegment, live: LivePosition?): Rotation {
        val wide = segment.aircraftType?.uppercase() in widebodies || segment.duration.toHours() >= 6
        val minutes = if (wide) listOf(165, 150, 180, null, 170, 155, 160) else listOf(45, 55, 40, 50, 65, 50, 45)
        val arrival = segment.arrivalLocal.toLocalDate()
        val nextTo = segment.origin
        val callsign = segment.icaoCallsign
        val nextCallsign = callsign?.let { cs ->
            val digits = cs.dropWhile { it.isLetter() }.toIntOrNull()
            digits?.let { cs.takeWhile { c -> c.isLetter() } + (it + 1) }
        }
        val days = minutes.mapIndexed { i, m ->
            TurnaroundDay(
                date = arrival.minusDays((i + 1).toLong()).toString(),
                icao24 = "sample",
                groundMinutes = m,
                stayed = m == null,
                nextCallsign = nextCallsign.takeIf { m != null },
                nextTo = nextTo.takeIf { m != null },
            )
        }
        val depSec = segment.departure.epochSecond
        val legs = if (segment.tail != null) {
            listOf(
                EarlierLeg(nextCallsign, segment.destination, segment.origin, depSec - 16 * 3_600, depSec - 3 * 3_600),
                EarlierLeg(callsign, segment.origin, segment.destination, depSec - 34 * 3_600, depSec - 21 * 3_600),
            )
        } else {
            emptyList()
        }
        return Rotation(true, segment.tail, live?.aircraft?.hex, live, legs, TurnaroundEstimate(days))
    }
}

/** OpenSky's own messages read fine; anything else (a network or parsing hiccup) gets a plain one. */
private fun friendly(e: Throwable): String =
    (e as? com.contrail.contrail.core.network.opensky.OpenSkyException)?.message ?: "Couldn't reach OpenSky just now. Try again in a minute."

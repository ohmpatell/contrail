package com.contrail.contrail.core.data.repository

import androidx.room.withTransaction
import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.model.LiveAircraft
import com.contrail.contrail.core.data.model.TrailPoint
import com.contrail.contrail.core.data.reference.AircraftClassifier
import com.contrail.contrail.core.database.ContrailDatabase
import com.contrail.contrail.core.database.TrailPointEntity
import com.contrail.contrail.core.database.toEntity
import com.contrail.contrail.core.database.toModel
import com.contrail.contrail.core.network.traffic.LiveTrafficSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

data class RefreshResult(val count: Int, val source: String, val receivedAtMs: Long)

interface LiveTrafficRepository {
    /** Aircraft seen recently enough to draw. Emits after every refresh. */
    fun observeTraffic(): Flow<List<LiveAircraft>>

    /** Recent positions for every aircraft, oldest first, keyed by hex. */
    fun observeTrails(): Flow<Map<String, List<TrailPoint>>>

    fun observeTrail(hex: String): Flow<List<TrailPoint>>

    /** Fetches live positions around [center] and stores them. Throws when every provider fails. */
    suspend fun refresh(center: LatLon, radiusNm: Int): RefreshResult

    /** Drops anything too old to be useful (e.g. after a long time offline). */
    suspend fun pruneStale()
}

@Singleton
class DefaultLiveTrafficRepository @Inject constructor(
    private val db: ContrailDatabase,
    private val source: LiveTrafficSource,
    private val clock: Clock,
) : LiveTrafficRepository {

    private val liveDao = db.liveAircraftDao()
    private val trailDao = db.trailDao()
    private val trailMutex = Mutex()
    private val lastTrailPoint = HashMap<String, TrailPointEntity>()

    override fun observeTraffic(): Flow<List<LiveAircraft>> =
        liveDao.observeSince(0).map { rows -> rows.map { it.toModel() } }

    override fun observeTrails(): Flow<Map<String, List<TrailPoint>>> =
        trailDao.observeSince(0)
            .distinctUntilChanged()
            .map { rows -> rows.groupBy({ it.hex }, { it.toModel() }) }

    override fun observeTrail(hex: String): Flow<List<TrailPoint>> =
        trailDao.observeForHex(hex, 0).map { rows -> rows.map { it.toModel() } }

    override suspend fun refresh(center: LatLon, radiusNm: Int): RefreshResult {
        val fetched = source.fetchAround(center.lat, center.lon, radiusNm)
        // Vehicles and obstacles are not aircraft: they never reach the database.
        val snapshot = fetched.copy(aircraft = fetched.aircraft.filterNot { AircraftClassifier.isClutter(it.category, it.typeCode, it.onGround) })
        val now = clock.nowMs()
        val points = trailMutex.withLock {
            snapshot.aircraft.mapNotNull { aircraft ->
                val candidate = TrailPointEntity(
                    hex = aircraft.hex,
                    timeMs = aircraft.positionTimeMs,
                    lat = aircraft.lat,
                    lon = aircraft.lon,
                    altitudeFt = aircraft.altitudeFt,
                    onGround = aircraft.onGround,
                    verticalRateFpm = aircraft.verticalRateFpm,
                    trackDeg = aircraft.trackDeg,
                )
                val last = lastTrailPoint[aircraft.hex]
                if (last == null || TrailThinning.shouldKeep(last, candidate)) {
                    lastTrailPoint[aircraft.hex] = candidate
                    candidate
                } else {
                    null
                }
            }
        }
        db.withTransaction {
            liveDao.upsertAll(snapshot.aircraft.map { it.toEntity() })
            trailDao.upsertAll(points)
            liveDao.deleteOlderThan(now - STALE_AFTER_MS)
            trailDao.deleteOlderThan(now - TRAIL_WINDOW_MS)
        }
        return RefreshResult(snapshot.aircraft.size, snapshot.source, snapshot.receivedAtMs)
    }

    override suspend fun pruneStale() {
        val now = clock.nowMs()
        liveDao.deleteOlderThan(now - STALE_AFTER_MS)
        trailDao.deleteOlderThan(now - TRAIL_WINDOW_MS)
        trailMutex.withLock { lastTrailPoint.entries.removeAll { it.value.timeMs < now - TRAIL_WINDOW_MS } }
    }

    companion object {
        /** Aircraft not refreshed for this long are dropped from the radar. */
        const val STALE_AFTER_MS = 75_000L

        /** How much history a trail keeps. */
        const val TRAIL_WINDOW_MS = 4 * 60_000L
    }
}

/**
 * Keeps trails light: straight legs need few points, turns and climbs need more. A point
 * is stored when the aircraft turns, changes altitude, lands or lifts off, or after a gap.
 */
internal object TrailThinning {
    private const val MIN_TURN_DEG = 4.0
    private const val MIN_TURN_MOVE_M = 120.0
    private const val MIN_ALT_CHANGE_FT = 400
    private const val MAX_GAP_MS = 20_000L

    fun shouldKeep(last: TrailPointEntity, next: TrailPointEntity): Boolean {
        if (next.timeMs <= last.timeMs) return false
        if (next.onGround != last.onGround) return true
        if (next.timeMs - last.timeMs >= MAX_GAP_MS) return true
        if (abs((next.altitudeFt ?: 0) - (last.altitudeFt ?: 0)) >= MIN_ALT_CHANGE_FT) return true
        val lastTrack = last.trackDeg ?: return true
        val nextTrack = next.trackDeg ?: return true
        val turned = abs(Geo.headingDelta(lastTrack.toDouble(), nextTrack.toDouble())) >= MIN_TURN_DEG
        return turned && Geo.distanceM(last.lat, last.lon, next.lat, next.lon) >= MIN_TURN_MOVE_M
    }
}

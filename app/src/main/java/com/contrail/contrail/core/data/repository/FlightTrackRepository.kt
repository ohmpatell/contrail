package com.contrail.contrail.core.data.repository

import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.network.track.FlightTrack
import com.contrail.contrail.core.network.track.TrackSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Flown tracks for the selected aircraft. Live and short-lived, so cached in memory only
 * (see DECISIONS.md) and refreshed at most every [TTL_MS] per aircraft.
 */
@Singleton
class FlightTrackRepository @Inject constructor(
    private val source: TrackSource,
    private val clock: Clock,
) {
    private val mutex = Mutex()
    private val cache = HashMap<String, Pair<Long, FlightTrack?>>()

    suspend fun track(hex: String): FlightTrack? = mutex.withLock {
        val now = clock.nowMs()
        cache[hex]?.let { (at, track) -> if (now - at < TTL_MS) return track }
        val track = runCatching { source.trackFor(hex) }.getOrNull()
        cache[hex] = now to track
        if (cache.size > 64) cache.entries.minByOrNull { it.value.first }?.let { cache.remove(it.key) }
        track
    }

    companion object {
        const val TTL_MS = 90_000L
    }
}

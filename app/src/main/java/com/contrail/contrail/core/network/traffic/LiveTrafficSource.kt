package com.contrail.contrail.core.network.traffic

import com.contrail.contrail.core.data.model.LiveAircraft

/** Live aircraft positions around a point. Implemented by adsb.lol, airplanes.live and fakes. */
interface LiveTrafficSource {
    /** Short provider name shown in the radar HUD and attribution. */
    val name: String

    suspend fun fetchAround(lat: Double, lon: Double, radiusNm: Int): LiveSnapshot
}

data class LiveSnapshot(
    val aircraft: List<LiveAircraft>,
    val receivedAtMs: Long,
    val source: String,
)

class LiveTrafficException(
    message: String,
    cause: Throwable? = null,
    /** Set when the provider asked us to back off (HTTP 429). */
    val retryAfterMs: Long? = null,
) : Exception(message, cause)

/**
 * Tries each provider in order, remembering failures so a dead provider is skipped
 * for a cool-down period instead of slowing every refresh.
 */
class FailoverLiveTrafficSource(
    private val providers: List<LiveTrafficSource>,
    private val coolDownMs: Long = 60_000,
    private val clock: () -> Long = System::currentTimeMillis,
) : LiveTrafficSource {
    private val failedUntil = mutableMapOf<String, Long>()

    override val name: String get() = providers.first().name

    override suspend fun fetchAround(lat: Double, lon: Double, radiusNm: Int): LiveSnapshot {
        val now = clock()
        val ordered = providers.sortedBy { (failedUntil[it.name] ?: 0L) > now }
        val errors = mutableListOf<Throwable>()
        for (provider in ordered) {
            try {
                val snapshot = provider.fetchAround(lat, lon, radiusNm)
                failedUntil.remove(provider.name)
                return snapshot
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                errors += e
                android.util.Log.w("ContrailTraffic", "${provider.name} failed: $e")
                val wait = (e as? LiveTrafficException)?.retryAfterMs?.coerceAtLeast(coolDownMs) ?: coolDownMs
                failedUntil[provider.name] = clock() + wait
            }
        }
        throw LiveTrafficException("All live traffic providers failed", errors.firstOrNull()).apply {
            errors.drop(1).forEach { addSuppressed(it) }
        }
    }
}

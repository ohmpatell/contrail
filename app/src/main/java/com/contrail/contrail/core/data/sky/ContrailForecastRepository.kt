package com.contrail.contrail.core.data.sky

import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.database.CachedResponseEntity
import com.contrail.contrail.core.database.ContrailDatabase
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Upper-air temperature and humidity from Open-Meteo (free, keyless, CC BY 4.0) at the three
 * pressure levels airliners cruise in, turned into a contrail forecast. Cached for an hour per
 * half-degree square.
 */
@Singleton
class ContrailForecastRepository @Inject constructor(
    db: ContrailDatabase,
    private val client: HttpClient,
    private val json: Json,
    private val clock: Clock,
) {
    private val cache = db.cacheDao()

    suspend fun forecast(at: LatLon): ContrailForecast? {
        val lat = Math.round(at.lat * 2) / 2.0
        val lon = Math.round(at.lon * 2) / 2.0
        val key = "contrails:$lat:$lon"
        val now = clock.nowMs()
        val body = cache.get(key)?.takeIf { now - it.fetchedAtMs < TTL_MS }?.body ?: run {
            val url = String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&hourly=%s&forecast_days=2&timeformat=unixtime",
                lat, lon, LEVELS.joinToString(",") { "temperature_${it}hPa,relative_humidity_${it}hPa" },
            )
            val response = runCatching { client.get(url) }.getOrNull() ?: return null
            if (!response.status.isSuccess()) return null
            response.body<String>().also { cache.put(CachedResponseEntity(key, it, now)) }
        }
        return parse(body, now)
    }

    fun parse(body: String, now: Long): ContrailForecast? {
        val r = runCatching { json.decodeFromString<OpenMeteoUpper>(body) }.getOrNull() ?: return null
        val h = r.hourly
        val hours = h.time.indices.mapNotNull { i ->
            val levels = LEVELS.mapNotNull { p ->
                val t = h.temps(p).getOrNull(i) ?: return@mapNotNull null
                val rh = h.rhs(p).getOrNull(i) ?: return@mapNotNull null
                ContrailPhysics.level(p, t, rh)
            }
            if (levels.isEmpty()) null else ContrailForecast.Hour(h.time[i] * 1000, levels)
        }
        return ContrailForecast(hours, now)
    }

    private companion object {
        val LEVELS = listOf(300, 250, 200)
        const val TTL_MS = 3_600_000L
    }
}

@Serializable
internal data class OpenMeteoUpper(val hourly: Hourly) {
    @Serializable
    data class Hourly(
        val time: List<Long> = emptyList(),
        @SerialName("temperature_300hPa") val t300: List<Double?> = emptyList(),
        @SerialName("relative_humidity_300hPa") val rh300: List<Double?> = emptyList(),
        @SerialName("temperature_250hPa") val t250: List<Double?> = emptyList(),
        @SerialName("relative_humidity_250hPa") val rh250: List<Double?> = emptyList(),
        @SerialName("temperature_200hPa") val t200: List<Double?> = emptyList(),
        @SerialName("relative_humidity_200hPa") val rh200: List<Double?> = emptyList(),
    ) {
        fun temps(p: Int) = when (p) { 300 -> t300; 250 -> t250; else -> t200 }
        fun rhs(p: Int) = when (p) { 300 -> rh300; 250 -> rh250; else -> rh200 }
    }
}

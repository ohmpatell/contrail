package com.contrail.contrail.core.network.weather

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/** Precipitation radar tiles for the radar's weather layer. */
interface WeatherRadarSource {
    /** An XYZ raster tile URL template for the latest frame, or null when unavailable. */
    suspend fun latestTileUrl(): WeatherTiles?
}

data class WeatherTiles(val urlTemplate: String, val frameTimeSec: Long, val maxZoom: Int, val attribution: String)

/** RainViewer's free public weather maps API (no key). */
class RainViewerSource(
    private val client: HttpClient,
    private val indexUrl: String = "https://api.rainviewer.com/public/weather-maps.json",
) : WeatherRadarSource {

    override suspend fun latestTileUrl(): WeatherTiles? {
        val response = client.get(indexUrl)
        if (!response.status.isSuccess()) return null
        val index: RainViewerIndex = response.body()
        val frame = index.radar?.past?.lastOrNull() ?: return null
        return WeatherTiles(
            // 256 px tiles, colour scheme 2 ("universal blue"), smoothed, snow shown.
            urlTemplate = "${index.host}${frame.path}/256/{z}/{x}/{y}/2/1_1.png",
            frameTimeSec = frame.time,
            maxZoom = 7,
            attribution = "Weather radar © RainViewer",
        )
    }
}

@Serializable
internal data class RainViewerIndex(val host: String, val radar: RainViewerRadar? = null)

@Serializable
internal data class RainViewerRadar(val past: List<RainViewerFrame> = emptyList())

@Serializable
internal data class RainViewerFrame(val time: Long, val path: String)

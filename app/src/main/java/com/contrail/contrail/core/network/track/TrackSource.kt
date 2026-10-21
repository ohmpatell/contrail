package com.contrail.contrail.core.network.track

import com.contrail.contrail.core.common.LatLon
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/** The path an aircraft has flown on its current flight, oldest point first. */
data class FlightTrack(val hex: String, val startTimeSec: Long, val points: List<LatLon>)

interface TrackSource {
    /** The current flight's track, or null when the provider has none. Throws on network errors. */
    suspend fun trackFor(hex: String): FlightTrack?
}

/** OpenSky's anonymous `tracks/all` endpoint: waypoints since take-off, free with a daily credit budget. */
class OpenSkyTrackSource(
    private val client: HttpClient,
    private val baseUrl: String = "https://opensky-network.org/api",
) : TrackSource {
    override suspend fun trackFor(hex: String): FlightTrack? {
        val response = client.get("$baseUrl/tracks/all?icao24=${hex.lowercase()}&time=0")
        if (response.status.value == 404) return null
        if (!response.status.isSuccess()) error("OpenSky returned HTTP ${response.status.value}")
        val body: OpenSkyTrack = response.body()
        val points = body.path.orEmpty().mapNotNull { p ->
            val lat = p.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val lon = p.getOrNull(2)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            LatLon(lat, lon)
        }
        if (points.size < 2) return null
        return FlightTrack(hex.lowercase(), body.startTime?.toLong() ?: 0L, points)
    }
}

@Serializable
internal data class OpenSkyTrack(
    val icao24: String? = null,
    val startTime: Double? = null,
    val path: List<JsonArray>? = null,
)

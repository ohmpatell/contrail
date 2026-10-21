package com.contrail.contrail.core.network.airport

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLQueryComponent
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

/** aviationweather.gov: METAR and TAF as JSON, free and keyless. Returns raw bodies for caching. */
class AviationWeatherSource(private val client: HttpClient) {
    suspend fun metar(icao: String): String? = get("https://aviationweather.gov/api/data/metar?ids=${icao.encodeURLQueryComponent()}&format=json")
    suspend fun taf(icao: String): String? = get("https://aviationweather.gov/api/data/taf?ids=${icao.encodeURLQueryComponent()}&format=json")

    private suspend fun get(url: String): String? {
        val r = client.get(url)
        return if (r.status.isSuccess()) r.bodyAsText() else null
    }
}

@Serializable
data class MetarCloud(val cover: String? = null, val base: Int? = null)

@Serializable
data class Metar(
    val icaoId: String,
    val obsTime: Long? = null,
    val temp: Double? = null,
    val dewp: Double? = null,
    /** Degrees, or "VRB" as a string in some reports. */
    val wdir: kotlinx.serialization.json.JsonElement? = null,
    val wspd: Int? = null,
    val wgst: Int? = null,
    val visib: kotlinx.serialization.json.JsonElement? = null,
    val altim: Double? = null,
    val rawOb: String? = null,
    val fltCat: String? = null,
    val clouds: List<MetarCloud> = emptyList(),
    val wxString: String? = null,
)

@Serializable
data class Taf(val icaoId: String, val rawTAF: String? = null, val issueTime: String? = null)

object WeatherParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true; explicitNulls = false }
    fun metar(body: String): Metar? = runCatching { json.decodeFromString<List<Metar>>(body).firstOrNull() }.getOrNull()
    fun taf(body: String): Taf? = runCatching { json.decodeFromString<List<Taf>>(body).firstOrNull() }.getOrNull()
}

/**
 * OpenStreetMap geometry for an airport diagram (runways, taxiways, terminals) from the
 * free Overpass API. Returns the raw JSON for caching.
 */
class OverpassSource(private val client: HttpClient) {
    suspend fun airportGeometry(lat: Double, lon: Double, radiusM: Int = 4_500): String? {
        val query = String.format(
            Locale.US,
            """[out:json][timeout:25];(way["aeroway"~"^(runway|taxiway|terminal|apron)$"](around:%d,%.5f,%.5f);relation["aeroway"="terminal"](around:%d,%.5f,%.5f););out geom tags;""",
            radiusM, lat, lon, radiusM, lat, lon,
        )
        // Overpass can take a while on big airports; give it longer than the default 15 s.
        val r = client.submitForm("https://overpass-api.de/api/interpreter", parameters { append("data", query) }) {
            timeout {
                requestTimeoutMillis = 45_000
                socketTimeoutMillis = 45_000
            }
        }
        return if (r.status.isSuccess()) r.bodyAsText() else null
    }
}

@Serializable
data class OsmPoint(val lat: Double, val lon: Double)

@Serializable
data class OsmMember(val type: String? = null, val role: String? = null, val geometry: List<OsmPoint> = emptyList())

@Serializable
data class OsmElement(
    val type: String,
    val tags: Map<String, String> = emptyMap(),
    val geometry: List<OsmPoint> = emptyList(),
    val members: List<OsmMember> = emptyList(),
)

@Serializable
data class OsmResult(val elements: List<OsmElement> = emptyList())

object OverpassParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
    fun parse(body: String): OsmResult = runCatching { json.decodeFromString<OsmResult>(body) }.getOrDefault(OsmResult())
}

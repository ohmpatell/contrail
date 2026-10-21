package com.contrail.contrail.core.network.logostream

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One HTTP response from logostream plus the quota headers it carried. */
data class LogostreamResponse(
    val status: Int,
    val body: String,
    val limit: String?,
    val remaining: String?,
    val reset: String?,
)

/**
 * logostream's Aviation Data API (free "Community" plan): airlines, routes, airport stats.
 * Responses are returned raw so the repository can cache them; see [LogostreamParser].
 */
class LogostreamSource(
    private val client: HttpClient,
    private val baseUrl: String = "https://aviation-api.logostream.dev",
) {
    suspend fun airline(icao: String, key: String) = get("/v1/airlines?icao=${icao.uppercase().encodeURLPathPart()}", key)

    suspend fun routesFrom(iata: String, page: Int, key: String) =
        get("/v1/routes?departureIata=${iata.uppercase().encodeURLPathPart()}&limit=50&offset=${(page - 1) * 50}", key)

    suspend fun airportStats(iata: String, key: String) = get("/v1/stats/airport/${iata.uppercase().encodeURLPathPart()}", key)

    private suspend fun get(path: String, key: String): LogostreamResponse {
        val response = client.get(baseUrl + path) { header("x-api-key", key) }
        return LogostreamResponse(
            status = response.status.value,
            body = response.bodyAsText(),
            limit = response.headers["X-RateLimit-Limit"],
            remaining = response.headers["X-RateLimit-Remaining"],
            reset = response.headers["X-RateLimit-Reset"],
        )
    }
}

@Serializable
data class LsLogo(
    val icon: String? = null,
    val logo: String? = null,
    @SerialName("logo_white") val logoWhite: String? = null,
    val tail: String? = null,
)

@Serializable
data class LsAirline(
    val iata: String? = null,
    val icao: String? = null,
    val name: String,
    val callsign: String? = null,
    val alliance: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    @SerialName("airline_group") val group: String? = null,
    val logo: LsLogo? = null,
    val colors: String? = null,
    val color2: String? = null,
    val color3: String? = null,
    val description: String? = null,
    @SerialName("fleet_size") val fleetSize: Int? = null,
    @SerialName("year_founded") val yearFounded: Int? = null,
    val website: String? = null,
)

@Serializable
data class LsRouteAirline(
    @SerialName("airline_iata") val iata: String? = null,
    @SerialName("airline_icao") val icao: String? = null,
)

@Serializable
data class LsRoute(
    @SerialName("departure_iata") val from: String,
    @SerialName("arrival_iata") val to: String,
    val airlines: List<LsRouteAirline> = emptyList(),
    @SerialName("distance_km") val distanceKm: Int? = null,
    @SerialName("duration_min") val durationMin: Int? = null,
)

@Serializable
data class LsAirportInfo(
    val iata: String? = null,
    val icao: String? = null,
    val name: String? = null,
    val timezone: String? = null,
)

@Serializable
data class LsStats(
    @SerialName("destinations_count") val destinations: Int? = null,
    @SerialName("airlines_count") val airlines: Int? = null,
    @SerialName("countries_reached") val countries: Int? = null,
)

@Serializable
data class LsHub(val iata: String? = null, val icao: String? = null, val name: String? = null)

@Serializable
data class LsAirportStats(
    val airport: LsAirportInfo? = null,
    val stats: LsStats? = null,
    val hubs: List<LsHub> = emptyList(),
    @SerialName("focus_cities") val focusCities: List<LsHub> = emptyList(),
)

@Serializable
internal data class LsPage<T>(val data: List<T> = emptyList(), val total: Int? = null, val truncated: Boolean = false)

object LogostreamParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true; explicitNulls = false }

    fun airline(body: String): LsAirline? = json.decodeFromString<LsPage<LsAirline>>(body).data.firstOrNull()

    fun routes(body: String): Pair<List<LsRoute>, Boolean> =
        json.decodeFromString<LsPage<LsRoute>>(body).let { it.data to it.truncated }

    fun stats(body: String): LsAirportStats = json.decodeFromString(body)

    fun encodeRoutes(routes: List<LsRoute>): String = json.encodeToString(routes)
    fun decodeRoutes(body: String): List<LsRoute> = json.decodeFromString(body)
}

package com.contrail.contrail.core.network.route

import com.contrail.contrail.core.data.model.AirportRef
import com.contrail.contrail.core.data.model.FlightRoute
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/** Callsign → origin and destination. Results are "route from callsign", never confirmed. */
interface RouteSource {
    /** The route, or null when the database doesn't know this callsign. Throws on network errors. */
    suspend fun routeForCallsign(callsign: String): FlightRoute?
}

/** adsbdb.com, free and keyless. */
class AdsbdbRouteSource(
    private val client: HttpClient,
    private val json: Json,
    private val baseUrl: String = "https://api.adsbdb.com",
) : RouteSource {

    override suspend fun routeForCallsign(callsign: String): FlightRoute? {
        val response = client.get("$baseUrl/v0/callsign/${callsign.trim().uppercase().encodeURLPathPart()}")
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) error("adsbdb returned HTTP ${response.status.value}")
        val envelope: AdsbdbEnvelope = response.body()
        // An unknown callsign comes back as {"response":"unknown callsign"}.
        val obj = envelope.response as? JsonObject ?: return null
        val route = obj["flightroute"]?.let { json.decodeFromJsonElement<AdsbdbFlightRoute>(it) } ?: return null
        return FlightRoute(
            callsign = route.callsign,
            origin = route.origin.toRef(),
            destination = route.destination.toRef(),
            airlineName = route.airline?.name,
        )
    }
}

@Serializable
internal data class AdsbdbEnvelope(val response: JsonElement? = null)

@Serializable
internal data class AdsbdbFlightRoute(
    val callsign: String,
    val airline: AdsbdbAirline? = null,
    val origin: AdsbdbAirport,
    val destination: AdsbdbAirport,
)

@Serializable
internal data class AdsbdbAirline(val name: String? = null, val icao: String? = null, val iata: String? = null)

@Serializable
internal data class AdsbdbAirport(
    @SerialName("iata_code") val iata: String? = null,
    @SerialName("icao_code") val icao: String? = null,
    val name: String,
    val municipality: String? = null,
    @SerialName("country_iso_name") val countryIso: String? = null,
    val latitude: Double,
    val longitude: Double,
) {
    fun toRef() = AirportRef(iata, icao, name, municipality, countryIso, latitude, longitude)
}

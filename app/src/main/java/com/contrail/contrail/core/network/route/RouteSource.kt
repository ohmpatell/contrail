package com.contrail.contrail.core.network.route

import com.contrail.contrail.core.data.model.AirportRef
import com.contrail.contrail.core.data.model.FlightRoute
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/** Callsign → origin and destination. Results are "route from callsign", never confirmed. */
fun interface RouteSource {
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

/** Registration → transponder hex and type, from adsbdb. */
data class AircraftRecord(val registration: String, val hex: String, val typeCode: String?, val owner: String?)

class AdsbdbAircraftSource(
    private val client: HttpClient,
    private val json: Json,
    private val baseUrl: String = "https://api.adsbdb.com",
) {
    suspend fun byRegistration(registration: String): AircraftRecord? {
        val response = client.get("$baseUrl/v0/aircraft/${registration.trim().uppercase().encodeURLPathPart()}")
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) error("adsbdb returned HTTP ${response.status.value}")
        val obj = response.body<AdsbdbEnvelope>().response as? JsonObject ?: return null
        val a = obj["aircraft"]?.let { json.decodeFromJsonElement<AdsbdbAircraft>(it) } ?: return null
        val hex = a.modeS?.lowercase() ?: return null
        return AircraftRecord(a.registration ?: registration.uppercase(), hex, a.icaoType, a.registeredOwner)
    }
}

@Serializable
internal data class AdsbdbAircraft(
    @SerialName("mode_s") val modeS: String? = null,
    val registration: String? = null,
    @SerialName("icao_type") val icaoType: String? = null,
    @SerialName("registered_owner") val registeredOwner: String? = null,
)

/**
 * adsb.lol's copy of the VRS standing data: static JSON per callsign, free and keyless. It lists
 * every stop of a multi-stop flight number and is often more current than adsbdb.
 */
class StandingDataRouteSource(
    private val client: HttpClient,
    private val json: Json,
    private val baseUrl: String = "https://vrs-standing-data.adsb.lol",
) : RouteSource {

    override suspend fun routeForCallsign(callsign: String): FlightRoute? {
        val cs = callsign.trim().uppercase()
        if (cs.length < 3) return null
        val response = client.get("$baseUrl/routes/${cs.take(2).encodeURLPathPart()}/${cs.encodeURLPathPart()}.json")
        if (response.status == HttpStatusCode.NotFound || response.status == HttpStatusCode.Forbidden) return null
        if (!response.status.isSuccess()) error("standing data returned HTTP ${response.status.value}")
        val body: StandingRoute = json.decodeFromString(response.body<String>())
        val stops = body.airports.map { AirportRef(it.iata, it.icao, it.name, it.location, it.country, it.lat, it.lon) }
        if (stops.size < 2) return null
        return FlightRoute(
            callsign = body.callsign,
            origin = stops.first(),
            destination = stops.last(),
            airlineName = null,
            stops = if (stops.size > 2) stops else emptyList(),
        )
    }
}

@Serializable
internal data class StandingRoute(
    val callsign: String,
    @SerialName("_airports") val airports: List<StandingAirport> = emptyList(),
)

@Serializable
internal data class StandingAirport(
    val name: String,
    val icao: String? = null,
    val iata: String? = null,
    val location: String? = null,
    @SerialName("countryiso2") val country: String? = null,
    val lat: Double,
    val lon: Double,
)

/**
 * Asks both databases and keeps both answers when they disagree: the first that answers is the
 * route, the other rides along as an alternative. Which one is right is decided later from what
 * the plane is actually doing (see RouteCheck).
 */
class CombinedRouteSource(private val sources: List<RouteSource>) : RouteSource {
    override suspend fun routeForCallsign(callsign: String): FlightRoute? = kotlinx.coroutines.coroutineScope {
        val answers = sources.map { s -> async { runCatching { s.routeForCallsign(callsign) } } }.map { it.await() }
        // Every source failing is a network error worth retrying; a clean "not found" is a miss.
        if (answers.all { it.isFailure }) throw answers.first().exceptionOrNull()!!
        val found = answers.mapNotNull { it.getOrNull() }
        val first = found.firstOrNull() ?: return@coroutineScope null
        val others = found.drop(1).filter { other -> other.airports.map { it.code } != first.airports.map { it.code } }
        first.copy(
            airlineName = first.airlineName ?: found.firstNotNullOfOrNull { it.airlineName },
            alternatives = others,
        )
    }
}

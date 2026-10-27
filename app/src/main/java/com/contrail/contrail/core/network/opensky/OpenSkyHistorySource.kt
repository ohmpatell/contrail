package com.contrail.contrail.core.network.opensky

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One flight from OpenSky's history (published the day after it flies). Airports are ICAO codes. */
@Serializable
data class HistoricFlight(
    val icao24: String,
    val callsign: String? = null,
    @SerialName("estDepartureAirport") val from: String? = null,
    @SerialName("estArrivalAirport") val to: String? = null,
    val firstSeen: Long,
    val lastSeen: Long,
) {
    val cleanCallsign: String? get() = callsign?.trim()?.uppercase()?.ifEmpty { null }
}

data class OpenSkyCredentials(val clientId: String, val clientSecret: String)

class OpenSkyException(message: String, val retryAfterSec: Long? = null) : Exception(message)

/** OpenSky's flights endpoints. They need a (free) account. */
interface FlightHistorySource {
    suspend fun flightsByAircraft(icao24: String, beginSec: Long, endSec: Long): List<HistoricFlight>
    suspend fun arrivals(airportIcao: String, beginSec: Long, endSec: Long): List<HistoricFlight>
    suspend fun departures(airportIcao: String, beginSec: Long, endSec: Long): List<HistoricFlight>
}

/**
 * OpenSky REST API with OAuth2 client credentials (accounts made since 2025 use API clients
 * instead of passwords). The token lasts 30 minutes and is refreshed a minute early.
 * [onCredits] receives the `X-Rate-Limit-Remaining` header so Settings can show the budget.
 */
class OpenSkyHistorySource(
    private val client: HttpClient,
    private val credentials: suspend () -> OpenSkyCredentials?,
    private val onCredits: suspend (remaining: Int?) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
    private val baseUrl: String = "https://opensky-network.org/api",
    private val tokenUrl: String = "https://auth.opensky-network.org/auth/realms/opensky-network/protocol/openid-connect/token",
) : FlightHistorySource {
    private val tokenLock = Mutex()
    private var token: String? = null
    private var tokenExpiresAt = 0L

    override suspend fun flightsByAircraft(icao24: String, beginSec: Long, endSec: Long) =
        get("$baseUrl/flights/aircraft?icao24=${icao24.lowercase()}&begin=$beginSec&end=$endSec")

    override suspend fun arrivals(airportIcao: String, beginSec: Long, endSec: Long) =
        get("$baseUrl/flights/arrival?airport=${airportIcao.uppercase()}&begin=$beginSec&end=$endSec")

    override suspend fun departures(airportIcao: String, beginSec: Long, endSec: Long) =
        get("$baseUrl/flights/departure?airport=${airportIcao.uppercase()}&begin=$beginSec&end=$endSec")

    private suspend fun get(url: String): List<HistoricFlight> {
        val bearer = token() ?: throw OpenSkyException("No OpenSky account in Settings")
        val r = client.get(url) { header(HttpHeaders.Authorization, "Bearer $bearer") }
        onCredits(r.headers["X-Rate-Limit-Remaining"]?.toIntOrNull())
        return when {
            // No flights in the interval.
            r.status == HttpStatusCode.NotFound -> emptyList()
            r.status == HttpStatusCode.TooManyRequests ->
                throw OpenSkyException("OpenSky credits used up", r.headers["X-Rate-Limit-Retry-After-Seconds"]?.toLongOrNull())
            r.status == HttpStatusCode.Unauthorized -> {
                tokenLock.withLock { token = null }
                throw OpenSkyException("OpenSky rejected the account (HTTP 401)")
            }
            !r.status.isSuccess() -> throw OpenSkyException("OpenSky returned HTTP ${r.status.value}")
            // Some rows come back with null times; they can't be used for a turnaround, so skip them.
            else -> r.body<List<RawFlight>>().mapNotNull { it.toFlight() }
        }
    }

    private suspend fun token(): String? = tokenLock.withLock {
        token?.takeIf { clock() < tokenExpiresAt }?.let { return it }
        val creds = credentials() ?: return null
        val r = client.submitForm(
            tokenUrl,
            parameters {
                append("grant_type", "client_credentials")
                append("client_id", creds.clientId)
                append("client_secret", creds.clientSecret)
            },
        )
        if (!r.status.isSuccess()) throw OpenSkyException("OpenSky sign-in failed (HTTP ${r.status.value})")
        val body: TokenResponse = r.body()
        token = body.accessToken
        tokenExpiresAt = clock() + (body.expiresIn - 60).coerceAtLeast(60) * 1000L
        body.accessToken
    }

    @Serializable
    private data class RawFlight(
        val icao24: String? = null,
        val callsign: String? = null,
        val estDepartureAirport: String? = null,
        val estArrivalAirport: String? = null,
        val firstSeen: Long? = null,
        val lastSeen: Long? = null,
    ) {
        fun toFlight(): HistoricFlight? = HistoricFlight(
            icao24 ?: return null, callsign, estDepartureAirport, estArrivalAirport,
            firstSeen ?: return null, lastSeen ?: return null,
        )
    }

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in") val expiresIn: Long = 1800,
    )
}

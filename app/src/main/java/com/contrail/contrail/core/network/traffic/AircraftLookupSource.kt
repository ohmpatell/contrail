package com.contrail.contrail.core.network.traffic

import com.contrail.contrail.core.data.model.LiveAircraft
import com.contrail.contrail.core.network.HostThrottles
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess

/** Finds one aircraft live: your flight by its ICAO callsign, or your aircraft by registration. */
interface AircraftLookupSource {
    val name: String
    suspend fun byCallsign(callsign: String): List<LiveAircraft>
    suspend fun byRegistration(registration: String): List<LiveAircraft>
}

/** readsb-style lookups (adsb.fi, adsb.lol), sharing each host's throttle with the radar. */
class ReadsbLookupSource(
    override val name: String,
    private val client: HttpClient,
    private val callsignUrl: (String) -> String,
    private val registrationUrl: (String) -> String,
    private val clock: () -> Long = System::currentTimeMillis,
) : AircraftLookupSource {
    private val throttle = HostThrottles.of(name)

    override suspend fun byCallsign(callsign: String) = fetch(callsignUrl(callsign.trim().uppercase().encodeURLPathPart()))

    override suspend fun byRegistration(registration: String) = fetch(registrationUrl(registration.trim().uppercase().encodeURLPathPart()))

    private suspend fun fetch(url: String): List<LiveAircraft> {
        val response = throttle.run { client.get(url) }
        if (!response.status.isSuccess()) throw LiveTrafficException("$name returned HTTP ${response.status.value}")
        val now = clock()
        return response.body<ReadsbResponse>().ac.orEmpty().mapNotNull { it.toLiveAircraft(now, name) }
    }

    companion object {
        fun adsbFi(client: HttpClient) = ReadsbLookupSource(
            "adsb.fi", client,
            callsignUrl = { "https://opendata.adsb.fi/api/v2/callsign/$it" },
            registrationUrl = { "https://opendata.adsb.fi/api/v2/registration/$it" },
        )

        fun adsbLol(client: HttpClient) = ReadsbLookupSource(
            "adsb.lol", client,
            callsignUrl = { "https://api.adsb.lol/v2/callsign/$it" },
            registrationUrl = { "https://api.adsb.lol/v2/reg/$it" },
        )
    }
}

/** Asks each provider in turn until one sees the aircraft. */
class FailoverLookupSource(private val providers: List<AircraftLookupSource>) : AircraftLookupSource {
    override val name: String get() = providers.first().name

    override suspend fun byCallsign(callsign: String) = first { it.byCallsign(callsign) }

    override suspend fun byRegistration(registration: String) = first { it.byRegistration(registration) }

    private suspend fun first(block: suspend (AircraftLookupSource) -> List<LiveAircraft>): List<LiveAircraft> {
        var error: Throwable? = null
        for (p in providers) {
            try {
                val found = block(p)
                if (found.isNotEmpty()) return found
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = e
            }
        }
        if (error != null) throw error
        return emptyList()
    }
}

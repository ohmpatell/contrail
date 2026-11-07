package com.contrail.contrail.core.network.schedule

import com.contrail.contrail.core.network.RequestThrottle
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

// ---------------------------------------------------------------- what the app uses

/** One line on a departures or arrivals board. [other] is the destination (departures) or origin (arrivals). */
data class BoardRow(
    val number: String,
    val airlineIata: String?,
    val airlineName: String?,
    val otherIata: String?,
    val otherName: String?,
    val scheduled: Instant,
    /** The airline's latest estimate, when it differs from the timetable. */
    val revised: Instant?,
    /** When it actually left or touched down (from the runway), once known. */
    val runway: Instant?,
    val terminal: String?,
    val gate: String?,
    val status: String,
    val aircraftModel: String?,
    val registration: String?,
    val callsign: String?,
) {
    val delayMinutes: Long get() = revised?.let { Duration.between(scheduled, it).toMinutes() } ?: 0
    val best: Instant get() = runway ?: revised ?: scheduled
}

data class AirportBoard(val iata: String, val fetchedAtMs: Long, val departures: List<BoardRow>, val arrivals: List<BoardRow>)

/** One end of your flight according to the airline: times, terminal, gate. */
data class StatusEnd(val iata: String?, val scheduled: Instant?, val revised: Instant?, val runway: Instant?, val terminal: String?, val gate: String?)

data class FlightStatus(
    val number: String,
    val status: String,
    val departure: StatusEnd,
    val arrival: StatusEnd,
    val registration: String?,
    val aircraftModel: String?,
)

/** Units left this month, from the response headers. */
data class UnitsHeader(val limit: Int?, val remaining: Int?, val resetAtMs: Long?)

/** Timetables and airport boards. Behind an interface so a missing key or a test can swap it. */
interface ScheduleSource {
    val configured: Boolean
    /** Raw JSON for the last 2 and next 10 hours of departures and arrivals; costs 2 units. */
    suspend fun board(iata: String): String?
    /** A flight by number on a local date, costs 2 units. More than one result means it runs twice that day. */
    suspend fun flight(number: String, date: LocalDate): List<FlightStatus>
}

// ---------------------------------------------------------------- AeroDataBox on RapidAPI

@Serializable data class AdbTime(val utc: String? = null, val local: String? = null)
@Serializable data class AdbAirport(val icao: String? = null, val iata: String? = null, val name: String? = null, val timeZone: String? = null)
@Serializable data class AdbAircraft(val reg: String? = null, val modeS: String? = null, val model: String? = null)
@Serializable data class AdbAirline(val name: String? = null, val iata: String? = null, val icao: String? = null)

@Serializable
data class AdbMovement(
    val airport: AdbAirport? = null,
    val scheduledTime: AdbTime? = null,
    val revisedTime: AdbTime? = null,
    val runwayTime: AdbTime? = null,
    val terminal: String? = null,
    val gate: String? = null,
)

@Serializable
data class AdbFlight(
    val movement: AdbMovement? = null,
    val departure: AdbMovement? = null,
    val arrival: AdbMovement? = null,
    val number: String? = null,
    val callSign: String? = null,
    val status: String? = null,
    val codeshareStatus: String? = null,
    val isCargo: Boolean = false,
    val aircraft: AdbAircraft? = null,
    val airline: AdbAirline? = null,
)

@Serializable
data class AdbBoard(val departures: List<AdbFlight> = emptyList(), val arrivals: List<AdbFlight> = emptyList())

/**
 * AeroDataBox through RapidAPI (free Basic plan: 400 units a month, hard limit, attribution
 * required, results may be kept up to 7 days). Every response's unit headers are reported to
 * [onUnits] so Settings can show what's left and boards stop before the month runs dry.
 */
class AeroDataBoxSource(
    private val client: HttpClient,
    private val json: Json,
    private val key: suspend () -> String?,
    private val onUnits: suspend (UnitsHeader) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
    private val throttle: RequestThrottle = RequestThrottle(1_100),
) : ScheduleSource {
    override val configured: Boolean = true

    private suspend fun call(path: String, params: Map<String, String>): HttpResponse? {
        val k = key()?.takeIf { it.isNotBlank() } ?: return null
        return throttle.run {
            client.get("https://$HOST$path") {
                header("X-RapidAPI-Key", k)
                header("X-RapidAPI-Host", HOST)
                params.forEach { (n, v) -> parameter(n, v) }
            }
        }.also { r -> onUnits(units(r)) }
    }

    private fun units(r: HttpResponse): UnitsHeader = UnitsHeader(
        r.headers["X-RateLimit-API-Units-Limit"]?.toIntOrNull(),
        r.headers["X-RateLimit-API-Units-Remaining"]?.toIntOrNull(),
        r.headers["X-RateLimit-API-Units-Reset"]?.toLongOrNull()?.let { now() + it * 1000 },
    )

    override suspend fun board(iata: String): String? {
        val r = call(
            "/flights/airports/iata/${iata.uppercase()}",
            mapOf(
                "offsetMinutes" to "-120", "durationMinutes" to "720", "direction" to "Both",
                "withLeg" to "false", "withCancelled" to "true", "withCodeshared" to "false",
                "withCargo" to "false", "withPrivate" to "false", "withLocation" to "false",
            ),
        ) ?: return null
        if (!r.status.isSuccess()) return null
        return r.bodyAsText()
    }

    override suspend fun flight(number: String, date: LocalDate): List<FlightStatus> {
        val r = call(
            "/flights/number/${number.replace(" ", "")}/$date",
            mapOf("withAircraftImage" to "false", "withLocation" to "false", "dateLocalRole" to "Departure"),
        ) ?: return emptyList()
        // 204: no such flight that day.
        if (!r.status.isSuccess() || r.status.value == 204) return emptyList()
        return parseFlights(json, r.bodyAsText())
    }

    companion object {
        const val HOST = "aerodatabox.p.rapidapi.com"
        const val ATTRIBUTION = "Flight data by AeroDataBox"
        const val ATTRIBUTION_URL = "https://aerodatabox.com"

        private val UTC = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mmX")

        fun instant(t: AdbTime?): Instant? = t?.utc?.let { runCatching { OffsetDateTime.parse(it, UTC).toInstant() }.getOrNull() }

        fun parseBoard(json: Json, iata: String, body: String, nowMs: Long): AirportBoard {
            val b = json.decodeFromString<AdbBoard>(body)
            fun rows(list: List<AdbFlight>) = list.filter { !it.isCargo && it.codeshareStatus != "IsCodeshared" }.mapNotNull { f ->
                val m = f.movement ?: return@mapNotNull null
                BoardRow(
                    number = f.number?.trim() ?: return@mapNotNull null,
                    airlineIata = f.airline?.iata,
                    airlineName = f.airline?.name,
                    otherIata = m.airport?.iata,
                    otherName = m.airport?.name,
                    scheduled = instant(m.scheduledTime) ?: return@mapNotNull null,
                    revised = instant(m.revisedTime),
                    runway = instant(m.runwayTime),
                    terminal = m.terminal,
                    gate = m.gate,
                    status = f.status ?: "Unknown",
                    aircraftModel = f.aircraft?.model,
                    registration = f.aircraft?.reg,
                    callsign = f.callSign,
                )
            }.sortedBy { it.scheduled }
            return AirportBoard(iata, nowMs, rows(b.departures), rows(b.arrivals))
        }

        fun parseFlights(json: Json, body: String): List<FlightStatus> =
            json.decodeFromString<List<AdbFlight>>(body).map { f ->
                fun end(m: AdbMovement?) = StatusEnd(m?.airport?.iata, instant(m?.scheduledTime), instant(m?.revisedTime), instant(m?.runwayTime), m?.terminal, m?.gate)
                FlightStatus(f.number.orEmpty(), f.status ?: "Unknown", end(f.departure), end(f.arrival), f.aircraft?.reg, f.aircraft?.model)
            }

        /** Statuses AeroDataBox uses, in words for the board. */
        fun label(status: String): String = when (status) {
            "Expected" -> "On time"
            "EnRoute" -> "In the air"
            "CheckIn" -> "Check-in"
            "Boarding" -> "Boarding"
            "GateClosed" -> "Gate closed"
            "Departed" -> "Departed"
            "Delayed" -> "Delayed"
            "Approaching" -> "Landing"
            "Arrived" -> "Landed"
            "Canceled", "CanceledUncertain" -> "Cancelled"
            "Diverted" -> "Diverted"
            else -> status
        }
    }
}

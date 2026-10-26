package com.contrail.contrail.core.data.trip

import com.contrail.contrail.core.data.reference.Airlines
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

enum class Cabin(val label: String) {
    First("First"),
    Business("Business"),
    PremiumEconomy("Premium Economy"),
    Economy("Economy"),
}

enum class SeatType(val label: String) { Window("Window"), Middle("Middle"), Aisle("Aisle") }

enum class FlightReason(val label: String) { Leisure("Leisure"), Business("Business"), Crew("Crew"), Other("Other") }

/** One flight of a trip. Times are local to each airport, as printed on the ticket. */
data class FlightSegment(
    val id: Long,
    val tripId: Long,
    val ordinal: Int,
    val airlineIata: String,
    /** Digits plus an optional suffix letter, without leading zeros ("140", "15"). */
    val flightNumber: String,
    val origin: String,
    val destination: String,
    val departureLocal: LocalDateTime,
    val arrivalLocal: LocalDateTime,
    val departureZone: ZoneId,
    val arrivalZone: ZoneId,
    val departureTerminal: String? = null,
    val arrivalTerminal: String? = null,
    val gate: String? = null,
    val cabin: Cabin? = null,
    val fareClass: String? = null,
    val seat: String? = null,
    /** ICAO type designator from the ticket ("B789"), until a tail is known. */
    val aircraftType: String? = null,
    /** Registration you entered by hand ("A6-BLT"). */
    val tail: String? = null,
    val operatedBy: String? = null,
    /** The airline's name from an import, for airlines outside the curated list. */
    val airlineName: String? = null,
    val seatType: SeatType? = null,
    val reason: FlightReason? = null,
    val note: String? = null,
    /** No times were given, so they're worked out from the distance. Shown as "estimated". */
    val timesEstimated: Boolean = false,
) {
    val departure: Instant get() = departureLocal.atZone(departureZone).toInstant()
    val arrival: Instant get() = arrivalLocal.atZone(arrivalZone).toInstant()
    val duration: Duration get() = Duration.between(departure, arrival)
    val designator: String get() = "$airlineIata $flightNumber".trim()

    /** The ATC callsign ADS-B feeds use: EY 141 → ETD141. Null for airlines we can't map. */
    val icaoCallsign: String? get() = Airlines.callsignFor(airlineIata, flightNumber)
}

enum class ConnectionKind { Layover, Stopover }

enum class ConnectionLength(val label: String) { Tight("Tight"), Comfortable("Comfortable"), Long("Long") }

/** The time on the ground between two segments. */
data class Connection(
    val inbound: FlightSegment,
    val outbound: FlightSegment,
    val kind: ConnectionKind,
    /** True when [kind] is the automatic guess rather than your choice. */
    val guessed: Boolean,
    val hotel: Boolean,
) {
    val airport: String get() = inbound.destination
    val duration: Duration get() = Duration.between(inbound.arrival, outbound.departure)
    val length: ConnectionLength get() = ConnectionRules.length(duration)

    /** "T1" → "T3" when you change terminals, null when you stay or the ticket doesn't say. */
    val terminalChange: Pair<String, String>?
        get() {
            val a = inbound.arrivalTerminal ?: return null
            val b = outbound.departureTerminal ?: return null
            return if (a.equals(b, ignoreCase = true)) null else a to b
        }

    /** Arriving at one airport and leaving from another (e.g. LHR → LGW). */
    val changesAirport: Boolean get() = inbound.destination != outbound.origin
}

object ConnectionRules {
    val TIGHT_UNDER: Duration = Duration.ofMinutes(90)
    val LONG_OVER: Duration = Duration.ofHours(4)
    private val STOPOVER_OVER: Duration = Duration.ofHours(24)
    private val NIGHT_START: LocalTime = LocalTime.of(1, 0)
    private val NIGHT_END: LocalTime = LocalTime.of(5, 0)

    fun length(d: Duration): ConnectionLength = when {
        d < TIGHT_UNDER -> ConnectionLength.Tight
        d > LONG_OVER -> ConnectionLength.Long
        else -> ConnectionLength.Comfortable
    }

    /** Over 24 hours, or overnight with a hotel, is a stopover; anything else is a layover. */
    fun guess(inbound: FlightSegment, outbound: FlightSegment, hotel: Boolean): ConnectionKind {
        val d = Duration.between(inbound.arrival, outbound.departure)
        if (d > STOPOVER_OVER) return ConnectionKind.Stopover
        if (hotel && isOvernight(inbound.arrivalLocal, outbound.departure.atZone(inbound.arrivalZone).toLocalDateTime())) {
            return ConnectionKind.Stopover
        }
        return ConnectionKind.Layover
    }

    /** The connection covers 01:00–05:00 local time on some night. */
    fun isOvernight(arrival: LocalDateTime, departure: LocalDateTime): Boolean {
        var day: LocalDate = arrival.toLocalDate()
        while (!day.isAfter(departure.toLocalDate())) {
            val start = day.atTime(NIGHT_START)
            val end = day.atTime(NIGHT_END)
            if (!arrival.isAfter(start) && !departure.isBefore(end)) return true
            day = day.plusDays(1)
        }
        return false
    }

    fun connections(segments: List<FlightSegment>, prefs: Map<Long, ConnectionPref>): List<Connection> =
        segments.zipWithNext { a, b ->
            val pref = prefs[a.id]
            val hotel = pref?.hotel ?: false
            val override = pref?.kind
            Connection(a, b, override ?: guess(a, b, hotel), guessed = override == null, hotel = hotel)
        }
}

/** Your choices for the connection after segment [inboundSegmentId]. */
data class ConnectionPref(val inboundSegmentId: Long, val kind: ConnectionKind?, val hotel: Boolean)

data class Trip(
    val id: Long,
    val title: String,
    val pnr: String?,
    val passengerName: String?,
    val segments: List<FlightSegment>,
    val connections: List<Connection>,
) {
    val first: FlightSegment get() = segments.first()
    val last: FlightSegment get() = segments.last()
    val start: Instant get() = first.departure
    val end: Instant get() = last.arrival

    fun isPast(now: Instant): Boolean = end.isBefore(now)

    /** The next segment that hasn't landed yet. */
    fun nextSegment(now: Instant): FlightSegment? = segments.firstOrNull { it.arrival.isAfter(now) }

    fun connectionAfter(segmentId: Long): Connection? = connections.firstOrNull { it.inbound.id == segmentId }

    companion object {
        /** "Toronto to Manila" from the first origin and final destination cities. */
        fun defaultTitle(originCity: String?, destinationCity: String?, origin: String, destination: String): String =
            "${originCity ?: origin} to ${destinationCity ?: destination}"
    }
}

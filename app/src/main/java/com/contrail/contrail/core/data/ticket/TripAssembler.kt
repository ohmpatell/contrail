package com.contrail.contrail.core.data.ticket

import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.data.trip.FlightSegment
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** What import needs to know about an airport. */
data class AirportInfo(
    val iata: String,
    val icao: String,
    val city: String,
    val countryIso: String,
    val zone: ZoneId,
    val lat: Double,
    val lon: Double,
)

fun interface AirportDirectory {
    fun find(iata: String): AirportInfo?
}

/** Turns reviewed drafts into segments, and segments into trips. */
object TripAssembler {
    private val MIN_FLIGHT: Duration = Duration.ofMinutes(20)
    private val MAX_FLIGHT: Duration = Duration.ofHours(22)
    private val RETURN_GAP: Duration = Duration.ofHours(24)
    private val ALWAYS_SPLIT: Duration = Duration.ofDays(14)

    /** A complete segment from a draft, or null while required fields are missing or unknown. */
    fun resolve(d: SegmentDraft, airports: AirportDirectory, ordinal: Int = 0): FlightSegment? {
        if (d.missing.isNotEmpty()) return null
        val from = airports.find(d.origin!!) ?: return null
        val to = airports.find(d.destination!!) ?: return null
        val dep = LocalDateTime.of(d.date!!, d.departureTime!!)
        val arrDate = d.arrivalDate ?: arrivalDate(d.date, d.departureTime, d.arrivalTime!!, from.zone, to.zone)
        return FlightSegment(
            id = 0, tripId = 0, ordinal = ordinal,
            airlineIata = d.airlineIata!!.uppercase(),
            flightNumber = d.flightNumber!!.uppercase().trimStart('0'),
            origin = from.iata, destination = to.iata,
            departureLocal = dep,
            arrivalLocal = LocalDateTime.of(arrDate, d.arrivalTime!!),
            departureZone = from.zone, arrivalZone = to.zone,
            departureTerminal = d.departureTerminal, arrivalTerminal = d.arrivalTerminal, gate = d.gate,
            cabin = d.cabin, fareClass = d.fareClass, seat = d.seat,
            aircraftType = d.aircraftType, operatedBy = d.operatedBy,
        )
    }

    /**
     * Tickets usually print only local times. The arrival is on the first day (0 to 2 days
     * after departure) that gives a plausible flight time once both time zones are applied.
     */
    fun arrivalDate(date: LocalDate, dep: java.time.LocalTime, arr: java.time.LocalTime, depZone: ZoneId, arrZone: ZoneId): LocalDate {
        val departure = LocalDateTime.of(date, dep).atZone(depZone).toInstant()
        for (k in 0L..2L) {
            val candidate = date.plusDays(k)
            val arrival = LocalDateTime.of(candidate, arr).atZone(arrZone).toInstant()
            val d = Duration.between(departure, arrival)
            if (d >= MIN_FLIGHT && d <= MAX_FLIGHT) return candidate
        }
        // Crossing the date line eastbound can land on the local day before take-off.
        val dayBefore = date.minusDays(1)
        val d = Duration.between(departure, LocalDateTime.of(dayBefore, arr).atZone(arrZone).toInstant())
        if (d >= MIN_FLIGHT && d <= MAX_FLIGHT) return dayBefore
        return if (arr.isBefore(dep)) date.plusDays(1) else date
    }

    /**
     * Where a new trip starts: before a segment that heads back toward the trip's origin
     * after more than a day on the ground (the return of a round trip), or after two weeks
     * on the ground anywhere. A stopover on the way (Abu Dhabi en route to Delhi) keeps
     * heading away from home, so it stays in the trip.
     */
    fun autoSplits(segments: List<FlightSegment>, airports: AirportDirectory): Set<Int> {
        val splits = HashSet<Int>()
        var tripOrigin = segments.firstOrNull()?.origin ?: return splits
        for (i in 1 until segments.size) {
            val prev = segments[i - 1]
            val next = segments[i]
            val gap = Duration.between(prev.arrival, next.departure)
            val home = airports.find(tripOrigin)
            val a = airports.find(next.origin)
            val b = airports.find(next.destination)
            val headsHome = home != null && a != null && b != null &&
                Geo.distanceM(b.lat, b.lon, home.lat, home.lon) < Geo.distanceM(a.lat, a.lon, home.lat, home.lon)
            if (gap > ALWAYS_SPLIT || (gap > RETURN_GAP && headsHome) || prev.destination != next.origin && gap > RETURN_GAP) {
                splits += i
                tripOrigin = next.origin
            }
        }
        return splits
    }

    /** Splits [segments] (in time order) before each index in [splits]. */
    fun group(segments: List<FlightSegment>, splits: Set<Int>): List<List<FlightSegment>> {
        val out = ArrayList<MutableList<FlightSegment>>()
        segments.forEachIndexed { i, s ->
            if (i == 0 || i in splits) out += mutableListOf<FlightSegment>()
            out.last() += s
        }
        return out
    }

    fun title(trip: List<FlightSegment>, airports: AirportDirectory): String {
        val first = trip.first().origin
        val last = trip.last().destination
        return com.contrail.contrail.core.data.trip.Trip.defaultTitle(airports.find(first)?.city, airports.find(last)?.city, first, last)
    }
}

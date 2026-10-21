package com.contrail.contrail.testing

import com.contrail.contrail.core.data.ticket.AirportInfo
import com.contrail.contrail.core.data.trip.Cabin
import com.contrail.contrail.core.data.trip.ConnectionPref
import com.contrail.contrail.core.data.trip.ConnectionRules
import com.contrail.contrail.core.data.trip.FlightSegment
import com.contrail.contrail.core.data.trip.Trip
import java.time.Instant
import java.time.LocalDateTime

/** Realistic made-up trips for tests and screenshots. "Now" is Oct 21, 2026, 12:00 UTC. */
object TripFixtures {
    val now: Instant = Instant.parse("2026-10-21T12:00:00Z")

    private val places: Map<String, AirportInfo> = TicketSamples.airports

    private fun seg(
        id: Long, trip: Long, ordinal: Int, airline: String, number: String, from: String, to: String,
        dep: String, arr: String, depT: String? = null, arrT: String? = null, type: String? = null,
        seat: String? = null, cabin: Cabin? = Cabin.Business, fare: String? = null, tail: String? = null, gate: String? = null,
    ) = FlightSegment(
        id, trip, ordinal, airline, number, from, to, LocalDateTime.parse(dep), LocalDateTime.parse(arr),
        places.getValue(from).zone, places.getValue(to).zone, depT, arrT, gate, cabin, fare, seat, type, tail,
    )

    private fun trip(id: Long, title: String, pnr: String?, segments: List<FlightSegment>, prefs: Map<Long, ConnectionPref> = emptyMap()) =
        Trip(id, title, pnr, "Ohm Patel", segments, ConnectionRules.connections(segments, prefs))

    /** Toronto to Manila with Cathay Pacific, a layover in Hong Kong. */
    val manila = trip(
        1, "Toronto to Manila", "6XJ4PD",
        listOf(
            seg(11, 1, 0, "CX", "829", "YYZ", "HKG", "2026-12-19T01:15", "2026-12-20T05:00", "3", "1", "A35K", "15A", fare = "I", gate = "E71"),
            seg(12, 1, 1, "CX", "907", "HKG", "MNL", "2026-12-20T08:35", "2026-12-20T10:45", "1", "3", "A333", "11K", fare = "I"),
        ),
    )

    /** Toronto to Delhi with Etihad, two nights in Abu Dhabi on the way. */
    val delhi = trip(
        2, "Toronto to New Delhi", "K7Q2LM",
        listOf(
            seg(21, 2, 0, "EY", "140", "YYZ", "AUH", "2026-11-12T22:10", "2026-11-13T19:55", "3", "A", "B789", "7K", fare = "Z", tail = "A6-BLT"),
            seg(22, 2, 1, "EY", "218", "AUH", "DEL", "2026-11-15T02:35", "2026-11-15T07:50", "A", "3", "A21N", "3A", fare = "Z"),
        ),
        prefs = mapOf(21L to ConnectionPref(21, null, hotel = true)),
    )

    /** Past flying, for the Passport. */
    val vancouver = trip(
        3, "Toronto to Vancouver", "AB12CD",
        listOf(seg(31, 3, 0, "AC", "103", "YYZ", "YVR", "2026-03-17T08:00", "2026-03-17T10:21", "1", null, "B789", "32A", Cabin.Economy, "K", tail = "C-FGDZ")),
    )
    val vancouverBack = trip(
        4, "Vancouver to Toronto", "AB12CD",
        listOf(seg(41, 4, 0, "AC", "126", "YVR", "YYZ", "2026-03-22T23:45", "2026-03-23T07:15", null, "1", "A333", "28C", Cabin.Economy, "K")),
    )
    val mumbai = trip(
        5, "Toronto to Mumbai", "QX7T2B",
        listOf(
            seg(51, 5, 0, "AC", "856", "YYZ", "LHR", "2026-06-02T21:30", "2026-06-03T09:30", "1", "2", "B77W", "4A", Cabin.Business, "J", tail = "C-FIVQ"),
            seg(52, 5, 1, "AI", "130", "LHR", "BOM", "2026-06-03T12:05", "2026-06-04T02:45", "2", "2", "B788", "2A", Cabin.Business, "C"),
        ),
    )
    val hongKongPast = trip(
        6, "Toronto to Hong Kong", "8DKP2L",
        listOf(seg(61, 6, 0, "CX", "827", "YYZ", "HKG", "2026-08-09T23:55", "2026-08-11T03:45", "3", "1", "B77W", "40K", Cabin.PremiumEconomy, "R", tail = "B-KQA")),
    )

    val upcoming = listOf(delhi, manila)
    val past = listOf(hongKongPast, mumbai, vancouverBack, vancouver)
    val all = past + upcoming

    fun placesFor(trips: List<Trip>): Map<String, AirportInfo> =
        trips.flatMap { t -> t.segments.flatMap { listOf(it.origin, it.destination) } }.toSet().associateWith { places.getValue(it) }
}

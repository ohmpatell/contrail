package com.contrail.contrail.feature

import com.contrail.contrail.core.data.ticket.AirportInfo
import com.contrail.contrail.core.data.trip.ConnectionRules
import com.contrail.contrail.core.data.trip.FlightSegment
import com.contrail.contrail.core.data.trip.Trip
import com.contrail.contrail.feature.passport.StampKind
import com.contrail.contrail.feature.passport.StampRules
import com.contrail.contrail.feature.passport.StampStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

private fun place(code: String, city: String, iso: String, zone: String) = AirportInfo(code, "X$code", city, iso, ZoneId.of(zone), 0.0, 0.0)

private val places = listOf(
    place("YYZ", "Toronto", "CA", "America/Toronto"),
    place("YVR", "Vancouver", "CA", "America/Vancouver"),
    place("FRA", "Frankfurt", "DE", "Europe/Berlin"),
    place("MUC", "Munich", "DE", "Europe/Berlin"),
    place("CDG", "Paris", "FR", "Europe/Paris"),
    place("HKG", "Hong Kong", "HK", "Asia/Hong_Kong"),
    place("MNL", "Manila", "PH", "Asia/Manila"),
).associateBy { it.iata }

private var nextId = 1L
private fun seg(from: String, to: String, dep: String, arr: String) = FlightSegment(
    nextId++, 0, 0, "XX", "1", from, to, LocalDateTime.parse(dep), LocalDateTime.parse(arr),
    places.getValue(from).zone, places.getValue(to).zone,
)

private fun trip(vararg s: FlightSegment) = Trip(s.first().id, "", null, null, s.toList(), ConnectionRules.connections(s.toList(), emptyMap()))

class StampRulesTest {
    private fun stamps(vararg trips: Trip) = StampRules.stamps(trips.toList(), trips.flatMap { t -> t.segments.map { it.id } }.toSet(), places)

    @Test
    fun `crossing borders gives an exit where you left and an entry where you landed`() {
        val s = stamps(
            trip(seg("YYZ", "FRA", "2025-06-01T18:00", "2025-06-02T08:00")),
            trip(seg("FRA", "YYZ", "2025-06-10T11:00", "2025-06-10T14:00")),
        )
        assertEquals(
            listOf("YYZ" to StampKind.Exit, "FRA" to StampKind.Entry, "FRA" to StampKind.Exit, "YYZ" to StampKind.Entry),
            s.map { it.airport to it.kind },
        )
    }

    @Test
    fun `no stamps inside Schengen, just a cachet for each new airport`() {
        val s = stamps(trip(seg("MUC", "CDG", "2025-07-01T09:00", "2025-07-01T10:30")), trip(seg("CDG", "FRA", "2025-07-05T09:00", "2025-07-05T10:15")))
        assertFalse(s.any { it.kind == StampKind.Entry || it.kind == StampKind.Exit })
        assertEquals(listOf("MUC", "CDG", "FRA"), s.map { it.airport })
    }

    @Test
    fun `a layover stays airside with a transit mark`() {
        val s = stamps(
            trip(
                seg("YYZ", "HKG", "2026-12-19T01:15", "2026-12-20T05:00"),
                seg("HKG", "MNL", "2026-12-20T08:35", "2026-12-20T10:45"),
            ),
        )
        assertEquals(
            listOf("YYZ" to StampKind.Exit, "HKG" to StampKind.Transit, "MNL" to StampKind.Entry),
            s.map { it.airport to it.kind },
        )
    }

    @Test
    fun `a domestic connection still leaves the country at the connecting airport`() {
        val s = stamps(
            trip(
                seg("YVR", "YYZ", "2026-03-01T07:00", "2026-03-01T14:30"),
                seg("YYZ", "FRA", "2026-03-01T17:00", "2026-03-02T07:00"),
            ),
        )
        assertEquals(listOf("YYZ" to StampKind.Exit, "FRA" to StampKind.Entry), s.filter { it.kind != StampKind.Visit }.map { it.airport to it.kind })
    }

    @Test
    fun `regional families`() {
        assertEquals(StampStyle.Schengen, StampRules.styleFor("DE"))
        assertEquals(StampStyle.Admitted, StampRules.styleFor("US"))
        assertEquals(StampStyle.Band, StampRules.styleFor("HK"))
        assertEquals(StampStyle.Hexagon, StampRules.styleFor("AE"))
        assertEquals(StampStyle.Round, StampRules.styleFor("CA"))
    }
}

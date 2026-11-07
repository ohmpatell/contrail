package com.contrail.contrail.feature

import com.contrail.contrail.core.data.trip.FlightPhase
import com.contrail.contrail.core.data.trip.FlightProgress
import com.contrail.contrail.core.data.trip.FlightSegment
import com.contrail.contrail.feature.trips.live.LiveFlightNotification
import com.contrail.contrail.feature.trips.live.LiveFlightUpdates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class LiveFlightUpdatesTest {
    private val utc = ZoneId.of("UTC")
    private fun seg(id: Long, dep: Int, arr: Int, from: String, to: String) = FlightSegment(
        id = id, tripId = 1, ordinal = id.toInt(), airlineIata = "EY", flightNumber = "$id",
        origin = from, destination = to,
        departureLocal = LocalDateTime.of(2026, 11, 7, dep, 0), arrivalLocal = LocalDateTime.of(2026, 11, 7, arr, 0),
        departureZone = utc, arrivalZone = utc, gate = "B12", seat = "32K",
    )
    private fun at(h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, 7, h, m).atZone(utc).toInstant()

    // A connection: AUH → DOH lands 11:00, DOH → LHR leaves 12:30.
    private val legs = listOf(seg(1, 9, 11, "AUH", "DOH"), seg(2, 12, 18, "DOH", "LHR"))

    @Test fun followsTheNextLegOnceItOpens() {
        assertNull(LiveFlightUpdates.current(legs, at(5)))
        assertEquals(1L, LiveFlightUpdates.current(legs, at(10))?.id)
        // First leg has landed and the second is within three hours: the second wins.
        assertEquals(2L, LiveFlightUpdates.current(legs, at(11, 10))?.id)
        assertEquals(2L, LiveFlightUpdates.upcoming(legs, at(11, 31))?.id)
        assertNull(LiveFlightUpdates.upcoming(legs, at(18, 31)))
    }

    @Test fun wordingPerPhase() {
        val s = legs[1]
        fun p(phase: FlightPhase, km: Double? = null) = FlightProgress(phase, 0.4f, at(17, 50), live = km != null, remainingKm = km)
        assertEquals("Departs 12:00 · Gate B12 · Seat 32K", LiveFlightNotification.text(s, p(FlightPhase.Upcoming), "London"))
        assertEquals("Boarding · departs 12:00 · Gate B12", LiveFlightNotification.text(s, p(FlightPhase.Boarding), "London"))
        assertEquals("Lands in London at 17:50 · 1,234 km to go", LiveFlightNotification.text(s, p(FlightPhase.Airborne, 1234.4), "London"))
        assertEquals("Landed in London", LiveFlightNotification.text(s, p(FlightPhase.Landed), "London"))
        assertEquals("1h 20m", LiveFlightNotification.chip(p(FlightPhase.Airborne), at(17, 50), at(16, 30)))
        assertEquals("Landed", LiveFlightNotification.chip(p(FlightPhase.Landed), at(17, 50), at(18, 0)))
    }
}

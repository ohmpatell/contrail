package com.contrail.contrail.core.data

import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.trip.FlightPhase
import com.contrail.contrail.core.data.trip.FlightProgressCalc
import com.contrail.contrail.core.data.trip.FlightSegment
import com.contrail.contrail.testing.aircraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

class FlightProgressTest {
    private val utc = ZoneId.of("UTC")
    // YYZ → YVR, 5 h on the timetable.
    private val seg = FlightSegment(
        id = 1, tripId = 1, ordinal = 0, airlineIata = "AC", flightNumber = "115",
        origin = "YYZ", destination = "YVR",
        departureLocal = LocalDateTime.of(2026, 11, 7, 10, 0), arrivalLocal = LocalDateTime.of(2026, 11, 7, 15, 0),
        departureZone = utc, arrivalZone = utc,
    )
    private val yyz = LatLon(43.677, -79.630)
    private val yvr = LatLon(49.194, -123.184)
    private fun at(h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, 7, h, m).atZone(utc).toInstant()

    @Test fun timetablePhasesFollowTheClock() {
        assertEquals(FlightPhase.Upcoming, FlightProgressCalc.compute(seg, at(8)).phase)
        assertEquals(FlightPhase.Boarding, FlightProgressCalc.compute(seg, at(9, 30)).phase)
        assertEquals(FlightPhase.Departing, FlightProgressCalc.compute(seg, at(10, 5)).phase)
        val mid = FlightProgressCalc.compute(seg, at(12, 30))
        assertEquals(FlightPhase.Airborne, mid.phase)
        assertEquals(0.5f, mid.fraction, 0.01f)
        assertFalse(mid.live)
        assertEquals(FlightPhase.Landing, FlightProgressCalc.compute(seg, at(14, 50)).phase)
        assertEquals(FlightPhase.Landed, FlightProgressCalc.compute(seg, at(15, 10)).phase)
    }

    @Test fun windowRunsFromThreeHoursBeforeToHalfAnHourAfter() {
        assertFalse(FlightProgressCalc.isInWindow(seg, at(6, 59)))
        assertTrue(FlightProgressCalc.isInWindow(seg, at(7)))
        assertTrue(FlightProgressCalc.isInWindow(seg, at(15, 29)))
        assertFalse(FlightProgressCalc.isInWindow(seg, at(15, 30)))
    }

    @Test fun livePositionDrivesFractionAndEta() {
        // Over Winnipeg, roughly 45% of the way, 480 kt.
        val ac = aircraft(lat = 49.9, lon = -97.2, groundSpeedKt = 480f)
        val p = FlightProgressCalc.compute(seg, at(12), yyz, yvr, ac)
        assertTrue(p.live)
        assertEquals(FlightPhase.Airborne, p.phase)
        assertTrue(p.fraction in 0.35f..0.55f)
        // ~1,870 km left at 480 kt ≈ 2 h 06 m, plus 10 minutes.
        val left = Duration.between(at(12), p.eta).toMinutes()
        assertTrue("eta $left min", left in 125..145)
    }

    @Test fun descendingNearDestinationIsLanding() {
        val ac = aircraft(lat = 49.3, lon = -122.5, altitudeFt = 8_000, verticalRateFpm = -1_200, groundSpeedKt = 250f)
        assertEquals(FlightPhase.Landing, FlightProgressCalc.compute(seg, at(14, 40), yyz, yvr, ac).phase)
    }

    @Test fun onTheGroundAtEitherEnd() {
        val taxi = aircraft(lat = 43.68, lon = -79.63, onGround = true, groundSpeedKt = 12f, altitudeFt = null)
        assertEquals(FlightPhase.Departing, FlightProgressCalc.compute(seg, at(10, 5), yyz, yvr, taxi).phase)
        val parked = aircraft(lat = 49.195, lon = -123.18, onGround = true, groundSpeedKt = 0f, altitudeFt = null)
        val landed = FlightProgressCalc.compute(seg, at(14, 45), yyz, yvr, parked)
        assertEquals(FlightPhase.Landed, landed.phase)
        assertEquals(1f, landed.fraction)
    }

    @Test fun groundedElsewhereFallsBackToTimetable() {
        val elsewhere = aircraft(lat = 45.0, lon = -100.0, onGround = true, groundSpeedKt = 0f)
        assertFalse(FlightProgressCalc.compute(seg, at(12), yyz, yvr, elsewhere).live)
    }

    @Test fun shortDurations() {
        assertEquals("now", FlightProgressCalc.shortDuration(Duration.ofSeconds(20)))
        assertEquals("35m", FlightProgressCalc.shortDuration(Duration.ofMinutes(35)))
        assertEquals("2h 05m", FlightProgressCalc.shortDuration(Duration.ofMinutes(125)))
    }
}

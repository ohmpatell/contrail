package com.contrail.contrail.core.data

import com.contrail.contrail.core.data.repository.ScheduleRepository
import com.contrail.contrail.core.data.trip.ActualTimes
import com.contrail.contrail.core.data.trip.FlightPhase
import com.contrail.contrail.core.data.trip.FlightProgress
import com.contrail.contrail.core.database.SegmentEntity
import com.contrail.contrail.core.network.ContrailHttp
import com.contrail.contrail.core.network.schedule.AeroDataBoxSource
import com.contrail.contrail.core.network.schedule.FlightStatus
import com.contrail.contrail.core.network.schedule.StatusEnd
import com.contrail.contrail.testing.aircraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ScheduleTest {
    private val body = javaClass.classLoader!!.getResource("aerodatabox_fids_yyz.json")!!.readText()

    @Test fun parsesTheRealBoard() {
        val b = AeroDataBoxSource.parseBoard(ContrailHttp.json, "YYZ", body, 0)
        assertEquals(6, b.departures.size)
        val first = b.departures.first()
        assertEquals("DL 5256", first.number)
        assertEquals("DTW", first.otherIata)
        assertEquals("A6", first.gate)
        assertEquals("N904XJ", first.registration)
        assertEquals(Instant.parse("2026-11-07T17:10:00Z"), first.scheduled)
        assertEquals(Instant.parse("2026-11-07T17:23:00Z"), first.runway)
        assertTrue(b.arrivals.isNotEmpty())
        assertTrue(b.departures.zipWithNext().all { (a, c) -> !c.scheduled.isBefore(a.scheduled) })
    }

    @Test fun labelsReadLikeABoard() {
        assertEquals("On time", AeroDataBoxSource.label("Expected"))
        assertEquals("Landed", AeroDataBoxSource.label("Arrived"))
        assertEquals("Cancelled", AeroDataBoxSource.label("Canceled"))
    }

    private val seg = SegmentEntity(
        tripId = 1, ordinal = 0, airlineIata = "EY", flightNumber = "140", origin = "YYZ", destination = "AUH",
        departureLocal = "2026-11-12T22:10", arrivalLocal = "2026-11-13T19:55", departureZone = "America/Toronto", arrivalZone = "Asia/Dubai",
        departureUtcMs = Instant.parse("2026-11-13T03:10:00Z").toEpochMilli(), arrivalUtcMs = Instant.parse("2026-11-13T15:55:00Z").toEpochMilli(),
        departureTerminal = null, arrivalTerminal = null, gate = null, cabin = null, fareClass = null, seat = "7K",
        aircraftType = "B789", tail = null, operatedBy = null,
    )

    @Test fun airlineStatusFillsGateTailAndTimes() {
        val st = FlightStatus(
            "EY 140", "Delayed",
            StatusEnd("YYZ", null, Instant.parse("2026-11-13T03:40:00Z"), null, "3", "C32"),
            StatusEnd("AUH", null, Instant.parse("2026-11-13T16:20:00Z"), null, "A", null),
            "A6-BLT", "Boeing 787-9",
        )
        val out = ScheduleRepository.apply(seg, st, 99)
        assertEquals("C32", out.gate)
        assertEquals("3", out.departureTerminal)
        assertEquals("A6-BLT", out.tail)
        assertEquals("Delayed", out.liveStatus)
        assertEquals(Instant.parse("2026-11-13T03:40:00Z").toEpochMilli(), out.revisedDepartureUtcMs)
        assertEquals(99L, out.statusCheckedAtMs)
        // A tail you typed yourself isn't overwritten.
        assertEquals("A6-BLX", ScheduleRepository.apply(seg.copy(tail = "A6-BLX"), st, 99).tail)
    }

    @Test fun threeCheckpointsPerFlight() {
        val c = ScheduleRepository.checkpoints(seg)
        assertEquals(3, c.size)
        assertTrue(c[0] < seg.departureUtcMs && c[1] < seg.departureUtcMs && c[2] > seg.arrivalUtcMs)
    }

    @Test fun takeoffWorkedBackFromTheClimb() {
        val now = seg.departureUtcMs + 20 * 60_000
        val climbing = aircraft(altitudeFt = 10_000, verticalRateFpm = 2_000, onGround = false)
        val p = FlightProgress(FlightPhase.Airborne, 0.01f, Instant.ofEpochMilli(seg.arrivalUtcMs), live = true)
        val out = ActualTimes.observe(seg, climbing, p, now)
        assertNotNull(out)
        // 10,000 ft at 2,000 ft/min: wheels-up about five minutes ago.
        assertEquals(now - 5 * 60_000, out!!.actualDepartureUtcMs)
        assertEquals("adsb", out.actualSource)
        assertNull(ActualTimes.observe(out, climbing, p, now + 60_000))
    }

    @Test fun estimatedTimesBecomeRealOnLanding() {
        val flying = seg.copy(timesEstimated = true, actualDepartureUtcMs = seg.departureUtcMs + 25 * 60_000, actualSource = "adsb")
        val landedAt = seg.arrivalUtcMs - 10 * 60_000
        val p = FlightProgress(FlightPhase.Landed, 1f, Instant.ofEpochMilli(landedAt), live = true)
        val out = ActualTimes.observe(flying, aircraft(onGround = true, groundSpeedKt = 10f), p, landedAt)!!
        assertEquals(landedAt, out.actualArrivalUtcMs)
        assertFalse(out.timesEstimated)
        assertEquals(flying.actualDepartureUtcMs, out.departureUtcMs)
        assertEquals("2026-11-12T22:35", out.departureLocal)
    }
}

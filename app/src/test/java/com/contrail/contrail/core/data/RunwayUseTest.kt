package com.contrail.contrail.core.data

import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.airport.RunwayHabit
import com.contrail.contrail.core.data.airport.RunwaySighting
import com.contrail.contrail.core.data.airport.RunwayUse
import com.contrail.contrail.core.database.RunwayEntity
import com.contrail.contrail.testing.aircraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A Pearson-like field: three runways roughly 06/24 (05/23 and two parallels) and two 15/33 parallels. */
private val field = LatLon(43.6777, -79.6248)
private const val ELEVATION = 569

private fun runway(le: String, he: String, headingDeg: Double, offsetM: Double, lengthFt: Int): RunwayEntity {
    // Parallel runways sit side by side: shift the middle sideways, then lay the runway along its heading.
    val mid = Geo.destination(field, headingDeg + 90, offsetM)
    val half = lengthFt * 0.3048 / 2
    val a = Geo.destination(mid, headingDeg + 180, half)
    val b = Geo.destination(mid, headingDeg, half)
    return RunwayEntity("CYYZ", le, he, lengthFt, 200, "ASP", a.lat, a.lon, b.lat, b.lon, headingDeg, (headingDeg + 180) % 360)
}

private val pearson = listOf(
    runway("05", "23", 57.0, -2_500.0, 11_120),
    runway("06L", "24R", 57.0, -900.0, 9_697),
    runway("06R", "24L", 57.0, 600.0, 9_000),
    runway("15L", "33R", 147.0, -800.0, 11_050),
    runway("15R", "33L", 147.0, 800.0, 9_088),
)

class RunwayUseTest {
    private val ends = RunwayUse.ends(pearson)
    private fun end(ident: String) = ends.first { it.ident == ident }

    @Test
    fun `a plane on final is landing on that runway`() {
        val e = end("23")
        // 9 km out on the extended centreline, about 3,000 ft above the field, descending.
        val p = Geo.destination(e.threshold, e.headingDeg + 180, 9_000.0)
        val plane = aircraft(hex = "aaa001", lat = p.lat, lon = p.lon, altitudeFt = ELEVATION + 2_900, trackDeg = e.headingDeg.toFloat(), verticalRateFpm = -750)
        val seen = RunwayUse.sightings(ends, ELEVATION, listOf(plane), emptyMap(), now = 1_000)
        assertEquals(listOf("23" to RunwaySighting.Kind.Arrival), seen.map { it.runway to it.kind })
    }

    @Test
    fun `a plane climbing straight out past the far end is departing that runway`() {
        val e = end("24L")
        val p = Geo.destination(e.farEnd, e.headingDeg, 4_000.0)
        val plane = aircraft(hex = "aaa002", lat = p.lat, lon = p.lon, altitudeFt = ELEVATION + 2_200, trackDeg = e.headingDeg.toFloat(), verticalRateFpm = 2_400)
        val seen = RunwayUse.sightings(ends, ELEVATION, listOf(plane), emptyMap(), now = 1_000)
        assertTrue(seen.toString(), seen.any { it.runway == "24L" && it.kind == RunwaySighting.Kind.Departure })
    }

    @Test
    fun `a plane crossing overhead at altitude isn't using any runway`() {
        val plane = aircraft(hex = "aaa003", lat = field.lat, lon = field.lon, altitudeFt = 34_000, trackDeg = 57f, verticalRateFpm = 0)
        assertTrue(RunwayUse.sightings(ends, ELEVATION, listOf(plane), emptyMap(), now = 1_000).isEmpty())
    }

    @Test
    fun `what's seen beats any guess, busiest first`() {
        val now = 10_000_000L
        val seen = listOf(
            RunwaySighting("23", RunwaySighting.Kind.Arrival, "a", now - 60_000),
            RunwaySighting("24R", RunwaySighting.Kind.Arrival, "b", now - 120_000),
            RunwaySighting("23", RunwaySighting.Kind.Arrival, "c", now - 300_000),
            RunwaySighting("24L", RunwaySighting.Kind.Departure, "d", now - 200_000),
            RunwaySighting("33R", RunwaySighting.Kind.Arrival, "old", now - 40 * 60_000),
        )
        val use = RunwayUse.inUse(seen, now)!!
        assertTrue(use.observed)
        assertEquals(listOf("23", "24R"), use.arrivals)
        assertEquals(listOf("24L"), use.departures)
        assertEquals(4, use.planes)
    }

    @Test
    fun `a north-westerly at Pearson guesses the 23 and 24s, not 33`() {
        val use = RunwayUse.guess(ends, windFromDeg = 300.0, windKt = 15, habits = emptyList())!!
        assertFalse(use.observed)
        assertTrue(use.arrivals.toString(), use.arrivals.containsAll(listOf("23", "24R", "24L")))
        assertFalse(use.arrivals.any { it.startsWith("33") || it.startsWith("15") })
    }

    @Test
    fun `a strong crosswind on the main runways moves the guess to the crosswind pair`() {
        val use = RunwayUse.guess(ends, windFromDeg = 330.0, windKt = 34, habits = emptyList())!!
        assertTrue(use.arrivals.toString(), use.arrivals.all { it.startsWith("33") })
    }

    @Test
    fun `habits seen in a similar wind lead the guess`() {
        val habits = listOf(
            RunwayHabit("33R", RunwaySighting.Kind.Arrival, 310.0, 5),
            RunwayHabit("33L", RunwaySighting.Kind.Departure, 305.0, 4),
            RunwayHabit("23", RunwaySighting.Kind.Arrival, 120.0, 9),
        )
        val use = RunwayUse.guess(ends, windFromDeg = 300.0, windKt = 15, habits = habits)!!
        assertEquals(listOf("33R"), use.arrivals)
        assertEquals(listOf("33L"), use.departures)
    }
}

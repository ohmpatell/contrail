package com.contrail.contrail.core.data

import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.events.EventDetector
import com.contrail.contrail.core.data.model.AirportRef
import com.contrail.contrail.core.data.model.TrailPoint
import com.contrail.contrail.core.data.spotting.Light
import com.contrail.contrail.testing.aircraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val yyz = AirportRef("YYZ", "CYYZ", "Toronto Pearson", "Toronto", "CA", 43.6777, -79.6248)
private val field = LatLon(yyz.lat, yyz.lon)

/** A trail along a heading, through [alts] (one point every ~1.2 km). */
private fun trail(start: LatLon, heading: Double, alts: List<Int>, onGround: (Int) -> Boolean = { false }) = alts.mapIndexed { i, alt ->
    val p = Geo.destination(start, heading, i * 1_200.0)
    TrailPoint("abc123", i * 10_000L, p.lat, p.lon, alt, onGround(i), null)
}

class EventDetectorTest {
    @Test
    fun `down low near the runway then climbing away is a go-around`() {
        val start = Geo.destination(field, 237.0 + 180, 6_000.0)
        val t = trail(start, 237.0, listOf(2_600, 2_100, 1_600, 1_150, 1_000, 1_300, 1_800))
        val last = t.last()
        val plane = aircraft(lat = last.lat, lon = last.lon, altitudeFt = 2_300, trackDeg = 237f, verticalRateFpm = 1_800)
        assertTrue(EventDetector.isGoAround(t, plane, yyz, 569))
    }

    @Test
    fun `a normal landing is not a go-around`() {
        val start = Geo.destination(field, 237.0 + 180, 6_000.0)
        val t = trail(start, 237.0, listOf(2_600, 2_000, 1_400, 900, 600, 569), onGround = { it == 5 })
        val plane = aircraft(lat = field.lat, lon = field.lon, altitudeFt = 569, onGround = true, trackDeg = 237f, verticalRateFpm = 0)
        assertFalse(EventDetector.isGoAround(t, plane, yyz, 569))
    }

    @Test
    fun `a departure climbing out is not a go-around`() {
        val t = trail(field, 237.0, listOf(800, 1_300, 1_900, 2_600))
        val last = t.last()
        val plane = aircraft(lat = last.lat, lon = last.lon, altitudeFt = 3_100, trackDeg = 237f, verticalRateFpm = 2_500)
        assertFalse(EventDetector.isGoAround(t, plane, yyz, 569))
    }

    @Test
    fun `circling while level is holding, flying straight is not`() {
        val centre = Geo.destination(field, 45.0, 40_000.0)
        val circle = (0..12).map { i ->
            val p = Geo.destination(centre, i * 30.0, 6_000.0)
            TrailPoint("abc123", i * 20_000L, p.lat, p.lon, 12_000, false, 0)
        }
        val holding = aircraft(lat = circle.last().lat, lon = circle.last().lon, altitudeFt = 12_000, groundSpeedKt = 230f, verticalRateFpm = 0)
        assertTrue(EventDetector.isHolding(circle, holding))

        val straight = trail(centre, 90.0, List(10) { 12_000 })
        val cruising = aircraft(lat = straight.last().lat, lon = straight.last().lon, altitudeFt = 12_000, groundSpeedKt = 300f)
        assertFalse(EventDetector.isHolding(straight, cruising))
    }
}

class LightTest {
    @Test
    fun `sun to the south lights the left side of planes landing south-west, so stand south-east`() {
        assertEquals("left", Light.litSide(237.0, 180.0))
        assertEquals("south-east", Light.standSide(237.0, "left"))
    }

    @Test
    fun `sun straight down the approach lights neither side`() {
        assertNull(Light.litSide(237.0, 240.0))
        assertNull(Light.litSide(237.0, 60.0))
    }

    @Test
    fun `compass words`() {
        assertEquals("north", Light.compassWord(350.0))
        assertEquals("south-west", Light.compassWord(225.0))
    }
}

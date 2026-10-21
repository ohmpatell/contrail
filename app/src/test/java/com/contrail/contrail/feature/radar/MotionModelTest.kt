package com.contrail.contrail.feature.radar

import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.feature.radar.map.MotionModel
import com.contrail.contrail.testing.aircraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionModelTest {
    private val out = MotionModel.Sample()

    private fun MotionModel.at(hex: String, t: Long): MotionModel.Sample {
        assertTrue(sample(hex, t, out))
        return out
    }

    @Test
    fun `dead reckons along the track at ground speed`() {
        val model = MotionModel()
        // 360 kt is exactly 0.1 nm per second.
        model.update(listOf(aircraft(groundSpeedKt = 360f, trackDeg = 90f, lat = 0.0, lon = 0.0)), nowMs = 0)
        val s = model.at("c00e9d", 10_000)
        assertEquals(Geo.METERS_PER_NM, Geo.distanceM(0.0, 0.0, s.lat, s.lon), 5.0)
        assertTrue(s.lon > 0)
        assertEquals(0.0, s.lat, 1e-6)
    }

    @Test
    fun `stops extrapolating after the cap`() {
        val model = MotionModel()
        model.update(listOf(aircraft(groundSpeedKt = 360f, lat = 0.0, lon = 0.0)), nowMs = 0)
        val atCap = Geo.distanceM(0.0, 0.0, model.at("c00e9d", 30_000).lat, out.lon)
        val wayPast = Geo.distanceM(0.0, 0.0, model.at("c00e9d", 120_000).lat, out.lon)
        assertEquals(atCap, wayPast, 1.0)
    }

    @Test
    fun `a new fix glides in instead of jumping`() {
        val model = MotionModel(correctionTauMs = 900.0)
        model.update(listOf(aircraft(lat = 43.0, lon = -79.0, positionTimeMs = 0)), nowMs = 0)
        val before = model.at("c00e9d", 4_000).let { it.lat to it.lon }
        // The new fix says the plane is 500 m further north than we predicted.
        val fix = aircraft(lat = before.first + 500 / Geo.METERS_PER_DEG_LAT, lon = before.second, positionTimeMs = 4_000)
        model.update(listOf(fix), nowMs = 4_000)
        val right = model.at("c00e9d", 4_000)
        assertEquals("no visible jump at the instant of the update", 0.0, Geo.distanceM(before.first, before.second, right.lat, right.lon), 1.0)
        // Five time constants later the correction has all but vanished.
        val later = model.at("c00e9d", 8_500)
        val predicted = MotionModel().apply { update(listOf(fix), 4_000) }.let { m ->
            MotionModel.Sample().also { m.sample("c00e9d", 8_500, it) }
        }
        assertEquals(0.0, Geo.distanceM(later.lat, later.lon, predicted.lat, predicted.lon), 5.0)
    }

    @Test
    fun `big jumps snap rather than smear across the map`() {
        val model = MotionModel()
        model.update(listOf(aircraft(lat = 43.0, lon = -79.0)), nowMs = 0)
        model.update(listOf(aircraft(lat = 45.0, lon = -79.0, positionTimeMs = 1_000)), nowMs = 1_000)
        assertEquals(45.0, model.at("c00e9d", 1_000).lat, 1e-6)
    }

    @Test
    fun `a turn rate bends the path and the heading`() {
        val model = MotionModel()
        model.update(listOf(aircraft(trackDeg = 0f, trackRateDegS = 3f, groundSpeedKt = 250f)), nowMs = 0)
        val s = model.at("c00e9d", 10_000)
        assertEquals(30.0, s.headingDeg, 0.5)
        assertTrue("turning right drifts east", s.lon > -79.6)
    }

    @Test
    fun `parked aircraft do not creep`() {
        val model = MotionModel()
        model.update(listOf(aircraft(onGround = true, altitudeFt = null, groundSpeedKt = 0f, lat = 43.68, lon = -79.63)), nowMs = 0)
        val s = model.at("c00e9d", 20_000)
        assertEquals(43.68, s.lat, 1e-9)
        assertTrue(s.onGround)
    }

    @Test
    fun `stale contacts fade out`() {
        val model = MotionModel()
        model.update(listOf(aircraft(fetchedAtMs = 0)), nowMs = 0)
        assertEquals(1.0, model.at("c00e9d", 10_000).freshness, 1e-9)
        assertTrue(model.at("c00e9d", 40_000).freshness in 0.01..0.99)
        assertEquals(0.0, model.at("c00e9d", 70_000).freshness, 1e-9)
    }

    @Test
    fun `reduced motion draws fixes exactly where reported`() {
        val model = MotionModel(extrapolate = false)
        model.update(listOf(aircraft(lat = 43.0, lon = -79.0)), nowMs = 0)
        val s = model.at("c00e9d", 15_000)
        assertEquals(43.0, s.lat, 1e-9)
        assertEquals(-79.0, s.lon, 1e-9)
    }

    @Test
    fun `contacts missing from an update are dropped`() {
        val model = MotionModel()
        model.update(listOf(aircraft(hex = "a"), aircraft(hex = "b")), nowMs = 0)
        model.update(listOf(aircraft(hex = "b")), nowMs = 1_000)
        assertFalse(model.sample("a", 1_000, out))
        assertEquals(setOf("b"), model.hexes)
    }
}

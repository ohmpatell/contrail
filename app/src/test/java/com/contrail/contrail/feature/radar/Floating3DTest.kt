package com.contrail.contrail.feature.radar

import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.feature.radar.map.FloatCamera
import com.contrail.contrail.feature.radar.map.Floating3D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Floating3DTest {
    private val target = LatLon(43.7, -79.6)

    @Test
    fun `a tilted camera sits behind and above the middle of the map`() {
        val (below, height) = Floating3D.camera(FloatCamera(target, 9.0, 60.0, 0.0, 800.0))
        // Bearing 0 looks north, so the camera is south of the target.
        assertTrue(below.lat < target.lat)
        assertTrue(height > 0)
        val flat = Floating3D.camera(FloatCamera(target, 9.0, 0.0, 0.0, 800.0))
        assertEquals(target.lat, flat.first.lat, 1e-6)
    }

    @Test
    fun `a plane up high is drawn further from the camera than its shadow, and more so the higher it is`() {
        val (below, height) = Floating3D.camera(FloatCamera(target, 9.0, 60.0, 0.0, 800.0))
        val p = Geo.destination(target, 0.0, 5_000.0)
        val low = Floating3D.lift(p, 5_000.0, below, height)
        val high = Floating3D.lift(p, 35_000.0, below, height)
        val shadowDist = Geo.distanceM(below, p)
        assertTrue(Geo.distanceM(below, low) > shadowDist)
        assertTrue(Geo.distanceM(below, high) > Geo.distanceM(below, low))
        // On the ground it stays put.
        assertEquals(p.lat, Floating3D.lift(p, 0.0, below, height).lat, 1e-9)
    }

    @Test
    fun `route altitude climbs, cruises and descends`() {
        assertEquals(0.0, Floating3D.routeAltitudeFt(0.0, 5_000_000.0), 1e-6)
        assertEquals(37_000.0, Floating3D.routeAltitudeFt(1_000_000.0, 1_000_000.0), 1e-6)
        assertTrue(Floating3D.routeAltitudeFt(100_000.0, 5_000_000.0) < 37_000.0)
    }
}

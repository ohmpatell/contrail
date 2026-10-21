package com.contrail.contrail.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {
    private val yyz = LatLon(43.6777, -79.6248)
    private val lhr = LatLon(51.4700, -0.4543)

    @Test
    fun `YYZ to LHR is about 5,710 km`() {
        assertEquals(5_710_000.0, Geo.distanceM(yyz, lhr), 25_000.0)
    }

    @Test
    fun `initial bearing YYZ to LHR heads north-east`() {
        assertEquals(53.0, Geo.bearingDeg(yyz, lhr), 2.0)
    }

    @Test
    fun `destination round-trips with distance and bearing`() {
        val p = Geo.destination(yyz, 117.0, 42_000.0)
        assertEquals(42_000.0, Geo.distanceM(yyz, p), 1.0)
        assertEquals(117.0, Geo.bearingDeg(yyz, p), 0.2)
    }

    @Test
    fun `heading delta takes the short way round`() {
        assertEquals(20.0, Geo.headingDelta(350.0, 10.0), 1e-9)
        assertEquals(-20.0, Geo.headingDelta(10.0, 350.0), 1e-9)
        assertEquals(180.0, Geo.headingDelta(0.0, 180.0), 1e-9)
    }

    @Test
    fun `longitudes wrap across the antimeridian`() {
        assertEquals(-170.0, Geo.normalizeLon(190.0), 1e-9)
        assertEquals(170.0, Geo.normalizeLon(-190.0), 1e-9)
        assertEquals(359.0, Geo.normalizeHeading(-1.0), 1e-9)
    }
}

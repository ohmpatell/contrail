package com.contrail.contrail.core.data

import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.cabin.Bumpiness
import com.contrail.contrail.core.data.cabin.CabinMath
import com.contrail.contrail.core.data.cabin.PackPlace
import com.contrail.contrail.core.data.cabin.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

private val yyz = LatLon(43.6777, -79.6248)
private val hkg = LatLon(22.308, 113.918)
private val route = Geo.greatCircle(yyz, hkg, 128)

class CabinMathTest {
    @Test
    fun `progress by the clock is slower at each end than in cruise`() {
        val takeoff = 0L
        val landing = 15 * 3_600_000L
        assertEquals(0.0, CabinMath.estimatedFraction(takeoff, takeoff, landing), 1e-9)
        assertEquals(1.0, CabinMath.estimatedFraction(landing, takeoff, landing), 1e-9)
        assertEquals(0.5, CabinMath.estimatedFraction(landing / 2, takeoff, landing), 1e-6)
        // Ten minutes in: still climbing, so less than a straight-line share of the distance.
        assertTrue(CabinMath.estimatedFraction(10 * 60_000L, takeoff, landing) < 10.0 / (15 * 60))
    }

    @Test
    fun `a point found along the route maps back to the same fraction`() {
        for (f in listOf(0.1, 0.37, 0.8)) {
            val (p, _) = CabinMath.pointAt(route, f)
            assertEquals(f, CabinMath.fractionOf(route, p), 0.01)
        }
    }

    @Test
    fun `window sides from the seat letter`() {
        assertEquals(Side.Left, CabinMath.windowSide("15A", widebody = true))
        assertEquals(Side.Right, CabinMath.windowSide("11K", widebody = true))
        assertEquals(Side.Right, CabinMath.windowSide("23F", widebody = false))
        assertNull(CabinMath.windowSide("23F", widebody = true))
        assertNull(CabinMath.windowSide("14C", widebody = false))
    }

    @Test
    fun `places ahead come with their side and time`() {
        // Heading north: a place to the east of the route is on the right.
        val north = listOf(LatLon(40.0, -80.0), LatLon(50.0, -80.0))
        val place = PackPlace("Somewhere", "CA", 45.0, -79.0, along = 0.5)
        val up = CabinMath.upcoming(north, listOf(place), at = 0.3, speedKt = 480.0)
        assertEquals(1, up.size)
        assertEquals(Side.Right, up.first().side)
        assertTrue(up.first().minutes in 10..40)
        // Already passed: not coming up.
        assertTrue(CabinMath.upcoming(north, listOf(place), at = 0.6, speedKt = 480.0).isEmpty())
    }

    @Test
    fun `the horizon dips about 3 degrees at cruise`() {
        assertEquals(3.4, CabinMath.horizonDipDeg(11_000.0), 0.15)
        assertEquals(0.0, CabinMath.horizonDipDeg(0.0), 1e-9)
    }

    @Test
    fun `cabin altitude from pressure`() {
        assertEquals(0, CabinMath.pressureAltitudeFt(1013.25f))
        // A typical pressurised cabin.
        assertTrue(abs(CabinMath.pressureAltitudeFt(795f) - 6_800) < 400)
    }

    @Test
    fun `bumpiness from the accelerometer`() {
        assertEquals(Bumpiness.Smooth, CabinMath.bumpiness(List(50) { 0.05f * sin(it.toFloat()) }))
        assertEquals(Bumpiness.Moderate, CabinMath.bumpiness(List(50) { 1.6f * sin(it * 1.7f) }))
    }

    @Test
    fun `borders are where the country below changes`() {
        val b = CabinMath.borders(listOf(0.0 to "CA", 0.1 to "CA", 0.2 to null, 0.3 to "US", 0.5 to "US", 0.6 to "RU"))
        assertEquals(listOf(0.3 to "US", 0.6 to "RU"), b)
    }
}

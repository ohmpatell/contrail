package com.contrail.contrail.feature.radar

import com.contrail.contrail.feature.radar.map.SignedDistanceField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignedDistanceFieldTest {

    @Test
    fun `squared distance transform matches brute force`() {
        val w = 9
        val h = 7
        val grid = FloatArray(w * h) { 1e20f }
        grid[3 * w + 4] = 0f
        grid[0] = 0f
        SignedDistanceField.transform2d(grid, w, h)
        for (y in 0 until h) for (x in 0 until w) {
            val a = (x - 4) * (x - 4) + (y - 3) * (y - 3)
            val b = x * x + y * y
            assertEquals("($x,$y)", minOf(a, b).toFloat(), grid[y * w + x], 1e-3f)
        }
    }

    @Test
    fun `encoding puts the edge at 0_75 and rises inside`() {
        val size = 21
        // A filled disc of radius 6 in the middle.
        val coverage = FloatArray(size * size) { i ->
            val x = i % size - 10
            val y = i / size - 10
            if (x * x + y * y <= 36) 1f else 0f
        }
        val sdf = SignedDistanceField.encode(coverage, size, size, pixelRatio = 1f)
        fun v(x: Int, y: Int) = (sdf[y * size + x].toInt() and 0xFF) / 255f
        assertTrue("centre is solidly inside", v(10, 10) > 0.95f)
        assertTrue("far corner is outside", v(0, 0) < 0.05f)
        // Just inside and just outside the rim straddle the 0.75 edge value.
        assertTrue(v(16, 10) > 0.75f)
        assertTrue(v(17, 10) < 0.75f)
        assertTrue("outside falls off by 1/8 per pixel", v(17, 10) - v(18, 10) in 0.10f..0.15f)
    }
}

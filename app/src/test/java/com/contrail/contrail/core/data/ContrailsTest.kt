package com.contrail.contrail.core.data

import com.contrail.contrail.core.data.sky.ContrailOutlook
import com.contrail.contrail.core.data.sky.ContrailPhysics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrailsTest {
    @Test
    fun `the threshold at 250 hPa in saturated air is about minus 41`() {
        // Schumann (1996): roughly -40 to -41 °C at 250 hPa for modern engines at water saturation.
        assertEquals(-41.0, ContrailPhysics.thresholdC(250, 100.0), 1.0)
    }

    @Test
    fun `drier air needs colder air to make a contrail`() {
        assertTrue(ContrailPhysics.thresholdC(250, 20.0) < ContrailPhysics.thresholdC(250, 80.0))
    }

    @Test
    fun `humidity over ice is higher than over water when it's cold`() {
        assertEquals(42.0 * 1.71, ContrailPhysics.rhIce(42.0, -56.5), 2.0)
    }

    @Test
    fun `outlooks`() {
        // Too warm: no contrail.
        assertEquals(ContrailOutlook.None, ContrailPhysics.level(300, -35.0, 70.0).outlook)
        // Cold and dry: forms, fades fast (Toronto, Nov 23 2026, 00 UTC, 250 hPa).
        assertEquals(ContrailOutlook.ShortLived, ContrailPhysics.level(250, -56.5, 42.0).outlook)
        // Cold and moist enough to be ice-supersaturated: lasts.
        assertEquals(ContrailOutlook.LongLasting, ContrailPhysics.level(250, -55.0, 65.0).outlook)
    }

    @Test
    fun `the nearest level for a plane's altitude`() {
        val levels = listOf(300, 250, 200).map { ContrailPhysics.level(it, -55.0, 50.0) }
        assertEquals(250, ContrailPhysics.nearest(levels, 35_000)?.hPa)
        assertEquals(null, ContrailPhysics.nearest(levels, 8_000))
    }
}

package com.contrail.contrail.feature.radar

import com.contrail.contrail.feature.radar.map.Terminator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class TerminatorTest {
    private fun utc(y: Int, m: Int, d: Int, h: Int) =
        ZonedDateTime.of(y, m, d, h, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun `sun is over the Greenwich meridian near the equator at a March noon`() {
        val sun = Terminator.subSolarPoint(utc(2026, 3, 20, 12))
        assertEquals(0.0, sun.lat, 1.0)
        assertEquals(0.0, sun.lon, 3.0)
    }

    @Test
    fun `June solstice puts the sun over the Tropic of Cancer`() {
        assertEquals(23.4, Terminator.subSolarPoint(utc(2026, 6, 21, 12)).lat, 0.3)
    }

    @Test
    fun `polygon edge sits on the requested sun altitude`() {
        listOf(utc(2026, 6, 21, 3), utc(2026, 12, 21, 18), utc(2026, 10, 20, 0)).forEach { t ->
            listOf(0.0, -6.0, -12.0).forEach { altitude ->
                val edge = Terminator.nightPolygon(t, altitude).dropLast(3) // drop the polar closing points
                edge.filter { (_, lat) -> lat > -85 && lat < 85 }.forEach { (lon, lat) ->
                    assertEquals("alt $altitude at $lon,$lat", altitude, Terminator.sunElevationDeg(t, lat, lon), 0.6)
                }
            }
        }
    }

    @Test
    fun `night closes over the pole facing away from the sun`() {
        val june = Terminator.nightPolygon(utc(2026, 6, 21, 12))
        assertTrue(june[june.size - 2].second < 0)
        val december = Terminator.nightPolygon(utc(2026, 12, 21, 12))
        assertTrue(december[december.size - 2].second > 0)
    }
}

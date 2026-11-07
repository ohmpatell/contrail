package com.contrail.contrail.feature

import androidx.compose.ui.graphics.Color
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.common.Sun
import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.core.data.settings.Appearance
import com.contrail.contrail.core.data.settings.ThemeResolver
import com.contrail.contrail.core.data.trip.FlightSegment
import com.contrail.contrail.core.designsystem.theme.ChartColors
import com.contrail.contrail.core.designsystem.theme.GoldenHour
import com.contrail.contrail.core.designsystem.theme.NightCockpitColors
import com.contrail.contrail.core.designsystem.theme.ThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ThemesTest {
    private val toronto = LatLon(43.65, -79.38)
    private fun ms(h: Int) = LocalDateTime.of(2026, 6, 21, h, 0).atZone(ZoneId.of("America/Toronto")).toInstant().toEpochMilli()

    @Test fun sunIsUpAtNoonAndDownAtMidnight() {
        assertTrue(Sun.elevationDeg(ms(13), toronto.lat, toronto.lon) > 60)
        assertTrue(Sun.elevationDeg(ms(1), toronto.lat, toronto.lon) < -15)
    }

    @Test fun goldenHourIsNightInTheDarkAndBlendsAtDusk() {
        assertEquals(NightCockpitColors, GoldenHour.colors(-20.0))
        assertEquals(NightCockpitColors, GoldenHour.colors(null))
        val dusk = GoldenHour.colors(4.0)
        assertNotEquals(NightCockpitColors.accent, dusk.accent)
        // Halfway between two keyframes is neither.
        val between = GoldenHour.colors(-1.0)
        assertNotEquals(dusk.accent, between.accent)
        assertEquals("Golden hour", GoldenHour.phase(4.0))
    }

    @Test fun liveryTintsOnlyDuringAFlight() {
        val zone = ZoneId.of("America/Toronto")
        val seg = FlightSegment(
            id = 1, tripId = 1, ordinal = 0, airlineIata = "EY", flightNumber = "140", origin = "YYZ", destination = "AUH",
            departureLocal = LocalDateTime.of(2026, 6, 21, 22, 0), arrivalLocal = LocalDateTime.of(2026, 6, 22, 19, 0),
            departureZone = zone, arrivalZone = ZoneId.of("Asia/Dubai"),
        )
        val chart = Appearance(ThemeChoice.Chart, livery = true)
        val before = ThemeResolver.resolve(chart, null, listOf(seg), ms(13))
        assertEquals(ChartColors, before.colors)
        assertNull(before.liveryAirline)
        val during = ThemeResolver.resolve(chart, null, listOf(seg), ms(21))
        Airlines.byIata("EY")?.let { assertEquals(Color(it.accentArgb), during.colors.accent) }
        val off = ThemeResolver.resolve(chart.copy(livery = false), null, listOf(seg), ms(21))
        assertEquals(ChartColors, off.colors)
    }
}

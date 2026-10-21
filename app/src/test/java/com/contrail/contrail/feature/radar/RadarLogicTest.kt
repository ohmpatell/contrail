package com.contrail.contrail.feature.radar

import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.reference.AircraftTypes
import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.core.data.reference.Rarity
import com.contrail.contrail.core.data.reference.Silhouette
import com.contrail.contrail.core.designsystem.component.flapSequence
import com.contrail.contrail.testing.aircraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarFiltersTest {
    private val traffic = listOf(
        aircraft(hex = "1", callsign = "ACA112", typeCode = "B789", altitudeFt = 36_000),
        aircraft(hex = "2", callsign = "ETD141", typeCode = "A388", altitudeFt = 41_000),
        aircraft(hex = "3", callsign = "CGQIR", typeCode = "P28A", altitudeFt = null, onGround = true),
        aircraft(hex = "4", callsign = "RCH402", typeCode = "C17", altitudeFt = 22_000, military = true),
        aircraft(hex = "5", callsign = "ACA856", typeCode = "B744", altitudeFt = 8_000),
    )

    private fun RadarFilters.hexes() = traffic.filter { matches(it) }.map { it.hex }

    @Test
    fun `empty filters match everything`() {
        assertTrue(RadarFilters().isEmpty)
        assertEquals(5, RadarFilters().hexes().size)
    }

    @Test
    fun `altitude bands are OR-ed`() {
        val f = RadarFilters().toggleBand(AltitudeBand.Ground).toggleBand(AltitudeBand.High)
        assertEquals(listOf("2", "3"), f.hexes())
    }

    @Test
    fun `facets are AND-ed`() {
        val f = RadarFilters(airlines = setOf("ACA"), bands = setOf(AltitudeBand.Cruise))
        assertEquals(listOf("1"), f.hexes())
        assertEquals(2, f.activeCount)
    }

    @Test
    fun `rare only keeps legendary and rare types`() {
        assertEquals(listOf("2", "4", "5"), RadarFilters(rareOnly = true).hexes())
    }

    @Test
    fun `military only uses the feed flag and the type table`() {
        assertEquals(listOf("4"), RadarFilters(militaryOnly = true).hexes())
    }

    @Test
    fun `toggling twice clears a choice`() {
        assertTrue(RadarFilters().toggleType("B789").toggleType("B789").isEmpty)
    }

    @Test
    fun `facets count what is on screen and keep selected choices`() {
        val facets = FilterFacets.from(traffic, RadarFilters(airlines = setOf("UAL")))
        assertEquals(2, facets.airlines.first { it.key == "ACA" }.count)
        assertEquals(0, facets.airlines.first { it.key == "UAL" }.count)
        assertEquals(1, facets.militaryCount)
        assertEquals("Air Canada", facets.airlines.first().label)
    }
}

class ReferenceDataTest {
    @Test
    fun `airline comes from a three-letter callsign prefix`() {
        assertEquals("ETD", Airlines.icaoFromCallsign("ETD141"))
        assertEquals("CPA", Airlines.icaoFromCallsign(" cpa889 "))
        assertNull("registrations are not airlines", Airlines.icaoFromCallsign("CFWLV"))
        assertNull(Airlines.icaoFromCallsign("N194V"))
        assertEquals("Etihad Airways", Airlines.fromCallsign("ETD141")?.name)
    }

    @Test
    fun `rarity tiers follow the design doc`() {
        assertEquals(Rarity.Legendary, AircraftTypes.rarityFor("B748", null))
        assertEquals(Rarity.Legendary, AircraftTypes.rarityFor("A346", null))
        assertEquals(Rarity.Rare, AircraftTypes.rarityFor("A388", null))
        assertEquals(Rarity.Uncommon, AircraftTypes.rarityFor("BCS3", null))
        assertEquals(Rarity.Common, AircraftTypes.rarityFor("A20N", null))
        assertEquals(Rarity.Common, AircraftTypes.rarityFor(null, null))
    }

    @Test
    fun `a cargo 747 is not a passenger legend`() {
        assertEquals(Rarity.Uncommon, AircraftTypes.rarityFor("B744", Airlines.byIcao("UPS")))
    }

    @Test
    fun `silhouette falls back to the emitter category`() {
        assertEquals(Silhouette.Quad, AircraftTypes.silhouetteFor("A388", "A5"))
        assertEquals(Silhouette.Helicopter, AircraftTypes.silhouetteFor("ZZZZ", "A7"))
        assertEquals(Silhouette.Generic, AircraftTypes.silhouetteFor(null, null))
    }

    @Test
    fun `emergency squawks are recognised`() {
        assertTrue(aircraft(squawk = "7700").isEmergency)
        assertTrue(aircraft(squawk = "7600").isEmergency)
        assertFalse(aircraft(squawk = "1200").isEmergency)
    }
}

class RadarFormatTest {
    @Test
    fun readouts() {
        assertEquals("34,000", RadarFormat.altitude(aircraft(altitudeFt = 34_000)).value)
        assertEquals("GND", RadarFormat.altitude(aircraft(onGround = true, altitudeFt = null)).value)
        assertEquals("FL340", RadarFormat.flightLevel(34_000))
        assertNull(RadarFormat.flightLevel(9_000))
        assertEquals("+1,216", RadarFormat.verticalRate(1_216).value)
        assertEquals("−832", RadarFormat.verticalRate(-832).value)
        assertEquals("0", RadarFormat.verticalRate(32).value)
        assertEquals("005", RadarFormat.heading(aircraft(trackDeg = 4.6f)).value)
        assertEquals("452", RadarFormat.speed(aircraft(groundSpeedKt = 451.7f)).value)
    }
}

class SplitFlapTest {
    @Test
    fun `flips forward through the alphabet and lands on the target`() {
        assertEquals(listOf('B', 'C', 'D'), flapSequence('A', 'D'))
        assertEquals(emptyList<Char>(), flapSequence('A', 'A'))
    }

    @Test
    fun `long jumps are capped so boards settle quickly`() {
        val seq = flapSequence(' ', 'Z')
        assertTrue(seq.size <= 9)
        assertEquals('Z', seq.last())
    }

    @Test
    fun `characters outside the alphabet snap`() {
        assertEquals(listOf('é'), flapSequence('A', 'é'))
    }
}

class PollingTest {
    @Test
    fun `small pans reuse the query area, big ones do not`() {
        val a = Viewport(LatLon(43.7, -79.6), radiusNm = 100.0, zoom = 7.0)
        assertTrue(RadarViewModel.sameArea(a, a.copy(center = LatLon(43.9, -79.6))))
        assertFalse(RadarViewModel.sameArea(a, a.copy(center = LatLon(45.0, -79.6))))
        assertFalse(RadarViewModel.sameArea(a, a.copy(radiusNm = 40.0)))
    }

    @Test
    fun `backoff grows and caps at thirty seconds`() {
        assertEquals(4_000L, RadarViewModel.backoff(1))
        assertEquals(8_000L, RadarViewModel.backoff(2))
        assertEquals(30_000L, RadarViewModel.backoff(10))
    }
}

package com.contrail.contrail.feature

import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.model.Watchlist
import com.contrail.contrail.core.data.sky.AlertRules
import com.contrail.contrail.core.data.sky.SkyGeometry
import com.contrail.contrail.feature.passport.Continents
import com.contrail.contrail.feature.passport.PassportCalculator
import com.contrail.contrail.feature.passport.PassportInsights
import com.contrail.contrail.feature.passport.Tier
import com.contrail.contrail.feature.sky.compass
import com.contrail.contrail.testing.PassportFixtures
import com.contrail.contrail.testing.aircraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SkyTest {
    private val me = LatLon(43.65, -79.38)

    @Test
    fun `a plane due east at 10 km is east and up`() {
        val there = Geo.destination(me, 90.0, 10_000.0)
        val c = SkyGeometry.contact(me, aircraft(lat = there.lat, lon = there.lon, altitudeFt = 10_000, groundSpeedKt = null, trackDeg = null))
        assertEquals(90.0, c.bearingDeg, 0.5)
        // 10,500 ft geometric ≈ 3.2 km up over 10 km: about 17.7°.
        assertEquals(17.7, c.elevationDeg, 0.6)
        assertTrue(c.east > 0.9 && c.up > 0.25)
        assertEquals("E", compass(c.bearingDeg))
    }

    @Test
    fun `closest approach for a plane crossing in front of you`() {
        // 20 km west, heading east at 400 kt: passes overhead in about 97 seconds.
        val west = Geo.destination(me, 270.0, 20_000.0)
        val ap = SkyGeometry.approach(me, aircraft(lat = west.lat, lon = west.lon, groundSpeedKt = 400f, trackDeg = 90f))
        assertNotNull(ap)
        assertEquals(97.0, ap!!.inSeconds.toDouble(), 3.0)
        assertTrue(ap.minDistanceKm < 0.5)
        // Flying away: no approach.
        assertNull(SkyGeometry.approach(me, aircraft(lat = west.lat, lon = west.lon, groundSpeedKt = 400f, trackDeg = 270f)))
    }

    @Test
    fun `contacts are sorted by distance and limited to the radius`() {
        val near = Geo.destination(me, 0.0, 5_000.0)
        val far = Geo.destination(me, 0.0, 60_000.0)
        val list = SkyGeometry.contacts(
            me,
            listOf(aircraft(hex = "far", lat = far.lat, lon = far.lon), aircraft(hex = "near", lat = near.lat, lon = near.lon), aircraft(hex = "ground", lat = near.lat, lon = near.lon, onGround = true)),
            radiusKm = 25.0, nowMs = 0,
        )
        assertEquals(listOf("near"), list.map { it.aircraft.hex })
    }

    @Test
    fun `alerts pick the most important reason`() {
        val c = SkyGeometry.contact(me, aircraft(typeCode = "A388", callsign = "UAE241"))
        val rules = AlertRules(enabled = true)
        assertTrue(rules.reason(c, Watchlist(emptyList()))!!.contains("aircraft"))
        assertNull(rules.copy(rare = false).reason(c, Watchlist(emptyList())))
        val low = SkyGeometry.contact(me, aircraft(typeCode = "B38M", altitudeFt = 2_000))
        assertEquals("Low and close", rules.copy(lowerThanFt = 3_000).reason(low, Watchlist(emptyList())))
    }

    @Test
    fun `passport insights from a long history`() {
        val stats = PassportCalculator.compute(PassportFixtures.trips, PassportFixtures.places, emptyList(), emptySet(), Instant.parse("2026-10-21T12:00:00Z"))
        assertEquals(40, stats.flights)
        assertEquals("AC", stats.insights.topAirlines.first().key)
        assertEquals("YYZ", stats.insights.topAirports.first().key)
        // YYZ first, then every new airport in order.
        assertEquals("YYZ", stats.stamps.first().airport)
        assertEquals(stats.stamps.size, stats.stamps.map { it.airport }.distinct().size)
        val medals = stats.achievements.associateBy { it.id }
        assertEquals(Tier.Platinum, medals.getValue("superjumbo").tier)
        assertEquals(Tier.Platinum, medals.getValue("dateline").tier) // Vancouver–Sydney.
        assertTrue(medals.getValue("equator").unlocked)
        assertTrue(medals.getValue("newyear").unlocked)
        assertTrue(stats.insights.continents.containsAll(setOf("North America", "Europe", "Asia", "Oceania")))
        assertEquals("Oceania", Continents.of("AU"))
        assertTrue(PassportInsights.isRedEye(stats.flown.first { it.designator == "EY 140" }))
    }
}

class OpenSkyCredentialsTest {
    @Test
    fun `reads credentials json pasted into a key field`() {
        val parsed = com.contrail.contrail.core.data.settings.ApiKeys.openSkyCredentials("""{"clientId":"me-api-client","clientSecret":"abc123XYZ"}""")
        assertEquals("me-api-client" to "abc123XYZ", parsed)
        assertEquals("a" to "b", com.contrail.contrail.core.data.settings.ApiKeys.openSkyCredentials("""{ "client_id" : "a", "client_secret": "b" }"""))
        assertNull(com.contrail.contrail.core.data.settings.ApiKeys.openSkyCredentials("plain-client-id"))
    }
}

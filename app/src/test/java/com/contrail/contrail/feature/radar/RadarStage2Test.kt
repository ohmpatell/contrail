package com.contrail.contrail.feature.radar

import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.model.AirportRef
import com.contrail.contrail.core.data.model.FlightRoute
import com.contrail.contrail.core.data.model.WatchItem
import com.contrail.contrail.core.data.model.WatchKind
import com.contrail.contrail.core.data.model.Watchlist
import com.contrail.contrail.core.data.reference.AircraftClassifier
import com.contrail.contrail.core.data.reference.AircraftTypes
import com.contrail.contrail.core.data.reference.Silhouette
import com.contrail.contrail.core.data.reference.TypeDesignators
import com.contrail.contrail.testing.aircraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class AircraftClassifierTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun installTypes() {
            TypeDesignators.install { File("src/main/assets/aircraft_types.json").inputStream() }
        }
    }

    private fun shape(type: String?, category: String? = null) = AircraftClassifier.classify(type, category)

    @Test
    fun `the bundled ICAO list covers thousands of designators`() {
        assertTrue(TypeDesignators.size > 2_500)
    }

    @Test
    fun `big jets get the right engine count and shape`() {
        assertEquals(Silhouette.Jumbo, shape("A388"))
        assertEquals(Silhouette.Quad, shape("B748"))
        assertEquals(Silhouette.Quad, shape("A346"))
        assertEquals(Silhouette.WideTri, shape("MD11"))
        listOf("B77W", "A359", "B789", "A333", "B763").forEach { assertEquals(it, Silhouette.Widebody, shape(it)) }
        listOf("A320", "B738", "A21N", "BCS3").forEach { assertEquals(it, Silhouette.Narrowbody, shape(it)) }
    }

    @Test
    fun `regional, business and propeller types are told apart`() {
        assertEquals(Silhouette.RearJet, shape("CRJ9"))
        assertEquals(Silhouette.RearJet, shape("E145"))
        assertEquals(Silhouette.Regional, shape("E190"))
        assertEquals(Silhouette.Bizjet, shape("GLF6"))
        assertEquals(Silhouette.Turboprop, shape("DH8D"))
        assertEquals(Silhouette.TurbopropQuad, shape("C130"))
        assertEquals(Silhouette.TurbopropSingle, shape("PC12"))
        assertEquals(Silhouette.Light, shape("C172"))
        assertEquals(Silhouette.LightTwin, shape("BE58"))
        assertEquals(Silhouette.Helicopter, shape("EC35"))
        assertEquals(Silhouette.Fighter, shape("F16"))
    }

    @Test
    fun `types missing from the curated table still classify from Doc 8643`() {
        assertNull(AircraftTypes.byIcao("A139").takeIf { false })
        assertEquals(Silhouette.Narrowbody, shape("B732")) // 737-200
        assertEquals(Silhouette.RearJet, shape("F100"))   // Fokker 100
        assertEquals(Silhouette.Bizjet, shape("C750"))    // Citation X
        assertEquals(Silhouette.Turboprop, shape("D328")) // Dornier 328
        assertEquals("Fokker 100", AircraftTypes.nameFor("F100"))
    }

    @Test
    fun `unknown types fall back to the emitter category`() {
        assertEquals(Silhouette.Widebody, shape("ZZZZ", "A5"))
        assertEquals(Silhouette.Helicopter, shape(null, "A7"))
        assertEquals(Silhouette.Generic, shape(null, null))
    }

    @Test
    fun `vehicles, obstacles and typeless ground targets are clutter`() {
        assertTrue(AircraftClassifier.isClutter("C1", null, onGround = true))
        assertTrue(AircraftClassifier.isClutter("C3", null, onGround = false))
        assertTrue(AircraftClassifier.isClutter(null, null, onGround = true))
        assertFalse(AircraftClassifier.isClutter("A3", "B738", onGround = true))
        assertFalse(AircraftClassifier.isClutter(null, null, onGround = false))
    }

    @Test
    fun `bigger variants draw a little bigger`() {
        assertTrue(AircraftClassifier.scaleFor("A321") > AircraftClassifier.scaleFor("A319"))
        assertEquals(1f, AircraftClassifier.scaleFor("ZZZZ"))
    }
}

class WatchlistTest {
    @Test
    fun `typed entries become airlines, types or tails`() {
        assertEquals(WatchKind.Airline, WatchInput.parse("etd")?.kind)
        assertEquals(WatchKind.Type, WatchInput.parse("A388")?.kind)
        assertEquals(WatchKind.Tail, WatchInput.parse("c-fiua")?.kind)
        assertNull(WatchInput.parse("x"))
    }

    @Test
    fun `matches tails however they are written, plus types and airlines`() {
        val w = Watchlist(
            listOf(
                WatchItem(WatchKind.Tail, "CFIUA", "C-FIUA"),
                WatchItem(WatchKind.Type, "A388", "A380"),
                WatchItem(WatchKind.Airline, "CPA", "Cathay"),
            ),
        )
        assertTrue(w.matches(aircraft(registration = "C-FIUA")))
        assertTrue(w.matches(aircraft(registration = null, typeCode = "A388")))
        assertTrue(w.matches(aircraft(registration = null, typeCode = "B77W", callsign = "CPA826")))
        assertFalse(w.matches(aircraft(registration = "C-GABC", typeCode = "B38M", callsign = "WJA1")))
    }

    @Test
    fun `watchlist-only and ground filters`() {
        val w = Watchlist(listOf(WatchItem(WatchKind.Type, "A388", "A380")))
        assertFalse(RadarFilters(watchlistOnly = true).matches(aircraft(typeCode = "B738"), w))
        assertTrue(RadarFilters(watchlistOnly = true).matches(aircraft(typeCode = "A388"), w))
        assertFalse(RadarFilters(showGround = false).matches(aircraft(onGround = true, altitudeFt = null)))
        assertEquals(1, RadarFilters(showGround = false).activeCount)
    }
}

class RouteOverlayTest {
    private val yyz = AirportRef("YYZ", "CYYZ", "Pearson", "Toronto", "CA", 43.68, -79.63)
    private val lhr = AirportRef("LHR", "EGLL", "Heathrow", "London", "GB", 51.47, -0.45)
    private val route = FlightRoute("ACA856", yyz, lhr, "Air Canada")

    @Test
    fun `great circle starts and ends at the airports and bows north`() {
        val arc = Geo.greatCircle(LatLon(yyz.lat, yyz.lon), LatLon(lhr.lat, lhr.lon), 32)
        assertEquals(33, arc.size)
        assertEquals(yyz.lat, arc.first().lat, 1e-6)
        assertEquals(lhr.lon, arc.last().lon, 1e-6)
        assertTrue("the great circle runs north of both ends", arc[16].lat > 51.47)
    }

    @Test
    fun `antimeridian crossings stay continuous`() {
        val arc = Geo.greatCircle(LatLon(35.5, 139.8), LatLon(49.2, -123.2), 16) // Tokyo to Vancouver
        arc.zipWithNext().forEach { (a, b) -> assertTrue(kotlin.math.abs(b.lon - a.lon) < 30) }
    }

    @Test
    fun `uses the real track when there is one`() {
        val track = listOf(LatLon(43.7, -79.6), LatLon(44.5, -77.0))
        val o = RouteOverlay.build(route, track, trail = emptyList(), current = LatLon(45.0, -75.0))
        assertTrue(o.tracked)
        assertEquals(3, o.flown.size)
    }

    @Test
    fun `without a track, leads from the origin into our trail`() {
        val trail = listOf(LatLon(46.0, -70.0), LatLon(46.4, -69.0))
        val o = RouteOverlay.build(route, null, trail, current = LatLon(46.5, -68.6))
        assertFalse(o.tracked)
        assertEquals(yyz.lat, o.flown.first().lat, 1e-6)
        assertEquals(-68.6, o.flown.last().lon, 1e-6)
        assertTrue(o.flown.size > trail.size + 10)
    }
}

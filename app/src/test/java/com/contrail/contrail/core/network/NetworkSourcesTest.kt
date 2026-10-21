package com.contrail.contrail.core.network

import com.contrail.contrail.core.network.photo.PlanespottersPhotoSource
import com.contrail.contrail.core.network.route.AdsbdbRouteSource
import com.contrail.contrail.core.network.traffic.FailoverLiveTrafficSource
import com.contrail.contrail.core.network.traffic.LiveSnapshot
import com.contrail.contrail.core.network.traffic.LiveTrafficException
import com.contrail.contrail.core.network.traffic.LiveTrafficSource
import com.contrail.contrail.core.network.traffic.ReadsbApiSource
import com.contrail.contrail.testing.ADSBDB_ROUTE_JSON
import com.contrail.contrail.testing.ADSBDB_UNKNOWN_JSON
import com.contrail.contrail.testing.ADSB_FI_JSON
import com.contrail.contrail.testing.ADSB_LOL_JSON
import com.contrail.contrail.testing.PLANESPOTTERS_EMPTY_JSON
import com.contrail.contrail.testing.PLANESPOTTERS_JSON
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
    HttpClient(MockEngine(handler)) {
        install(ContentNegotiation) { json(ContrailHttp.json) }
        expectSuccess = false
    }

private val noThrottle = RequestThrottle(0)

class ReadsbApiSourceTest {
    @Test
    fun `parses adsb_lol and normalises fields`() = runTest {
        var url = ""
        val source = ReadsbApiSource("adsb.lol", client { req -> url = req.url.toString(); respond(ADSB_LOL_JSON, headers = jsonHeaders) },
            urlFor = { lat, lon, r -> "https://api.adsb.lol/v2/point/$lat/$lon/$r" }, throttle = noThrottle, clock = { 10_000L })
        val snap = source.fetchAround(43.6777, -79.6248, 300)

        assertEquals("radius is capped at 250 nm", "https://api.adsb.lol/v2/point/43.6777/-79.6248/250", url)
        assertEquals("entries without a position are skipped", 3, snap.aircraft.size)
        val rpa = snap.aircraft.first { it.hex == "aaa90c" }
        assertEquals("RPA3549", rpa.callsign)
        assertEquals(34_000, rpa.altitudeFt)
        assertEquals("position time accounts for seen_pos", 10_000L - 244, rpa.positionTimeMs)
        assertFalse(rpa.military)
        assertTrue(snap.aircraft.first { it.hex == "c06b28" }.military)
        val ground = snap.aircraft.first { it.hex == "c0123a" }
        assertTrue(ground.onGround)
        assertNull(ground.altitudeFt)
    }

    @Test
    fun `parses adsb_fi's aircraft key and fractional now`() = runTest {
        val source = ReadsbApiSource("adsb.fi", client { respond(ADSB_FI_JSON, headers = jsonHeaders) },
            urlFor = { _, _, _ -> "https://opendata.adsb.fi/x" }, throttle = noThrottle)
        val snap = source.fetchAround(43.0, -79.0, 50)
        assertEquals(listOf("a48392"), snap.aircraft.map { it.hex })
        assertEquals("DV20", snap.aircraft.single().typeCode)
    }

    @Test
    fun `rate limiting surfaces the retry-after delay`() = runTest {
        val source = ReadsbApiSource("adsb.lol", client {
            respond("", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "90"))
        }, urlFor = { _, _, _ -> "https://x" }, throttle = noThrottle)
        try {
            source.fetchAround(0.0, 0.0, 10)
            fail("expected a rate-limit error")
        } catch (e: LiveTrafficException) {
            assertEquals(90_000L, e.retryAfterMs)
        }
    }
}

class FailoverTest {
    private class Fake(override val name: String, var fail: Throwable? = null) : LiveTrafficSource {
        var calls = 0
        override suspend fun fetchAround(lat: Double, lon: Double, radiusNm: Int): LiveSnapshot {
            calls++
            fail?.let { throw it }
            return LiveSnapshot(emptyList(), 0, name)
        }
    }

    @Test
    fun `falls back and then skips the failing provider during its cool-down`() = runTest {
        val primary = Fake("primary", LiveTrafficException("down"))
        val backup = Fake("backup")
        var now = 0L
        val failover = FailoverLiveTrafficSource(listOf(primary, backup), coolDownMs = 60_000, clock = { now })

        assertEquals("backup", failover.fetchAround(0.0, 0.0, 1).source)
        primary.fail = null
        now = 30_000
        assertEquals("backup is tried first while primary cools down", "backup", failover.fetchAround(0.0, 0.0, 1).source)
        assertEquals(1, primary.calls)
        now = 61_000
        assertEquals("primary", failover.fetchAround(0.0, 0.0, 1).source)
    }

    @Test
    fun `retry-after extends the cool-down`() = runTest {
        val primary = Fake("primary", LiveTrafficException("429", retryAfterMs = 300_000))
        val backup = Fake("backup")
        var now = 0L
        val failover = FailoverLiveTrafficSource(listOf(primary, backup), coolDownMs = 60_000, clock = { now })
        failover.fetchAround(0.0, 0.0, 1)
        primary.fail = null
        now = 120_000
        assertEquals("backup", failover.fetchAround(0.0, 0.0, 1).source)
    }

    @Test(expected = LiveTrafficException::class)
    fun `throws when everything fails`() = runTest {
        FailoverLiveTrafficSource(listOf(Fake("a", IllegalStateException()), Fake("b", IllegalStateException())))
            .fetchAround(0.0, 0.0, 1)
    }
}

class AdsbdbRouteSourceTest {
    @Test
    fun `parses a known route`() = runTest {
        val route = AdsbdbRouteSource(client { respond(ADSBDB_ROUTE_JSON, headers = jsonHeaders) }, ContrailHttp.json)
            .routeForCallsign("aca856")!!
        assertEquals("YYZ", route.origin.code)
        assertEquals("LHR", route.destination.code)
        assertEquals("London", route.destination.city)
        assertEquals("Air Canada", route.airlineName)
    }

    @Test
    fun `unknown callsigns are a miss, not an error`() = runTest {
        val source = AdsbdbRouteSource(client { respond(ADSBDB_UNKNOWN_JSON, HttpStatusCode.NotFound, jsonHeaders) }, ContrailHttp.json)
        assertNull(source.routeForCallsign("XXX000"))
        val ok = AdsbdbRouteSource(client { respond(ADSBDB_UNKNOWN_JSON, headers = jsonHeaders) }, ContrailHttp.json)
        assertNull(ok.routeForCallsign("XXX000"))
    }
}

class PlanespottersPhotoSourceTest {
    @Test
    fun `parses credit and link`() = runTest {
        val photo = PlanespottersPhotoSource(client { respond(PLANESPOTTERS_JSON, headers = jsonHeaders) }).photoFor("C00E9D", null)!!
        assertEquals("Frederick Tremblay", photo.photographer)
        assertTrue(photo.pageUrl.startsWith("https://www.planespotters.net/photo/1961022"))
        assertTrue(photo.largeUrl.endsWith("_280.jpg"))
    }

    @Test
    fun `falls back to the registration when the hex has no photo`() = runTest {
        val urls = mutableListOf<String>()
        val source = PlanespottersPhotoSource(client { req ->
            urls += req.url.encodedPath
            respond(if (req.url.encodedPath.contains("/reg/")) PLANESPOTTERS_JSON else PLANESPOTTERS_EMPTY_JSON, headers = jsonHeaders)
        })
        assertEquals("Frederick Tremblay", source.photoFor("c00e9d", "c-ffnw")?.photographer)
        assertEquals(listOf("/pub/photos/hex/c00e9d", "/pub/photos/reg/C-FFNW"), urls)
    }

    @Test
    fun `no photo anywhere is null`() = runTest {
        assertNull(PlanespottersPhotoSource(client { respond(PLANESPOTTERS_EMPTY_JSON, headers = jsonHeaders) }).photoFor("abc123", null))
    }
}

class ThrottleTest {
    @Test
    fun `waits out the minimum interval`() = runTest {
        var now = 0L
        val sleeps = mutableListOf<Long>()
        val throttle = RequestThrottle(1_000, clock = { now }, sleep = { sleeps += it; now += it })
        throttle.run { }
        now += 300
        throttle.run { }
        assertEquals(listOf(700L), sleeps)
    }

    @Test
    fun `user agent identifies the app with a contact url`() {
        val ua = ContrailHttp.userAgent("0.1.7")
        assertTrue(ua.startsWith("Contrail/0.1.7"))
        assertTrue(ua.contains("https://github.com/ohmpatell/contrail"))
    }
}

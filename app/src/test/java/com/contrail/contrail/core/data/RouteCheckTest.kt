package com.contrail.contrail.core.data

import com.contrail.contrail.core.data.model.AirportRef
import com.contrail.contrail.core.data.model.FlightRoute
import com.contrail.contrail.core.data.route.RouteCheck
import com.contrail.contrail.core.data.route.RouteVerdict
import com.contrail.contrail.core.network.ContrailHttp
import com.contrail.contrail.core.network.route.CombinedRouteSource
import com.contrail.contrail.core.network.route.RouteSource
import com.contrail.contrail.core.network.route.StandingDataRouteSource
import com.contrail.contrail.testing.aircraft
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun ap(code: String, icao: String, city: String, lat: Double, lon: Double) = AirportRef(code, icao, city, city, null, lat, lon)

private val ICN = ap("ICN", "RKSI", "Seoul", 37.4691, 126.4510)
private val EWR = ap("EWR", "KEWR", "Newark", 40.6925, -74.1687)
private val CLE = ap("CLE", "KCLE", "Cleveland", 41.4117, -81.8498)
private val DEN = ap("DEN", "KDEN", "Denver", 39.8617, -104.673)
private val YHZ = ap("YHZ", "CYHZ", "Halifax", 44.8808, -63.5086)
private val CUN = ap("CUN", "MMUN", "Cancun", 21.0365, -86.8771)
private val YYZ = ap("YYZ", "CYYZ", "Toronto", 43.6772, -79.6306)
private val YTZ = ap("YTZ", "CYTZ", "Toronto", 43.6275, -79.3962)
private val YKF = ap("YKF", "CYKF", "Kitchener", 43.4608, -80.3786)
private val aroundToronto = listOf(YTZ, YYZ, YKF)

private fun route(vararg airports: AirportRef, alternatives: List<FlightRoute> = emptyList()) =
    FlightRoute("TEST1", airports.first(), airports.last(), null, stops = if (airports.size > 2) airports.toList() else emptyList(), alternatives = alternatives)

class RouteCheckTest {

    // UAL286 over Orillia, a 787 heading south-east at FL370 (seen on the radar, Nov 2026).
    private val ual286 = aircraft(callsign = "UAL286", lat = 44.55, lon = -79.35, altitudeFt = 37_000, trackDeg = 135f, verticalRateFpm = 0)

    @Test
    fun `the database whose route fits wins, whichever answered first`() {
        val adsbdbFirst = RouteCheck.check(route(CLE, DEN, alternatives = listOf(route(ICN, EWR))), ual286, aroundToronto)
        val standingFirst = RouteCheck.check(route(ICN, EWR, alternatives = listOf(route(CLE, DEN))), ual286, aroundToronto)
        for (v in listOf(adsbdbFirst, standingFirst)) {
            v as RouteVerdict.OnRoute
            assertEquals("ICN", v.from.code)
            assertEquals("EWR", v.to.code)
        }
    }

    @Test
    fun `a route the plane isn't flying is a mismatch, not shown as fact`() {
        val v = RouteCheck.check(route(CLE, DEN), ual286, aroundToronto)
        assertTrue(v is RouteVerdict.Mismatch)
    }

    @Test
    fun `descending into Toronto on a Halifax to Cancun callsign is landing at Toronto`() {
        // East of Pearson at 7,000 ft, lined up westbound; Billy Bishop is closer but off to the side.
        val plane = aircraft(lat = 43.80, lon = -79.02, altitudeFt = 7_000, trackDeg = 255f, verticalRateFpm = -1_200)
        val v = RouteCheck.check(route(YHZ, CUN), plane, aroundToronto)
        v as RouteVerdict.Landing
        assertEquals("YYZ", v.at.code)
    }

    @Test
    fun `a through flight picks the leg being flown`() {
        val through = route(YHZ, YYZ, CUN)
        val firstLeg = RouteCheck.check(through, aircraft(lat = 45.2, lon = -72.0, altitudeFt = 36_000, trackDeg = 262f), emptyList())
        firstLeg as RouteVerdict.OnRoute
        assertEquals(0, firstLeg.leg)
        assertEquals("YYZ", firstLeg.to.code)
        assertTrue(firstLeg.multiStop)

        val secondLeg = RouteCheck.check(through, aircraft(lat = 33.0, lon = -83.3, altitudeFt = 37_000, trackDeg = 195f), emptyList())
        secondLeg as RouteVerdict.OnRoute
        assertEquals(1, secondLeg.leg)
        assertEquals("CUN", secondLeg.to.code)
    }

    @Test
    fun `climbing away from an airport the route doesn't start at is departing from it`() {
        val plane = aircraft(lat = 43.80, lon = -79.90, altitudeFt = 5_000, trackDeg = 300f, verticalRateFpm = 2_500)
        val v = RouteCheck.check(route(CLE, DEN), plane, aroundToronto)
        v as RouteVerdict.Departing
        assertEquals("YYZ", v.from.code)
    }

    @Test
    fun `descending into its own destination stays on the route`() {
        val plane = aircraft(lat = 43.80, lon = -79.02, altitudeFt = 7_000, trackDeg = 255f, verticalRateFpm = -1_200)
        val v = RouteCheck.check(route(YHZ, YYZ), plane, aroundToronto)
        v as RouteVerdict.OnRoute
        assertEquals("YYZ", v.to.code)
    }

    @Test
    fun `no route on file and nothing to go on is no route`() {
        assertEquals(RouteVerdict.NoRoute, RouteCheck.check(null, ual286, emptyList()))
    }
}

class StandingDataRouteSourceTest {
    private val json = """
        {"callsign":"WJA1234","number":"1234","airline_code":"WJA","airport_codes":"CYHZ-CYYZ-MMUN",
         "_airport_codes_iata":"YHZ-YYZ-CUN","_airports":[
          {"name":"Halifax Stanfield","icao":"CYHZ","iata":"YHZ","location":"Halifax","countryiso2":"CA","lat":44.8808,"lon":-63.5086,"alt_feet":477.0},
          {"name":"Lester B. Pearson","icao":"CYYZ","iata":"YYZ","location":"Toronto","countryiso2":"CA","lat":43.6772,"lon":-79.6306,"alt_feet":569.0},
          {"name":"Cancun","icao":"MMUN","iata":"CUN","location":"Cancun","countryiso2":"MX","lat":21.0365,"lon":-86.8771,"alt_feet":22.0}]}
    """.trimIndent()

    private fun client(status: HttpStatusCode = HttpStatusCode.OK, body: String = json, onUrl: (String) -> Unit = {}) = HttpClient(
        MockEngine { req ->
            onUrl(req.url.toString())
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    ) { expectSuccess = false }

    @Test
    fun `reads every stop of a multi-stop flight number`() = runTest {
        var url = ""
        val r = StandingDataRouteSource(client { url = it }, ContrailHttp.json).routeForCallsign("wja1234")!!
        assertEquals("https://vrs-standing-data.adsb.lol/routes/WJ/WJA1234.json", url)
        assertEquals(listOf("YHZ", "YYZ", "CUN"), r.airports.map { it.code })
        assertEquals("YHZ", r.origin.code)
        assertEquals("CUN", r.destination.code)
    }

    @Test
    fun `an unknown callsign is a miss`() = runTest {
        assertNull(StandingDataRouteSource(client(HttpStatusCode.NotFound, ""), ContrailHttp.json).routeForCallsign("ZZZ999"))
    }

    @Test
    fun `both databases are kept when they disagree`() = runTest {
        val a = RouteSource { route(ICN, EWR) }
        val b = RouteSource { route(CLE, DEN) }
        val same = RouteSource { route(ICN, EWR) }
        val combined = CombinedRouteSource(listOf(a, b, same)).routeForCallsign("UAL286")!!
        assertEquals("ICN", combined.origin.code)
        assertEquals(listOf("CLE"), combined.alternatives.map { it.origin.code })
    }
}

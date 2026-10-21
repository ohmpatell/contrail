package com.contrail.contrail.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.data.model.AirportRef
import com.contrail.contrail.core.data.model.FlightRoute
import com.contrail.contrail.core.data.repository.AirportRepository
import com.contrail.contrail.core.data.repository.AirportsAsset
import com.contrail.contrail.core.data.settings.ApiKeys
import com.contrail.contrail.core.data.settings.LogostreamUsage
import com.contrail.contrail.core.database.AirportWithUser
import com.contrail.contrail.core.database.ContrailDatabase
import com.contrail.contrail.core.database.RunwayEntity
import com.contrail.contrail.core.designsystem.livery.EngineLayout
import com.contrail.contrail.core.designsystem.livery.Liveries
import com.contrail.contrail.core.designsystem.livery.Profiles
import com.contrail.contrail.core.network.airport.WeatherParser
import com.contrail.contrail.core.network.logostream.LogostreamParser
import com.contrail.contrail.core.network.news.RssParser
import com.contrail.contrail.feature.airport.LiveBoard
import com.contrail.contrail.testing.aircraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

private const val LS_AIRLINE = """{"data":[{"id":96627,"iata":"EY","icao":"ETD","callsign":"ETIHAD","name":"Etihad Airways","alliance":null,"country_code":"AE","airline_group":"Etihad Aviation Group","logo":{"icon":"https://airlines-api.logostream.dev/airlines/icao/ETD?key=FREE","logo_white":"https://airlines-api.logostream.dev/airlines/icao/ETD?variant=logo-white&key=FREE","tail":"https://airlines-api.logostream.dev/airlines/icao/ETD?variant=tail&key=FREE"},"colors":"#676666","fleet_size":115,"year_founded":2003,"hubs":[],"policies":{}}],"total":1,"truncated":false,"page":1,"limit":50}"""
private const val LS_ROUTES = """{"data":[{"id":349547,"departure_iata":"YYZ","arrival_iata":"FCO","airlines":[{"airline_iata":"AC","airline_icao":"ACA","alliance":"star_alliance","weekdays":["mon"]},{"airline_iata":"TS","airline_icao":"TSC","alliance":null,"weekdays":[]}],"distance_km":7086,"duration_min":560}],"total":181,"truncated":true,"page":1,"limit":1}"""
private const val LS_STATS = """{"type":"airport","airport":{"iata":"YYZ","icao":"CYYZ","name":"Lester B. Pearson International","timezone":"America/Toronto"},"stats":{"destinations_count":181,"airlines_count":81,"countries_reached":61},"hubs":[{"iata":"AC","icao":"ACA","name":"Air Canada"}],"focus_cities":[]}"""
private const val METAR = """[{"icaoId":"CYYZ","obsTime":1792548000,"temp":11,"dewp":6,"wdir":240,"wspd":12,"visib":"15","altim":1029.2,"rawOb":"METAR CYYZ 210200Z 24012KT 15SM SKC 11/06 A3039","clouds":[],"fltCat":"VFR"}]"""
private const val RSS = """<?xml version="1.0"?><rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/"><channel><title>Feed</title>
<item><title>Etihad adds &amp; expands A350 flying</title><link>https://example.org/a</link><description><![CDATA[<p><img src="https://example.org/a.jpg"/>Etihad will fly its <b>A350</b> to Toronto.</p>]]></description><pubDate>Tue, 20 Oct 2026 18:00:00 +0000</pubDate></item>
<item><title>Cathay unveils new lounge - Simple Flying</title><link>https://example.org/b</link><pubDate>Mon, 19 Oct 2026 08:30:00 GMT</pubDate><source url="x">Simple Flying</source><media:content url="https://example.org/b.jpg" type="image/jpeg"/></item>
</channel></rss>"""

class LogostreamParserTest {
    @Test
    fun `airline profile with logo urls and brand colour`() {
        val a = LogostreamParser.airline(LS_AIRLINE)!!
        assertEquals("Etihad Airways", a.name)
        assertTrue(a.logo!!.tail!!.contains("variant=tail"))
        assertEquals("#676666", a.colors)
    }

    @Test
    fun `routes page and truncation flag`() {
        val (routes, more) = LogostreamParser.routes(LS_ROUTES)
        assertTrue(more)
        assertEquals("FCO", routes.single().to)
        assertEquals(listOf("ACA", "TSC"), routes.single().airlines.map { it.icao })
        assertEquals(routes, LogostreamParser.decodeRoutes(LogostreamParser.encodeRoutes(routes)))
    }

    @Test
    fun `airport stats`() {
        val s = LogostreamParser.stats(LS_STATS)
        assertEquals("America/Toronto", s.airport?.timezone)
        assertEquals(81, s.stats?.airlines)
        assertEquals("ACA", s.hubs.single().icao)
    }

    @Test
    fun `usage keeps a reserve and reports the used share`() {
        assertTrue(LogostreamUsage(20_000, 400, "2026-11-01", 3, 0).exhausted)
        val u = LogostreamUsage(20_000, 15_000, null, 3, 0)
        assertFalse(u.exhausted)
        assertEquals(0.25f, u.usedFraction!!, 1e-4f)
    }

    @Test
    fun `keys are masked for display`() {
        assertEquals("abcd••••yz", ApiKeys.mask("abcdefghijklmnopqrstuvwxyz"))
        assertEquals("••••", ApiKeys.mask("short"))
    }
}

class WeatherAndBoardTest {
    @Test
    fun `metar decodes wind, category and raw text`() {
        val m = WeatherParser.metar(METAR)!!
        assertEquals("VFR", m.fltCat)
        assertEquals(12, m.wspd)
        assertTrue(m.rawOb!!.startsWith("METAR CYYZ"))
    }

    private val rwys = listOf(
        RunwayEntity("CYYZ", "06L", "24R", 9697, 200, "ASP", null, null, null, null, 57.0, 237.0),
        RunwayEntity("CYYZ", "15L", "33R", 11120, 200, "ASP", null, null, null, null, 147.0, 327.0),
    )

    @Test
    fun `the runway end most into the wind is likely active`() {
        assertEquals("24R", LiveBoard.likelyActiveEnd(rwys, 240.0))
        assertEquals("33R", LiveBoard.likelyActiveEnd(rwys, 320.0))
        assertNull(LiveBoard.likelyActiveEnd(rwys, null))
    }

    private val yyz = AirportWithUser("CYYZ", "YYZ", "Pearson", "Toronto", "CA", "Canada", 43.68, -79.63, 569, "L", true, "America/Toronto", null, null)

    @Test
    fun `board sorts descending traffic into arrivals and climbing traffic into departures`() {
        // 30 km west, heading east toward the field, descending: an arrival.
        val inbound = aircraft(hex = "a", callsign = "ACA101", lat = 43.68, lon = -80.0, altitudeFt = 6_000, trackDeg = 90f, verticalRateFpm = -900, groundSpeedKt = 220f)
        // 20 km east, heading east away, climbing: a departure.
        val outbound = aircraft(hex = "b", callsign = "WJA202", lat = 43.68, lon = -79.38, altitudeFt = 8_000, trackDeg = 90f, verticalRateFpm = 2_000)
        // Route data overrides geometry: this one is going to YYZ even though it's level.
        val routed = aircraft(hex = "c", callsign = "ETD141", lat = 43.9, lon = -79.63, altitudeFt = 11_000, trackDeg = 0f, verticalRateFpm = 0)
        val routes = mapOf(
            "ETD141" to FlightRoute("ETD141", AirportRef("AUH", null, "Abu Dhabi", "Abu Dhabi", "AE", 24.4, 54.6), AirportRef("YYZ", null, "Pearson", "Toronto", "CA", 43.68, -79.63), null),
        )
        val (dep, arr) = LiveBoard.build(yyz, listOf(inbound, outbound, routed), routes)
        assertEquals(listOf("b"), dep.map { it.hex })
        assertEquals(setOf("a", "c"), arr.map { it.hex }.toSet())
        assertEquals("AUH", arr.first { it.hex == "c" }.otherEnd)
        assertTrue(arr.first { it.hex == "a" }.minutes!! in 3..6)
    }
}

class LiveryTest {
    @Test
    fun `brand colours parse and curated liveries win`() {
        assertNotNull(Liveries.parseHex("#676666"))
        assertNull(Liveries.parseHex("nope"))
        val etihad = Liveries.forAirline("ETD")
        val generic = Liveries.forAirline("XYZ", "#ff0000")
        assertTrue(etihad != generic)
        assertEquals(Liveries.parseHex("#ff0000"), generic.tail)
    }

    @Test
    fun `profiles follow the type`() {
        assertTrue(Profiles.forType("A388").doubleDeck)
        assertTrue(Profiles.forType("B748").hump > 0f)
        assertEquals(EngineLayout.Rear, Profiles.forType("CRJ9").engines)
        assertEquals(EngineLayout.Tail, Profiles.forType("MD11").engines)
        assertEquals(EngineLayout.Props, Profiles.forType("DH8D").engines)
    }
}

@RunWith(AndroidJUnit4::class)
class RssParserTest {
    @Test
    fun `reads titles, links, images, dates and Google News publishers`() {
        val items = RssParser.parse(RSS, publisher = null)
        assertEquals(2, items.size)
        val a = items[0]
        assertEquals("Etihad adds & expands A350 flying", a.title)
        assertEquals("https://example.org/a.jpg", a.imageUrl)
        assertTrue(a.summary.contains("A350 to Toronto"))
        assertEquals(1792519200000L, a.publishedAtMs)
        val b = items[1]
        assertEquals("Simple Flying", b.source)
        assertEquals("Cathay unveils new lounge", b.title)
        assertEquals("https://example.org/b.jpg", b.imageUrl)
    }
}

@RunWith(AndroidJUnit4::class)
class AirportRepositoryTest {
    private lateinit var db: ContrailDatabase
    private lateinit var repo: AirportRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), ContrailDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = AirportRepository(db, AirportsAsset { File("src/main/assets/airports.json").inputStream() }, Clock { 1L })
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `imports the bundled data and searches by code, city and name`() = runTest {
        assertEquals("CYYZ", repo.search("YYZ").first().first().icao)
        assertTrue(repo.search("toronto").first().any { it.iata == "YTZ" })
        assertEquals("OMAA", repo.search("AUH").first().first().icao)
        val runways = repo.runways("CYYZ").first()
        assertTrue(runways.size >= 5)
        assertEquals("America/Toronto", repo.byIata("YYZ")?.timezone)
    }

    @Test
    fun `favourites and visits are remembered`() = runTest {
        repo.ensureImported()
        repo.setFavourite("CYYZ", true)
        repo.setVisited("VHHH", true)
        assertEquals(listOf("YYZ"), repo.favourites().first().map { it.iata })
        assertEquals(listOf("HKG"), repo.visited().first().map { it.iata })
    }
}

package com.contrail.contrail.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.compose.setSingletonImageLoaderFactory
import com.contrail.contrail.core.data.model.AircraftPhoto
import com.contrail.contrail.core.data.model.AirportRef
import com.contrail.contrail.core.data.model.FlightRoute
import com.contrail.contrail.core.data.repository.AirportWeather
import com.contrail.contrail.core.data.repository.SightingEntity
import com.contrail.contrail.core.data.settings.ApiService
import com.contrail.contrail.core.data.settings.KeySource
import com.contrail.contrail.core.data.settings.KeyStatus
import com.contrail.contrail.core.data.settings.LogostreamUsage
import com.contrail.contrail.core.database.AirportWithUser
import com.contrail.contrail.core.database.FrequencyEntity
import com.contrail.contrail.core.database.RunwayEntity
import com.contrail.contrail.core.designsystem.component.AuroraBackground
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.livery.Liveries
import com.contrail.contrail.core.designsystem.livery.LiveryRender
import com.contrail.contrail.core.designsystem.livery.Profiles
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.core.network.airport.Metar
import com.contrail.contrail.core.network.airport.MetarCloud
import com.contrail.contrail.core.network.airport.Taf
import com.contrail.contrail.core.network.logostream.LsAirportStats
import com.contrail.contrail.core.network.logostream.LsStats
import com.contrail.contrail.core.network.news.NewsArticle
import com.contrail.contrail.core.network.news.NewsFeed
import com.contrail.contrail.feature.aircraft.AircraftScreen
import com.contrail.contrail.feature.aircraft.AircraftUiState
import com.contrail.contrail.feature.aircraft.HeroMode
import com.contrail.contrail.feature.airport.AirlineServed
import com.contrail.contrail.feature.airport.AirportScreen
import com.contrail.contrail.feature.airport.AirportUiState
import com.contrail.contrail.feature.airport.BoardRow
import com.contrail.contrail.feature.airport.Destination
import com.contrail.contrail.feature.airports.AirportsUiState
import com.contrail.contrail.feature.airports.NewsFilter
import com.contrail.contrail.feature.settings.SettingsScreen
import com.contrail.contrail.feature.settings.SettingsUiState
import com.contrail.contrail.testing.aircraft
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

internal const val NOW = 1_792_548_000_000L

private fun airport(icao: String, iata: String, name: String, city: String, country: String, lat: Double, lon: Double, tz: String, fav: Boolean = false, visited: Boolean = false) =
    AirportWithUser(icao, iata, name, city, "XX", country, lat, lon, 500, "L", true, tz, fav, visited)

private val yyz = airport("CYYZ", "YYZ", "Lester B. Pearson International Airport", "Toronto", "Canada", 43.6772, -79.6306, "America/Toronto", fav = true, visited = true)
private val auh = airport("OMAA", "AUH", "Zayed International Airport", "Abu Dhabi", "United Arab Emirates", 24.433, 54.651, "Asia/Dubai", fav = true)
private val hkg = airport("VHHH", "HKG", "Hong Kong International Airport", "Hong Kong", "Hong Kong", 22.308, 113.918, "Asia/Hong_Kong", visited = true)

internal val airportsState = AirportsUiState(
    favourites = listOf(yyz, auh),
    visited = listOf(yyz, hkg),
    filters = listOf(AirportsUiState.AllNews, NewsFilter("Toronto", NewsFeed.About("Toronto Pearson airport")), NewsFilter("Etihad Airways", NewsFeed.About("Etihad Airways"))),
    news = listOf(
        NewsArticle("Etihad brings the A350-1000 to Toronto for the winter season", "The upgauge adds premium seats on the Abu Dhabi route from December.", "https://example.org/1", "Simple Flying", "https://example.org/1.jpg", NOW - 42 * 60_000),
        NewsArticle("Cathay Pacific confirms Toronto–Hong Kong frequency boost", "", "https://example.org/2", "AeroTime", null, NOW - 5 * 3_600_000),
        NewsArticle("Pearson opens new Terminal 3 security hall", "Ten new lanes and automated bins aim to halve wait times.", "https://example.org/3", "AirlineGeeks", "https://example.org/3.jpg", NOW - 26 * 3_600_000),
    ),
)

private fun runway(le: String, he: String, lat1: Double, lon1: Double, lat2: Double, lon2: Double, h1: Double, len: Int) =
    RunwayEntity("CYYZ", le, he, len, 200, "ASP", lat1, lon1, lat2, lon2, h1, (h1 + 180) % 360)

private val airportState = AirportUiState(
    airport = yyz,
    runways = listOf(
        runway("05", "23", 43.6640, -79.6150, 43.6890, -79.5810, 57.0, 11120),
        runway("06L", "24R", 43.6490, -79.6540, 43.6670, -79.6230, 57.0, 9697),
        runway("06R", "24L", 43.6730, -79.6300, 43.6900, -79.6000, 57.0, 9000),
        runway("15L", "33R", 43.6890, -79.6390, 43.6620, -79.6140, 147.0, 11050),
        runway("15R", "33L", 43.6990, -79.6290, 43.6760, -79.6070, 147.0, 9088),
    ),
    frequencies = listOf(
        FrequencyEntity("CYYZ", "ATIS", "ATIS", 120.825),
        FrequencyEntity("CYYZ", "TWR", "Tower north", 118.35),
        FrequencyEntity("CYYZ", "GND", "Ground", 121.65),
        FrequencyEntity("CYYZ", "APP", "Toronto Arrival", 124.475),
    ),
    weather = AirportWeather(
        Metar("CYYZ", temp = 11.0, dewp = 6.0, wdir = JsonPrimitive(240), wspd = 12, wgst = 20, visib = JsonPrimitive("15+"), altim = 1018.0, rawOb = "METAR CYYZ 210200Z 24012G20KT 15SM FEW040 BKN250 11/06 A3006", fltCat = "VFR", clouds = listOf(MetarCloud("FEW", 4000), MetarCloud("BKN", 25000))),
        Taf("CYYZ", "TAF CYYZ 202340Z 2100/2206 24012KT P6SM FEW040 FM211500 26015G25KT P6SM SCT050"),
        NOW,
    ),
    stats = LsAirportStats(stats = LsStats(destinations = 181, airlines = 81, countries = 61)),
    airlines = listOf(
        AirlineServed("ACA", "AC", "Air Canada", 96, null, 0xFFF0384F),
        AirlineServed("WJA", "WS", "WestJet", 41, null, 0xFF2CC1C4),
        AirlineServed("TSC", "TS", "Air Transat", 22, null, 0xFF38C3F0),
        AirlineServed("ETD", "EY", "Etihad Airways", 1, null, 0xFFD2AE6D),
        AirlineServed("CPA", "CX", "Cathay Pacific", 1, null, 0xFF2BB59A),
    ),
    destinations = listOf(
        Destination("LHR", "London", 51.47, -0.45, 3), Destination("AUH", "Abu Dhabi", 24.43, 54.65, 1),
        Destination("HKG", "Hong Kong", 22.31, 113.92, 2), Destination("YVR", "Vancouver", 49.19, -123.18, 3),
        Destination("CUN", "Cancún", 21.04, -86.87, 4), Destination("FRA", "Frankfurt", 50.03, 8.56, 2),
        Destination("DEL", "Delhi", 28.56, 77.1, 2), Destination("GRU", "São Paulo", -23.43, -46.47, 1),
    ),
    nearby = listOf(
        aircraft(hex = "1", lat = 43.75, lon = -79.75, altitudeFt = 4_000, trackDeg = 120f),
        aircraft(hex = "2", lat = 43.55, lon = -79.40, altitudeFt = 12_000, trackDeg = 300f),
        aircraft(hex = "3", lat = 43.90, lon = -79.30, altitudeFt = 33_000, trackDeg = 80f),
        aircraft(hex = "4", lat = 43.68, lon = -79.62, altitudeFt = null, onGround = true, trackDeg = 60f),
    ),
    departures = listOf(
        BoardRow("1", "ACA856", "LHR", "B77W", "Climbing", 2, 5_200),
        BoardRow("2", "WJA1234", "YVR", "B38M", "Climbing", 4, 9_000),
        BoardRow("3", "ETD140", "AUH", "B789", "Departed", 6, 14_000),
    ),
)

private val aircraftState = AircraftUiState(
    hex = "c0179b",
    live = aircraft(hex = "c0179b", callsign = "ACA42", registration = "C-FIUA", typeCode = "B77L", lat = 47.0, lon = -60.0, altitudeFt = 35_000),
    registration = "C-FIUA",
    typeCode = "B77L",
    callsign = "ACA42",
    airlineIcao = "ACA",
    photo = AircraftPhoto("t", "https://example.org/p.jpg", "https://www.planespotters.net/photo/1", "Marc Najberg"),
    route = FlightRoute("ACA42", AirportRef("YYZ", "CYYZ", "Pearson", "Toronto", "CA", 43.68, -79.63), AirportRef("DEL", "VIDP", "Indira Gandhi Intl", "New Delhi", "IN", 28.56, 77.1), "Air Canada"),
    sightings = listOf(
        SightingEntity("c0179b", "ACA42", "2026-10-20", "C-FIUA", "B77L", "YYZ", "DEL", NOW, NOW),
        SightingEntity("c0179b", "ACA43", "2026-10-18", "C-FIUA", "B77L", "DEL", "YYZ", NOW, NOW),
    ),
    hero = HeroMode.Livery,
)

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class Stage2Screenshots {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun snap(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            setSingletonImageLoaderFactory { fakePhotoLoader(it) }
            ContrailTheme(reducedMotion = true) { content() }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun airportPage() = snap("airport_page") { AirportScreen(airportState, {}, {}, {}, {}, {}) }

    @Test fun aircraftPage() = snap("aircraft_page") { AircraftScreen(aircraftState, {}, {}, { _, _ -> }, {}) }

    @Test fun settings() = snap("settings") {
        SettingsScreen(
            SettingsUiState(
                keys = listOf(
                    KeyStatus(ApiService.Logostream, KeySource.LocalProperties, "FREE••••02"),
                    KeyStatus(ApiService.AeroDataBox, KeySource.Missing, null),
                ),
                usage = LogostreamUsage(20_000, 19_874, "2026-11-01", 126, NOW),
            ),
            onSave = { _, _ -> },
            onBack = {},
        )
    }

    @Test fun liveryGallery() = snap("livery_gallery") {
        AuroraBackground(Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(
                    "A388" to "ETD", "B748" to "DLH", "B77W" to "ACA", "A359" to "CPA",
                    "B789" to "WJA", "A21N" to "BAW", "CRJ9" to "JZA", "DH8D" to "POE",
                ).forEach { (type, airline) ->
                    Eyebrow("$type · $airline")
                    LiveryRender(Profiles.forType(type), Liveries.forAirline(airline, accentArgb = com.contrail.contrail.core.data.reference.Airlines.byIcao(airline)?.accentArgb), floating = false)
                }
            }
        }
    }
}

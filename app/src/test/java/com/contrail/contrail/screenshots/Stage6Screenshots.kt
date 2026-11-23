package com.contrail.contrail.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.compose.setSingletonImageLoaderFactory
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.cabin.Bumpiness
import com.contrail.contrail.core.data.cabin.PackPlace
import com.contrail.contrail.core.data.cabin.Side
import com.contrail.contrail.core.data.cabin.SunEvent
import com.contrail.contrail.core.data.cabin.Upcoming
import com.contrail.contrail.core.data.hangar.HangarPlane
import com.contrail.contrail.core.data.moments.Moment
import com.contrail.contrail.core.data.moments.MomentKind
import com.contrail.contrail.core.data.sky.ContrailForecast
import com.contrail.contrail.core.data.sky.ContrailPhysics
import com.contrail.contrail.core.designsystem.component.AuroraBackground
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.feature.cabin.CabinPhase
import com.contrail.contrail.feature.cabin.CabinScreen
import com.contrail.contrail.feature.cabin.CabinState
import com.contrail.contrail.feature.cabin.PositionSource
import com.contrail.contrail.feature.hangar.HangarCard
import com.contrail.contrail.feature.hangar.HangarScreen
import com.contrail.contrail.feature.hangar.HangarUiState
import com.contrail.contrail.feature.passport.MomentsData
import com.contrail.contrail.feature.passport.MomentsScreen
import com.contrail.contrail.feature.radar.RadarActions
import com.contrail.contrail.feature.radar.RadarPanel
import com.contrail.contrail.feature.radar.RadarScreen
import com.contrail.contrail.feature.radar.SearchResults
import com.contrail.contrail.feature.radar.SearchUiState
import com.contrail.contrail.feature.sky.SkyActions
import com.contrail.contrail.feature.sky.SkyScreen
import com.contrail.contrail.testing.TicketSamples
import com.contrail.contrail.testing.TripFixtures
import com.contrail.contrail.testing.aircraft
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

private val blxp = HangarPlane(
    registration = "B-LXP", hex = "780a1b", typeCode = "A35K", airlineIcao = "CPA", operator = "Cathay Pacific",
    story = "Sat in the captain's seat at the gate in Hong Kong. The first officer let me run the before-start flows.",
    photoPath = null, addedAtMs = NOW, alerts = true, lastSeenMs = NOW - 60_000, lastLat = 61.2, lastLon = -150.0, lastCallsign = "CPA826", lastAirborne = true,
)

private val cpa826 = aircraft(hex = "780a1b", callsign = "CPA826", registration = "B-LXP", typeCode = "A35K", lat = 61.2, lon = -150.0, altitudeFt = 39_000, groundSpeedKt = 529f, trackDeg = 92f)

internal val contrailForecast = ContrailForecast(
    hours = (0 until 12).map { h ->
        val t = NOW + h * 3_600_000L
        ContrailForecast.Hour(t, listOf(ContrailPhysics.level(300, -47.0, 63.0), ContrailPhysics.level(250, -55.0, 63.0 + h % 3 * 5), ContrailPhysics.level(200, -61.0, 55.0)))
    },
    fetchedAtMs = NOW,
)

private val moments = listOf(
    Moment("1", MomentKind.Cockpit, "Captain's seat on B-LXP", "The first officer let me run the before-start flows. Hands on the sidestick for a photo.", LocalDate.of(2025, 8, 14), null, "B-LXP", null, mine = true),
    Moment("2", MomentKind.Sunset, "Sunset at 38,000 ft", "AC 870 · captured in Cabin mode", LocalDate.of(2025, 6, 2), null, null, null, mine = false, captured = true),
    Moment("noticed:km-100000", MomentKind.Milestone, "100,000 km flown", "Reached on CX 829 · Toronto → Hong Kong", LocalDate.of(2024, 12, 19), null, null, null, mine = false),
    Moment("noticed:type-A388", MomentKind.FirstType, "First time on the Airbus A380", "Four engines, two aisles. EK 241 · Toronto → Dubai", LocalDate.of(2023, 3, 3), null, null, null, mine = false),
)

private val cabin = run {
    val seg = TripFixtures.manila.segments.first()
    val places = TicketSamples.airports
    CabinState(
        segment = seg, origin = places["YYZ"], destination = places["HKG"], phase = CabinPhase.Airborne,
        position = LatLon(66.0, -160.0), headingDeg = 268.0, fraction = 0.38, source = PositionSource.Gps, satellites = 9,
        groundSpeedKt = 492.0, altitudeFt = 37_000, cabinAltitudeFt = 6_800, bumpiness = Bumpiness.Light,
        minutesLeft = 452, landingMs = NOW + 452 * 60_000L,
        upcoming = listOf(
            Upcoming(PackPlace("Nome", "US", 64.5, -165.4, "City in Alaska, United States. Nome is a city in the Nome Census Area in the Unorganized Borough of Alaska.", 0.41), 9, Side.Left, 40.0),
            Upcoming(PackPlace("Anadyr", "RU", 64.7, 177.5, "Town in Chukotka, Russia. Anadyr is a port town and the administrative center of Chukotka Autonomous Okrug.", 0.47), 38, Side.Right, 85.0),
        ),
        nextBorder = "Russia" to 31,
        sunEvent = SunEvent(sunset = false, atMs = NOW + 128 * 60_000L, side = Side.Left),
        sunSide = null, window = Side.Left, captured = listOf("takeoff", "border-US"), nowMs = NOW,
    )
}

/** Stage 6's new screens, each in its main state. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class Stage6Screenshots {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun snap(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            setSingletonImageLoaderFactory { fakePhotoLoader(it) }
            ContrailTheme(reducedMotion = true) {
                Box(Modifier.fillMaxSize()) {
                    AuroraBackground(Modifier.fillMaxSize())
                    content()
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun radarSearch() = snap("radar_search") {
        val state = ScreenshotTests.idle.copy(
            panel = RadarPanel.Search,
            hangar = listOf(blxp),
            viewCenter = LatLon(43.68, -79.63),
            search = SearchUiState(
                query = "B-LXP",
                readings = listOf("Registration B-LXP"),
                results = SearchResults(query = "B-LXP", aircraft = listOf(cpa826)),
                recent = listOf("CX888", "B-LXP"),
            ),
        )
        RadarScreen(state, RadarActions()) { FakeRadarBackdrop(it) }
    }

    @Test fun hangar() = snap("hangar") {
        HangarScreen(HangarUiState(listOf(HangarCard(blxp, live = cpa826)), loading = false), onBack = {}, onPlane = {}, onShowOnRadar = {})
    }

    @Test fun moments() = snap("moments") { MomentsScreen(MomentsData(moments), onBack = {}, onAdd = {}, onMoment = {}) }

    @Test fun cabinMode() = snap("cabin_mode") { CabinScreen(cabin, onBack = {}) }

    @Test fun skyContrails() = snap("sky_contrails") {
        SkyScreen(skyState.copy(contrails = contrailForecast, nowMs = NOW), 30f, true, SkyActions())
    }
}

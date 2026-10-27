package com.contrail.contrail.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
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
import com.contrail.contrail.core.data.repository.RadarLayers
import com.contrail.contrail.core.designsystem.component.AuroraBackground
import com.contrail.contrail.core.designsystem.component.ContrailChip
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.GlassIconButton
import com.contrail.contrail.core.designsystem.component.GlassSurface
import com.contrail.contrail.core.designsystem.component.RarityBadge
import com.contrail.contrail.core.designsystem.component.Sparkline
import com.contrail.contrail.core.designsystem.component.SplitFlapText
import com.contrail.contrail.core.designsystem.component.StatTile
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.AltitudeRamp
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.core.designsystem.theme.RarityPalette
import com.contrail.contrail.feature.airports.AirportsScreen
import com.contrail.contrail.feature.passport.PassportScreen
import com.contrail.contrail.feature.radar.FeedStatus
import com.contrail.contrail.feature.radar.FilterFacets
import com.contrail.contrail.feature.radar.Lookup
import com.contrail.contrail.feature.radar.RadarActions
import com.contrail.contrail.feature.radar.RadarFilters
import com.contrail.contrail.feature.radar.RadarPanel
import com.contrail.contrail.feature.radar.RadarScreen
import com.contrail.contrail.feature.radar.RadarUiState
import com.contrail.contrail.feature.radar.SelectedAircraft
import com.contrail.contrail.feature.sky.SkyScreen
import com.contrail.contrail.feature.trips.TripsScreen
import com.contrail.contrail.navigation.ContrailApp
import com.contrail.contrail.navigation.TopLevelDestination
import com.contrail.contrail.testing.TripFixtures
import com.contrail.contrail.testing.aircraft
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot of every screen and key state, rendered on a Pixel 7-sized canvas. Motion is
 * reduced so split-flap boards and sheets show their settled state.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ScreenshotTests {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun snap(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            setSingletonImageLoaderFactory { fakePhotoLoader(it) }
            ContrailTheme(reducedMotion = true) { content() }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun shell(tab: TopLevelDestination, radar: RadarUiState = idle) = snap("tab_${tab.name.lowercase()}") {
        ContrailApp(initial = tab) { t ->
            when (t) {
                TopLevelDestination.Trips -> TripsScreen(tripsState, {}, {}, {}, fixedNow = TripFixtures.now)
                TopLevelDestination.Radar -> RadarScreen(radar, RadarActions()) { FakeRadarBackdrop(it) }
                TopLevelDestination.Sky -> com.contrail.contrail.feature.sky.SkyScreen(skyState, 30f, true, com.contrail.contrail.feature.sky.SkyActions())
                TopLevelDestination.Passport -> PassportScreen(passportState, com.contrail.contrail.feature.passport.PassportActions())
                TopLevelDestination.Airports -> AirportsScreen(airportsState, {}, {}, {}, {}, {}, nowMs = NOW)
            }
        }
    }

    private fun radar(name: String, state: RadarUiState, expanded: Boolean = false) = snap("radar_$name") {
        ContrailApp(initial = TopLevelDestination.Radar) {
            RadarScreen(state, RadarActions(), sheetStartsExpanded = expanded) { FakeRadarBackdrop(it) }
        }
    }

    @Test fun trips() = shell(TopLevelDestination.Trips)
    @Test fun radarTab() = shell(TopLevelDestination.Radar)
    @Test fun sky() = shell(TopLevelDestination.Sky)
    @Test fun passport() = shell(TopLevelDestination.Passport)
    @Test fun airports() = shell(TopLevelDestination.Airports)

    @Test fun radarFilters() = radar(
        "filters",
        idle.copy(
            panel = RadarPanel.Filters,
            filters = RadarFilters(airlines = setOf("ACA"), rareOnly = true),
            shownCount = 3,
        ),
    )

    @Test fun radarLayers() = radar("layers", idle.copy(panel = RadarPanel.Layers))
    @Test fun radarSheetPeek() = radar("sheet_peek", idle.copy(selected = selected))
    @Test fun radarSheetExpanded() = radar("sheet_expanded", idle.copy(selected = selected), expanded = true)
    @Test fun radarSheetNoPhoto() = radar("sheet_no_photo", idle.copy(selected = selected.copy(photo = Lookup.Missing, route = Lookup.Loading)))
    @Test fun radarFollowing() = radar("following", idle.copy(selected = selected, following = true))
    @Test fun radarEmergency() = radar("emergency", idle.copy(emergencies = listOf(emergency)))
    @Test fun radarOffline() = radar("offline", idle.copy(feed = FeedStatus.Offline(lastSuccessMs = 0L)))

    @Test fun designSystem() = snap("design_system") { DesignSystemGallery() }

    private companion object {
        val traffic = listOf(
            aircraft(hex = "1", callsign = "ACA112", typeCode = "B789"),
            aircraft(hex = "2", callsign = "ETD141", typeCode = "A388", altitudeFt = 39_000),
            aircraft(hex = "3", callsign = "CPA826", typeCode = "A35K"),
            aircraft(hex = "4", callsign = "WJA1234", typeCode = "B38M", altitudeFt = 12_000),
            aircraft(hex = "5", callsign = "ACA856", typeCode = "B77W"),
        )

        val idle = RadarUiState(
            feed = FeedStatus.Live("adsb.lol", 0L),
            shownCount = 148,
            totalCount = 148,
            facets = FilterFacets.from(traffic, RadarFilters()),
            layers = RadarLayers(),
        )

        val selected = SelectedAircraft(
            aircraft = aircraft(
                hex = "c0179b", callsign = "ACA42", registration = "C-FIUA", typeCode = "B77L",
                altitudeFt = 12_350, groundSpeedKt = 382f, verticalRateFpm = 2_432, squawk = "2227", trackDeg = 52f,
            ),
            route = Lookup.Found(
                FlightRoute(
                    "ACA42",
                    AirportRef("YYZ", "CYYZ", "Toronto Pearson", "Toronto", "CA", 43.68, -79.63),
                    AirportRef("DEL", "VIDP", "Indira Gandhi International", "New Delhi", "IN", 28.56, 77.1),
                    "Air Canada",
                ),
            ),
            photo = Lookup.Found(AircraftPhoto("t", "https://example.org/p.jpg", "https://www.planespotters.net/photo/1", "Marc Najberg")),
            verticalRates = listOf(0f, 640f, 1_800f, 2_400f, 2_200f, 1_600f, 2_100f, 2_600f, 2_432f, 1_900f, 2_432f),
        )

        val emergency = aircraft(hex = "e", callsign = "JZA8817", typeCode = "DH8D", squawk = "7700", altitudeFt = 6_200)
    }
}

@Composable
private fun DesignSystemGallery() {
    val spacing = ContrailTheme.spacing
    val colors = ContrailTheme.colors
    AuroraBackground(Modifier.fillMaxSize()) {
        Column(Modifier.padding(spacing.gutter).padding(top = spacing.xxxl), verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
            Eyebrow("Design system", color = colors.accent)
            Text("Night cockpit", style = ContrailTheme.type.displayLarge, color = colors.textPrimary)
            SplitFlapText("EY140 AUH")
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                RarityBadge("Legendary", RarityPalette.Legendary)
                RarityBadge("Rare", RarityPalette.Rare)
                RarityBadge("Uncommon", RarityPalette.Uncommon)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                ContrailChip("Selected", selected = true, onClick = {})
                ContrailChip("Idle", selected = false, onClick = {})
                GlassIconButton(ContrailIcons.Filter, "Filter", {})
                GlassIconButton(ContrailIcons.Layers, "Layers", {}, active = true)
            }
            GlassSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatTile("Altitude", "34,000", unit = "ft")
                        StatTile("Speed", "452", unit = "kt")
                        StatTile("Squawk", "7700", valueColor = colors.emergency)
                    }
                    Sparkline(listOf(0f, 800f, 1_600f, 900f, 0f, -600f, -1_200f, -400f), Modifier.fillMaxWidth().height(48.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                (0..45_000 step 5_000).forEach { alt ->
                    Column(Modifier.size(28.dp).background(AltitudeRamp.colorFor(alt), ContrailTheme.shapes.small)) {}
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                listOf(colors.statusOnTime, colors.statusDelayed, colors.statusCancelled, colors.statusAirborne, colors.accent).forEach {
                    Column(Modifier.size(28.dp).background(it, ContrailTheme.shapes.chip)) {}
                }
            }
        }
    }
}

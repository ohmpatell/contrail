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
import com.contrail.contrail.core.data.logbook.FlightLogParser
import com.contrail.contrail.core.data.logbook.FlightLogResolver
import com.contrail.contrail.core.data.logbook.LoggedFlight
import com.contrail.contrail.core.data.trip.Cabin
import com.contrail.contrail.core.data.trip.SeatType
import com.contrail.contrail.core.designsystem.component.AuroraBackground
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.feature.passport.LogbookScreen
import com.contrail.contrail.feature.passport.LogbookState
import com.contrail.contrail.feature.passport.PassportActions
import com.contrail.contrail.feature.passport.PassportCalculator
import com.contrail.contrail.feature.passport.PassportScreen
import com.contrail.contrail.feature.passport.PassportUiState
import com.contrail.contrail.feature.passport.StampBookScreen
import com.contrail.contrail.feature.trips.add.AddFlightScreen
import com.contrail.contrail.feature.trips.add.AddFlightState
import com.contrail.contrail.feature.trips.importing.ImportScreen
import com.contrail.contrail.feature.trips.importing.ImportUiState
import com.contrail.contrail.feature.trips.importing.LogImportScreen
import com.contrail.contrail.feature.trips.importing.LogImportState
import com.contrail.contrail.feature.trips.importing.LogStage
import com.contrail.contrail.testing.PassportFixtures
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private val now = Instant.parse("2026-10-21T12:00:00Z")

internal val historyState = PassportUiState(
    loading = false,
    stats = PassportCalculator.compute(PassportFixtures.trips, PassportFixtures.places, emptyList(), emptySet(), now),
    places = PassportFixtures.places,
)

private val logPreview = FlightLogResolver.preview(FlightLogParser.parse(PassportFixtures.csv), PassportFixtures.directory, { null }, setOf("2026-03-17|YYZ|YVR"))

private val addFlightState = run {
    val date = LocalDate.of(2026, 11, 12)
    val seg = FlightLogResolver.resolve(
        LoggedFlight(
            row = 0, date = date, airlineIata = "EY", flightNumber = "140", originIata = "YYZ", destinationIata = "AUH",
            departure = LocalDateTime.of(date, LocalTime.of(22, 10)), arrivalTime = LocalTime.of(19, 55),
            aircraftType = "B789", seat = "7K", seatType = SeatType.Window, cabin = Cabin.Business,
        ),
        PassportFixtures.directory,
    ) { null }
    AddFlightState(
        loading = false, flight = "EY 140", date = date, from = "YYZ", to = "AUH", departure = "22:10", arrival = "19:55",
        aircraft = "B789", seat = "7K", seatType = SeatType.Window, cabin = Cabin.Business, routeFromCallsign = true,
        preview = seg, places = PassportFixtures.places.filterKeys { it in setOf("YYZ", "AUH") }, distanceKm = 11_000, today = LocalDate.of(2026, 10, 21),
    )
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class Stage4Screenshots {
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

    /** The whole Passport scroll in one tall image. */
    @Config(qualifiers = "w411dp-h5200dp-mdpi")
    @Test fun passportFull() = snap("passport_full") { PassportScreen(historyState, PassportActions()) }

    @Test fun passport() = snap("passport_history") { PassportScreen(historyState, PassportActions()) }

    @Test fun stampBookCover() = snap("stamp_book_cover") { StampBookScreen(historyState, onBack = {}) }

    @Test fun stampBookIdentity() = snap("stamp_book_identity") { StampBookScreen(historyState, onBack = {}, startPage = 1) }

    @Test fun stampBookPage() = snap("stamp_book_page") { StampBookScreen(historyState, onBack = {}, startPage = 2) }

    @Test fun logbook() = snap("logbook") {
        LogbookScreen(
            LogbookState(loading = false, flown = historyState.stats.flown, places = PassportFixtures.places),
            onBack = {}, onFlight = {}, onAdd = {}, onImport = {},
        )
    }

    @Test fun addFlight() = snap("add_flight") { AddFlightScreen(addFlightState, {}, {}, { _, _ -> }, { _, _ -> }, {}, {}, {}) }

    @Test fun logImport() = snap("log_import_preview") {
        LogImportScreen(LogImportState(stage = LogStage.Preview, preview = logPreview, places = PassportFixtures.places, distanceKm = 312_000), onBack = {})
    }

    @Test fun importHub() = snap("import_hub") { ImportScreen(ImportUiState(), onBack = {}) }
}

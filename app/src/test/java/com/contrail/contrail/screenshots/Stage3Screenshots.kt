package com.contrail.contrail.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.compose.setSingletonImageLoaderFactory
import com.contrail.contrail.core.data.reference.Lounges
import com.contrail.contrail.core.data.repository.AirportWeather
import com.contrail.contrail.core.data.repository.Lounge
import com.contrail.contrail.core.data.repository.LoungeAccess
import com.contrail.contrail.core.data.repository.LoungePhase
import com.contrail.contrail.core.data.repository.LoungeSource
import com.contrail.contrail.core.data.repository.LoungeVisit
import com.contrail.contrail.core.data.rotation.SampleRotation
import com.contrail.contrail.core.data.ticket.ImportSource
import com.contrail.contrail.core.data.ticket.TicketTextParser
import com.contrail.contrail.core.data.ticket.TripAssembler
import com.contrail.contrail.core.data.trip.Connection
import com.contrail.contrail.core.data.trip.ConnectionPref
import com.contrail.contrail.core.data.trip.ConnectionRules
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.core.network.airport.Metar
import com.contrail.contrail.core.network.airport.MetarCloud
import com.contrail.contrail.feature.passport.PassportCalculator
import com.contrail.contrail.feature.passport.PassportUiState
import com.contrail.contrail.feature.passport.YearInReviewScreen
import com.contrail.contrail.feature.trips.TripDetailScreen
import com.contrail.contrail.feature.trips.TripDetailUiState
import com.contrail.contrail.feature.trips.TripsUiState
import com.contrail.contrail.feature.trips.flight.FlightScreen
import com.contrail.contrail.feature.trips.flight.FlightUiState
import com.contrail.contrail.feature.trips.importing.ImportScreen
import com.contrail.contrail.feature.trips.importing.ImportStage
import com.contrail.contrail.feature.trips.importing.ImportUiState
import com.contrail.contrail.feature.trips.importing.ReviewPreview
import com.contrail.contrail.feature.trips.importing.SegmentForm
import com.contrail.contrail.feature.trips.lounge.LoungeForm
import com.contrail.contrail.feature.trips.lounge.LoungeScreen
import com.contrail.contrail.feature.trips.lounge.LoungeUiState
import com.contrail.contrail.testing.TicketSamples
import com.contrail.contrail.testing.TripFixtures
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

private val allPlaces = TripFixtures.placesFor(TripFixtures.all)

internal val tripsState = TripsUiState.from(TripFixtures.all, TripFixtures.now, allPlaces)

private val afterTrips = Instant.parse("2027-01-10T00:00:00Z")

private val loungeVisits = listOf(
    LoungeVisit(1, 61, LoungePhase.Arrival, Lounges.key("HKG", "Cathay Pacific The Pier, Business"), "Cathay Pacific The Pier, Business", "HKG", "1", LocalDate.of(2026, 8, 11), LocalTime.of(5, 10), LocalTime.of(7, 0), LoungeAccess.Cabin, 5, "Noodle bar and a shower after the long one."),
    LoungeVisit(2, 51, LoungePhase.Departure, Lounges.key("YYZ", "Air Canada Signature Suite"), "Air Canada Signature Suite", "YYZ", "1", LocalDate.of(2026, 6, 2), LocalTime.of(19, 15), LocalTime.of(21, 0), LoungeAccess.Cabin, 5, ""),
    LoungeVisit(3, 52, LoungePhase.Departure, Lounges.key("LHR", "Plaza Premium Lounge"), "Plaza Premium Lounge", "LHR", "2", LocalDate.of(2026, 6, 3), LocalTime.of(10, 0), LocalTime.of(11, 25), LoungeAccess.Card, 3, ""),
)

internal val passportState = PassportUiState(
    loading = false,
    stats = PassportCalculator.compute(TripFixtures.all, allPlaces, loungeVisits, setOf(loungeVisits[0].loungeKey), afterTrips),
    places = allPlaces,
)

private val yearState = PassportUiState(
    loading = false,
    stats = PassportCalculator.compute(TripFixtures.all, allPlaces, loungeVisits, emptySet(), afterTrips, year = 2026),
    places = allPlaces,
)

private val delhiPlaces = TripFixtures.placesFor(listOf(TripFixtures.delhi))

private val tripDetailState = TripDetailUiState(
    loading = false,
    trip = TripFixtures.delhi,
    places = delhiPlaces,
    lounges = mapOf(
        22L to listOf(LoungeVisit(9, 22, LoungePhase.Departure, Lounges.key("AUH", "Etihad Business Class Lounge"), "Etihad Business Class Lounge", "AUH", "A", LocalDate.of(2026, 11, 14), LocalTime.of(23, 50), LocalTime.of(1, 55), LoungeAccess.Cabin, 4, "")),
    ),
    weather = mapOf("AUH" to AirportWeather(Metar("OMAA", temp = 27.0, fltCat = "VFR", clouds = listOf(MetarCloud("FEW", 4000))), null, 0)),
)

private val flightState = TripFixtures.delhi.segments.first().let { s ->
    FlightUiState(
        loading = false,
        trip = TripFixtures.delhi,
        segment = s,
        places = delhiPlaces,
        rotation = SampleRotation.build(s, null),
        flightsOnTail = 2,
    )
}

private val importState: ImportUiState = run {
    val dir = TicketSamples.directory
    val draft = TicketTextParser(TicketSamples.codes).parse(TicketSamples.etihadEticket, LocalDate.of(2026, 10, 21), ImportSource.Pdf)
    val forms = draft.segments.map(SegmentForm::from)
    val drafts = forms.map { it.toDraft() }
    val resolved = drafts.mapIndexed { i, d -> TripAssembler.resolve(d, dir, i)?.copy(id = i + 1L) }
    val complete = resolved.filterNotNull()
    val connections = HashMap<Int, Connection>()
    for (i in 0 until resolved.size - 1) {
        val a = resolved[i]!!
        connections[i] = ConnectionRules.connections(listOf(a, resolved[i + 1]!!), mapOf(a.id to ConnectionPref(a.id, null, drafts[i].hotelAfter))).first()
    }
    ImportUiState(
        stage = ImportStage.Review,
        pnr = draft.pnr.orEmpty(),
        passenger = draft.passengerName.orEmpty(),
        segments = forms,
        sources = draft.sources,
        preview = ReviewPreview(resolved, emptySet(), connections, TripAssembler.group(complete, emptySet()).map { TripAssembler.title(it, dir) }, emptySet()),
    )
}

private val loungeState = LoungeUiState(
    segment = TripFixtures.delhi.segments[1],
    phase = LoungePhase.Departure,
    airport = "AUH",
    city = "Abu Dhabi",
    suggestions = Lounges.at("AUH").map { Lounge(Lounges.key("AUH", it.name), "AUH", it.terminal, it.name, LoungeSource.Curated, favourite = it.name.contains("First")) } +
        Lounge(Lounges.key("AUH", "Etihad Arrivals Lounge"), "AUH", "A", "Etihad Arrivals Lounge", LoungeSource.Yours),
    form = LoungeForm(
        loungeKey = Lounges.key("AUH", "Etihad Business Class Lounge"), loungeName = "Etihad Business Class Lounge",
        terminal = "A", date = "14 Nov 2026", timeIn = "23:50", timeOut = "01:55", access = LoungeAccess.Cabin, rating = 4,
        note = "Quiet corner by the windows overlooking the apron.",
    ),
)

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class Stage3Screenshots {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun snap(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            setSingletonImageLoaderFactory { fakePhotoLoader(it) }
            ContrailTheme(reducedMotion = true) { content() }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun tripDetail() = snap("trip_detail") {
        TripDetailScreen(tripDetailState, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, fixedNow = TripFixtures.now)
    }

    @Test fun importReview() = snap("import_review") { ImportScreen(importState, onBack = {}) }

    @Test fun flightDetail() = snap("flight_detail") {
        FlightScreen(flightState, {}, {}, {}, {}, {}, {}, {}, fixedNow = TripFixtures.now)
    }

    @Test fun loungeLog() = snap("lounge_log") { LoungeScreen(loungeState, {}, {}, {}, {}, {}, {}, {}) }

    @Test fun yearInReview() = snap("year_in_review") { YearInReviewScreen(yearState, 2026, onClose = {}, startPage = 1) }
}

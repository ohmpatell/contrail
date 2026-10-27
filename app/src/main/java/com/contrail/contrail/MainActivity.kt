package com.contrail.contrail

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.contrail.contrail.core.data.ticket.SharedTicket
import com.contrail.contrail.core.data.ticket.SharedTicketInbox
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.feature.aircraft.AircraftRoute
import com.contrail.contrail.feature.airport.AirportRoute
import com.contrail.contrail.feature.airports.AirportsRoute
import com.contrail.contrail.feature.passport.PassportRoute
import com.contrail.contrail.feature.passport.YearInReviewRoute
import com.contrail.contrail.feature.trips.flight.FlightRoute
import com.contrail.contrail.feature.trips.lounge.LoungeRoute
import com.contrail.contrail.feature.radar.RadarRoute
import com.contrail.contrail.feature.settings.SettingsRoute
import com.contrail.contrail.feature.sky.SkyRoute
import com.contrail.contrail.feature.sky.SkyViewRoute
import com.contrail.contrail.navigation.SkyViewKey
import com.contrail.contrail.navigation.ShareCardKey
import com.contrail.contrail.feature.passport.ShareCardRoute
import com.contrail.contrail.feature.trips.TripDetailRoute
import com.contrail.contrail.feature.trips.TripsRoute
import com.contrail.contrail.feature.trips.importing.ImportRoute
import com.contrail.contrail.feature.passport.LogbookRoute
import com.contrail.contrail.feature.passport.StampBookRoute
import com.contrail.contrail.navigation.StampBookKey
import com.contrail.contrail.feature.trips.add.AddFlightRoute
import com.contrail.contrail.feature.trips.importing.LogImportRoute
import com.contrail.contrail.feature.trips.importing.LogImportViewModel
import com.contrail.contrail.navigation.AddFlightKey
import com.contrail.contrail.navigation.LogImportKey
import com.contrail.contrail.navigation.LogbookKey
import com.contrail.contrail.navigation.FlightKey
import com.contrail.contrail.navigation.ImportKey
import com.contrail.contrail.navigation.LoungeKey
import com.contrail.contrail.navigation.TripKey
import com.contrail.contrail.navigation.YearInReviewKey
import com.contrail.contrail.navigation.AircraftKey
import com.contrail.contrail.navigation.AirportKey
import com.contrail.contrail.navigation.ContrailApp
import com.contrail.contrail.navigation.DetailOverlayStrategy
import com.contrail.contrail.navigation.LocalNavigator
import com.contrail.contrail.navigation.Navigator
import com.contrail.contrail.navigation.SettingsKey
import com.contrail.contrail.navigation.ShellKey
import com.contrail.contrail.navigation.TabRequests
import com.contrail.contrail.navigation.TopLevelDestination
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var inbox: SharedTicketInbox

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (savedInstanceState == null) receiveShare(intent)
        TabRequests.request(intent?.getStringExtra(EXTRA_TAB))
        addOnNewIntentListener {
            receiveShare(it)
            TabRequests.request(it.getStringExtra(EXTRA_TAB))
        }
        setContent {
            ContrailTheme { ContrailNavHost(inbox) }
        }
    }

    companion object {
        /** Opens on this tab (a TopLevelDestination name), from notifications and widgets. */
        const val EXTRA_TAB = "contrail.tab"
    }

    /** "Share to Contrail" from another app: hand the files or text to the import screen. */
    private fun receiveShare(intent: Intent?) {
        intent ?: return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> return
        }
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (uris.isEmpty() && text.isNullOrBlank()) return
        inbox.deliver(SharedTicket(uris, intent.type, text))
    }
}

@Composable
private fun ContrailNavHost(inbox: SharedTicketInbox) {
    val backStack = rememberNavBackStack(ShellKey)
    val motion = ContrailTheme.motion
    val navigator = remember(backStack) {
        object : Navigator {
            override fun openAircraft(hex: String) = push(AircraftKey(hex.lowercase()))
            override fun openAirport(icao: String) = push(AirportKey(icao.uppercase()))
            override fun openSettings() = push(SettingsKey)
            override fun openTrip(id: Long) = push(TripKey(id))
            override fun openFlight(segmentId: Long) = push(FlightKey(segmentId))
            override fun openImport() = push(ImportKey)
            override fun openLounge(segmentId: Long, phase: String, visitId: Long) = push(LoungeKey(segmentId, phase, visitId))
            override fun openYearInReview(year: Int) = push(YearInReviewKey(year))
            override fun openAddFlight(segmentId: Long) = push(AddFlightKey(segmentId))
            override fun openLogImport() = push(LogImportKey)
            override fun openLogbook() = push(LogbookKey)
            override fun openStampBook() = push(StampBookKey)
            override fun openSkyView() = push(SkyViewKey)
            override fun openShareCard() = push(ShareCardKey)
            override fun back() {
                if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
            }

            private fun push(key: NavKey) {
                if (backStack.lastOrNull() != key) backStack.add(key)
            }
        }
    }
    // Something shared to Contrail opens the import screen, which picks it up from the inbox.
    LaunchedEffect(inbox) {
        inbox.shared.collect { shared ->
            if (shared == null) return@collect
            // Flight-log CSVs go to the log importer; tickets to the ticket importer.
            val key: NavKey = if (LogImportViewModel.isLog(shared.mime, shared.uris)) LogImportKey else ImportKey
            if (backStack.lastOrNull() != key) backStack.add(key)
        }
    }
    CompositionLocalProvider(LocalNavigator provides navigator) {
        // Detail pages are overlays on the shell, and NavDisplay only enables system back for
        // the scene underneath them, so back would close the app. Registered before NavDisplay,
        // so a screen's own back handling (a sheet, an import step) still comes first.
        BackHandler(enabled = backStack.size > 1) { navigator.back() }
        NavDisplay(
            backStack = backStack,
            onBack = { navigator.back() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            sceneStrategies = listOf(remember(motion) { DetailOverlayStrategy<NavKey>(motion) }),
            entryProvider = entryProvider {
                entry<ShellKey> {
                    ContrailApp { tab ->
                        when (tab) {
                            TopLevelDestination.Trips -> TripsRoute()
                            TopLevelDestination.Radar -> RadarRoute(onOpenAircraft = navigator::openAircraft)
                            TopLevelDestination.Sky -> SkyRoute()
                            TopLevelDestination.Airports -> AirportsRoute()
                            TopLevelDestination.Passport -> PassportRoute()
                        }
                    }
                }
                entry<AircraftKey>(metadata = DetailOverlayStrategy.detail) { AircraftRoute(it.hex) }
                entry<AirportKey>(metadata = DetailOverlayStrategy.detail) { AirportRoute(it.icao) }
                entry<SettingsKey>(metadata = DetailOverlayStrategy.detail) { SettingsRoute() }
                entry<TripKey>(metadata = DetailOverlayStrategy.detail) { TripDetailRoute(it.id) }
                entry<ImportKey>(metadata = DetailOverlayStrategy.detail) { ImportRoute() }
                entry<FlightKey>(metadata = DetailOverlayStrategy.detail) { FlightRoute(it.segmentId) }
                entry<LoungeKey>(metadata = DetailOverlayStrategy.detail) { LoungeRoute(it.segmentId, it.phase, it.visitId) }
                entry<YearInReviewKey>(metadata = DetailOverlayStrategy.detail) { YearInReviewRoute(it.year) }
                entry<AddFlightKey>(metadata = DetailOverlayStrategy.detail) { AddFlightRoute(it.segmentId) }
                entry<LogImportKey>(metadata = DetailOverlayStrategy.detail) { LogImportRoute() }
                entry<LogbookKey>(metadata = DetailOverlayStrategy.detail) { LogbookRoute() }
                entry<StampBookKey>(metadata = DetailOverlayStrategy.detail) { StampBookRoute() }
                entry<SkyViewKey>(metadata = DetailOverlayStrategy.detail) { SkyViewRoute() }
                entry<ShareCardKey>(metadata = DetailOverlayStrategy.detail) { ShareCardRoute() }
            },
        )
    }
}

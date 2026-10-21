package com.contrail.contrail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
import com.contrail.contrail.feature.passport.PassportScreen
import com.contrail.contrail.feature.radar.RadarRoute
import com.contrail.contrail.feature.settings.SettingsRoute
import com.contrail.contrail.feature.sky.SkyScreen
import com.contrail.contrail.feature.trips.TripsScreen
import com.contrail.contrail.navigation.AircraftKey
import com.contrail.contrail.navigation.AirportKey
import com.contrail.contrail.navigation.ContrailApp
import com.contrail.contrail.navigation.DetailOverlayStrategy
import com.contrail.contrail.navigation.LocalNavigator
import com.contrail.contrail.navigation.Navigator
import com.contrail.contrail.navigation.SettingsKey
import com.contrail.contrail.navigation.ShellKey
import com.contrail.contrail.navigation.TopLevelDestination
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            ContrailTheme { ContrailNavHost() }
        }
    }
}

@Composable
private fun ContrailNavHost() {
    val backStack = rememberNavBackStack(ShellKey)
    val motion = ContrailTheme.motion
    val navigator = remember(backStack) {
        object : Navigator {
            override fun openAircraft(hex: String) = push(AircraftKey(hex.lowercase()))
            override fun openAirport(icao: String) = push(AirportKey(icao.uppercase()))
            override fun openSettings() = push(SettingsKey)
            override fun back() {
                if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
            }

            private fun push(key: NavKey) {
                if (backStack.lastOrNull() != key) backStack.add(key)
            }
        }
    }
    CompositionLocalProvider(LocalNavigator provides navigator) {
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
                            TopLevelDestination.Trips -> TripsScreen()
                            TopLevelDestination.Radar -> RadarRoute(onOpenAircraft = navigator::openAircraft)
                            TopLevelDestination.Sky -> SkyScreen()
                            TopLevelDestination.Airports -> AirportsRoute()
                            TopLevelDestination.Passport -> PassportScreen()
                        }
                    }
                }
                entry<AircraftKey>(metadata = DetailOverlayStrategy.detail) { AircraftRoute(it.hex) }
                entry<AirportKey>(metadata = DetailOverlayStrategy.detail) { AirportRoute(it.icao) }
                entry<SettingsKey>(metadata = DetailOverlayStrategy.detail) { SettingsRoute() }
            },
        )
    }
}

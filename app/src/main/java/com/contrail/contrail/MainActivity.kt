package com.contrail.contrail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.feature.logbook.LogbookScreen
import com.contrail.contrail.feature.passport.PassportScreen
import com.contrail.contrail.feature.radar.RadarRoute
import com.contrail.contrail.feature.sky.SkyScreen
import com.contrail.contrail.feature.trips.TripsScreen
import com.contrail.contrail.navigation.ContrailApp
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
            ContrailTheme {
                ContrailApp { tab ->
                    when (tab) {
                        TopLevelDestination.Trips -> TripsScreen()
                        TopLevelDestination.Radar -> RadarRoute()
                        TopLevelDestination.Sky -> SkyScreen()
                        TopLevelDestination.Passport -> PassportScreen()
                        TopLevelDestination.Logbook -> LogbookScreen()
                    }
                }
            }
        }
    }
}

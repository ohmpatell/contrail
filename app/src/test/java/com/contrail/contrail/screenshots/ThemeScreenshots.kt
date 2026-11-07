package com.contrail.contrail.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.compose.setSingletonImageLoaderFactory
import com.contrail.contrail.core.data.settings.Appearance
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.core.designsystem.theme.ThemeChoice
import com.contrail.contrail.core.designsystem.theme.colorsFor
import com.contrail.contrail.core.designsystem.theme.typeFor
import com.contrail.contrail.feature.passport.PassportActions
import com.contrail.contrail.feature.passport.PassportScreen
import com.contrail.contrail.feature.settings.SettingsScreen
import com.contrail.contrail.feature.settings.SettingsUiState
import com.contrail.contrail.feature.trips.TripsScreen
import com.contrail.contrail.navigation.ContrailApp
import com.contrail.contrail.navigation.TopLevelDestination
import com.contrail.contrail.testing.TripFixtures
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Trips tab in each new theme, and the Appearance picker. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ThemeScreenshots {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun snap(name: String, choice: ThemeChoice, sun: Double? = null, content: @Composable () -> Unit) {
        compose.setContent {
            setSingletonImageLoaderFactory { fakePhotoLoader(it) }
            ContrailTheme(reducedMotion = true, palette = colorsFor(choice, sun), typography = typeFor(choice)) { content() }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Composable
    private fun trips() = ContrailApp(initial = TopLevelDestination.Trips) { t ->
        when (t) {
            TopLevelDestination.Passport -> PassportScreen(passportState, PassportActions())
            else -> TripsScreen(tripsState, {}, {}, {}, fixedNow = TripFixtures.now)
        }
    }

    @Test fun chart() = snap("theme_chart", ThemeChoice.Chart) { trips() }

    @Test fun glassCockpit() = snap("theme_glass", ThemeChoice.GlassCockpit) { trips() }

    @Test fun goldenHour() = snap("theme_golden", ThemeChoice.GoldenHour, sun = 3.0) { trips() }

    @Test fun chartPassport() = snap("theme_chart_passport", ThemeChoice.Chart) {
        ContrailApp(initial = TopLevelDestination.Passport) { PassportScreen(passportState, PassportActions()) }
    }

    @Test fun appearance() = snap("settings_appearance", ThemeChoice.NightCockpit) {
        SettingsScreen(SettingsUiState(appearance = Appearance(ThemeChoice.GoldenHour), sunElevationDeg = 3.0), onSave = { _, _ -> }, onBack = {})
    }
}

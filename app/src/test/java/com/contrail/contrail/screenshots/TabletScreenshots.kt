package com.contrail.contrail.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.compose.setSingletonImageLoaderFactory
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.feature.passport.StampBookScreen
import com.contrail.contrail.feature.radar.RadarActions
import com.contrail.contrail.feature.radar.RadarScreen
import com.contrail.contrail.navigation.ContrailApp
import com.contrail.contrail.navigation.TopLevelDestination
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A landscape tablet: the rail, the radar's side panel and the stamp book open flat. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-land-mdpi")
class TabletScreenshots {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun snap(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            setSingletonImageLoaderFactory { fakePhotoLoader(it) }
            ContrailTheme(reducedMotion = true) { content() }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun radar() = snap("tablet_radar") {
        ContrailApp(initial = TopLevelDestination.Radar) {
            RadarScreen(ScreenshotTests.idle.copy(selected = ScreenshotTests.selected), RadarActions()) { FakeRadarBackdrop(it) }
        }
    }

    @Test fun sky() = snap("tablet_sky") {
        ContrailApp(initial = TopLevelDestination.Sky) {
            com.contrail.contrail.feature.sky.SkyScreen(skyState, 30f, true, com.contrail.contrail.feature.sky.SkyActions())
        }
    }

    @Test fun stampBook() = snap("tablet_stamp_book") { StampBookScreen(historyState, onBack = {}, startPage = 1) }
}

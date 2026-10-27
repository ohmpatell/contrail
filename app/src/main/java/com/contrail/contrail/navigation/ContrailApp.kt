package com.contrail.contrail.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.contrail.contrail.core.designsystem.component.AuroraBackground
import com.contrail.contrail.core.designsystem.component.LocalBackdropHaze
import com.contrail.contrail.core.designsystem.component.LocalChromeHaze
import com.contrail.contrail.core.designsystem.theme.ContrailMotion
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * The app shell: five tabs behind a floating glass nav bar. [tabContent] is a slot so
 * screenshot tests can swap the live radar map for a static backdrop.
 */
@Composable
fun ContrailApp(
    modifier: Modifier = Modifier,
    initial: TopLevelDestination = TopLevelDestination.Start,
    tabContent: @Composable (TopLevelDestination) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(initial) }
    val chromeHaze = rememberHazeState()
    val backdropHaze = rememberHazeState()
    val stateHolder = rememberSaveableStateHolder()
    val motion = ContrailTheme.motion
    val shell = remember { ShellChrome() }
    val navBarShift by animateFloatAsState(if (shell.navBarHidden) 1f else 0f, motion.gentle(), label = "navBarShift")
    androidx.compose.runtime.LaunchedEffect(Unit) {
        TabRequests.requested.collect { tab -> if (tab != null) { selected = tab; TabRequests.requested.value = null } }
    }

    CompositionLocalProvider(
        LocalChromeHaze provides chromeHaze,
        LocalBackdropHaze provides backdropHaze,
        LocalShellChrome provides shell,
    ) {
        Box(modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().hazeSource(chromeHaze)) {
                AuroraBackground(Modifier.fillMaxSize().hazeSource(backdropHaze, zIndex = 0f))
                AnimatedContent(
                    targetState = selected,
                    transitionSpec = { tabTransition(initialState, targetState, motion) },
                    label = "tabs",
                ) { tab ->
                    stateHolder.SaveableStateProvider(tab.name) { tabContent(tab) }
                }
            }
            GlassNavBar(
                selected = selected,
                onSelect = { selected = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        // Slides below the screen edge when a tab asks for the space.
                        translationY = navBarShift * size.height * 1.6f
                        alpha = 1f - navBarShift
                    }
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(
                        horizontal = ContrailTheme.spacing.gutter,
                        vertical = ContrailTheme.spacing.md,
                    ),
            )
        }
    }
}

/** Tabs slide in from the side they sit on in the bar, so the motion matches the tap. */
private fun tabTransition(
    from: TopLevelDestination,
    to: TopLevelDestination,
    motion: ContrailMotion,
): ContentTransform {
    if (motion.reduced) return fadeIn(tween(0)) togetherWith fadeOut(tween(0))
    val direction = if (to.ordinal > from.ordinal) 1 else -1
    val enter = slideInHorizontally(motion.gentle()) { width -> direction * width / 6 } +
        fadeIn(tween(220, delayMillis = 60)) +
        scaleIn(motion.gentle(), initialScale = 0.98f)
    val exit = slideOutHorizontally(motion.gentle()) { width -> -direction * width / 8 } +
        fadeOut(tween(160))
    return enter togetherWith exit
}

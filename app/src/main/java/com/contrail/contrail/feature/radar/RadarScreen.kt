package com.contrail.contrail.feature.radar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.contrail.contrail.core.data.model.WatchItem
import com.contrail.contrail.core.data.model.WatchKind
import com.contrail.contrail.core.data.repository.RadarLayers
import com.contrail.contrail.core.designsystem.component.GlassIconButton
import com.contrail.contrail.core.designsystem.component.LocalBackdropHaze
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.feature.radar.components.AircraftSheet
import com.contrail.contrail.feature.radar.components.FilterPanel
import com.contrail.contrail.feature.radar.components.FollowHud
import com.contrail.contrail.feature.radar.components.LayersPanel
import com.contrail.contrail.feature.radar.components.RadarHud
import com.contrail.contrail.navigation.LocalShellChrome
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** Everything the radar overlays can ask for. */
data class RadarActions(
    val onTogglePanel: (RadarPanel) -> Unit = {},
    val onFiltersChange: ((RadarFilters) -> RadarFilters) -> Unit = {},
    val onClearFilters: () -> Unit = {},
    val onLayersChange: ((RadarLayers) -> RadarLayers) -> Unit = {},
    val onSelectAircraft: (String) -> Unit = {},
    val onDismissAircraft: () -> Unit = {},
    val onToggleFollow: () -> Unit = {},
    val onLocate: () -> Unit = {},
    val onSheetHeightChanged: (Int) -> Unit = {},
    val onWatch: (WatchItem) -> Unit = {},
    val onUnwatch: (WatchKind, String) -> Unit = { _, _ -> },
    val onOpenAircraft: (String) -> Unit = {},
)

/**
 * The radar: a full-screen map with frosted overlays. [map] draws the live MapLibre view
 * in the app and a static stand-in in screenshot tests.
 */
@Composable
fun RadarScreen(
    state: RadarUiState,
    actions: RadarActions,
    modifier: Modifier = Modifier,
    sheetStartsExpanded: Boolean = false,
    map: @Composable (Modifier) -> Unit,
) {
    val mapHaze = rememberHazeState()
    val spacing = ContrailTheme.spacing
    val motion = ContrailTheme.motion
    val density = LocalDensity.current
    var sheetHeightPx by remember { mutableIntStateOf(0) }
    val shell = LocalShellChrome.current
    val sheetOpen = state.selected != null
    LaunchedEffect(sheetOpen) { shell.navBarHidden = sheetOpen }
    DisposableEffect(shell) { onDispose { shell.navBarHidden = false } }

    CompositionLocalProvider(LocalBackdropHaze provides mapHaze) {
        BoxWithConstraints(modifier.fillMaxSize()) {
            val panelMaxHeight = maxHeight * 0.62f
            map(Modifier.fillMaxSize().hazeSource(mapHaze))

            Column(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = spacing.lg, vertical = spacing.sm),
            ) {
                val followed = state.selected?.aircraft?.takeIf { state.following }
                AnimatedContent(
                    targetState = followed != null,
                    transitionSpec = { (fadeIn(motion.fade()) + slideInVertically(motion.gentle()) { -it / 3 }) togetherWith fadeOut(motion.fade(150)) },
                    label = "hud",
                ) { isFollowing ->
                    if (isFollowing && followed != null) {
                        FollowHud(followed, onStop = actions.onToggleFollow)
                    } else {
                        RadarHud(state, actions.onTogglePanel, actions.onSelectAircraft)
                    }
                }
                AnimatedVisibility(
                    visible = state.panel != RadarPanel.None,
                    enter = fadeIn(motion.fade(180)) + expandVertically(motion.gentle(), expandFrom = Alignment.Top),
                    exit = fadeOut(motion.fade(140)) + shrinkVertically(motion.gentle(), shrinkTowards = Alignment.Top),
                    modifier = Modifier.padding(top = spacing.sm),
                ) {
                    AnimatedContent(targetState = state.panel, label = "panel") { panel ->
                        when (panel) {
                            RadarPanel.Filters -> FilterPanel(
                                filters = state.filters,
                                facets = state.facets,
                                shownCount = state.shownCount,
                                totalCount = state.totalCount,
                                onChange = actions.onFiltersChange,
                                onClear = actions.onClearFilters,
                                watchlist = state.watchlist,
                                watchedCount = state.watchedCount,
                                onWatch = actions.onWatch,
                                onUnwatch = actions.onUnwatch,
                                maxHeight = panelMaxHeight,
                            )
                            RadarPanel.Layers -> LayersPanel(state.layers, actions.onLayersChange)
                            RadarPanel.None -> Box(Modifier)
                        }
                    }
                }
            }

            val sheetVisible = state.selected != null
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = spacing.navBarClearance - spacing.md),
            ) {
                AnimatedVisibility(
                    visible = !sheetVisible,
                    enter = scaleIn(motion.bouncy()) + fadeIn(),
                    exit = scaleOut(motion.snappy()) + fadeOut(),
                    modifier = Modifier.align(Alignment.End).padding(end = spacing.lg, bottom = spacing.sm),
                ) {
                    GlassIconButton(ContrailIcons.Locate, "Centre on my location", actions.onLocate)
                }
                Attribution(Modifier.padding(horizontal = spacing.lg).graphicsLayer { alpha = if (sheetVisible) 0f else 1f })
            }

            AircraftSheet(
                selected = state.selected,
                following = state.following,
                onFollow = actions.onToggleFollow,
                onDismiss = actions.onDismissAircraft,
                watchlist = state.watchlist,
                onWatch = actions.onWatch,
                onUnwatch = actions.onUnwatch,
                onOpenAircraft = actions.onOpenAircraft,
                onVisibleHeightChanged = {
                    sheetHeightPx = it
                    actions.onSheetHeightChanged(it)
                },
                startExpanded = sheetStartsExpanded,
            )
        }
    }
}

/** Data and tile credits, as OpenFreeMap, OpenMapTiles, OSM and adsb.lol (ODbL) ask. */
@Composable
private fun Attribution(modifier: Modifier = Modifier) {
    Text(
        "© OpenFreeMap · OpenStreetMap · ADS-B adsb.fi · adsb.lol",
        style = ContrailTheme.type.eyebrow.copy(letterSpacing = ContrailTheme.type.bodySmall.letterSpacing),
        color = ContrailTheme.colors.textTertiary.copy(alpha = 0.8f),
        modifier = modifier,
        maxLines = 1,
    )
}

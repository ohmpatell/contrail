package com.contrail.contrail.feature.radar

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.repository.RadarCamera
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.feature.radar.map.RadarMap
import com.contrail.contrail.feature.radar.map.RadarMapController
import com.contrail.contrail.feature.radar.components.HangarEdgeLayer
import com.contrail.contrail.feature.radar.components.HangarEdges
import com.contrail.contrail.feature.radar.components.SearchActions
import com.contrail.contrail.navigation.LocalNavigator
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest

/** The live radar tab: wires the view model to the MapLibre controller and the overlays. */
@Composable
fun RadarRoute(onOpenAircraft: (String) -> Unit = {}, viewModel: RadarViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var controller by remember { mutableStateOf<RadarMapController?>(null) }
    var userLocation by remember { mutableStateOf<LatLon?>(null) }
    val camera by produceState<RadarCamera?>(null) { value = viewModel.initialCamera() }

    DisposableEffect(Unit) {
        viewModel.setActive(true)
        onDispose { viewModel.setActive(false) }
    }

    // Plane motion never goes through Compose state: these collectors feed MapLibre directly.
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        viewModel.mapTraffic.collectLatest { c.setTraffic(it) }
    }
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        viewModel.trails.collectLatest { c.setTrails(it) }
    }
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        viewModel.selected.collectLatest { c.setSelected(it) }
    }
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        viewModel.isFollowing.collectLatest { c.setFollowing(it) }
    }
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        viewModel.layers.collectLatest { c.setLayers(it) }
    }
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        viewModel.weatherTiles.collectLatest { c.setWeather(it) }
    }
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        viewModel.routeOverlay.collectLatest { c.setRoute(it) }
    }
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        viewModel.watch.collectLatest { c.setWatchlist(it) }
    }
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        viewModel.hangarKeys.collectLatest { c.setHangar(it) }
    }
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        viewModel.cameraMove.collect { move ->
            if (move != null) {
                c.flyTo(move.to, move.zoom)
                viewModel.cameraMoved()
            }
        }
    }
    LaunchedEffect(controller, userLocation) { controller?.setUserLocation(userLocation) }
    var insets by remember { mutableStateOf(listOf(0, 0, 0, 0)) }
    LaunchedEffect(controller, insets) { controller?.setViewInsets(insets[0], insets[1], insets[2], insets[3]) }

    val locate: () -> Unit = {
        requestLocation(context) { loc ->
            userLocation = loc
            controller?.flyTo(loc, 9.0)
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) locate()
    }

    BackHandler(enabled = state.selected != null || state.panel != RadarPanel.None) {
        if (state.panel != RadarPanel.None) viewModel.togglePanel(state.panel) else viewModel.clearSelection()
    }

    // Bumped on every camera move; only the edge-marker overlay reads it, so only it redraws.
    val cameraTick = remember { mutableIntStateOf(0) }
    val callbacks = remember(viewModel) {
        object : RadarMapController.Callbacks {
            override fun onAircraftTapped(hex: String) = viewModel.onAircraftTapped(hex)
            override fun onMapTapped() = viewModel.onMapTapped()
            override fun onViewportChanged(viewport: Viewport) = viewModel.onViewportChanged(viewport)
            override fun onUserGesture() = viewModel.onUserGesture()
            override fun onCameraMoved() {
                cameraTick.intValue++
            }
        }
    }
    val nav = LocalNavigator.current
    val density = LocalDensity.current

    RadarScreen(
        state = state,
        actions = RadarActions(
            onTogglePanel = viewModel::togglePanel,
            onFiltersChange = viewModel::updateFilters,
            onClearFilters = viewModel::clearFilters,
            onLayersChange = viewModel::updateLayers,
            onSelectAircraft = viewModel::onAircraftTapped,
            onDismissAircraft = viewModel::clearSelection,
            onToggleFollow = viewModel::toggleFollow,
            onLocate = {
                if (hasLocationPermission(context)) {
                    locate()
                } else {
                    permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            },
            onViewInsets = { l, t, r, b -> insets = listOf(l, t, r, b) },
            onRecentre = viewModel::recentre,
            onExpandSheet = viewModel::expandSheet,
            search = SearchActions(
                onText = viewModel::onSearchText,
                onSubmit = viewModel::submitSearch,
                onAircraft = viewModel::openSearchResult,
                onAirport = viewModel::openSearchAirport,
                onClearRecent = viewModel::clearRecentSearches,
                onHangarPlane = viewModel::openHangarPlane,
                onOpenHangar = nav::openHangar,
                onPinRegistration = viewModel::pinRegistration,
            ),
            onTogglePin = viewModel::togglePin,
            onWatch = viewModel::watch,
            onUnwatch = viewModel::unwatch,
            onOpenAircraft = onOpenAircraft,
        ),
        overlay = {
            val c = controller
            cameraTick.intValue
            val pinned = state.hangar
            if (c != null && pinned.isNotEmpty()) {
                val (center, bearing) = c.cameraCenter() ?: (null to 0.0)
                if (center != null) {
                    val markers = HangarEdges.place(
                        pinned, center, bearing, c.clearArea(),
                        marginPx = with(density) { 36.dp.toPx() },
                        now = System.currentTimeMillis(),
                        inView = c::isInView,
                    )
                    HangarEdgeLayer(markers, onTap = viewModel::openHangarPlane)
                }
            }
        },
    ) { mapModifier ->
        val initial = camera
        AnimatedVisibility(visible = initial != null, enter = fadeIn(ContrailTheme.motion.fade(500))) {
            if (initial != null) {
                RadarMap(
                    initialCamera = initial,
                    callbacks = callbacks,
                    onReady = { controller = it },
                    modifier = mapModifier.then(Modifier.fillMaxSize()),
                )
            }
        }
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** Uses the freshest recent fix if there is one, otherwise asks for a new one. */
@SuppressLint("MissingPermission")
private fun requestLocation(context: Context, onResult: (LatLon) -> Unit) {
    if (!hasLocationPermission(context)) return
    val lm = context.getSystemService(LocationManager::class.java) ?: return
    val recent = lm.getProviders(true)
        .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }
    if (recent != null) {
        onResult(LatLon(recent.latitude, recent.longitude))
        return
    }
    val provider = when {
        lm.isProviderEnabled(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
        lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> LocationManager.GPS_PROVIDER
    }
    runCatching {
        lm.getCurrentLocation(provider, null, ContextCompat.getMainExecutor(context)) { loc ->
            if (loc != null) onResult(LatLon(loc.latitude, loc.longitude))
        }
    }
}

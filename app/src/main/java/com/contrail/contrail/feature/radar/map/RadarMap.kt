package com.contrail.contrail.feature.radar.map

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.contrail.contrail.core.data.repository.RadarCamera
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * Hosts the MapLibre view. Uses a TextureView so the frosted-glass overlays can blur the
 * map, and forwards the lifecycle so rendering pauses with the screen.
 */
@Composable
fun RadarMap(
    initialCamera: RadarCamera,
    callbacks: RadarMapController.Callbacks,
    onReady: (RadarMapController) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val canvas = ContrailTheme.colors.canvas.toArgb()
    val reduced = ContrailTheme.motion.reduced
    val latestCallbacks = rememberUpdatedState(callbacks)
    val latestOnReady = rememberUpdatedState(onReady)
    val holder = remember { ControllerHolder() }

    val mapView = remember {
        MapView(context, options(context, initialCamera, canvas)).apply { onCreate(null) }
    }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    holder.controller?.start()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    holder.controller?.stop()
                    mapView.onPause()
                }
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            holder.controller?.stop()
            holder.controller = null
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    DisposableEffect(mapView) {
        mapView.getMapAsync { map ->
            val json = context.assets.open(STYLE_ASSET).bufferedReader().use { it.readText() }
            map.setStyle(Style.Builder().fromJson(json)) { style ->
                if (mapView.isDestroyed) return@setStyle
                val controller = RadarMapController(
                    map = map,
                    style = style,
                    density = context.resources.displayMetrics.density,
                    reducedMotion = reduced,
                    callbacks = object : RadarMapController.Callbacks {
                        override fun onAircraftTapped(hex: String) = latestCallbacks.value.onAircraftTapped(hex)
                        override fun onMapTapped() = latestCallbacks.value.onMapTapped()
                        override fun onViewportChanged(viewport: com.contrail.contrail.feature.radar.Viewport) =
                            latestCallbacks.value.onViewportChanged(viewport)
                        override fun onUserGesture() = latestCallbacks.value.onUserGesture()
                    },
                )
                holder.controller = controller
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) controller.start()
                latestOnReady.value(controller)
            }
        }
        onDispose { }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private class ControllerHolder {
    var controller: RadarMapController? = null
}

private fun options(context: Context, camera: RadarCamera, canvas: Int) =
    MapLibreMapOptions.createFromAttributes(context)
        .textureMode(true)
        .foregroundLoadColor(canvas)
        .camera(CameraPosition.Builder().target(LatLng(camera.lat, camera.lon)).zoom(camera.zoom).build())
        .attributionEnabled(false)
        .logoEnabled(false)
        .compassEnabled(false)
        .tiltGesturesEnabled(false)
        .minZoomPreference(2.0)
        .maxZoomPreference(16.5)

private const val STYLE_ASSET = "map/contrail_dark.json"

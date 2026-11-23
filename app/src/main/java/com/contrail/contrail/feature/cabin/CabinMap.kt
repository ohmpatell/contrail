package com.contrail.contrail.feature.cabin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.reference.Silhouette
import com.contrail.contrail.feature.radar.RouteOverlay
import com.contrail.contrail.feature.radar.map.AircraftIcons
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * The cabin's moving map: the night style (whose tiles the flight pack saved along the route, so
 * it draws with no internet), the planned route dashed, the path you've really flown solid, and
 * your plane. It follows the plane until you pan, and [recentre] brings it back.
 */
@Composable
fun CabinMap(
    route: List<LatLon>,
    flown: List<LatLon>,
    position: LatLon?,
    headingDeg: Double,
    accent: Color,
    silhouette: Silhouette,
    follow: Boolean,
    onUserPan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current.density
    val mapView = remember {
        MapView(
            context,
            MapLibreMapOptions.createFromAttributes(context)
                .textureMode(true)
                .camera(CameraPosition.Builder().target(LatLng(position?.lat ?: 0.0, position?.lon ?: 0.0)).zoom(4.2).build())
                .attributionEnabled(false)
                .logoEnabled(false)
                .compassEnabled(false)
                .tiltGesturesEnabled(false)
                .minZoomPreference(1.5)
                .maxZoomPreference(9.0),
        ).apply { onCreate(null) }
    }
    var ready by remember { mutableStateOf<Pair<MapLibreMap, Style>?>(null) }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }
    DisposableEffect(mapView) {
        mapView.getMapAsync { map ->
            val json = context.assets.open("map/contrail_dark.json").bufferedReader().use { it.readText() }
            map.setStyle(Style.Builder().fromJson(json)) { style ->
                if (mapView.isDestroyed) return@setStyle
                AircraftIcons.render(density).forEach { (name, bmp) -> style.addImage(name, bmp, true) }
                style.addSource(GeoJsonSource(SRC_ROUTE))
                style.addSource(GeoJsonSource(SRC_FLOWN))
                style.addSource(GeoJsonSource(SRC_PLANE))
                style.addLayer(
                    LineLayer(L_ROUTE, SRC_ROUTE).withProperties(
                        PropertyFactory.lineColor(accent.copy(alpha = 0.55f).toArgb()),
                        PropertyFactory.lineWidth(2f),
                        PropertyFactory.lineDasharray(arrayOf(2f, 2f)),
                    ),
                )
                style.addLayer(
                    LineLayer(L_FLOWN, SRC_FLOWN).withProperties(
                        PropertyFactory.lineColor(accent.toArgb()),
                        PropertyFactory.lineWidth(3.5f),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    ),
                )
                style.addLayer(
                    SymbolLayer(L_PLANE, SRC_PLANE).withProperties(
                        PropertyFactory.iconImage(AircraftIcons.imageName(silhouette)),
                        PropertyFactory.iconColor(Color.White.toArgb()),
                        PropertyFactory.iconHaloColor(accent.toArgb()),
                        PropertyFactory.iconHaloWidth(3f),
                        PropertyFactory.iconSize(1.25f),
                        PropertyFactory.iconRotate(Expression.get("hdg")),
                        PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                        PropertyFactory.iconAllowOverlap(true),
                    ),
                )
                map.uiSettings.isAttributionEnabled = false
                map.uiSettings.isLogoEnabled = false
                map.addOnMoveListener(object : MapLibreMap.OnMoveListener {
                    override fun onMoveBegin(detector: org.maplibre.android.gestures.MoveGestureDetector) = onUserPan()
                    override fun onMove(detector: org.maplibre.android.gestures.MoveGestureDetector) = Unit
                    override fun onMoveEnd(detector: org.maplibre.android.gestures.MoveGestureDetector) = Unit
                })
                ready = map to style
            }
        }
        onDispose { }
    }

    LaunchedEffect(ready, route) {
        val (_, style) = ready ?: return@LaunchedEffect
        (style.getSource(SRC_ROUTE) as? GeoJsonSource)?.setGeoJson(line(route))
    }
    LaunchedEffect(ready, flown) {
        val (_, style) = ready ?: return@LaunchedEffect
        (style.getSource(SRC_FLOWN) as? GeoJsonSource)?.setGeoJson(line(flown + listOfNotNull(position)))
    }
    LaunchedEffect(ready, position, headingDeg, follow) {
        val (map, style) = ready ?: return@LaunchedEffect
        val p = position ?: return@LaunchedEffect
        val plane = Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).apply { addNumberProperty("hdg", headingDeg) }
        (style.getSource(SRC_PLANE) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeature(plane))
        if (follow) map.animateCamera(CameraUpdateFactory.newLatLng(LatLng(p.lat, p.lon)), 900)
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun line(points: List<LatLon>): FeatureCollection {
    if (points.size < 2) return FeatureCollection.fromFeatures(emptyList())
    val unwrapped = RouteOverlay.unwrap(points)
    return FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(unwrapped.map { Point.fromLngLat(it.lon, it.lat) })))
}

private const val SRC_ROUTE = "cabin-route"
private const val SRC_FLOWN = "cabin-flown"
private const val SRC_PLANE = "cabin-plane"
private const val L_ROUTE = "cabin-route"
private const val L_FLOWN = "cabin-flown"
private const val L_PLANE = "cabin-plane"

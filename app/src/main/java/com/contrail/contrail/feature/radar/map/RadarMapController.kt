package com.contrail.contrail.feature.radar.map

import android.graphics.PointF
import android.graphics.RectF
import android.os.SystemClock
import android.view.Choreographer
import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.model.LiveAircraft
import com.contrail.contrail.core.data.model.TrailPoint
import com.contrail.contrail.core.data.reference.Rarity
import com.contrail.contrail.core.data.repository.RadarLayers as LayerToggles
import com.contrail.contrail.core.data.repository.DefaultLiveTrafficRepository
import com.contrail.contrail.core.network.weather.WeatherTiles
import com.contrail.contrail.feature.radar.Viewport
import com.google.gson.JsonObject
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.hypot

/**
 * Drives the radar's MapLibre layers. Aircraft positions are interpolated here, on the
 * main thread's frame clock, and pushed straight into a GeoJSON source: Compose never
 * recomposes for plane motion.
 */
class RadarMapController(
    private val map: MapLibreMap,
    private val style: Style,
    private val density: Float,
    reducedMotion: Boolean,
    private val callbacks: Callbacks,
) : Choreographer.FrameCallback {

    interface Callbacks {
        fun onAircraftTapped(hex: String)
        fun onMapTapped()
        fun onViewportChanged(viewport: Viewport)
        fun onUserGesture()
    }

    private val motion = MotionModel(extrapolate = !reducedMotion)
    private val animate = !reducedMotion
    private val sample = MotionModel.Sample()
    private val features = ArrayList<Feature>(512)
    private val props = HashMap<String, JsonObject>()
    private val trailTips = HashMap<String, TrailPoint>()

    private val aircraftSource = GeoJsonSource(RadarLayers.SRC_AIRCRAFT, GeoJsonOptions().withBuffer(16).withTolerance(0.2f))
    private val trailSource = GeoJsonSource(RadarLayers.SRC_TRAILS, GeoJsonOptions().withTolerance(0.3f))
    private val nightSource = GeoJsonSource(RadarLayers.SRC_NIGHT)
    private val meSource = GeoJsonSource(RadarLayers.SRC_ME)

    private var selectedHex: String? = null
    private var following = false
    private var followZoom: Double? = null
    private var showTrails = true
    private var running = false
    private var dirty = true
    private var frameSkip = 0
    private var frameCounter = 0
    private var lastTrails: Map<String, List<TrailPoint>> = emptyMap()
    private var lastNightUpdate = 0L
    private var hasEmergency = false
    private var weatherUrl: String? = null

    init {
        AircraftIcons.render(density).forEach { (name, bitmap) -> style.addImage(name, bitmap, true) }
        style.addSource(nightSource)
        style.addSource(trailSource)
        style.addSource(aircraftSource)
        style.addSource(meSource)
        addBelowLabels(RadarLayers.night())
        style.addLayer(RadarLayers.trails())
        style.addLayer(RadarLayers.trailHeads())
        style.addLayer(RadarLayers.rareGlow())
        style.addLayer(RadarLayers.emergency())
        style.addLayer(RadarLayers.selected())
        style.addLayer(RadarLayers.rareRing())
        style.addLayer(RadarLayers.aircraft())
        style.addLayer(RadarLayers.labels())
        style.addLayer(RadarLayers.me())
        updateNight(System.currentTimeMillis())

        map.uiSettings.isRotateGesturesEnabled = true
        map.uiSettings.isTiltGesturesEnabled = false
        map.uiSettings.isAttributionEnabled = false
        map.uiSettings.isLogoEnabled = false
        map.uiSettings.isCompassEnabled = false

        map.addOnMapClickListener { latLng -> handleTap(map.projection.toScreenLocation(latLng)); true }
        map.addOnCameraIdleListener { callbacks.onViewportChanged(currentViewport()) }
        map.addOnMoveListener(object : MapLibreMap.OnMoveListener {
            override fun onMoveBegin(detector: org.maplibre.android.gestures.MoveGestureDetector) = callbacks.onUserGesture()
            override fun onMove(detector: org.maplibre.android.gestures.MoveGestureDetector) = Unit
            override fun onMoveEnd(detector: org.maplibre.android.gestures.MoveGestureDetector) = Unit
        })
    }

    // region Inputs

    fun setTraffic(aircraft: List<LiveAircraft>) {
        motion.update(aircraft, System.currentTimeMillis())
        props.keys.retainAll(motion.hexes)
        aircraft.forEach { props[it.hex] = staticProps(it) }
        hasEmergency = aircraft.any { it.isEmergency }
        dirty = true
        if (!running) renderFrame()
    }

    fun setTrails(trails: Map<String, List<TrailPoint>>) {
        lastTrails = trails
        rebuildTrails()
    }

    fun setSelected(hex: String?) {
        if (hex == selectedHex) return
        val previous = selectedHex
        selectedHex = hex
        listOfNotNull(previous, hex).forEach { h -> motion.fix(h)?.let { props[h] = staticProps(it) } }
        rebuildTrails()
        dirty = true
        if (!running) renderFrame()
    }

    fun setFollowing(follow: Boolean) {
        following = follow
        followZoom = if (follow) maxOf(map.cameraPosition.zoom, FOLLOW_MIN_ZOOM) else null
        if (follow && !animate) {
            val hex = selectedHex ?: return
            if (motion.sample(hex, System.currentTimeMillis(), sample)) {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(sample.lat, sample.lon), followZoom!!))
            }
        }
    }

    fun setLayers(layers: LayerToggles) {
        RadarLayers.AIRPORT_LAYERS.forEach { setVisible(it, layers.airportLabels) }
        setVisible(RadarLayers.L_NIGHT, layers.dayNight)
        setVisible(RadarLayers.L_LABELS, layers.callsigns)
        setVisible(RadarLayers.L_TRAILS, layers.trails)
        setVisible(RadarLayers.L_TRAIL_HEADS, layers.trails)
        setVisible(RadarLayers.L_WEATHER, layers.weather)
        if (showTrails != layers.trails) {
            showTrails = layers.trails
            dirty = true
        }
    }

    fun setWeather(tiles: WeatherTiles?) {
        if (tiles?.urlTemplate == weatherUrl) return
        style.getLayer(RadarLayers.L_WEATHER)?.let { style.removeLayer(it) }
        style.getSource(RadarLayers.SRC_WEATHER)?.let { style.removeSource(it) }
        weatherUrl = tiles?.urlTemplate
        if (tiles == null) return
        val tileSet = TileSet("2.2.0", tiles.urlTemplate).apply {
            maxZoom = tiles.maxZoom.toFloat()
            attribution = tiles.attribution
        }
        style.addSource(RasterSource(RadarLayers.SRC_WEATHER, tileSet, 256))
        val layer = RasterLayer(RadarLayers.L_WEATHER, RadarLayers.SRC_WEATHER).withProperties(
            PropertyFactory.rasterOpacity(0.55f),
            PropertyFactory.rasterFadeDuration(300f),
        )
        addBelowLabels(layer, below = RadarLayers.L_NIGHT)
    }

    fun setUserLocation(location: LatLon?) {
        meSource.setGeoJson(
            if (location == null) FeatureCollection.fromFeatures(emptyList())
            else FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(location.lon, location.lat))),
        )
    }

    fun flyTo(location: LatLon, zoom: Double) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.lat, location.lon), zoom), 900)
    }

    fun setBottomPadding(px: Int) {
        map.moveCamera(CameraUpdateFactory.paddingTo(0.0, 0.0, 0.0, px.toDouble()))
    }

    // endregion

    // region Frame loop

    fun start() {
        if (running) return
        running = true
        if (animate) Choreographer.getInstance().postFrameCallback(this) else renderFrame()
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        frameCounter++
        if (frameSkip == 0 || frameCounter % (frameSkip + 1) == 0) {
            val began = SystemClock.elapsedRealtimeNanos()
            renderFrame()
            adaptFrameBudget((SystemClock.elapsedRealtimeNanos() - began) / 1_000_000.0)
        }
        Choreographer.getInstance().postFrameCallback(this)
    }

    /** If building the frame gets expensive (huge traffic counts), update every 2nd or 3rd vsync. */
    private var avgFrameMs = 0.0
    private fun adaptFrameBudget(ms: Double) {
        avgFrameMs = avgFrameMs * 0.9 + ms * 0.1
        frameSkip = when {
            avgFrameMs > 14 -> 2
            avgFrameMs > 7 -> 1
            else -> 0
        }
    }

    private fun renderFrame() {
        val now = System.currentTimeMillis()
        if (!animate && !dirty) return
        dirty = false
        features.clear()
        val headProps = if (showTrails) JsonObject() else null
        motion.forEach(now, sample) { fix, s ->
            val p = props[fix.hex] ?: return@forEach
            p.addProperty(RadarLayers.P_HEADING, s.headingDeg)
            p.addProperty(RadarLayers.P_ALT, s.altitudeFt.toInt())
            p.addProperty(RadarLayers.P_FRESH, s.freshness)
            features += Feature.fromGeometry(Point.fromLngLat(s.lon, s.lat), p)
            if (headProps != null) {
                val tip = trailTips[fix.hex]
                if (tip != null && Geo.distanceM(tip.lat, tip.lon, s.lat, s.lon) < 30_000) {
                    val head = JsonObject().apply {
                        addProperty(RadarLayers.P_ALT, s.altitudeFt.toInt())
                        addProperty(RadarLayers.P_GROUND, s.onGround)
                        addProperty(RadarLayers.P_FRESH, s.freshness)
                        addProperty(RadarLayers.P_SELECTED, fix.hex == selectedHex)
                    }
                    features += Feature.fromGeometry(
                        LineString.fromLngLats(listOf(Point.fromLngLat(tip.lon, tip.lat), Point.fromLngLat(s.lon, s.lat))),
                        head,
                    )
                }
            }
            if (following && fix.hex == selectedHex) {
                // Glide the camera onto the aircraft and ease in to a readable zoom.
                val cam = map.cameraPosition
                val target = cam.target
                val ease = if (target == null) 1.0 else FOLLOW_EASE
                val lat = if (target == null) s.lat else target.latitude + (s.lat - target.latitude) * ease
                val lon = if (target == null) s.lon else target.longitude + Geo.headingDelta(target.longitude, s.lon) * ease
                val zoom = followZoom?.let { cam.zoom + (it - cam.zoom) * FOLLOW_EASE } ?: cam.zoom
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), zoom))
            }
        }
        aircraftSource.setGeoJson(FeatureCollection.fromFeatures(features))
        if (hasEmergency && animate) pulseEmergencies(now)
        if (now - lastNightUpdate > 60_000) updateNight(now)
    }

    private fun pulseEmergencies(now: Long) {
        val phase = (now % 1_400L) / 1_400f
        style.getLayer(RadarLayers.L_EMERGENCY)?.setProperties(
            PropertyFactory.circleRadius(12f + 26f * phase),
            PropertyFactory.circleOpacity(0.35f * (1f - phase)),
            PropertyFactory.circleStrokeOpacity(1f - phase),
        )
    }

    // endregion

    private fun staticProps(a: LiveAircraft): JsonObject = JsonObject().apply {
        addProperty(RadarLayers.P_HEX, a.hex)
        addProperty(RadarLayers.P_ICON, AircraftIcons.imageName(a.silhouette))
        addProperty(RadarLayers.P_GROUND, a.onGround)
        addProperty(RadarLayers.P_SELECTED, a.hex == selectedHex)
        addProperty(RadarLayers.P_EMERGENCY, a.isEmergency)
        addProperty(
            RadarLayers.P_RARITY,
            when (a.rarity) {
                Rarity.Legendary -> 2
                Rarity.Rare -> 1
                else -> 0
            },
        )
        addProperty(RadarLayers.P_LABEL, a.displayCallsign)
        addProperty(RadarLayers.P_LABEL2, altitudeLabel(a))
        // Draw order: selected on top, then emergencies, then by altitude.
        val sort = when {
            a.hex == selectedHex -> 200_000
            a.isEmergency -> 150_000
            else -> (a.altitudeFt ?: 0)
        }
        addProperty(RadarLayers.P_SORT, sort)
    }

    private fun altitudeLabel(a: LiveAircraft): String = when {
        a.onGround -> "GND"
        a.altitudeFt == null -> ""
        a.altitudeFt >= 18_000 -> "FL" + (a.altitudeFt / 100).toString().padStart(3, '0')
        else -> "%,d ft".format(a.altitudeFt)
    }

    private fun rebuildTrails() {
        val now = System.currentTimeMillis()
        val out = ArrayList<Feature>()
        trailTips.clear()
        for ((hex, points) in lastTrails) {
            if (points.isEmpty()) continue
            trailTips[hex] = points.last()
            if (points.size < 2) continue
            val selected = hex == selectedHex
            // Split each trail into a handful of chunks so colour and fade follow altitude and age.
            val chunks = if (selected) points.size - 1 else minOf(TRAIL_CHUNKS, points.size - 1)
            val per = (points.size - 1).toDouble() / chunks
            for (c in 0 until chunks) {
                val from = (c * per).toInt()
                val to = minOf(points.size - 1, ((c + 1) * per).toInt().coerceAtLeast(from + 1))
                val slice = points.subList(from, to + 1)
                val mid = slice[slice.size / 2]
                val age = ((now - mid.timeMs).toDouble() / DefaultLiveTrafficRepository.TRAIL_WINDOW_MS).coerceIn(0.0, 1.0)
                val p = JsonObject().apply {
                    addProperty(RadarLayers.P_ALT, mid.altitudeFt ?: 0)
                    addProperty(RadarLayers.P_GROUND, mid.onGround)
                    addProperty(RadarLayers.P_AGE, age)
                    addProperty(RadarLayers.P_SELECTED, selected)
                }
                out += Feature.fromGeometry(LineString.fromLngLats(slice.map { Point.fromLngLat(it.lon, it.lat) }), p)
            }
        }
        trailSource.setGeoJson(FeatureCollection.fromFeatures(out))
        dirty = true
    }

    private fun updateNight(now: Long) {
        lastNightUpdate = now
        // Three nested bands: night, nautical and astronomical twilight darken progressively.
        val bands = listOf(0.0, -6.0, -12.0).map { altitude ->
            val ring = Terminator.nightPolygon(now, altitude).map { (lon, lat) -> Point.fromLngLat(lon, lat) }
            Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))
        }
        nightSource.setGeoJson(FeatureCollection.fromFeatures(bands))
    }

    private fun handleTap(screen: PointF) {
        val r = TAP_RADIUS_DP * density
        val groundHidden = map.cameraPosition.zoom < RadarLayers.GROUND_HIDDEN_BELOW_ZOOM + 0.5
        val hits = map.queryRenderedFeatures(RectF(screen.x - r, screen.y - r, screen.x + r, screen.y + r), RadarLayers.L_AIRCRAFT)
            .filterNot { groundHidden && it.getBooleanProperty(RadarLayers.P_GROUND) == true }
        val best = hits.minByOrNull { f ->
            val pt = f.geometry() as? Point ?: return@minByOrNull Double.MAX_VALUE
            val s = map.projection.toScreenLocation(LatLng(pt.latitude(), pt.longitude()))
            hypot((s.x - screen.x).toDouble(), (s.y - screen.y).toDouble())
        }
        val hex = best?.getStringProperty(RadarLayers.P_HEX)
        if (hex != null) callbacks.onAircraftTapped(hex) else callbacks.onMapTapped()
    }

    private fun currentViewport(): Viewport {
        val camera = map.cameraPosition
        val target = camera.target ?: LatLng(0.0, 0.0)
        val bounds = map.projection.visibleRegion.latLngBounds
        val center = LatLon(target.latitude, target.longitude)
        val corner = LatLon(bounds.latitudeNorth, bounds.longitudeWest)
        val radiusNm = Geo.distanceM(center, corner) / Geo.METERS_PER_NM
        return Viewport(center, radiusNm, camera.zoom)
    }

    private fun setVisible(layerId: String, visible: Boolean) {
        style.getLayer(layerId)?.setProperties(PropertyFactory.visibility(if (visible) Property.VISIBLE else Property.NONE))
    }

    private fun addBelowLabels(layer: Layer, below: String = RadarLayers.BELOW_LABELS) {
        if (style.getLayer(below) != null) style.addLayerBelow(layer, below) else style.addLayer(layer)
    }

    private companion object {
        const val TRAIL_CHUNKS = 6
        const val TAP_RADIUS_DP = 22f
        const val FOLLOW_MIN_ZOOM = 9.0
        /** Fraction of the remaining distance the follow camera covers each frame. */
        const val FOLLOW_EASE = 0.12
    }
}

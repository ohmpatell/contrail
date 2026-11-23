package com.contrail.contrail.feature.radar.map

import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.common.LatLon
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/** The map camera, as far as floating planes need it. [viewHeightDp] is the map's height in dp. */
data class FloatCamera(val target: LatLon, val zoom: Double, val pitchDeg: Double, val bearingDeg: Double, val viewHeightDp: Double)

/**
 * Makes planes float in the tilted radar. MapLibre Native draws everything on the ground, so a
 * plane at altitude is drawn where the camera's line of sight through it meets the ground: from
 * the camera that looks exactly like a plane up in the air, over its shadow.
 *
 * Heights are drawn in proportion to the camera's own height (cruise at about a tenth of it), so
 * the effect reads the same at every zoom; the order of heights is real, the scale is not.
 */
object Floating3D {
    /** MapLibre's vertical field of view. */
    const val FOV_DEG = 36.86989764584402
    private const val CRUISE_FT = 40_000.0
    private const val CRUISE_SHARE = 0.1

    /** Metres per dp at [zoom] and [lat] (512-dp tiles). */
    fun metersPerDp(lat: Double, zoom: Double): Double = 2 * PI * 6_378_137.0 * cos(Geo.toRad(lat)) / (512 * 2.0.pow(zoom))

    /** Where the camera is: the ground point below it, and its height in metres. */
    fun camera(c: FloatCamera): Pair<LatLon, Double> {
        val distanceDp = (c.viewHeightDp / 2) / tan(Geo.toRad(FOV_DEG / 2))
        val distanceM = distanceDp * metersPerDp(c.target.lat, c.zoom)
        val pitch = Geo.toRad(c.pitchDeg)
        val below = Geo.destination(c.target, c.bearingDeg + 180, distanceM * sin(pitch))
        return below to distanceM * cos(pitch)
    }

    /** How high to draw [altitudeFt] for a camera [cameraM] up. */
    fun drawnHeight(altitudeFt: Double, cameraM: Double): Double = cameraM * CRUISE_SHARE * (altitudeFt / CRUISE_FT).coerceIn(0.0, 1.15)

    /** The ground point a plane above [p] lines up with, seen from a camera above [below] at [cameraM]. */
    fun lift(p: LatLon, altitudeFt: Double, below: LatLon, cameraM: Double): LatLon {
        val h = drawnHeight(altitudeFt, cameraM)
        if (h < 1 || cameraM <= h) return p
        val k = h / (cameraM - h)
        val mPerDegLat = 111_320.0
        val mPerDegLon = 111_320.0 * cos(Geo.toRad(p.lat)).coerceAtLeast(0.01)
        val dy = (p.lat - below.lat) * mPerDegLat
        val dx = Geo.headingDelta(below.lon, p.lon) * mPerDegLon
        return LatLon(p.lat + dy * k / mPerDegLat, p.lon + dx * k / mPerDegLon)
    }

    /**
     * A believable altitude along a route with no track: climbing over the first 200 km,
     * descending over the last 250, cruise between.
     */
    fun routeAltitudeFt(fromStartM: Double, toEndM: Double): Double =
        minOf(37_000.0, fromStartM / 200_000.0 * 37_000.0, toEndM / 250_000.0 * 37_000.0).coerceAtLeast(0.0)
}

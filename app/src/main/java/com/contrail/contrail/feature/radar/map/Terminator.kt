package com.contrail.contrail.feature.radar.map

import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.common.Sun
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** The day/night terminator for the radar's night layer. */
object Terminator {

    data class SubSolar(val lat: Double, val lon: Double)

    /** Point on Earth where the sun is directly overhead. */
    fun subSolarPoint(timeMs: Long): SubSolar = Sun.subSolarPoint(timeMs).let { SubSolar(it.lat, it.lon) }

    /**
     * Polygon (lon, lat pairs) covering everywhere the sun is below [sunAltitudeDeg].
     * 0 is the terminator itself; -6, -12 and -18 are civil, nautical and astronomical twilight.
     */
    fun nightPolygon(timeMs: Long, sunAltitudeDeg: Double = 0.0, stepDeg: Int = 2): List<Pair<Double, Double>> {
        val sun = subSolarPoint(timeMs)
        val decl = Geo.toRad(sun.lat)
        val sinH = sin(Geo.toRad(sunAltitudeDeg))
        // Night covers the pole that is tilted away from the sun.
        val pole = if (sun.lat >= 0) -89.9 else 89.9
        val edge = ArrayList<Pair<Double, Double>>(360 / stepDeg + 4)
        var lon = -180.0
        while (lon <= 180.0) {
            val hourAngle = Geo.toRad(lon - sun.lon)
            // Solve sin(h) = sin(lat)·sin(decl) + cos(lat)·cos(decl)·cos(H) for lat.
            val a = sin(decl)
            val b = cos(decl) * cos(hourAngle)
            val r = sqrt(a * a + b * b)
            val alpha = atan2(b, a)
            val ratio = sinH / r
            val lat = when {
                // The sun never gets this low on this meridian: the band shrinks to the night pole.
                ratio < -1.0 -> pole
                // The sun is always below this altitude here: the band covers the whole meridian.
                ratio > 1.0 -> -pole
                else -> {
                    // Two solutions; the one we want borders the pole that is in polar night.
                    val raw = if (sun.lat >= 0) asin(ratio) - alpha else PI - asin(ratio) - alpha
                    Geo.toDeg(atan2(sin(raw), cos(raw)))
                }
            }
            edge += lon to lat.coerceIn(-89.9, 89.9)
            lon += stepDeg
        }
        return buildList {
            addAll(edge)
            add(180.0 to pole)
            add(-180.0 to pole)
            add(edge.first())
        }
    }

    fun sunElevationDeg(timeMs: Long, lat: Double, lon: Double): Double = Sun.elevationDeg(timeMs, lat, lon)
}

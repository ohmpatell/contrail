package com.contrail.contrail.feature.radar.map

import com.contrail.contrail.core.common.Geo
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

    /** Point on Earth where the sun is directly overhead (NOAA low-precision formulae). */
    fun subSolarPoint(timeMs: Long): SubSolar {
        val t = Instant.ofEpochMilli(timeMs).atOffset(ZoneOffset.UTC)
        val hours = t.hour + t.minute / 60.0 + t.second / 3600.0
        val gamma = 2 * PI / 365.0 * (t.dayOfYear - 1 + (hours - 12) / 24.0)
        val eqTimeMin = 229.18 * (
            0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
                0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma)
            )
        val decl = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
            0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) -
            0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
        val lon = Geo.normalizeLon(-15.0 * (hours - 12.0 + eqTimeMin / 60.0))
        return SubSolar(Geo.toDeg(decl), lon)
    }

    /**
     * Polygon (lon, lat pairs) covering everywhere the sun is below [sunAltitudeDeg].
     * 0 is the terminator itself; -6, -12 and -18 are civil, nautical and astronomical twilight.
     */
    fun nightPolygon(timeMs: Long, sunAltitudeDeg: Double = 0.0, stepDeg: Int = 2): List<Pair<Double, Double>> {
        val sun = subSolarPoint(timeMs)
        val decl = Geo.toRad(sun.lat)
        val sinH = sin(Geo.toRad(sunAltitudeDeg))
        val edge = ArrayList<Pair<Double, Double>>(360 / stepDeg + 4)
        var lon = -180.0
        while (lon <= 180.0) {
            val hourAngle = Geo.toRad(lon - sun.lon)
            // Solve sin(h) = sin(lat)·sin(decl) + cos(lat)·cos(decl)·cos(H) for lat.
            val a = sin(decl)
            val b = cos(decl) * cos(hourAngle)
            val r = sqrt(a * a + b * b)
            val alpha = atan2(b, a)
            val ratio = (sinH / r).coerceIn(-1.0, 1.0)
            // Two solutions; the one we want borders the pole that is in polar night.
            val raw = if (sun.lat >= 0) asin(ratio) - alpha else PI - asin(ratio) - alpha
            val lat = Geo.toDeg(atan2(sin(raw), cos(raw)))
            edge += lon to lat.coerceIn(-89.9, 89.9)
            lon += stepDeg
        }
        // Night covers the pole that is tilted away from the sun.
        val pole = if (sun.lat >= 0) -89.9 else 89.9
        return buildList {
            addAll(edge)
            add(180.0 to pole)
            add(-180.0 to pole)
            add(edge.first())
        }
    }

    /** Sun elevation in degrees at a point, for tests and future "is it dark there" checks. */
    fun sunElevationDeg(timeMs: Long, lat: Double, lon: Double): Double {
        val sun = subSolarPoint(timeMs)
        val p = Geo.toRad(lat)
        val d = Geo.toRad(sun.lat)
        val h = Geo.toRad(lon - sun.lon)
        return Geo.toDeg(asin(sin(p) * sin(d) + cos(p) * cos(d) * cos(h)))
    }
}

package com.contrail.contrail.core.common

import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/** Where the sun is: for the radar's night layer and the Golden Hour theme. */
object Sun {
    /** Point on Earth where the sun is directly overhead (NOAA low-precision formulae). */
    fun subSolarPoint(timeMs: Long): LatLon {
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
        return LatLon(Geo.toDeg(decl), lon)
    }

    /** Sun elevation in degrees above the horizon at a point. */
    fun elevationDeg(timeMs: Long, lat: Double, lon: Double): Double {
        val sun = subSolarPoint(timeMs)
        val p = Geo.toRad(lat)
        val d = Geo.toRad(sun.lat)
        val h = Geo.toRad(lon - sun.lon)
        return Geo.toDeg(asin(sin(p) * sin(d) + cos(p) * cos(d) * cos(h)))
    }

    /** Compass direction of the sun from a point, degrees from north. */
    fun azimuthDeg(timeMs: Long, lat: Double, lon: Double): Double = Geo.bearingDeg(LatLon(lat, lon), subSolarPoint(timeMs))

    /**
     * The next time after [fromMs] (within [withinMs]) that the sun crosses [elevationDeg] going
     * down ([setting]) or up, to the minute. Null if it doesn't (polar day or night, or too far off).
     */
    fun nextCrossing(fromMs: Long, lat: Double, lon: Double, elevationDeg: Double = -0.833, setting: Boolean = true, withinMs: Long = 26 * 3_600_000L): Long? {
        var t = fromMs
        var prev = elevationDeg(t, lat, lon)
        val step = 5 * 60_000L
        while (t - fromMs < withinMs) {
            val next = elevationDeg(t + step, lat, lon)
            val crossed = if (setting) prev >= elevationDeg && next < elevationDeg else prev <= elevationDeg && next > elevationDeg
            if (crossed) {
                // Refine to the minute.
                var a = t
                while (a < t + step) {
                    val e = elevationDeg(a + 60_000L, lat, lon)
                    if (if (setting) e < elevationDeg else e > elevationDeg) return a + 60_000L
                    a += 60_000L
                }
                return t + step
            }
            prev = next
            t += step
        }
        return null
    }
}

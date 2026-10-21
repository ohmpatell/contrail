package com.contrail.contrail.core.common

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

/** Small, allocation-light geodesy helpers on a spherical Earth. */
object Geo {
    const val EARTH_RADIUS_M = 6_371_008.8
    const val METERS_PER_NM = 1_852.0
    const val MPS_PER_KNOT = 0.514444
    const val METERS_PER_DEG_LAT = 111_320.0

    fun toRad(deg: Double) = deg * PI / 180.0
    fun toDeg(rad: Double) = rad * 180.0 / PI

    /** Great-circle distance in metres. */
    fun distanceM(a: LatLon, b: LatLon): Double = distanceM(a.lat, a.lon, b.lat, b.lon)

    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = toRad(lat1)
        val p2 = toRad(lat2)
        val dp = toRad(lat2 - lat1)
        val dl = toRad(lon2 - lon1)
        val h = sin(dp / 2).let { it * it } + cos(p1) * cos(p2) * sin(dl / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Initial bearing from [a] to [b], degrees clockwise from true north in [0, 360). */
    fun bearingDeg(a: LatLon, b: LatLon): Double {
        val p1 = toRad(a.lat)
        val p2 = toRad(b.lat)
        val dl = toRad(b.lon - a.lon)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (toDeg(atan2(y, x)) + 360.0) % 360.0
    }

    /** Point reached travelling [distanceM] from [from] on [bearingDeg]. */
    fun destination(from: LatLon, bearingDeg: Double, distanceM: Double): LatLon {
        val d = distanceM / EARTH_RADIUS_M
        val b = toRad(bearingDeg)
        val p1 = toRad(from.lat)
        val l1 = toRad(from.lon)
        val p2 = asin(sin(p1) * cos(d) + cos(p1) * sin(d) * cos(b))
        val l2 = l1 + atan2(sin(b) * sin(d) * cos(p1), cos(d) - sin(p1) * sin(p2))
        return LatLon(toDeg(p2), normalizeLon(toDeg(l2)))
    }

    fun normalizeLon(lon: Double): Double {
        var l = (lon + 180.0) % 360.0
        if (l < 0) l += 360.0
        return l - 180.0
    }

    /** Shortest signed difference `to - from` between two headings, in (-180, 180]. */
    fun headingDelta(from: Double, to: Double): Double {
        var d = (to - from) % 360.0
        if (d > 180.0) d -= 360.0
        if (d <= -180.0) d += 360.0
        return d
    }

    fun normalizeHeading(h: Double): Double = ((h % 360.0) + 360.0) % 360.0

    /**
     * [segments] + 1 points along the great circle from [a] to [b]. Longitudes are unwrapped
     * (they may leave -180..180) so a line crossing the antimeridian draws continuously.
     */
    fun greatCircle(a: LatLon, b: LatLon, segments: Int = 64): List<LatLon> {
        val p1 = toRad(a.lat); val l1 = toRad(a.lon)
        val p2 = toRad(b.lat); val l2 = toRad(b.lon)
        val d = distanceM(a, b) / EARTH_RADIUS_M
        if (d < 1e-9) return listOf(a, b)
        val out = ArrayList<LatLon>(segments + 1)
        var prevLon = a.lon
        for (i in 0..segments) {
            val f = i.toDouble() / segments
            val sa = kotlin.math.sin((1 - f) * d) / kotlin.math.sin(d)
            val sb = kotlin.math.sin(f * d) / kotlin.math.sin(d)
            val x = sa * cos(p1) * cos(l1) + sb * cos(p2) * cos(l2)
            val y = sa * cos(p1) * sin(l1) + sb * cos(p2) * sin(l2)
            val z = sa * sin(p1) + sb * sin(p2)
            val lat = toDeg(atan2(z, sqrt(x * x + y * y)))
            var lon = toDeg(atan2(y, x))
            while (lon - prevLon > 180) lon -= 360
            while (lon - prevLon < -180) lon += 360
            prevLon = lon
            out += LatLon(lat, lon)
        }
        return out
    }
}

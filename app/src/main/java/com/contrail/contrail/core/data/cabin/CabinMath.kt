package com.contrail.contrail.core.data.cabin

import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.common.Sun
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.pow
import kotlin.math.sqrt

/** A place below the route, from the flight pack. */
data class PackPlace(
    val name: String,
    val countryIso: String?,
    val lat: Double,
    val lon: Double,
    /** A line or two from Wikipedia, when one was found. */
    val blurb: String? = null,
    /** How far along the route (0..1) it's passed. */
    val along: Double = 0.0,
)

/** Where the route crosses into another country (best guess from the nearest airports). */
data class BorderCrossing(val countryIso: String, val countryName: String?, val along: Double)

/** Which side of the plane something is on. */
enum class Side { Left, Right, Ahead, Behind }

/** Something coming up, with when and on which side. */
data class Upcoming(val place: PackPlace, val minutes: Int, val side: Side, val distanceKm: Double)

/** How rough it is right now, from the phone's accelerometer. */
enum class Bumpiness(val label: String) { Smooth("Smooth"), Light("Light chop"), Moderate("Moderate turbulence"), Strong("Strong turbulence") }

/**
 * The maths behind Cabin mode, all of it offline and pure (so it's tested): where you are along
 * the route by the clock, which window things are out of, what's coming up, when the sun sets as
 * seen from cruising height, cabin altitude from air pressure, and how bumpy it is.
 */
object CabinMath {

    /** Total length of a path in metres. */
    fun length(path: List<LatLon>): Double = path.zipWithNext().sumOf { (a, b) -> Geo.distanceM(a, b) }

    /** The point [fraction] (0..1) of the way along [path], and the heading there. */
    fun pointAt(path: List<LatLon>, fraction: Double): Pair<LatLon, Double> {
        if (path.size < 2) return (path.firstOrNull() ?: LatLon(0.0, 0.0)) to 0.0
        val total = length(path)
        var left = total * fraction.coerceIn(0.0, 1.0)
        for ((a, b) in path.zipWithNext()) {
            val d = Geo.distanceM(a, b)
            if (left <= d || b == path.last()) {
                val h = Geo.bearingDeg(a, b)
                return Geo.destination(a, h, left.coerceAtMost(d)) to h
            }
            left -= d
        }
        return path.last() to Geo.bearingDeg(path[path.size - 2], path.last())
    }

    /** How far (0..1) along [path] the point nearest to [p] is. */
    fun fractionOf(path: List<LatLon>, p: LatLon): Double {
        if (path.size < 2) return 0.0
        val total = length(path).coerceAtLeast(1.0)
        var best = Double.MAX_VALUE
        var bestAlong = 0.0
        var run = 0.0
        for ((a, b) in path.zipWithNext()) {
            val d = Geo.distanceM(a, b)
            val at = Geo.alongTrackM(p, a, b).coerceIn(0.0, d)
            val q = if (d > 0) Geo.destination(a, Geo.bearingDeg(a, b), at) else a
            val off = Geo.distanceM(p, q)
            if (off < best) {
                best = off
                bestAlong = run + at
            }
            run += d
        }
        return (bestAlong / total).coerceIn(0.0, 1.0)
    }

    /**
     * Progress by the clock when there's no GPS fix: a slower climb and descent at each end
     * (about 20 minutes each at roughly half cruise speed), steady cruise in between.
     */
    fun estimatedFraction(nowMs: Long, takeoffMs: Long, landingMs: Long): Double {
        val total = (landingMs - takeoffMs).toDouble()
        if (total <= 0) return 0.0
        val t = ((nowMs - takeoffMs) / total).coerceIn(0.0, 1.0)
        val edge = (20 * 60_000.0 / total).coerceAtMost(0.25)
        // Distance per unit time: half speed in the edge phases, full in cruise; normalised to 1.
        val cruise = 1.0 - 2 * edge
        val unit = edge * 0.5 * 2 + cruise
        return when {
            t < edge -> (t * 0.5) / unit
            t > 1 - edge -> 1 - ((1 - t) * 0.5) / unit
            else -> (edge * 0.5 + (t - edge)) / unit
        }.coerceIn(0.0, 1.0)
    }

    /**
     * Your window side from a seat letter: A is the left window everywhere; the right window is F on
     * a narrowbody and J, K or L on a widebody. Anything else is an aisle or middle seat (null).
     */
    fun windowSide(seat: String?, widebody: Boolean): Side? {
        val letter = seat?.trim()?.lastOrNull()?.uppercaseChar() ?: return null
        return when {
            letter == 'A' -> Side.Left
            widebody && letter in "JKL" -> Side.Right
            !widebody && letter == 'F' -> Side.Right
            else -> null
        }
    }

    /** Which side of a plane flying [headingDeg] at [from] something at [to] is on. */
    fun sideOf(from: LatLon, headingDeg: Double, to: LatLon): Side {
        val rel = Geo.headingDelta(headingDeg, Geo.bearingDeg(from, to))
        return when {
            abs(rel) < 20 -> Side.Ahead
            abs(rel) > 160 -> Side.Behind
            rel > 0 -> Side.Right
            else -> Side.Left
        }
    }

    /**
     * Places within [withinKm] of the route still ahead in the next [horizonMin] minutes, soonest
     * first, with the side they'll pass on.
     */
    fun upcoming(path: List<LatLon>, places: List<PackPlace>, at: Double, speedKt: Double, horizonMin: Int = 45, withinKm: Double = 160.0): List<Upcoming> {
        if (path.size < 2 || speedKt < 50) return emptyList()
        val total = length(path)
        val mps = speedKt * Geo.MPS_PER_KNOT
        return places.mapNotNull { p ->
            if (p.along <= at) return@mapNotNull null
            val metres = (p.along - at) * total
            val minutes = (metres / mps / 60).toInt()
            if (minutes > horizonMin) return@mapNotNull null
            val (onRoute, heading) = pointAt(path, p.along)
            val off = Geo.distanceM(onRoute, LatLon(p.lat, p.lon)) / 1000
            if (off > withinKm) return@mapNotNull null
            Upcoming(p, minutes, sideOf(onRoute, heading, LatLon(p.lat, p.lon)).takeIf { off > 8 } ?: Side.Ahead, off)
        }.sortedBy { it.minutes }
    }

    /** How far below level the horizon is from [altitudeM] up, in degrees (about 3.4° at 11,000 m). */
    fun horizonDipDeg(altitudeM: Double): Double = Geo.toDeg(acos(Geo.EARTH_RADIUS_M / (Geo.EARTH_RADIUS_M + altitudeM.coerceAtLeast(0.0))))

    /**
     * The next sunset (or sunrise) you'll see from the plane, walking forward along the route by
     * the clock: the sun sets later up high, because the horizon is lower.
     */
    fun nextSunEvent(path: List<LatLon>, fromFraction: Double, nowMs: Long, landingMs: Long, altitudeM: Double, takeoffMs: Long): SunEvent? {
        val dip = horizonDipDeg(altitudeM)
        val threshold = -0.833 - dip
        val step = 5 * 60_000L
        var t = nowMs
        val pos0 = pointAt(path, fromFraction).first
        var prev = Sun.elevationDeg(t, pos0.lat, pos0.lon)
        while (t < landingMs) {
            val next = t + step
            val f = estimatedFraction(next, takeoffMs, landingMs).coerceAtLeast(fromFraction)
            val (p, heading) = pointAt(path, f)
            val e = Sun.elevationDeg(next, p.lat, p.lon)
            if (prev >= threshold && e < threshold) return SunEvent(true, next, sideOf(p, heading, Sun.subSolarPoint(next)))
            if (prev <= threshold && e > threshold) return SunEvent(false, next, sideOf(p, heading, Sun.subSolarPoint(next)))
            prev = e
            t = next
        }
        return null
    }

    /** Altitude in feet for a pressure in hPa (standard atmosphere). In a cabin this is the cabin altitude. */
    fun pressureAltitudeFt(hPa: Float): Int = (44_330.0 * (1 - (hPa / 1013.25).toDouble().pow(1 / 5.255)) * 3.28084).toInt()

    /**
     * Bumpiness from vertical acceleration (m/s², gravity removed) over the last few seconds: the
     * standard deviation is what you feel.
     */
    fun bumpiness(samples: List<Float>): Bumpiness {
        if (samples.size < 8) return Bumpiness.Smooth
        val mean = samples.average()
        val sd = sqrt(samples.sumOf { (it - mean) * (it - mean) } / samples.size)
        return when {
            sd < 0.35 -> Bumpiness.Smooth
            sd < 0.9 -> Bumpiness.Light
            sd < 2.0 -> Bumpiness.Moderate
            else -> Bumpiness.Strong
        }
    }

    /** Airborne by GPS: fast enough that you can't be taxiing. */
    fun airborne(speedKt: Double?, altitudeFt: Double?, groundElevationFt: Double = 0.0): Boolean =
        (speedKt ?: 0.0) > 150 || ((speedKt ?: 0.0) > 100 && (altitudeFt ?: 0.0) - groundElevationFt > 1_000)

    /** The countries the route passes over, in order, from the nearest airport's country along the way. */
    fun borders(samples: List<Pair<Double, String?>>): List<Pair<Double, String>> {
        val out = mutableListOf<Pair<Double, String>>()
        var last: String? = null
        for ((along, iso) in samples) {
            if (iso != null && iso != last) {
                if (last != null) out += along to iso
                last = iso
            }
        }
        return out
    }
}

/** Sunset or sunrise as seen from the plane. */
data class SunEvent(val sunset: Boolean, val atMs: Long, val side: Side)

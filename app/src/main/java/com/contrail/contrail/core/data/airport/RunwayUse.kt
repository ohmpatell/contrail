package com.contrail.contrail.core.data.airport

import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.model.LiveAircraft
import com.contrail.contrail.core.data.model.TrailPoint
import com.contrail.contrail.core.database.RunwayEntity
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** One landing direction: you land on [ident] at its threshold, flying [headingDeg]. */
data class RunwayEnd(val ident: String, val threshold: LatLon, val headingDeg: Double, val lengthFt: Int?, val farEnd: LatLon)

/** A plane seen landing on or taking off from a runway end. */
data class RunwaySighting(val runway: String, val kind: Kind, val hex: String, val timeMs: Long) {
    enum class Kind { Arrival, Departure }
}

/** A runway use seen before, with the wind at the time, so guesses learn the airport's habits. */
data class RunwayHabit(val runway: String, val kind: RunwaySighting.Kind, val windFromDeg: Double?, val count: Int)

/**
 * Which runways are in use. [observed] when planes were actually seen on them recently; otherwise
 * a guess from the wind, the airport's main runway direction and its habits, and labelled so.
 */
data class RunwaysInUse(
    val arrivals: List<String>,
    val departures: List<String>,
    val observed: Boolean,
    /** How many planes the answer rests on (0 for a guess). */
    val planes: Int = 0,
) {
    val all: Set<String> get() = (arrivals + departures).toSet()
}

object RunwayUse {

    fun ends(runways: List<RunwayEntity>): List<RunwayEnd> = runways.flatMap { r ->
        val le = if (r.leLat != null && r.leLon != null) LatLon(r.leLat, r.leLon) else null
        val he = if (r.heLat != null && r.heLon != null) LatLon(r.heLat, r.heLon) else null
        if (le == null || he == null) return@flatMap emptyList()
        listOf(
            RunwayEnd(r.leIdent, le, r.leHeadingDeg ?: Geo.bearingDeg(le, he), r.lengthFt, he),
            RunwayEnd(r.heIdent, he, r.heHeadingDeg ?: Geo.bearingDeg(he, le), r.lengthFt, le),
        )
    }

    /**
     * Planes on final for, or climbing straight out of, each runway end: from where they are now and
     * from their trails (the last four minutes). Each plane counts once per runway.
     */
    fun sightings(ends: List<RunwayEnd>, elevationFt: Int, aircraft: List<LiveAircraft>, trails: Map<String, List<TrailPoint>>, now: Long): List<RunwaySighting> {
        if (ends.isEmpty()) return emptyList()
        val out = LinkedHashMap<Pair<String, String>, RunwaySighting>()
        for (a in aircraft) {
            val points = buildList {
                trails[a.hex]?.zipWithNext()?.forEach { (p, q) ->
                    val dist = Geo.distanceM(p.lat, p.lon, q.lat, q.lon)
                    val track = if (dist > 150) Geo.bearingDeg(LatLon(p.lat, p.lon), LatLon(q.lat, q.lon)) else null
                    add(Sample(q.lat, q.lon, q.altitudeFt, q.verticalRateFpm, track, q.onGround, null, q.timeMs))
                }
                add(Sample(a.lat, a.lon, a.altitudeFt, a.verticalRateFpm, a.trackDeg?.toDouble(), a.onGround, a.groundSpeedKt, now))
            }
            for (s in points) {
                for (e in ends) {
                    classify(e, s, elevationFt)?.let { kind -> out.getOrPut(e.ident to a.hex) { RunwaySighting(e.ident, kind, a.hex, s.timeMs) } }
                }
            }
        }
        return out.values.toList()
    }

    private data class Sample(val lat: Double, val lon: Double, val alt: Int?, val vr: Int?, val track: Double?, val onGround: Boolean, val gs: Float?, val timeMs: Long)

    private fun classify(e: RunwayEnd, s: Sample, elevationFt: Int): RunwaySighting.Kind? {
        val p = LatLon(s.lat, s.lon)
        val track = s.track ?: return null
        if (abs(Geo.headingDelta(track, e.headingDeg)) > 20) return null
        // On the ground a landing roll-out looks just like a take-off roll, so only airborne planes count.
        if (s.onGround) return null
        val agl = (s.alt ?: return null) - elevationFt
        // On final: lined up before the threshold, near the 3° glide path (planes often join it from above).
        val (along, cross) = axis(p, e.threshold, e.headingDeg)
        if (along < 300 && along > -18_000 && abs(cross) < 700 + -along * 0.04 && agl < 4_000 && agl < 330 * (-along / 1_852.0) + 1_500 && (s.vr ?: 0) < 300) {
            return RunwaySighting.Kind.Arrival
        }
        // Climbing straight out past the far end.
        val (out, crossOut) = axis(p, e.farEnd, e.headingDeg)
        if (out > -500 && out < 12_000 && abs(crossOut) < 1_200 + out * 0.05 && agl < 5_000 && (s.vr ?: 0) > 300) {
            return RunwaySighting.Kind.Departure
        }
        return null
    }

    /** Position relative to [origin] along a runway heading: metres ahead (negative = behind) and to the side. */
    private fun axis(p: LatLon, origin: LatLon, headingDeg: Double): Pair<Double, Double> {
        val d = Geo.distanceM(origin, p)
        if (d < 1) return 0.0 to 0.0
        val rel = Geo.toRad(Geo.headingDelta(headingDeg, Geo.bearingDeg(origin, p)))
        return d * cos(rel) to d * sin(rel)
    }

    /** Runways seen in use, busiest first. Null when nothing was seen. */
    fun inUse(sightings: List<RunwaySighting>, now: Long, windowMs: Long = WINDOW_MS): RunwaysInUse? {
        val recent = sightings.filter { now - it.timeMs <= windowMs }
        if (recent.isEmpty()) return null
        fun busiest(kind: RunwaySighting.Kind) = recent.filter { it.kind == kind }.groupingBy { it.runway }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key }
        return RunwaysInUse(busiest(RunwaySighting.Kind.Arrival), busiest(RunwaySighting.Kind.Departure), observed = true, planes = recent.map { it.hex }.distinct().size)
    }

    /**
     * A guess when nothing's been seen: what this airport has done before in a similar wind, or else
     * its main runway direction (the one with the most parallel runways) unless the wind rules it
     * out (more than 5 kt behind or 25 kt across), or else whatever is most into the wind.
     */
    fun guess(ends: List<RunwayEnd>, windFromDeg: Double?, windKt: Int?, habits: List<RunwayHabit>): RunwaysInUse? {
        if (ends.isEmpty()) return null
        val similar = habits.filter { h -> windFromDeg == null || h.windFromDeg == null || abs(Geo.headingDelta(h.windFromDeg, windFromDeg)) <= 35 }
        if (similar.sumOf { it.count } >= 3) {
            fun top(kind: RunwaySighting.Kind) = similar.filter { it.kind == kind }.groupBy { it.runway }.mapValues { (_, v) -> v.sumOf { it.count } }
                .entries.sortedByDescending { it.value }.take(2).map { it.key }
            val arr = top(RunwaySighting.Kind.Arrival)
            val dep = top(RunwaySighting.Kind.Departure)
            if (arr.isNotEmpty() || dep.isNotEmpty()) return RunwaysInUse(arr.ifEmpty { dep }, dep.ifEmpty { arr }, observed = false)
        }
        val wind = windKt ?: 0
        // Directions: ends within 20° of each other form one group (parallel runways).
        val groups = mutableListOf<MutableList<RunwayEnd>>()
        for (e in ends.sortedByDescending { it.lengthFt ?: 0 }) {
            groups.firstOrNull { g -> abs(Geo.headingDelta(g.first().headingDeg, e.headingDeg)) <= 20 }?.add(e) ?: groups.add(mutableListOf(e))
        }
        fun head(g: List<RunwayEnd>) = if (windFromDeg == null) 0.0 else wind * cos(Geo.toRad(Geo.headingDelta(g.first().headingDeg, windFromDeg)))
        fun cross(g: List<RunwayEnd>) = if (windFromDeg == null) 0.0 else abs(wind * sin(Geo.toRad(Geo.headingDelta(g.first().headingDeg, windFromDeg))))
        val usable = groups.filter { g -> wind < 5 || (head(g) >= -5 && cross(g) <= 25) }
        val pick = if (usable.isNotEmpty()) {
            // Most parallel runways first; between equals, more into the wind.
            val maxSize = usable.maxOf { it.size }
            usable.filter { it.size == maxSize }.maxByOrNull { head(it) }!!
        } else {
            groups.maxByOrNull { head(it) } ?: return null
        }
        val idents = pick.map { it.ident }
        return RunwaysInUse(idents, idents, observed = false)
    }

    const val WINDOW_MS = 20 * 60_000L
}

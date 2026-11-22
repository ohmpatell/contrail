package com.contrail.contrail.core.data.events

import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.model.AirportRef
import com.contrail.contrail.core.data.model.LiveAircraft
import com.contrail.contrail.core.data.model.TrailPoint
import kotlin.math.abs

/** What kind of thing happened. */
enum class HappeningKind(val key: String) {
    Emergency("emergency"),
    GoAround("go-around"),
    Holding("holding"),
    Diversion("diversion"),
    RareVisitor("rare"),
    HangarTookOff("hangar-off"),
    HangarLanded("hangar-on"),
    ;

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key }
    }
}

/**
 * Spots manoeuvres in a plane's recent trail (the last four minutes). Pure, so it's tested with
 * made-up trails.
 */
object EventDetector {
    private data class P(val lat: Double, val lon: Double, val alt: Int?, val onGround: Boolean)

    private fun points(trail: List<TrailPoint>, a: LiveAircraft) =
        trail.map { P(it.lat, it.lon, it.altitudeFt, it.onGround) } + P(a.lat, a.lon, a.altitudeFt, a.onGround)

    /**
     * Came down low near [airport] and climbed away again without touching down: a go-around (or,
     * for light aircraft, a touch-and-go).
     */
    fun isGoAround(trail: List<TrailPoint>, a: LiveAircraft, airport: AirportRef, elevationFt: Int): Boolean {
        val pts = points(trail, a)
        if (pts.size < 4 || pts.any { it.onGround }) return false
        val lowest = pts.withIndex()
            .filter { (_, p) -> p.alt != null && Geo.distanceM(p.lat, p.lon, airport.lat, airport.lon) < 10_000 }
            .minByOrNull { it.value.alt!! } ?: return false
        val low = lowest.value.alt!!
        if (low - elevationFt > 1_500) return false
        val before = pts.subList(0, lowest.index).mapNotNull { it.alt }.maxOrNull() ?: return false
        val after = pts.subList(lowest.index + 1, pts.size).mapNotNull { it.alt }.maxOrNull() ?: return false
        return before - low >= 300 && after - low >= 600 && (a.verticalRateFpm ?: 0) > 300
    }

    /** Turned through most of a circle in the last few minutes while level and up high: holding. */
    fun isHolding(trail: List<TrailPoint>, a: LiveAircraft): Boolean {
        if (a.onGround || (a.altitudeFt ?: 0) < 4_000 || (a.groundSpeedKt ?: 0f) < 150f) return false
        val pts = points(trail, a).filter { !it.onGround }
        if (pts.size < 6) return false
        val alts = pts.mapNotNull { it.alt }
        if (alts.isEmpty() || alts.max() - alts.min() > 1_500) return false
        var turned = 0.0
        var lastHeading: Double? = null
        var last = pts.first()
        for (p in pts.drop(1)) {
            if (Geo.distanceM(last.lat, last.lon, p.lat, p.lon) < 400) continue
            val h = Geo.bearingDeg(LatLon(last.lat, last.lon), LatLon(p.lat, p.lon))
            lastHeading?.let { turned += Geo.headingDelta(it, h) }
            lastHeading = h
            last = p
        }
        return abs(turned) >= 300
    }
}

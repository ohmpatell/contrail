package com.contrail.contrail.core.data.route

import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.model.AirportRef
import com.contrail.contrail.core.data.model.FlightRoute
import com.contrail.contrail.core.data.model.LiveAircraft
import kotlin.math.abs
import kotlin.math.max

/**
 * What a callsign route looks like next to what the plane is actually doing.
 *
 * Callsign databases map a flight number to one route, but airlines reuse numbers, change
 * them by season and run through-flights under one number. So a route is only shown as the
 * plane's route when a leg of it fits the plane's position, track and phase of flight.
 */
sealed interface RouteVerdict {
    /** A leg of a route on file fits. [stops] lists every airport when the flight number makes stops; [leg] is the one being flown (0-based). */
    data class OnRoute(val from: AirportRef, val to: AirportRef, val stops: List<AirportRef>, val leg: Int, val route: FlightRoute) : RouteVerdict {
        val multiStop: Boolean get() = stops.size > 2
    }

    /**
     * No route on file fits, but the plane is plainly coming down into [at]. [diverting] when its
     * route did fit but it's landing far from that route's destination: likely a diversion.
     */
    data class Landing(val at: AirportRef, val claimed: FlightRoute?, val diverting: Boolean = false) : RouteVerdict

    /** No route on file fits, but the plane is climbing away from [from]. */
    data class Departing(val from: AirportRef, val claimed: FlightRoute?) : RouteVerdict

    /** The route on file doesn't match where the plane is or where it's heading. */
    data class Mismatch(val claimed: FlightRoute) : RouteVerdict

    data object NoRoute : RouteVerdict
}

object RouteCheck {

    /**
     * Checks [route] (and its alternatives) against [a]. [nearby] are airports around the plane,
     * used to recognise an approach or a climb-out when nothing on file fits.
     */
    fun check(route: FlightRoute?, a: LiveAircraft, nearby: List<AirportRef>): RouteVerdict {
        val p = LatLon(a.lat, a.lon)
        val alt = a.altitudeFt
        val vr = a.verticalRateFpm ?: 0
        val low = !a.onGround && alt != null && alt < LOW_FT
        val approach = if (low && vr < -DESCENT_FPM) airportAhead(p, a.trackDeg?.toDouble(), nearby) else null
        val climbOut = if (low && vr > DESCENT_FPM) airportBehind(p, a.trackDeg?.toDouble(), nearby) else null

        val legs = buildList {
            route?.let { r -> (listOf(r) + r.alternatives).forEach { candidate -> addAll(legsOf(candidate)) } }
        }
        val best = legs.mapNotNull { leg -> fit(leg, p, a.trackDeg?.toDouble())?.let { leg to it } }.minByOrNull { it.second }?.first

        if (best != null) {
            // Coming down low far from the leg's end means it's landing somewhere else.
            val landingElsewhere = approach != null && alt != null && alt < 8_000 &&
                !same(approach, best.to) && Geo.distanceM(p, best.to.latLon) > 250_000
            if (!landingElsewhere) return best.verdict()
            return RouteVerdict.Landing(approach!!, route, diverting = true)
        }
        // A leg that ends where it's landing, or starts where it's climbing out of, wins even if it bends.
        approach?.let { ap -> legs.firstOrNull { same(it.to, ap) }?.let { return it.verdict() } }
        climbOut?.let { ap -> legs.firstOrNull { same(it.from, ap) }?.let { return it.verdict() } }
        if (approach != null) return RouteVerdict.Landing(approach, route)
        if (climbOut != null) return RouteVerdict.Departing(climbOut, route)
        return if (route == null) RouteVerdict.NoRoute else RouteVerdict.Mismatch(route)
    }

    private data class Leg(val route: FlightRoute, val stops: List<AirportRef>, val index: Int, val from: AirportRef, val to: AirportRef) {
        fun verdict() = RouteVerdict.OnRoute(from, to, stops, index, route)
    }

    private fun legsOf(r: FlightRoute): List<Leg> {
        val stops = r.airports
        return stops.zipWithNext().mapIndexed { i, (x, y) -> Leg(r, stops, i, x, y) }
    }

    /**
     * Lower is better; null when the plane can't be on this leg. Long hauls get more room either
     * side of the great circle (oceanic tracks and jet streams bend them).
     */
    private fun fit(leg: Leg, p: LatLon, trackDeg: Double?): Double? {
        val f = leg.from.latLon
        val t = leg.to.latLon
        val d = Geo.distanceM(f, t)
        if (d < 1_000) return null
        val xt = abs(Geo.crossTrackM(p, f, t))
        if (xt > max(150_000.0, 0.22 * d)) return null
        val at = Geo.alongTrackM(p, f, t)
        if (at < -120_000 || at > d + 120_000) return null
        var score = xt / d
        val toEnd = Geo.distanceM(p, t)
        val fromStart = Geo.distanceM(p, f)
        // Near either end it may be turning anywhere; in between it should be heading for the end.
        if (trackDeg != null && toEnd > 120_000 && fromStart > 120_000) {
            val off = abs(Geo.headingDelta(trackDeg, Geo.bearingDeg(p, t)))
            if (off > 75) return null
            score += off / 900.0
        }
        return score
    }

    /**
     * The airport a descending plane is pointing at within 120 km: the best line-up with its track
     * wins, distance breaks near-ties (so a jet lined up on Pearson isn't credited to the airport
     * slightly closer but off to one side).
     */
    private fun airportAhead(p: LatLon, trackDeg: Double?, nearby: List<AirportRef>): AirportRef? {
        trackDeg ?: return null
        return nearby
            .mapNotNull { ap ->
                val dist = Geo.distanceM(p, ap.latLon)
                val off = if (dist < 12_000) 0.0 else abs(Geo.headingDelta(trackDeg, Geo.bearingDeg(p, ap.latLon)))
                if (dist > 120_000 || off > 35) null else ap to off + dist / 10_000
            }
            .minByOrNull { it.second }?.first
    }

    /** The airport a climbing plane is flying straight away from within 80 km: where it took off. */
    private fun airportBehind(p: LatLon, trackDeg: Double?, nearby: List<AirportRef>): AirportRef? {
        trackDeg ?: return null
        return nearby
            .mapNotNull { ap ->
                val dist = Geo.distanceM(p, ap.latLon)
                val off = if (dist < 10_000) 0.0 else abs(Geo.headingDelta(trackDeg, Geo.bearingDeg(ap.latLon, p)))
                if (dist > 80_000 || off > 45) null else ap to off + dist / 10_000
            }
            .minByOrNull { it.second }?.first
    }

    private fun same(a: AirportRef, b: AirportRef): Boolean =
        (a.icao != null && a.icao == b.icao) || (a.iata != null && a.iata == b.iata) || Geo.distanceM(a.latLon, b.latLon) < 3_000

    private val AirportRef.latLon: LatLon get() = LatLon(lat, lon)

    private const val LOW_FT = 12_000
    private const val DESCENT_FPM = 400
}

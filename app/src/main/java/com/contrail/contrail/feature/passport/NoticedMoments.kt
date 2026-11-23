package com.contrail.contrail.feature.passport

import com.contrail.contrail.core.data.moments.Moment
import com.contrail.contrail.core.data.moments.MomentKind
import com.contrail.contrail.core.data.reference.AircraftTypes
import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.core.data.reference.Silhouette
import com.contrail.contrail.core.data.ticket.AirportInfo
import com.contrail.contrail.core.data.trip.FlightSegment
import java.util.Locale

/**
 * Moments hiding in your logbook: the first flight, the first time on each aircraft type and
 * with each airline, round-number milestones, the equator and the date line, and flights on a
 * plane you've pinned to My Hangar. Pure, so it's tested.
 */
object NoticedMoments {
    private val MILESTONES = listOf(1, 10, 25, 50, 100, 150, 200, 250, 300, 400, 500, 750, 1000)
    private val DISTANCE_KM = listOf(100_000L, 250_000L, 500_000L, 1_000_000L)

    fun from(flown: List<FlightSegment>, places: Map<String, AirportInfo>, hangar: Set<String>): List<Moment> {
        val segs = flown.sortedBy { it.departure }
        if (segs.isEmpty()) return emptyList()
        val out = mutableListOf<Moment>()
        fun add(kind: MomentKind, title: String, note: String?, s: FlightSegment, key: String) {
            out += Moment("noticed:$key", kind, title, note, s.departureLocal.toLocalDate(), s.id, s.tail, null, mine = false)
        }
        fun route(s: FlightSegment) = "${places[s.origin]?.city ?: s.origin} → ${places[s.destination]?.city ?: s.destination}"

        val first = segs.first()
        add(MomentKind.FirstFlight, "Your first flight in Contrail", "${first.designator} · ${route(first)}", first, "first")

        val seenTypes = HashSet<String>()
        val seenAirlines = HashSet<String>()
        var km = 0.0
        var nextDistance = 0
        segs.forEachIndexed { i, s ->
            val n = i + 1
            if (n in MILESTONES && n > 1) add(MomentKind.Milestone, "Flight number $n", "${s.designator} · ${route(s)}", s, "flight-$n")
            val a = places[s.origin]
            val b = places[s.destination]
            if (a != null && b != null) km += com.contrail.contrail.core.common.Geo.distanceM(a.lat, a.lon, b.lat, b.lon) / 1000
            while (nextDistance < DISTANCE_KM.size && km >= DISTANCE_KM[nextDistance]) {
                val d = DISTANCE_KM[nextDistance]
                add(MomentKind.Milestone, String.format(Locale.US, "%,d km flown", d), "Reached on ${s.designator} · ${route(s)}", s, "km-$d")
                nextDistance++
            }
            s.aircraftType?.uppercase()?.let { t ->
                if (seenTypes.add(t) && segs.size > 1 && i > 0) {
                    val name = AircraftTypes.nameFor(t) ?: t
                    val big = AircraftTypes.silhouetteFor(t, null).let { it == Silhouette.Jumbo || it == Silhouette.Quad }
                    add(MomentKind.FirstType, "First time on the $name", if (big) "Four engines, two aisles. ${s.designator} · ${route(s)}" else "${s.designator} · ${route(s)}", s, "type-$t")
                }
            }
            s.airlineIata.takeIf { it.isNotBlank() }?.let { al ->
                if (seenAirlines.add(al) && i > 0) {
                    val name = Airlines.byIata(al)?.name ?: s.airlineName ?: al
                    add(MomentKind.FirstAirline, "First flight with $name", "${s.designator} · ${route(s)}", s, "airline-$al")
                }
            }
            if (s.tail != null && s.tail.uppercase() in hangar) {
                add(MomentKind.HangarFlight, "You flew on ${s.tail.uppercase()}", "${s.designator} · ${route(s)}. It's in your Hangar.", s, "hangar-${s.id}")
            }
        }
        segs.firstOrNull { PassportInsights.crossesEquator(it, places) }?.let { add(MomentKind.Crossing, "Crossed the equator", "${it.designator} · ${route(it)}", it, "equator") }
        segs.firstOrNull { PassportInsights.crossesDateLine(it, places) }?.let { add(MomentKind.Crossing, "Crossed the International Date Line", "${it.designator} · ${route(it)}", it, "dateline") }
        return out
    }
}

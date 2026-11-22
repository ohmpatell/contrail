package com.contrail.contrail.core.data.spotting

import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.common.Sun
import com.contrail.contrail.core.data.airport.RunwayEnd
import com.contrail.contrail.core.data.model.AirportRef
import com.contrail.contrail.core.data.model.LiveAircraft
import com.contrail.contrail.core.data.reference.Rarity
import com.contrail.contrail.core.data.reference.Silhouette
import com.contrail.contrail.core.data.route.RouteVerdict
import kotlin.math.abs
import kotlin.math.roundToInt

/** Why an arrival is worth looking up for. Shown as badges; the first decides its colour. */
enum class Highlight(val label: String) { Hangar("Your Hangar"), Legendary("Legendary"), Rare("Rare"), Jumbo("Four engines"), Widebody("Widebody"), Military("Military"), Emergency("Emergency") }

/** A plane heading in to land, with roughly when. */
data class SpotterArrival(
    val aircraft: LiveAircraft,
    val from: AirportRef?,
    val etaMinutes: Int,
    val highlights: List<Highlight>,
) {
    val worthSeeing: Boolean get() = highlights.isNotEmpty()
}

object SpotterBoard {
    /**
     * Arrivals at [airport] from what's flying around it: a plane whose checked route ends here, or
     * that's plainly coming down into it. ETA is distance over ground speed plus a few minutes for
     * the approach, so it's always labelled estimated.
     */
    fun arrivals(
        airport: AirportRef,
        aircraft: List<LiveAircraft>,
        verdicts: Map<String, RouteVerdict?>,
        hangar: Set<String>,
    ): List<SpotterArrival> = aircraft.mapNotNull { a ->
        if (a.onGround) return@mapNotNull null
        val v = verdicts[a.hex]
        val here = when (v) {
            is RouteVerdict.OnRoute -> same(v.to, airport)
            is RouteVerdict.Landing -> same(v.at, airport)
            else -> false
        }
        if (!here) return@mapNotNull null
        val from = (v as? RouteVerdict.OnRoute)?.from
        val dist = Geo.distanceM(a.lat, a.lon, airport.lat, airport.lon)
        val gs = (a.groundSpeedKt ?: 0f).coerceAtLeast(140f)
        val minutes = (dist / (gs * Geo.MPS_PER_KNOT) / 60 + APPROACH_MIN * (dist / 150_000.0).coerceAtMost(1.0)).roundToInt()
        SpotterArrival(a, from, minutes.coerceAtLeast(1), highlightsOf(a, hangar))
    }.sortedBy { it.etaMinutes }

    fun highlightsOf(a: LiveAircraft, hangar: Set<String>): List<Highlight> = buildList {
        if (a.hex in hangar || (a.registration?.uppercase() in hangar)) add(Highlight.Hangar)
        if (a.isEmergency) add(Highlight.Emergency)
        when (a.rarity) {
            Rarity.Legendary -> add(Highlight.Legendary)
            Rarity.Rare -> add(Highlight.Rare)
            else -> Unit
        }
        when (a.silhouette) {
            Silhouette.Jumbo, Silhouette.Quad -> add(Highlight.Jumbo)
            Silhouette.Widebody, Silhouette.WideTri -> add(Highlight.Widebody)
            else -> Unit
        }
        if (a.military) add(Highlight.Military)
    }

    private fun same(a: AirportRef, b: AirportRef) =
        (a.icao != null && a.icao == b.icao) || (a.iata != null && a.iata == b.iata) || Geo.distanceM(a.lat, a.lon, b.lat, b.lon) < 3_000

    /** Minutes for the approach on top of the straight-line flight, for planes still far out. */
    private const val APPROACH_MIN = 6.0
}

/** Where the light is for photographing arrivals on one runway. */
data class PhotoLight(
    val runway: String,
    /** Which side of the approach to stand on, as a compass word ("south"), with the sun behind you. */
    val standSide: String?,
    /** Which side of the plane is lit: "left" or "right" as it flies. */
    val litSide: String?,
    val sunElevationDeg: Double,
    val sunAzimuthDeg: Double,
    val golden: Boolean,
    val night: Boolean,
    /** When golden light ends (sunset) or begins, if it's within the next few hours. */
    val sunsetMs: Long?,
)

object Light {
    private val COMPASS = listOf("north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west")

    fun compassWord(deg: Double): String = COMPASS[(((deg % 360 + 360) % 360 + 22.5) / 45).toInt() % 8]

    /** "left" or "right": the side of a plane flying [headingDeg] that faces a sun at [sunAzimuthDeg]; null when the sun is along its path. */
    fun litSide(headingDeg: Double, sunAzimuthDeg: Double): String? {
        val rel = Geo.headingDelta(headingDeg, sunAzimuthDeg)
        return when {
            abs(rel) < 20 || abs(rel) > 160 -> null
            rel > 0 -> "right"
            else -> "left"
        }
    }

    /** The compass side of the approach to stand on to see the [lit] side. */
    fun standSide(headingDeg: Double, lit: String): String = compassWord(headingDeg + if (lit == "right") 90 else -90)

    /**
     * For planes landing on [end]: the sun's side of the approach is lit, so stand on that side with
     * the sun at your back. A sun straight ahead of or behind the approach lights both sides about the
     * same; then there's no better side.
     */
    fun forRunway(end: RunwayEnd, at: LatLon, nowMs: Long): PhotoLight {
        val el = Sun.elevationDeg(nowMs, at.lat, at.lon)
        val az = Sun.azimuthDeg(nowMs, at.lat, at.lon)
        val side = if (el < -0.833) null else litSide(end.headingDeg, az)
        val stand = side?.let { standSide(end.headingDeg, it) }
        return PhotoLight(
            runway = end.ident,
            standSide = stand,
            litSide = side,
            sunElevationDeg = el,
            sunAzimuthDeg = az,
            golden = el in -0.833..10.0,
            night = el < -6,
            sunsetMs = Sun.nextCrossing(nowMs, at.lat, at.lon, setting = true, withinMs = 6 * 3_600_000L),
        )
    }
}

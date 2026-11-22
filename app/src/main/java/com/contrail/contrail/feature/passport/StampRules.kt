package com.contrail.contrail.feature.passport

import com.contrail.contrail.core.data.ticket.AirportInfo
import com.contrail.contrail.core.data.trip.ConnectionKind
import com.contrail.contrail.core.data.trip.FlightSegment
import com.contrail.contrail.core.data.trip.Trip

/** The family of stamp a country uses: the look is inspired by real stamps, never a copy of one. */
enum class StampStyle {
    /** Schengen: a rectangle with a country code and a little plane; square corners in, rounded corners out. */
    Schengen,
    /** "ADMITTED" / "DEPARTED" rectangles with a port and date band (US, UK, Ireland, Australia, New Zealand). */
    Admitted,
    /** A round port stamp with the city around the rim (the Americas, Africa, most of the rest). */
    Round,
    /** A rectangle with an inked ARRIVAL or DEPARTURE band (East and South-East Asia, India). */
    Band,
    /** A hexagon (the Gulf, the Levant, Turkey, Egypt). */
    Hexagon,
}

/**
 * The stamps a passport would really collect, as far as a flight log can tell:
 * - crossing a border gives an exit stamp where you left and an entry stamp where you landed;
 * - flying between Schengen countries crosses no border, so there's no stamp;
 * - changing planes airside on a layover gives a small transit mark instead of in and out;
 * - domestic airports get a souvenir cachet the first time you land there (and your first airport too).
 */
object StampRules {
    private val SCHENGEN = setOf(
        "AT", "BE", "BG", "CH", "CZ", "DE", "DK", "EE", "ES", "FI", "FR", "GR", "HR", "HU", "IS", "IT",
        "LI", "LT", "LU", "LV", "MT", "NL", "NO", "PL", "PT", "RO", "SE", "SI", "SK",
    )
    private val ADMITTED = setOf("US", "GB", "IE", "AU", "NZ")
    private val BAND = setOf("JP", "KR", "CN", "HK", "MO", "TW", "SG", "TH", "MY", "VN", "PH", "ID", "IN", "LK", "NP", "BD", "KH")
    private val HEXAGON = setOf("AE", "QA", "SA", "BH", "OM", "KW", "JO", "EG", "TR", "IL", "LB", "MA")

    fun styleFor(countryIso: String): StampStyle = when (countryIso.uppercase()) {
        in SCHENGEN -> StampStyle.Schengen
        in ADMITTED -> StampStyle.Admitted
        in BAND -> StampStyle.Band
        in HEXAGON -> StampStyle.Hexagon
        else -> StampStyle.Round
    }

    /** One border: both in Schengen counts as the same country. */
    fun sameBorder(a: String, b: String): Boolean = a.equals(b, ignoreCase = true) || (a.uppercase() in SCHENGEN && b.uppercase() in SCHENGEN)

    fun stamps(trips: List<Trip>, flown: Set<Long>, places: Map<String, AirportInfo>): List<Stamp> {
        val segments = trips.flatMap { it.segments }.filter { it.id in flown }.sortedBy { it.departure }
        if (segments.isEmpty()) return emptyList()
        // Arrivals that continue on a layover from the same airport stay airside.
        val airsideAfter = trips.flatMap { t -> t.connections }
            .filter { it.kind == ConnectionKind.Layover && !it.changesAirport && it.inbound.id in flown && it.outbound.id in flown }
            .associate { it.inbound.id to it.outbound.id }
        val airsideBefore = airsideAfter.entries.associate { (inb, out) -> out to inb }
        val visits = segments.groupingBy { it.destination }.eachCount()
        val cacheted = HashSet<String>()
        val out = mutableListOf<Stamp>()
        fun country(code: String) = places[code]?.countryIso.orEmpty()
        fun stamp(code: String, s: FlightSegment, kind: StampKind, arriving: Boolean) {
            val p = places[code]
            val date = if (arriving) s.arrivalLocal.toLocalDate() else s.departureLocal.toLocalDate()
            out += Stamp(
                airport = code,
                city = p?.city ?: code,
                countryIso = p?.countryIso.orEmpty(),
                date = date,
                visits = visits[code] ?: 0,
                kind = kind,
                seq = officer(code, date.toEpochDay(), kind),
                flight = s.designator.ifBlank { null },
            )
        }

        // The airport you first left from, as a cachet: it's where the book starts.
        segments.first().let { s ->
            if (sameBorder(country(s.origin), country(s.destination))) {
                stamp(s.origin, s, StampKind.Visit, arriving = false)
                cacheted += s.origin
            }
        }
        for (s in segments) {
            val from = country(s.origin)
            val to = country(s.destination)
            val crossing = from.isNotEmpty() && to.isNotEmpty() && !sameBorder(from, to)
            val continuesAirside = s.id in airsideAfter
            if (crossing) {
                // Leaving: unless you flew in from abroad and stayed airside (a domestic connection still exits here).
                val inbound = airsideBefore[s.id]?.let { id -> segments.firstOrNull { it.id == id } }
                val arrivedFromAbroad = inbound != null && !sameBorder(country(inbound.origin), country(inbound.destination))
                if (!arrivedFromAbroad) stamp(s.origin, s, StampKind.Exit, arriving = false)
                if (continuesAirside) {
                    stamp(s.destination, s, StampKind.Transit, arriving = true)
                } else {
                    stamp(s.destination, s, StampKind.Entry, arriving = true)
                }
            } else if (continuesAirside) {
                // A domestic leg into a connection: nothing to stamp.
            } else if (s.destination !in cacheted) {
                stamp(s.destination, s, StampKind.Visit, arriving = true)
                cacheted += s.destination
            }
        }
        return out
    }

    /** A three-digit officer number that never changes for the same stamp. */
    private fun officer(code: String, day: Long, kind: StampKind): Int =
        ((code.hashCode() * 31 + day.toInt()) * 31 + kind.ordinal).let { (it and 0x7FFFFFFF) % 900 + 100 }
}

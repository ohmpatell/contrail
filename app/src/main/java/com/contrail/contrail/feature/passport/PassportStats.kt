package com.contrail.contrail.feature.passport

import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.data.reference.AircraftTypes
import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.core.data.reference.Rarity
import com.contrail.contrail.core.data.repository.LoungeVisit
import com.contrail.contrail.core.data.ticket.AirportInfo
import com.contrail.contrail.core.data.trip.ConnectionKind
import com.contrail.contrail.core.data.trip.FlightSegment
import com.contrail.contrail.core.data.trip.Trip
import java.time.Duration
import java.time.Instant
import java.time.Month

/** One aircraft type in the collection: flown (with the airline you first flew it with) or not yet. */
data class CollectedType(val code: String, val name: String, val rarity: Rarity, val flights: Int, val firstAirlineIcao: String?)

data class LoungeCard(val key: String, val name: String, val airport: String, val visits: Int, val minutes: Long, val bestRating: Int, val favourite: Boolean)

data class LoungeStats(
    val visits: Int = 0,
    val lounges: Int = 0,
    val minutes: Long = 0,
    val averageRating: Float? = null,
    val favourites: List<LoungeCard> = emptyList(),
    val collection: List<LoungeCard> = emptyList(),
)

data class PassportStats(
    val flights: Int = 0,
    val distanceKm: Long = 0,
    val minutesAloft: Long = 0,
    /** Origins, final destinations and stopover cities. */
    val visited: List<String> = emptyList(),
    /** Airports you only changed planes at (layovers). */
    val transited: List<String> = emptyList(),
    val countries: Set<String> = emptySet(),
    val airlines: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val tails: Set<String> = emptySet(),
    val longest: FlightSegment? = null,
    val rarest: FlightSegment? = null,
    /** "YYZ–HKG" either way, and how often. */
    val topRoute: Pair<String, Int>? = null,
    val topTail: Pair<String, Int>? = null,
    val topType: Pair<String, Int>? = null,
    val busiestMonth: Pair<Month, Int>? = null,
    val legs: List<Pair<String, String>> = emptyList(),
    val collection: List<CollectedType> = emptyList(),
    val lounges: LoungeStats = LoungeStats(),
    val years: List<Int> = emptyList(),
) {
    /** Times around the equator. */
    val laps: Float get() = distanceKm / 40_075f
}

object PassportCalculator {
    /** Passenger airliners worth collecting, roughly by size. Anything else you fly is added on. */
    val COLLECTION = listOf(
        "A388", "B748", "B744", "A346", "A343", "B77W", "B77L", "B772", "B773", "B779",
        "A35K", "A359", "B78X", "B789", "B788", "A339", "A333", "A332", "B764", "B763",
        "B753", "B752", "A21N", "A321", "A20N", "A320", "A319", "BCS3", "BCS1", "B39M",
        "B38M", "B739", "B738", "B737", "E295", "E195", "E190", "E75L", "CRJ9", "CRJ7", "DH8D", "AT76",
    )

    /**
     * Your lifetime (or one [year]'s) flying. Only flights that have landed count. Lounge
     * stats include every logged visit (or that year's).
     */
    fun compute(
        trips: List<Trip>,
        places: Map<String, AirportInfo>,
        visits: List<LoungeVisit>,
        favouriteLounges: Set<String>,
        now: Instant,
        year: Int? = null,
    ): PassportStats {
        fun inYear(s: FlightSegment) = year == null || s.departureLocal.year == year
        val flown = trips.flatMap { t -> t.segments.filter { it.arrival.isBefore(now) && inYear(it) }.map { t to it } }
        val segments = flown.map { it.second }
        if (segments.isEmpty()) return PassportStats(lounges = loungeStats(visits.filter { year == null || it.date.year == year }, favouriteLounges), years = years(trips, now))

        val visited = LinkedHashSet<String>()
        val transited = LinkedHashSet<String>()
        for (t in trips) {
            val done = t.segments.filter { it.arrival.isBefore(now) && inYear(it) }
            if (done.isEmpty()) continue
            if (done.first() == t.first) visited += t.first.origin
            if (done.last() == t.last) visited += t.last.destination
            for (c in t.connections) {
                if (c.inbound !in done) continue
                if (c.kind == ConnectionKind.Stopover) visited += c.airport else transited += c.airport
            }
            // A one-way part of a trip still in progress: where you are now counts as visited.
            if (done.last() != t.last) visited += done.last().destination
        }
        transited.removeAll(visited)

        val distance = segments.sumOf { s ->
            val a = places[s.origin]
            val b = places[s.destination]
            if (a != null && b != null) Geo.distanceM(a.lat, a.lon, b.lat, b.lon) / 1000 else 0.0
        }
        val routeKey = { s: FlightSegment -> listOf(s.origin, s.destination).sorted().joinToString("–") }
        val types = segments.mapNotNull { it.aircraftType?.uppercase() }
        val collection = (COLLECTION + types.distinct().filter { it !in COLLECTION }).map { code ->
            val on = segments.filter { it.aircraftType.equals(code, ignoreCase = true) }
            CollectedType(
                code,
                AircraftTypes.nameFor(code) ?: code,
                AircraftTypes.byIcao(code)?.rarity ?: Rarity.Common,
                on.size,
                on.minByOrNull { it.departure }?.let { Airlines.byIata(it.airlineIata)?.icao },
            )
        }
        return PassportStats(
            flights = segments.size,
            distanceKm = distance.toLong(),
            minutesAloft = segments.sumOf { it.duration.toMinutes() },
            visited = visited.toList(),
            transited = transited.toList(),
            countries = visited.mapNotNull { places[it]?.countryIso }.toSet(),
            airlines = segments.map { it.airlineIata }.toSet(),
            types = types.toSet(),
            tails = segments.mapNotNull { it.tail }.toSet(),
            longest = segments.maxByOrNull { it.duration },
            rarest = segments.filter { it.aircraftType != null }.minByOrNull { AircraftTypes.rarityFor(it.aircraftType, Airlines.byIata(it.airlineIata)).ordinal },
            topRoute = segments.groupingBy(routeKey).eachCount().maxByOrNull { it.value }?.toPair(),
            topTail = segments.mapNotNull { it.tail }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.toPair(),
            topType = types.groupingBy { it }.eachCount().maxByOrNull { it.value }?.toPair(),
            busiestMonth = segments.groupingBy { it.departureLocal.month }.eachCount().maxByOrNull { it.value }?.toPair(),
            legs = segments.map { it.origin to it.destination }.distinct(),
            collection = collection,
            lounges = loungeStats(visits.filter { year == null || it.date.year == year }, favouriteLounges),
            years = years(trips, now),
        )
    }

    private fun years(trips: List<Trip>, now: Instant): List<Int> =
        trips.flatMap { t -> t.segments.filter { it.arrival.isBefore(now) }.map { it.departureLocal.year } }.distinct().sortedDescending()

    fun loungeStats(visits: List<LoungeVisit>, favourites: Set<String>): LoungeStats {
        if (visits.isEmpty()) return LoungeStats()
        val cards = visits.groupBy { it.loungeKey }.map { (key, vs) ->
            LoungeCard(
                key, vs.first().loungeName, vs.first().airport, vs.size,
                vs.sumOf { it.duration?.toMinutes() ?: 0 }, vs.maxOf { it.rating }, key in favourites,
            )
        }.sortedWith(compareByDescending<LoungeCard> { it.favourite }.thenByDescending { it.bestRating }.thenByDescending { it.visits })
        return LoungeStats(
            visits = visits.size,
            lounges = cards.size,
            minutes = visits.sumOf { it.duration?.toMinutes() ?: 0 },
            averageRating = visits.map { it.rating }.average().toFloat(),
            favourites = cards.filter { it.favourite || it.bestRating == 5 },
            collection = cards,
        )
    }

    fun hours(minutes: Long): String = Duration.ofMinutes(minutes).let { d -> if (d.toHours() > 0) "${d.toHours()}h ${d.toMinutesPart()}m" else "${d.toMinutes()}m" }
}

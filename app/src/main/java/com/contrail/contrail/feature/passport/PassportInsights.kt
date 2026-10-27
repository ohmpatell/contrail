package com.contrail.contrail.feature.passport

import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.reference.AircraftTypes
import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.core.data.repository.LoungeVisit
import com.contrail.contrail.core.data.ticket.AirportInfo
import com.contrail.contrail.core.data.trip.Cabin
import com.contrail.contrail.core.data.trip.ConnectionKind
import com.contrail.contrail.core.data.trip.FlightSegment
import com.contrail.contrail.core.data.trip.SeatType
import com.contrail.contrail.core.data.trip.Trip
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

/** One passport stamp: the first time you landed somewhere. */
data class Stamp(
    val airport: String,
    val city: String,
    val countryIso: String,
    val date: LocalDate,
    /** Arrivals here, all time. */
    val visits: Int,
    /** You only changed planes here. */
    val transit: Boolean,
)

enum class Tier(val label: String) { Bronze("Bronze"), Silver("Silver"), Gold("Gold"), Platinum("Platinum") }

/**
 * A badge for something you did in the air. [progress] runs 0..1 toward the next tier;
 * [tier] is the best tier reached, or null while it's still locked.
 */
data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    /** A short glyph for the medal face: a number, a code or a symbol. */
    val glyph: String,
    val tier: Tier?,
    val progress: Float,
    val progressLabel: String,
    /** The flight that earned it, for one-off badges. */
    val earnedOn: String? = null,
) {
    val unlocked: Boolean get() = tier != null
}

data class Ranked(val key: String, val label: String, val count: Int, val sub: String? = null)

data class Extremes(val north: String?, val south: String?, val east: String?, val west: String?)

data class Insights(
    val perYear: List<Pair<Int, Int>> = emptyList(),
    val kmPerYear: Map<Int, Long> = emptyMap(),
    /** Flights in each month, by year: years × 12. */
    val calendar: Map<Int, IntArray> = emptyMap(),
    val topAirlines: List<Ranked> = emptyList(),
    val topAirports: List<Ranked> = emptyList(),
    val topRoutes: List<Ranked> = emptyList(),
    val topTypes: List<Ranked> = emptyList(),
    val cabins: Map<Cabin, Int> = emptyMap(),
    val seats: Map<SeatType, Int> = emptyMap(),
    val redEyes: Int = 0,
    val domestic: Int = 0,
    val international: Int = 0,
    val shortest: Pair<FlightSegment, Long>? = null,
    val longestByDistance: Pair<FlightSegment, Long>? = null,
    val extremes: Extremes = Extremes(null, null, null, null),
    val continents: Set<String> = emptySet(),
    /** Countries with how many times you landed there, most first. */
    val countries: List<Pair<String, Int>> = emptyList(),
)

object PassportInsights {
    const val EARTH_KM = 40_075L
    const val MOON_KM = 384_400L

    fun distanceKm(s: FlightSegment, places: Map<String, AirportInfo>): Long {
        val a = places[s.origin] ?: return 0
        val b = places[s.destination] ?: return 0
        return (Geo.distanceM(a.lat, a.lon, b.lat, b.lon) / 1000).toLong()
    }

    /** A red-eye leaves between 22:00 and 04:59 local and flies at least three hours. */
    fun isRedEye(s: FlightSegment): Boolean {
        val h = s.departureLocal.hour
        return !s.timesEstimated && (h >= 22 || h < 5) && s.duration.toMinutes() >= 180
    }

    fun insights(segments: List<FlightSegment>, places: Map<String, AirportInfo>): Insights {
        if (segments.isEmpty()) return Insights()
        val km = segments.associateWith { distanceKm(it, places) }
        val airlineCounts = segments.groupingBy { it.airlineIata.ifEmpty { it.airlineName ?: "?" } }.eachCount()
        val airportCounts = segments.flatMap { listOf(it.origin, it.destination) }.groupingBy { it }.eachCount()
        val calendar = HashMap<Int, IntArray>()
        segments.forEach { s -> calendar.getOrPut(s.departureLocal.year) { IntArray(12) }[s.departureLocal.monthValue - 1]++ }
        val visitedPlaces = airportCounts.keys.mapNotNull { places[it] }
        fun nameOf(code: String?) = code?.let { c -> places[c]?.city?.let { "$it ($c)" } ?: c }
        val arrivalsByCountry = segments.mapNotNull { places[it.destination]?.countryIso }.groupingBy { it }.eachCount()
        val originCountries = segments.mapNotNull { places[it.origin]?.countryIso }
        return Insights(
            perYear = segments.groupingBy { it.departureLocal.year }.eachCount().toSortedMap().toList(),
            kmPerYear = segments.groupBy { it.departureLocal.year }.mapValues { (_, v) -> v.sumOf { km.getValue(it) } },
            calendar = calendar.toSortedMap(),
            topAirlines = airlineCounts.entries.sortedByDescending { it.value }.take(6).map { (code, n) ->
                val sample = segments.first { it.airlineIata.ifEmpty { it.airlineName ?: "?" } == code }
                Ranked(code, Airlines.byIata(sample.airlineIata)?.name ?: sample.airlineName ?: code, n)
            },
            topAirports = airportCounts.entries.sortedByDescending { it.value }.take(6).map { (c, n) -> Ranked(c, places[c]?.city ?: c, n) },
            topRoutes = segments.groupingBy { listOf(it.origin, it.destination).sorted().joinToString("–") }.eachCount()
                .entries.sortedByDescending { it.value }.take(5).map { (r, n) ->
                    val (a, b) = r.split("–")
                    Ranked(r, "${places[a]?.city ?: a} ⇄ ${places[b]?.city ?: b}", n, sub = "$a ⇄ $b")
                },
            topTypes = segments.mapNotNull { it.aircraftType?.uppercase() }.groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }.take(6).map { (t, n) -> Ranked(t, AircraftTypes.nameFor(t) ?: t, n) },
            cabins = segments.mapNotNull { it.cabin }.groupingBy { it }.eachCount(),
            seats = segments.mapNotNull { it.seatType }.groupingBy { it }.eachCount(),
            redEyes = segments.count(::isRedEye),
            domestic = segments.count { places[it.origin]?.countryIso != null && places[it.origin]?.countryIso == places[it.destination]?.countryIso },
            international = segments.count { places[it.origin]?.countryIso != null && places[it.destination]?.countryIso != null && places[it.origin]?.countryIso != places[it.destination]?.countryIso },
            shortest = segments.filter { km.getValue(it) > 0 }.minByOrNull { km.getValue(it) }?.let { it to km.getValue(it) },
            longestByDistance = segments.maxByOrNull { km.getValue(it) }?.let { it to km.getValue(it) },
            extremes = Extremes(
                north = nameOf(visitedPlaces.maxByOrNull { it.lat }?.iata),
                south = nameOf(visitedPlaces.minByOrNull { it.lat }?.iata),
                east = nameOf(visitedPlaces.maxByOrNull { it.lon }?.iata),
                west = nameOf(visitedPlaces.minByOrNull { it.lon }?.iata),
            ),
            continents = (arrivalsByCountry.keys + originCountries).mapNotNull(Continents::of).toSet(),
            countries = (originCountries.distinct().associateWith { 0 } + arrivalsByCountry).entries.sortedByDescending { it.value }.map { it.key to it.value },
        )
    }

    /**
     * One stamp per airport, in the order you first got there. The first origin counts too
     * (you left from there). Layover-only airports get a transit stamp.
     */
    fun stamps(trips: List<Trip>, flown: Set<Long>, places: Map<String, AirportInfo>): List<Stamp> {
        val first = LinkedHashMap<String, LocalDate>()
        val visits = HashMap<String, Int>()
        val visitedHere = HashSet<String>()
        val segments = trips.flatMap { it.segments }.filter { it.id in flown }.sortedBy { it.departure }
        segments.firstOrNull()?.let { first[it.origin] = it.departureLocal.toLocalDate(); visitedHere += it.origin }
        for (t in trips) {
            val done = t.segments.filter { it.id in flown }
            if (done.isEmpty()) continue
            if (done.first() == t.first) visitedHere += t.first.origin
            visitedHere += done.last().destination
            t.connections.filter { it.inbound in done && it.kind == ConnectionKind.Stopover }.forEach { visitedHere += it.airport }
        }
        for (s in segments) {
            first.putIfAbsent(s.destination, s.arrivalLocal.toLocalDate())
            visits[s.destination] = (visits[s.destination] ?: 0) + 1
        }
        return first.map { (code, date) ->
            val p = places[code]
            Stamp(code, p?.city ?: code, p?.countryIso.orEmpty(), date, visits[code] ?: 0, transit = code !in visitedHere)
        }
    }

    fun achievements(
        trips: List<Trip>,
        segments: List<FlightSegment>,
        places: Map<String, AirportInfo>,
        insights: Insights,
        distanceKm: Long,
        countries: Int,
        lounges: List<LoungeVisit>,
    ): List<Achievement> {
        val n = segments.size
        val km = segments.associateWith { distanceKm(it, places) }
        fun tiered(id: String, title: String, desc: String, glyph: String, value: Long, steps: List<Long>, unit: String = ""): Achievement {
            val reached = steps.indexOfLast { value >= it }
            val tier = if (reached < 0) null else Tier.entries[(reached + (4 - steps.size)).coerceIn(0, 3)]
            val next = steps.getOrNull(reached + 1)
            val progress = if (next == null) 1f else (value.toFloat() / next).coerceIn(0f, 1f)
            val label = if (next == null) String.format(Locale.US, "%,d%s", value, unit) else String.format(Locale.US, "%,d / %,d%s", value, next, unit)
            return Achievement(id, title, desc, glyph, tier, progress, label)
        }
        fun once(id: String, title: String, desc: String, glyph: String, earned: FlightSegment?, tier: Tier = Tier.Gold): Achievement =
            Achievement(id, title, desc, glyph, if (earned != null) tier else null, if (earned != null) 1f else 0f, if (earned != null) "Earned" else "Locked", earned?.let { "${it.designator.ifBlank { "${it.origin}–${it.destination}" }} · ${it.departureLocal.toLocalDate()}" })

        val byDuration = segments.sortedBy { it.departure }
        val window = segments.count { it.seatType == SeatType.Window }
        val tails = segments.mapNotNull { it.tail }.groupingBy { it }.eachCount()
        return listOf(
            tiered("flights", "Frequent flyer", "Flights flown", "✈", n.toLong(), listOf(1, 10, 50, 100)),
            tiered("distance", "Around the world", "Kilometres flown; once around the Earth is 40,075 km", "🌍", distanceKm, listOf(10_000, EARTH_KM, 5 * EARTH_KM, MOON_KM), " km"),
            tiered("countries", "Globetrotter", "Countries you've flown to or from", "⚑", countries.toLong(), listOf(3, 10, 25, 50)),
            tiered("airlines", "Airline collector", "Different airlines flown", "AL", segments.map { it.airlineIata.ifEmpty { it.airlineName } }.distinct().size.toLong(), listOf(3, 10, 20, 40)),
            tiered("types", "Type rated", "Different aircraft types flown", "TY", segments.mapNotNull { it.aircraftType }.distinct().size.toLong(), listOf(3, 10, 20, 30)),
            tiered("continents", "Five continents", "Continents you've flown to", "◎", insights.continents.size.toLong(), listOf(2, 3, 4, 5)),
            tiered("airports", "Hub hopper", "Different airports", "⌂", segments.flatMap { listOf(it.origin, it.destination) }.distinct().size.toLong(), listOf(5, 20, 50, 100)),
            once("ultra", "Ultra long haul", "A flight of 12 hours or more", "12h", byDuration.firstOrNull { !it.timesEstimated && it.duration.toHours() >= 12 || km.getValue(it) >= 9_000 }, Tier.Platinum),
            once("longhaul", "Long hauler", "A flight of 6 hours or more", "6h", byDuration.firstOrNull { !it.timesEstimated && it.duration.toHours() >= 6 || km.getValue(it) >= 4_500 }),
            once("redeye", "Red-eye", "Took off between 10 pm and 5 am", "☾", byDuration.firstOrNull(::isRedEye), Tier.Silver),
            once("equator", "Line crosser", "Crossed the equator", "0°", byDuration.firstOrNull { crossesEquator(it, places) }),
            once("arctic", "Polar route", "Flew north of the Arctic Circle", "66°", byDuration.firstOrNull { maxLat(it, places) >= 66.5 }, Tier.Platinum),
            once("dateline", "Date line", "Crossed the International Date Line", "180°", byDuration.firstOrNull { crossesDateLine(it, places) }, Tier.Platinum),
            once("superjumbo", "Double decker", "Flew on an A380 or a 747", "A380", byDuration.firstOrNull { it.aircraftType?.uppercase() in setOf("A388", "B744", "B748", "B742", "B743") }, Tier.Platinum),
            once("front", "Front of the plane", "Flew Business or First", "J", byDuration.firstOrNull { it.cabin == Cabin.Business || it.cabin == Cabin.First }),
            once("first", "First class", "Flew First", "F", byDuration.firstOrNull { it.cabin == Cabin.First }, Tier.Platinum),
            tiered("window", "Window seat devotee", "Flights in a window seat", "◫", window.toLong(), listOf(1, 10, 25, 50)),
            once("sametail", "Old friend", "Flew the same aircraft twice", "×2", tails.entries.firstOrNull { it.value >= 2 }?.let { e -> segments.last { it.tail == e.key } }, Tier.Silver),
            once("hop", "Short hop", "A flight under 300 km", "↯", byDuration.firstOrNull { km.getValue(it) in 1..299 }, Tier.Bronze),
            once("stopover", "Stopover", "Left the airport on the way somewhere", "⇄", trips.firstNotNullOfOrNull { t -> t.connections.firstOrNull { it.kind == ConnectionKind.Stopover && it.inbound in segments }?.inbound }, Tier.Silver),
            once("newyear", "New Year aloft", "Flew on 31 December or 1 January", "NY", byDuration.firstOrNull { (it.departureLocal.monthValue == 12 && it.departureLocal.dayOfMonth == 31) || (it.departureLocal.monthValue == 1 && it.departureLocal.dayOfMonth == 1) }, Tier.Gold),
            tiered("lounges", "Lounge lizard", "Lounge visits logged", "☕", lounges.size.toLong(), listOf(1, 5, 15, 30)),
        )
    }

    private fun points(s: FlightSegment, places: Map<String, AirportInfo>): List<LatLon>? {
        val a = places[s.origin] ?: return null
        val b = places[s.destination] ?: return null
        return Geo.greatCircle(LatLon(a.lat, a.lon), LatLon(b.lat, b.lon), 48)
    }

    fun crossesEquator(s: FlightSegment, places: Map<String, AirportInfo>): Boolean {
        val a = places[s.origin] ?: return false
        val b = places[s.destination] ?: return false
        return (a.lat > 0) != (b.lat > 0)
    }

    fun maxLat(s: FlightSegment, places: Map<String, AirportInfo>): Double = points(s, places)?.maxOf { abs(it.lat) } ?: 0.0

    fun crossesDateLine(s: FlightSegment, places: Map<String, AirportInfo>): Boolean =
        points(s, places)?.map { Geo.normalizeLon(it.lon) }?.zipWithNext()?.any { (p, q) -> abs(p - q) > 180 } ?: false
}

/** Country (ISO 3166 alpha-2) → continent, for the Five continents badge and the stamp book. */
object Continents {
    private val table: Map<String, String> = buildMap {
        fun add(continent: String, codes: String) = codes.split(' ').forEach { code -> this[code] = continent }
        add("Africa", "DZ AO BJ BW BF BI CM CV CF TD KM CG CD CI DJ EG GQ ER SZ ET GA GM GH GN GW KE LS LR LY MG MW ML MR MU YT MA MZ NA NE NG RE RW SH ST SN SC SL SO ZA SS SD TZ TG TN UG EH ZM ZW")
        add("Asia", "AF AM AZ BH BD BT BN KH CN CY GE HK IN ID IR IQ IL JP JO KZ KW KG LA LB MO MY MV MN MM NP KP OM PK PS PH QA SA SG KR LK SY TW TJ TH TL TR TM AE UZ VN YE")
        add("Europe", "AX AL AD AT BY BE BA BG HR CZ DK EE FO FI FR DE GI GR GG HU IS IE IM IT JE XK LV LI LT LU MT MD MC ME NL MK NO PL PT RO RU SM RS SK SI ES SJ SE CH UA GB VA")
        add("North America", "AI AG AW BS BB BZ BM BQ VG CA KY CR CU CW DM DO SV GL GD GP GT HT HN JM MQ MX MS NI PA PR BL KN LC MF PM VC SX TT TC US VI")
        add("South America", "AR BO BR CL CO EC FK GF GY PY PE SR UY VE")
        add("Oceania", "AS AU CK FJ PF GU KI MH FM NR NC NZ NU NF MP PW PG PN WS SB TK TO TV UM VU WF")
        add("Antarctica", "AQ")
    }

    fun of(iso: String): String? = table[iso.uppercase()]
}

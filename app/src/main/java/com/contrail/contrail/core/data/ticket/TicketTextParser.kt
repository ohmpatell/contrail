package com.contrail.contrail.core.data.ticket

import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.core.data.trip.Cabin
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs

/** Which three-letter codes are real airports (backed by the bundled OurAirports table). */
fun interface AirportCodes {
    fun isAirport(iata: String): Boolean
}

/**
 * Reads e-tickets, itineraries, confirmation emails and OCR'd boarding passes.
 *
 * The text is tokenised (flight numbers, airport codes, dates, times, terminals, seats,
 * cabins), then split into one window per flight. A window starts at its flight number or
 * at its route (the pair of airport codes nearest to it), whichever comes first, so layouts
 * that print the route before the flight number (Air Canada) and after it (Etihad, Cathay
 * Pacific) both work. Each window's tokens fill one segment.
 */
class TicketTextParser(private val airports: AirportCodes) {

    private enum class Kind { Flight, Airport, Date, Time, Terminal, Seat, Gate, Cabin, FareClass, Hotel }

    private data class Tok(val kind: Kind, val start: Int, val end: Int, val line: Int, val value: Any)

    private data class Time(val time: LocalTime, val plusDays: Int)
    private data class FlightNo(val airline: String, val number: String)
    private data class Route(val from: Tok, val to: Tok) {
        val start get() = minOf(from.start, to.start)
    }

    fun parse(raw: String, today: LocalDate, source: ImportSource): TicketDraft {
        val text = normalize(raw)
        val lines = LineIndex(text)
        val tokens = tokenize(text, lines, today).sortedBy { it.start }

        val flights = dedupeFlights(tokens.filter { it.kind == Kind.Flight })
        val airportsFound = tokens.filter { it.kind == Kind.Airport }
        val routes = pairRoutes(airportsFound)
        val assigned = alignRoutes(flights, routes)

        // Window starts, kept strictly increasing.
        val starts = IntArray(flights.size)
        for (i in flights.indices) {
            val r = assigned[i]
            var s = if (r != null) minOf(r.start, flights[i].start) else flights[i].start
            if (i > 0) s = maxOf(s, flights[i - 1].end)
            starts[i] = s
        }

        val segments = flights.mapIndexed { i, flight ->
            val from = starts[i]
            val to = if (i + 1 < flights.size) starts[i + 1] else text.length
            val window = tokens.filter { it.start in from until to }
            // A date printed before the window (a day header, or the previous leg's arrival) is the fallback.
            val before = tokens.lastOrNull { it.kind == Kind.Date && it.start < from }?.value as LocalDate?
            buildSegment(flight, assigned[i], window, text.substring(from, to), before)
        }

        return TicketDraft(
            pnr = findPnr(text),
            passengerName = findPassenger(text),
            ticketNumber = TICKET.find(text)?.let { "${it.groupValues[1]} ${it.groupValues[2]}" },
            segments = mergeDuplicates(segments),
            sources = setOf(source),
        )
    }

    // ---- Segment assembly ----

    private fun buildSegment(flightTok: Tok, route: Route?, window: List<Tok>, windowText: String, fallbackDate: LocalDate?): SegmentDraft {
        val flight = flightTok.value as FlightNo
        val times = window.filter { it.kind == Kind.Time }
        val dep = times.getOrNull(0)
        val arr = times.getOrNull(1)
        val arrLineEnd = arr?.line ?: dep?.line ?: Int.MAX_VALUE
        val leading = window.filter { it.kind == Kind.Date && it.line <= arrLineEnd }
        val depDate = (leading.firstOrNull { dep == null || it.start <= dep.end || it.line <= dep.line }?.value as LocalDate?)
            ?: fallbackDate
        val arrDateTok = if (arr != null && dep != null) {
            leading.firstOrNull { it.start > dep.start && it.line <= arr.line && it.value != depDate }
        } else {
            null
        }
        val arrTime = arr?.value as Time?
        val arrDate = (arrDateTok?.value as LocalDate?)
            ?: arrTime?.takeIf { it.plusDays > 0 }?.let { t -> depDate?.plusDays(t.plusDays.toLong()) }

        val airportsHere = window.filter { it.kind == Kind.Airport }
        val origin = route?.from?.value as String? ?: airportsHere.getOrNull(0)?.value as String?
        val destination = route?.to?.value as String?
            ?: airportsHere.firstOrNull { it.value != origin }?.value as String?

        val (depTerm, arrTerm) = terminals(window, dep, arr, route)
        val cabinTok = window.firstOrNull { it.kind == Kind.Cabin }
        val fare = window.firstOrNull { it.kind == Kind.FareClass }?.value as String?
        val cabin = cabinTok?.value as Cabin? ?: fare?.let { TicketText.cabinForClass(it[0], flight.airline) }

        return SegmentDraft(
            airlineIata = flight.airline,
            flightNumber = flight.number,
            origin = origin,
            destination = destination,
            date = depDate,
            departureTime = (dep?.value as Time?)?.time,
            arrivalTime = arrTime?.time,
            arrivalDate = arrDate,
            departureTerminal = depTerm,
            arrivalTerminal = arrTerm,
            gate = window.firstOrNull { it.kind == Kind.Gate }?.value as String?,
            cabin = cabin,
            fareClass = fare,
            seat = window.firstOrNull { it.kind == Kind.Seat }?.value as String?,
            aircraftType = AircraftNames.find(windowText),
            operatedBy = OPERATED_BY.find(windowText)?.groupValues?.get(1)?.let(::cleanOperator),
            hotelAfter = window.any { it.kind == Kind.Hotel && it.start > flightTok.start },
        )
    }

    /**
     * Departure and arrival terminals: a terminal on the departure-time line (or the origin's
     * line) is the departure's; on the arrival-time line (or the destination's) the arrival's;
     * otherwise they're taken in order.
     */
    private fun terminals(window: List<Tok>, dep: Tok?, arr: Tok?, route: Route?): Pair<String?, String?> {
        val terms = window.filter { it.kind == Kind.Terminal }
        if (terms.isEmpty()) return null to null
        var depT: String? = null
        var arrT: String? = null
        val unplaced = ArrayList<Tok>()
        val separateLines = route != null && route.from.line != route.to.line
        for (t in terms) {
            when {
                dep != null && t.line == dep.line && (arr == null || arr.line != dep.line) && depT == null -> depT = t.value as String
                arr != null && t.line == arr.line && (dep == null || arr.line != dep.line) && arrT == null -> arrT = t.value as String
                separateLines && t.line == route!!.from.line && depT == null -> depT = t.value as String
                separateLines && t.line == route!!.to.line && arrT == null -> arrT = t.value as String
                else -> unplaced += t
            }
        }
        for (t in unplaced) {
            if (depT == null) depT = t.value as String else if (arrT == null) arrT = t.value as String
        }
        return depT to arrT
    }

    private fun mergeDuplicates(segments: List<SegmentDraft>): List<SegmentDraft> {
        val out = ArrayList<SegmentDraft>()
        for (s in segments) {
            val i = out.indexOfFirst { it.sameFlightAs(s) }
            if (i >= 0) out[i] = out[i].fillFrom(s) else out += s
        }
        return out
    }

    // ---- Route alignment ----

    /** Consecutive pairs of different airport codes within three lines of each other. */
    private fun pairRoutes(airports: List<Tok>): List<Route> {
        val out = ArrayList<Route>()
        var k = 0
        while (k < airports.size - 1) {
            val a = airports[k]
            val b = airports[k + 1]
            if (a.value != b.value && b.line - a.line <= 3) {
                out += Route(a, b)
                k += 2
            } else {
                k += 1
            }
        }
        return out
    }

    /**
     * Gives each flight at most one route, in order, minimising the total distance between
     * each flight number and its route. Extra routes (a trip summary line) are skipped.
     */
    private fun alignRoutes(flights: List<Tok>, routes: List<Route>): List<Route?> {
        val n = flights.size
        val m = routes.size
        if (n == 0) return emptyList()
        val skipFlight = 4_000L
        val inf = Long.MAX_VALUE / 4
        // dp[i][j]: best cost for the first i flights using only the first j routes.
        val dp = Array(n + 1) { LongArray(m + 1) { inf } }
        val choice = Array(n + 1) { IntArray(m + 1) }
        for (j in 0..m) dp[0][j] = 0
        for (i in 1..n) {
            for (j in 0..m) {
                var best = dp[i - 1][j] + skipFlight
                var pick = 0 // 0 = no route, 1 = route j-1, 2 = skip route j-1
                if (j > 0) {
                    if (dp[i][j - 1] < best) { best = dp[i][j - 1]; pick = 2 }
                    val d = abs(routes[j - 1].start - flights[i - 1].start).toLong()
                    val cost = if (d > 900) inf else dp[i - 1][j - 1] + d
                    if (cost < best) { best = cost; pick = 1 }
                }
                dp[i][j] = best
                choice[i][j] = pick
            }
        }
        val out = arrayOfNulls<Route>(n)
        var i = n
        var j = m
        while (i > 0) {
            when (choice[i][j]) {
                1 -> { out[i - 1] = routes[j - 1]; i--; j-- }
                2 -> j--
                else -> i--
            }
        }
        return out.toList()
    }

    /** The same flight number twice in a row (header and detail, or a codeshare line) is one flight. */
    private fun dedupeFlights(flights: List<Tok>): List<Tok> {
        val out = ArrayList<Tok>()
        for (f in flights) {
            val prev = out.lastOrNull()
            if (prev != null && prev.value == f.value) continue
            // "AC 6512 operated by EY 140" on one line: keep the first, it's the one on the ticket.
            if (prev != null && prev.line == f.line && f.start - prev.end < 40) continue
            out += f
        }
        return out
    }

    // ---- Tokeniser ----

    private fun tokenize(text: String, lines: LineIndex, today: LocalDate): List<Tok> = buildList {
        fun emit(kind: Kind, m: MatchResult, group: Int, value: Any) {
            val r = m.groups[group]!!.range
            add(Tok(kind, r.first, r.last + 1, lines.lineOf(r.first), value))
        }

        for (m in FLIGHT.findAll(text)) {
            val code = m.groupValues[1]
            val number = m.groupValues[2].trimStart('0')
            if (number.isEmpty()) continue
            val before = text.substring(maxOf(0, m.range.first - 12), m.range.first)
            val after = text.substring(m.range.last + 1, minOf(text.length, m.range.last + 3))
            if (MAKER_BEFORE.containsMatchIn(before) || after.startsWith("-")) continue
            val labelled = FLIGHT_LABEL.containsMatchIn(before)
            if (Airlines.byIata(code) == null && !labelled) continue
            emit(Kind.Flight, m, 0, FlightNo(code, number))
        }

        val bracketed = BRACKETED_CODE.findAll(text).filter { airports.isAirport(it.groupValues[1]) }.toList()
        if (bracketed.size >= 2) {
            bracketed.forEach { emit(Kind.Airport, it, 1, it.groupValues[1]) }
        } else {
            BARE_CODE.findAll(text)
                .filter { it.groupValues[1] !in STOPWORDS && airports.isAirport(it.groupValues[1]) }
                .forEach { emit(Kind.Airport, it, 1, it.groupValues[1]) }
        }

        dates(text, today).forEach { (range, date) -> add(Tok(Kind.Date, range.first, range.last + 1, lines.lineOf(range.first), date)) }

        for (m in TIME.findAll(text)) {
            val lineStart = text.lastIndexOf('\n', m.range.first - 1).let { if (it < 0) 0 else it + 1 }
            var context = text.substring(maxOf(lineStart, m.range.first - 28), m.range.first)
            // OCR'd boarding passes put the label on the line above the value ("BOARDING TIME" / "07:55").
            if (context.isBlank() && lineStart > 0) {
                val prevStart = text.lastIndexOf('\n', lineStart - 2).let { if (it < 0) 0 else it + 1 }
                context = text.substring(prevStart, lineStart - 1)
            }
            if (NOT_FLIGHT_TIME.containsMatchIn(context)) continue
            var h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            val ampm = m.groupValues[3].lowercase().filter { it.isLetter() }
            if (ampm == "pm" && h < 12) h += 12
            if (ampm == "am" && h == 12) h = 0
            if (h > 23) continue
            val plus = m.groupValues[4].toIntOrNull() ?: 0
            emit(Kind.Time, m, 0, Time(LocalTime.of(h, min), plus))
        }

        for (m in TERMINAL.findAll(text)) {
            val v = m.groupValues[1].uppercase()
            if (v.any { it.isDigit() } || v.length == 1) emit(Kind.Terminal, m, 1, v)
        }
        TERMINAL_SHORT.findAll(text).forEach { emit(Kind.Terminal, it, 1, it.groupValues[1]) }

        SEAT.findAll(text).forEach { emit(Kind.Seat, it, 1, it.groupValues[1].uppercase().trimStart('0')) }
        for (m in GATE.findAll(text)) {
            if (m.groupValues[1].any { it.isDigit() }) emit(Kind.Gate, m, 1, m.groupValues[1].uppercase())
        }

        for (m in CABIN.findAll(text)) {
            val word = m.groupValues[1].lowercase()
            if (word == "first") {
                val after = text.substring(m.range.last + 1, minOf(text.length, m.range.last + 8)).lowercase()
                val before = text.substring(maxOf(0, m.range.first - 12), m.range.first).lowercase()
                if (!after.trimStart().startsWith("class") && "cabin" !in before) continue
            }
            val cabin = when {
                "premium" in word -> Cabin.PremiumEconomy
                "economy" in word -> Cabin.Economy
                word.startsWith("first") || "residence" in word || "apartment" in word -> Cabin.First
                else -> Cabin.Business
            }
            emit(Kind.Cabin, m, 1, cabin)
            // "Business (I)": the booking class in brackets right after the cabin.
            CLASS_IN_BRACKETS.matchAt(text, m.range.last + 1)?.let { emit(Kind.FareClass, it, 1, it.groupValues[1]) }
        }
        for (m in FARE_CLASS.findAll(text)) {
            val letter = m.groupValues[1]
            if (letter[0].isUpperCase()) emit(Kind.FareClass, m, 1, letter)
        }
        STATUS_CLASS.findAll(text).forEach { emit(Kind.FareClass, it, 1, it.groupValues[1]) }

        HOTEL.findAll(text).forEach { emit(Kind.Hotel, it, 0, true) }
    }

    private fun dates(text: String, today: LocalDate): List<Pair<IntRange, LocalDate>> {
        val found = ArrayList<Pair<IntRange, LocalDate>>()
        fun take(range: IntRange, date: LocalDate?) {
            if (date == null || found.any { it.first.first <= range.last && range.first <= it.first.last }) return
            found += range to date
        }
        for (m in DATE_DMY.findAll(text)) {
            val month = TicketText.month(m.groupValues[2]) ?: continue
            val day = m.groupValues[1].toInt()
            val y = m.groupValues[3].toIntOrNull()
            take(m.range, ymd(y, month, day, today))
        }
        for (m in DATE_MDY.findAll(text)) {
            val month = TicketText.month(m.groupValues[1]) ?: continue
            take(m.range, ymd(m.groupValues[3].toIntOrNull(), month, m.groupValues[2].toInt(), today))
        }
        for (m in DATE_ISO.findAll(text)) {
            take(m.range, runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull())
        }
        for (m in DATE_SLASH.findAll(text)) {
            take(m.range, runCatching { LocalDate.of(TicketText.fullYear(m.groupValues[3].toInt()), m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull())
        }
        return found
    }

    private fun ymd(year: Int?, month: Int, day: Int, today: LocalDate): LocalDate? =
        if (year == null) TicketText.inferYear(month, day, today)
        else runCatching { LocalDate.of(TicketText.fullYear(year), month, day) }.getOrNull()

    // ---- Whole-ticket fields ----

    private fun findPnr(text: String): String? = PNR.findAll(text)
        .map { it.groupValues[1] }
        .firstOrNull { v -> v == v.uppercase() && v.any { it.isLetter() } }

    private fun findPassenger(text: String): String? {
        for (m in PASSENGER.findAll(text)) {
            var v = m.groupValues[1]
            v = v.split(Regex("\\s{2,}|\\t")).first()
            v = v.split(Regex("(?i)\\b(aeroplan|frequent|ticket|seat|loyalty|etihad guest|asia miles|marco polo|booking|e-ticket|status)\\b")).first()
            val name = TicketText.cleanName(v) ?: continue
            if (name.length in 3..60 && name.none { it.isDigit() }) return name
        }
        return null
    }

    private fun cleanOperator(raw: String): String? =
        raw.split(Regex("\\s{2,}|\\n|(?i)\\b(aircraft|equipment|on behalf)\\b")).first().trim().trimEnd('.', ',').ifEmpty { null }

    private fun normalize(raw: String): String = raw
        .replace(' ', ' ')
        .replace('–', '-')
        .replace('—', '-')
        .replace('−', '-')
        .replace("\r\n", "\n")
        .replace('\r', '\n')

    private class LineIndex(text: String) {
        private val starts: IntArray = buildList {
            add(0)
            text.forEachIndexed { i, c -> if (c == '\n') add(i + 1) }
        }.toIntArray()

        fun lineOf(pos: Int): Int {
            val i = starts.binarySearch(pos)
            return if (i >= 0) i else -i - 2
        }
    }

    private companion object {
        val FLIGHT = Regex("(?<![A-Za-z0-9])([A-Z][A-Z0-9]|[0-9][A-Z]) ?(\\d{1,4})(?![0-9A-Za-z:])")
        val FLIGHT_LABEL = Regex("(?i)flight\\s*(no\\.?|number)?\\s*[:#]?\\s*$")
        val MAKER_BEFORE = Regex("(?i)(airbus|boeing|embraer|aircraft|equipment)[:\\s]*$")
        val BRACKETED_CODE = Regex("\\(([A-Z]{3})\\)")
        val BARE_CODE = Regex("(?<![A-Za-z])([A-Z]{3})(?![A-Za-z])")
        val STOPWORDS = setOf(
            "THE", "AND", "FOR", "PNR", "ETD", "ETA", "PAX", "BAG", "OUT", "ARR", "DEP", "SEQ", "VIA", "NON", "GMT", "UTC",
            "ONE", "TWO", "AIR", "FLT", "REF", "YES", "MRS", "DAY", "NEW", "ALL", "ANY", "NOT", "NOW", "OFF", "OUR", "PER",
            "RES", "TAX", "TKT", "USD", "CAD", "AED", "HKD", "PHP", "INR", "EUR", "GBP", "JPY", "FEE", "STD", "STA", "CHK",
            "REQ", "INF", "CHD", "ADT", "SSR", "GATE", "ZONE", "BOARDING", "SEAT", "FROM", "TO", "MR", "MS", "ECO", "BUS",
            "FIR", "CLS", "OPS", "KGS", "LBS", "PCS", "MAX", "NEO", "WEB", "APP", "SMS", "TEL", "FAX", "WWW", "COM", "ORG",
            "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC",
            "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN",
        ).filter { it.length == 3 }.toSet()

        private const val MONTHS = "(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:t(?:ember)?)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)"
        val DATE_DMY = Regex("(?i)(?<![0-9A-Za-z])(\\d{1,2})[ ]?-?$MONTHS\\.?(?:[ ]?-?(\\d{4}|\\d{2}))?(?![0-9A-Za-z])")
        val DATE_MDY = Regex("(?i)(?<![A-Za-z])$MONTHS\\.?\\s(\\d{1,2})(?:st|nd|rd|th)?,?\\s(\\d{4})(?![0-9])")
        val DATE_ISO = Regex("(?<![0-9])(\\d{4})-(\\d{2})-(\\d{2})(?![0-9])")
        val DATE_SLASH = Regex("(?<![0-9/])(\\d{1,2})/(\\d{1,2})/(\\d{4}|\\d{2})(?![0-9/])")

        val TIME = Regex("(?<![\\d:])([01]?\\d|2[0-3])[:h]([0-5]\\d)(?![\\d:])\\s?([AaPp]\\.?\\s?[Mm]\\.?)?(?:\\s?\\(?\\+\\s?(\\d)(?:\\s?days?)?\\)?)?")
        val NOT_FLIGHT_TIME = Regex("(?i)(board|check-?in|gate clos|closes|duration|flight time|travel time|journey|layover|connection|total|issued|printed|generated|sent)")

        val TERMINAL = Regex("(?i)\\b(?:terminal|term\\.)[ \\t]*[:\\-]?[ \\t]*([A-Z0-9]{1,2})\\b")
        val TERMINAL_SHORT = Regex("(?<![A-Za-z0-9])T([1-9][A-Z]?)(?![A-Za-z0-9])")
        val SEAT = Regex("(?i)\\bseats?(?:\\s*(?:number|no\\.?|#))?\\s*[:\\-]?\\s*(\\d{1,2}[A-K])\\b")
        val GATE = Regex("(?i)\\bgate(?!\\s*clos)[ \\t]*[:\\-]?\\s*([A-Z]?\\d{1,3}[A-Z]?)(?![\\w:])")
        val CABIN = Regex("(?i)\\b(premium economy|business studio|business|economy space|economy|signature class|first apartment|the residence|first)\\b")
        val CLASS_IN_BRACKETS = Regex("\\s?\\(([A-Z])\\)")
        val FARE_CLASS = Regex("(?i)\\b(?:booking class|fare class|class of service|reservation class|rbd|class)[ \\t]*[:\\-]?[ \\t]*([A-Z])\\b")
        val STATUS_CLASS = Regex("(?<![A-Za-z0-9])([A-Z])\\s+(?:OK|HK|KK|TK|RR)(?![A-Za-z])")
        val HOTEL = Regex("(?i)\\b(hotel|stopover)\\b")

        val PNR = Regex("(?i)(?:booking\\s+reference|booking\\s+ref\\.?|booking\\s+code|booking\\s+number|reservation\\s+(?:code|number)|confirmation\\s+(?:code|number)|record\\s+locator|\\bPNR\\b)\\s*(?:\\(PNR\\))?\\s*(?:number|no\\.?)?\\s*[:#\\-]?\\s*([A-Z0-9]{6})(?![A-Z0-9])")
        val PASSENGER = Regex("(?i)(?:passenger\\s+name|passengers?(?:\\(s\\))?|traveller(?:\\s+name)?|traveler(?:\\s+name)?|name\\s+of\\s+passenger|\\bname)\\s*(?:[:\\-]|\\n)\\s*([^\\n]+)")
        val TICKET = Regex("(?i)(?:e-?ticket|ticket)\\s*(?:number|no\\.?|#)?\\s*[:\\-]?\\s*(\\d{3})[\\s-]?(\\d{10})")
        val OPERATED_BY = Regex("(?i)operated\\s+by\\s*[:\\-]?\\s*([A-Za-z][A-Za-z .&'-]{2,40})")
    }
}

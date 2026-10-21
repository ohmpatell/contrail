package com.contrail.contrail.core.data.ticket

import com.contrail.contrail.core.data.trip.Cabin
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs

enum class ImportSource(val label: String) {
    Barcode("Boarding pass barcode"),
    Pdf("PDF ticket"),
    Image("Screenshot or photo"),
    Text("Pasted text"),
    Manual("Entered by hand"),
}

/** Everything an import found, before you review it. Every field may be missing. */
data class TicketDraft(
    val pnr: String? = null,
    val passengerName: String? = null,
    val ticketNumber: String? = null,
    val segments: List<SegmentDraft> = emptyList(),
    val sources: Set<ImportSource> = emptySet(),
) {
    val isEmpty: Boolean get() = segments.isEmpty() && pnr == null

    /**
     * Combines two reads of the same ticket (e.g. a PDF's barcode and its text). Segments
     * with the same flight and date are merged field by field; new ones are added in time order.
     */
    fun merge(other: TicketDraft): TicketDraft {
        val merged = segments.toMutableList()
        for (s in other.segments) {
            val i = merged.indexOfFirst { it.sameFlightAs(s) }
            if (i >= 0) merged[i] = merged[i].fillFrom(s) else merged += s
        }
        return TicketDraft(
            pnr = pnr ?: other.pnr,
            passengerName = passengerName ?: other.passengerName,
            ticketNumber = ticketNumber ?: other.ticketNumber,
            segments = merged.sortedWith(compareBy(nullsLast()) { it.date?.atTime(it.departureTime ?: LocalTime.MIDNIGHT) }),
            sources = sources + other.sources,
        )
    }
}

data class SegmentDraft(
    val airlineIata: String? = null,
    val flightNumber: String? = null,
    val origin: String? = null,
    val destination: String? = null,
    val date: LocalDate? = null,
    val departureTime: LocalTime? = null,
    val arrivalTime: LocalTime? = null,
    /** Set when the ticket gives it (a "+1" marker or a second date). */
    val arrivalDate: LocalDate? = null,
    val departureTerminal: String? = null,
    val arrivalTerminal: String? = null,
    val gate: String? = null,
    val cabin: Cabin? = null,
    val fareClass: String? = null,
    val seat: String? = null,
    val aircraftType: String? = null,
    val operatedBy: String? = null,
    /** The ticket mentions a hotel or stopover package after this flight. */
    val hotelAfter: Boolean = false,
) {
    fun sameFlightAs(o: SegmentDraft): Boolean =
        airlineIata != null && airlineIata == o.airlineIata && flightNumber == o.flightNumber &&
            (date == null || o.date == null || date == o.date)

    fun fillFrom(o: SegmentDraft) = SegmentDraft(
        airlineIata = airlineIata ?: o.airlineIata,
        flightNumber = flightNumber ?: o.flightNumber,
        origin = origin ?: o.origin,
        destination = destination ?: o.destination,
        date = date ?: o.date,
        departureTime = departureTime ?: o.departureTime,
        arrivalTime = arrivalTime ?: o.arrivalTime,
        arrivalDate = arrivalDate ?: o.arrivalDate,
        departureTerminal = departureTerminal ?: o.departureTerminal,
        arrivalTerminal = arrivalTerminal ?: o.arrivalTerminal,
        gate = gate ?: o.gate,
        cabin = cabin ?: o.cabin,
        fareClass = fareClass ?: o.fareClass,
        seat = seat ?: o.seat,
        aircraftType = aircraftType ?: o.aircraftType,
        operatedBy = operatedBy ?: o.operatedBy,
        hotelAfter = hotelAfter || o.hotelAfter,
    )

    /** Fields the review screen flags as missing: a segment can't be saved without these. */
    val missing: List<String>
        get() = buildList {
            if (airlineIata == null || flightNumber == null) add("flight number")
            if (origin == null) add("from")
            if (destination == null) add("to")
            if (date == null) add("date")
            if (departureTime == null) add("departure time")
            if (arrivalTime == null) add("arrival time")
        }
}

/** Shared parsing helpers. */
internal object TicketText {
    val months = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

    fun month(token: String): Int? = months.indexOf(token.take(3).uppercase()).takeIf { it >= 0 }?.plus(1)

    /**
     * A date printed without a year belongs to the year that puts it closest to [today],
     * preferring upcoming dates (tickets are usually imported before you fly).
     */
    fun inferYear(month: Int, day: Int, today: LocalDate): LocalDate? {
        val candidates = (today.year - 1..today.year + 1).mapNotNull { y -> runCatching { LocalDate.of(y, month, day) }.getOrNull() }
        return candidates.minByOrNull { d ->
            val days = ChronoUnit.DAYS.between(today, d)
            if (days >= -45) abs(days) else abs(days) + 400
        }
    }

    fun fullYear(y: Int): Int = if (y < 100) 2000 + y else y

    /** "PATEL/OHM MR" or "MR OHM PATEL" → "Ohm Patel". */
    fun cleanName(raw: String): String? {
        val titles = setOf("MR", "MRS", "MS", "MISS", "MSTR", "DR", "PROF", "MX")
        var s = raw.trim().trim(',', ';', ':').replace(Regex("\\s+"), " ")
        if (s.isEmpty()) return null
        if ('/' in s) {
            val (last, first) = s.split('/', limit = 2).map { it.trim() }
            s = "$first $last"
        }
        val words = s.split(' ').filter { w -> w.isNotBlank() && w.trimEnd('.').uppercase() !in titles }
        if (words.isEmpty()) return null
        return words.joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercase() } }
    }

    /** Cabin from a booking-class letter. Airlines differ, so a few are overridden. */
    fun cabinForClass(letter: Char, airlineIata: String?): Cabin {
        val c = letter.uppercaseChar()
        val overrides: Map<Char, Cabin> = when (airlineIata?.uppercase()) {
            "CX" -> mapOf('P' to Cabin.Business, 'W' to Cabin.PremiumEconomy, 'R' to Cabin.PremiumEconomy, 'E' to Cabin.PremiumEconomy)
            "AC" -> mapOf('P' to Cabin.Business, 'O' to Cabin.PremiumEconomy, 'E' to Cabin.PremiumEconomy, 'N' to Cabin.PremiumEconomy, 'W' to Cabin.Economy, 'A' to Cabin.Economy)
            "EY" -> mapOf('W' to Cabin.Business, 'R' to Cabin.Business)
            else -> emptyMap()
        }
        overrides[c]?.let { return it }
        return when (c) {
            'F', 'A' -> Cabin.First
            'J', 'C', 'D', 'I', 'Z' -> Cabin.Business
            'W' -> Cabin.PremiumEconomy
            else -> Cabin.Economy
        }
    }
}

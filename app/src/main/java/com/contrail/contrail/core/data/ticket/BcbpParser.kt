package com.contrail.contrail.core.data.ticket

import java.time.LocalDate

/**
 * IATA Bar Coded Boarding Pass (Resolution 792) parser: the PDF417, Aztec or QR code on
 * every boarding pass. Reads the mandatory fields of every leg, plus the date of issue from
 * the conditional section when present (it pins the year the Julian flight date belongs to).
 *
 * Layout: format "M", leg count, passenger name (20), e-ticket flag, then per leg:
 * PNR (7), from (3), to (3), carrier (3), flight (5), Julian date (3), compartment (1),
 * seat (4), sequence (5), status (1), and the size of that leg's variable section (2, hex).
 */
object BcbpParser {

    fun looksLikeBcbp(raw: String): Boolean =
        raw.length >= 60 && raw[0] == 'M' && raw[1] in '1'..'4'

    fun parse(raw: String, today: LocalDate): TicketDraft? {
        if (!looksLikeBcbp(raw)) return null
        return runCatching { parseUnsafe(raw, today) }.getOrNull()
    }

    private fun parseUnsafe(raw: String, today: LocalDate): TicketDraft? {
        val legs = raw[1] - '0'
        val name = TicketText.cleanName(raw.substring(2, 22))
        var pos = 23
        var issueDate: LocalDate? = null
        var ticketNumber: String? = null
        var pnr: String? = null
        val drafts = ArrayList<Pair<SegmentDraft, Int>>() // draft + Julian day
        for (leg in 0 until legs) {
            if (pos + 37 > raw.length) break
            val legPnr = raw.substring(pos, pos + 7).trim()
            val from = raw.substring(pos + 7, pos + 10).trim()
            val to = raw.substring(pos + 10, pos + 13).trim()
            val carrier = raw.substring(pos + 13, pos + 16).trim()
            val flight = raw.substring(pos + 16, pos + 21).trim()
            val julian = raw.substring(pos + 21, pos + 24).trim().toIntOrNull()
            val compartment = raw[pos + 24]
            val seat = raw.substring(pos + 25, pos + 29).trim()
            val varSize = raw.substring(pos + 35, pos + 37).toInt(16)
            val varStart = pos + 37
            val variable = raw.substring(varStart, minOf(raw.length, varStart + varSize))
            if (leg == 0) {
                val conditional = parseFirstConditional(variable, today)
                issueDate = conditional.first
                ticketNumber = conditional.second
            } else if (ticketNumber == null) {
                ticketNumber = parseRepeatedConditional(variable)
            }
            val draft = SegmentDraft(
                airlineIata = carrier.ifEmpty { null },
                flightNumber = normalizeFlight(flight),
                origin = from.takeIf { it.length == 3 },
                destination = to.takeIf { it.length == 3 },
                fareClass = compartment.takeIf { it.isLetter() }?.toString(),
                cabin = compartment.takeIf { it.isLetter() }?.let { TicketText.cabinForClass(it, carrier) },
                seat = normalizeSeat(seat),
            )
            drafts += draft to (julian ?: -1)
            // The first leg's PNR is the trip's booking reference.
            if (leg == 0) pnr = legPnr.ifEmpty { null }
            pos = varStart + varSize
        }
        val segments = drafts.map { (d, julian) -> d.copy(date = julianDate(julian, issueDate, today)) }
        return TicketDraft(
            pnr = pnr,
            passengerName = name,
            ticketNumber = ticketNumber,
            segments = segments,
            sources = setOf(ImportSource.Barcode),
        )
    }

    /** Version marker '>', version, unique-section size, then the unique fields. */
    private fun parseFirstConditional(v: String, today: LocalDate): Pair<LocalDate?, String?> {
        if (v.length < 4 || v[0] != '>') return null to null
        val uniqueSize = v.substring(2, 4).toIntOrNull(16) ?: return null to null
        val unique = v.substring(4, minOf(v.length, 4 + uniqueSize))
        // Passenger description (1), check-in source (1), issuance source (1), issue date (4: Y + DDD).
        val issue = unique.takeIf { it.length >= 7 }?.substring(3, 7)?.let { s ->
            val y = s[0].digitToIntOrNull()
            val ddd = s.substring(1).toIntOrNull()
            if (y != null && ddd != null && ddd in 1..366) y to ddd else null
        }
        val rest = v.substring(minOf(v.length, 4 + uniqueSize))
        return issue?.let { (y, ddd) -> issueDate(y, ddd, today) } to parseRepeatedConditional(rest)
    }

    /** Repeated section: size (2, hex), airline numeric code (3), document serial number (10). */
    private fun parseRepeatedConditional(v: String): String? {
        if (v.length < 2 + 13) return null
        val code = v.substring(2, 5)
        val serial = v.substring(5, 15)
        return if (code.all { it.isDigit() } && serial.all { it.isDigit() }) "$code $serial" else null
    }

    /** The issue year's last digit plus a Julian day; the year is the most recent one ending in that digit. */
    private fun issueDate(yearDigit: Int, ddd: Int, today: LocalDate): LocalDate? {
        val year = today.year - ((today.year % 10 - yearDigit + 10) % 10)
        return runCatching { LocalDate.ofYearDay(year, ddd) }.getOrNull()
    }

    private fun julianDate(ddd: Int, issue: LocalDate?, today: LocalDate): LocalDate? {
        if (ddd !in 1..366) return null
        if (issue != null) {
            // The flight is on or after the day the pass was issued.
            val sameYear = runCatching { LocalDate.ofYearDay(issue.year, ddd) }.getOrNull()
            return if (sameYear != null && !sameYear.isBefore(issue)) sameYear
            else runCatching { LocalDate.ofYearDay(issue.year + 1, ddd) }.getOrNull()
        }
        val candidates = (today.year - 1..today.year + 1).mapNotNull { y ->
            runCatching { LocalDate.ofYearDay(y, ddd) }.getOrNull()?.takeIf { it.year == y }
        }
        return candidates.minByOrNull { kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(today, it)) }
    }

    /** "0834 " → "834", "0015A" → "15A". */
    fun normalizeFlight(raw: String): String? {
        val t = raw.trim().uppercase()
        val digits = t.takeWhile { it.isDigit() }.trimStart('0')
        val suffix = t.drop(t.takeWhile { it.isDigit() }.length).filter { it.isLetter() }
        return (digits + suffix).ifEmpty { null }
    }

    /** "001A" → "1A"; blanks and "INF" (infant, no seat) → null. */
    fun normalizeSeat(raw: String): String? {
        val t = raw.trim().uppercase()
        val m = Regex("^0*(\\d{1,3})([A-K])$").find(t) ?: return null
        return m.groupValues[1] + m.groupValues[2]
    }
}

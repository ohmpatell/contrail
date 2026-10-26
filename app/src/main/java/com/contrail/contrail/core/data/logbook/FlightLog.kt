package com.contrail.contrail.core.data.logbook

import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.core.data.ticket.AircraftNames
import com.contrail.contrail.core.data.trip.Cabin
import com.contrail.contrail.core.data.trip.FlightReason
import com.contrail.contrail.core.data.trip.SeatType
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Which app a flight log came from. The columns differ; the parser reads them all by header name. */
enum class LogFormat(val label: String) {
    MyFlightradar24("myFlightradar24"),
    Flighty("Flighty"),
    OpenFlights("OpenFlights"),
    Generic("CSV"),
}

/** One row of a flight log, read but not yet checked against the airport list. */
data class LoggedFlight(
    val row: Int,
    val date: LocalDate,
    /** The log only gave a month or a year, so the day is a placeholder. */
    val roughDate: Boolean = false,
    val airlineIata: String? = null,
    val airlineIcao: String? = null,
    val airlineName: String? = null,
    /** Digits plus an optional suffix letter, without leading zeros. */
    val flightNumber: String? = null,
    val originIata: String? = null,
    val originIcao: String? = null,
    val destinationIata: String? = null,
    val destinationIcao: String? = null,
    /** Local wall-clock times. A full date-time is kept when the log gives one (Flighty). */
    val departure: LocalDateTime? = null,
    val arrival: LocalDateTime? = null,
    val arrivalTime: LocalTime? = null,
    val duration: Duration? = null,
    val aircraftType: String? = null,
    val registration: String? = null,
    val seat: String? = null,
    val seatType: SeatType? = null,
    val cabin: Cabin? = null,
    val reason: FlightReason? = null,
    val note: String? = null,
    val pnr: String? = null,
    val departureTerminal: String? = null,
    val arrivalTerminal: String? = null,
    val gate: String? = null,
)

data class ParsedLog(val format: LogFormat, val flights: List<LoggedFlight>, val skippedRows: Int)

/**
 * Reads flight-log CSV exports: myFlightradar24 (Settings → Export → Download CSV), Flighty
 * (Settings → Account Data → Export Your Flights), OpenFlights, or any CSV with similar headers.
 * Columns are found by name, so their order and extra columns don't matter.
 */
object FlightLogParser {
    private enum class Col { Date, Flight, Airline, From, To, DepTime, ArrTime, Duration, Aircraft, Registration, Seat, SeatType, Cabin, Reason, Note, Pnr, DepTerminal, ArrTerminal, Gate, Canceled }

    private val aliases: Map<Col, List<String>> = mapOf(
        Col.Date to listOf("date", "flight date", "departure date"),
        Col.Flight to listOf("flight number", "flight", "flight_number", "flight no", "flight no.", "flightnumber"),
        Col.Airline to listOf("airline", "carrier", "operating airline"),
        Col.From to listOf("from", "origin", "departure airport", "dep airport", "departure"),
        Col.To to listOf("to", "destination", "arrival airport", "arr airport", "arrival"),
        Col.DepTime to listOf("dep time", "gate departure (actual)", "gate departure (scheduled)", "take off (actual)", "take off (scheduled)", "departure time", "std"),
        Col.ArrTime to listOf("arr time", "gate arrival (actual)", "gate arrival (scheduled)", "landing (actual)", "landing (scheduled)", "arrival time", "sta"),
        Col.Duration to listOf("duration", "flight time", "block time"),
        Col.Aircraft to listOf("aircraft", "aircraft type name", "aircraft type", "plane", "equipment", "type"),
        Col.Registration to listOf("registration", "tail number", "tail", "reg"),
        Col.Seat to listOf("seat number", "seat"),
        Col.SeatType to listOf("seat type", "seat_type", "seat position"),
        Col.Cabin to listOf("flight class", "cabin class", "class", "cabin"),
        Col.Reason to listOf("flight reason", "reason", "trip reason"),
        Col.Note to listOf("note", "notes", "comment", "comments"),
        Col.Pnr to listOf("pnr", "booking reference", "confirmation"),
        Col.DepTerminal to listOf("dep terminal", "departure terminal"),
        Col.ArrTerminal to listOf("arr terminal", "arrival terminal"),
        Col.Gate to listOf("dep gate", "departure gate", "gate"),
        Col.Canceled to listOf("canceled", "cancelled"),
    )

    /** "Toronto / Toronto Pearson (YYZ/CYYZ)": either code may be blank. */
    private val bracketCodes = Regex("\\(([A-Z0-9]{0,3})\\s*/\\s*([A-Z0-9]{0,4})\\)")
    private val bracketOne = Regex("\\(([A-Z0-9-]{2,4})\\)\\s*$")
    private val flightRe = Regex("^([A-Z]{3}|[A-Z][A-Z0-9]|[0-9][A-Z])?\\s*-?\\s*0*(\\d{1,4}[A-Z]?)$")
    private val dateRe = Regex("^(\\d{4})(?:-(\\d{1,2})(?:-(\\d{1,2}))?)?")
    private val dmyRe = Regex("^(\\d{1,2})[/.](\\d{1,2})[/.](\\d{4})")
    private val timeRe = Regex("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?")
    private val durationRe = Regex("^(\\d{1,3}):(\\d{2})(?::(\\d{2}))?$")

    fun parse(text: String): ParsedLog {
        val rows = csv(text.removePrefix("﻿"))
        if (rows.isEmpty()) return ParsedLog(LogFormat.Generic, emptyList(), 0)
        val header = rows.first().map { it.trim().lowercase().replace('_', ' ') }
        val index = HashMap<Col, List<Int>>()
        for ((col, names) in aliases) {
            // Every matching column, in alias order: Flighty's actual times win over scheduled ones when present.
            names.map { n -> header.indexOf(n) }.filter { it >= 0 }.takeIf { it.isNotEmpty() }?.let { index[col] = it }
        }
        val format = when {
            "dep_id" in header || "dep id" in header || ("dep time" in header && "flight reason" in header) -> LogFormat.MyFlightradar24
            "gate departure (scheduled)" in header || "flight flighty id" in header -> LogFormat.Flighty
            "from oid" in header || "plane oid" in header -> LogFormat.OpenFlights
            else -> LogFormat.Generic
        }
        var skipped = 0
        val flights = rows.drop(1).mapIndexedNotNull { i, r ->
            if (r.all { it.isBlank() }) return@mapIndexedNotNull null
            val flight = runCatching { row(i + 2, r, index) }.getOrNull()
            if (flight == null) skipped++
            flight
        }
        return ParsedLog(format, flights, skipped)
    }

    private fun row(rowNumber: Int, r: List<String>, index: Map<Col, List<Int>>): LoggedFlight? {
        fun get(c: Col): String? = index[c].orEmpty().firstNotNullOfOrNull { i ->
            r.getOrNull(i)?.trim()?.takeUnless { it.isEmpty() || it == "\\N" || it == "N/A" }
        }
        if (get(Col.Canceled)?.lowercase() in setOf("true", "yes", "1")) return null

        val rawDate = get(Col.Date) ?: get(Col.DepTime) ?: return null
        val (date, rough) = date(rawDate) ?: return null
        val (fromIata, fromIcao) = airport(get(Col.From) ?: return null) ?: return null
        val (toIata, toIcao) = airport(get(Col.To) ?: return null) ?: return null

        // Airline: "Air Canada (AC/ACA)", an ICAO code (Flighty), an IATA code or a name.
        var airlineIata: String? = null
        var airlineIcao: String? = null
        var airlineName: String? = null
        get(Col.Airline)?.let { a ->
            val m = bracketCodes.find(a.uppercase())
            if (m != null) {
                airlineIata = m.groupValues[1].ifEmpty { null }
                airlineIcao = m.groupValues[2].ifEmpty { null }
                airlineName = a.substringBefore("(").trim().ifEmpty { null }
            } else {
                val u = a.uppercase()
                when {
                    u.length == 3 && u.all { it.isLetter() } -> airlineIcao = u
                    u.length == 2 && u.all { it.isLetterOrDigit() } -> airlineIata = u
                    else -> airlineName = a
                }
            }
        }
        var number: String? = null
        get(Col.Flight)?.uppercase()?.replace(" ", "")?.let { f ->
            flightRe.matchEntire(f)?.let { m ->
                val prefix = m.groupValues[1]
                when {
                    prefix.length == 3 -> airlineIcao = airlineIcao ?: prefix
                    prefix.length == 2 -> airlineIata = airlineIata ?: prefix
                }
                number = m.groupValues[2]
            }
        }
        val known = Airlines.byIcao(airlineIcao) ?: Airlines.byIata(airlineIata)
        airlineIata = airlineIata ?: known?.iata?.ifEmpty { null }
        airlineIcao = airlineIcao ?: known?.icao
        airlineName = airlineName ?: known?.name

        // OpenFlights puts the departure time in the date column.
        val depRaw = get(Col.DepTime) ?: rawDate.takeIf { it.length > 10 && timeRe.containsMatchIn(it.substring(10)) }
        val arrRaw = get(Col.ArrTime)
        val depTime = depRaw?.let(::time)
        val arrTime = arrRaw?.let(::time)
        // myFlightradar24 writes unknown times as 00:00:00.
        val unknownTimes = depTime == LocalTime.MIDNIGHT && (arrTime == null || arrTime == LocalTime.MIDNIGHT)
        val departure = if (rough || unknownTimes || depTime == null) null else (depRaw.let(::dateOf) ?: date).atTime(depTime)
        val arrivalFull = if (rough || unknownTimes) null else arrRaw?.let(::dateOf)?.let { d -> arrTime?.let { d.atTime(it) } }
        val arrivalTime = if (rough || unknownTimes || arrTime == LocalTime.MIDNIGHT && arrivalFull == null) null else arrTime

        val aircraft = get(Col.Aircraft)?.let { a ->
            bracketOne.find(a.uppercase())?.groupValues?.get(1)?.takeIf { it.length >= 3 }
                ?: AircraftNames.fromName(a)
                ?: a.uppercase().takeIf { it.length == 4 && it.any(Char::isDigit) }
        }

        return LoggedFlight(
            row = rowNumber,
            date = date,
            roughDate = rough,
            airlineIata = airlineIata,
            airlineIcao = airlineIcao,
            airlineName = airlineName,
            flightNumber = number,
            originIata = fromIata,
            originIcao = fromIcao,
            destinationIata = toIata,
            destinationIcao = toIcao,
            departure = departure,
            arrival = arrivalFull,
            arrivalTime = arrivalTime,
            duration = get(Col.Duration)?.let(::duration)?.takeIf { !it.isZero },
            aircraftType = aircraft,
            registration = get(Col.Registration)?.uppercase(),
            seat = get(Col.Seat)?.uppercase(),
            seatType = get(Col.SeatType)?.let(::seatType),
            cabin = get(Col.Cabin)?.let(::cabin),
            reason = get(Col.Reason)?.let(::reason),
            note = get(Col.Note),
            pnr = get(Col.Pnr)?.uppercase(),
            departureTerminal = get(Col.DepTerminal),
            arrivalTerminal = get(Col.ArrTerminal),
            gate = get(Col.Gate),
        )
    }

    /** An IATA and/or ICAO code from "City / Name (IATA/ICAO)", a bare code, or a name ending in "(XXX)". */
    internal fun airport(raw: String): Pair<String?, String?>? {
        val u = raw.trim().uppercase()
        bracketCodes.find(u)?.let { m ->
            val iata = m.groupValues[1].takeIf { it.length == 3 }
            val icao = m.groupValues[2].takeIf { it.length == 4 }
            if (iata != null || icao != null) return iata to icao
        }
        if (u.length == 3 && u.all { it.isLetterOrDigit() }) return u to null
        if (u.length == 4 && u.all { it.isLetterOrDigit() }) return null to u
        bracketOne.find(u)?.groupValues?.get(1)?.let { c -> return if (c.length == 3) c to null else null to c }
        return null
    }

    /** "2026-10-12", "2026-10", "2026", "2026-10-12 08:00", "12/10/2026" (day first). */
    internal fun date(raw: String): Pair<LocalDate, Boolean>? {
        val s = raw.trim()
        dateRe.find(s)?.let { m ->
            val y = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toIntOrNull()
            val d = m.groupValues[3].toIntOrNull()
            return runCatching { LocalDate.of(y, mo ?: 1, d ?: 1) to (d == null) }.getOrNull()
        }
        dmyRe.find(s)?.let { m ->
            return runCatching { LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()) to false }.getOrNull()
        }
        return null
    }

    private fun dateOf(raw: String): LocalDate? = if (raw.length >= 10 && dateRe.containsMatchIn(raw)) date(raw)?.takeIf { !it.second }?.first else null

    internal fun time(raw: String): LocalTime? {
        val m = timeRe.find(raw) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        return if (h in 0..23 && min in 0..59) LocalTime.of(h, min) else null
    }

    internal fun duration(raw: String): Duration? {
        val m = durationRe.matchEntire(raw.trim()) ?: return raw.trim().toLongOrNull()?.let { Duration.ofMinutes(it) }
        return Duration.ofHours(m.groupValues[1].toLong()).plusMinutes(m.groupValues[2].toLong())
    }

    internal fun seatType(raw: String): SeatType? = when (raw.trim().lowercase()) {
        "1", "w", "window" -> SeatType.Window
        "2", "m", "middle", "centre", "center" -> SeatType.Middle
        "3", "a", "aisle" -> SeatType.Aisle
        else -> null
    }

    /** myFlightradar24: 1 economy, 2 business, 3 first, 4 economy+. OpenFlights: Y, P, C, F. */
    internal fun cabin(raw: String): Cabin? = when (raw.trim().lowercase()) {
        "1", "y", "economy", "coach" -> Cabin.Economy
        "2", "c", "j", "business" -> Cabin.Business
        "3", "f", "first", "first class" -> Cabin.First
        "4", "p", "w", "economy+", "premium economy", "premium", "premium_economy" -> Cabin.PremiumEconomy
        else -> null
    }

    internal fun reason(raw: String): FlightReason? = when (raw.trim().lowercase()) {
        "1", "l", "leisure", "personal", "vacation" -> FlightReason.Leisure
        "2", "b", "business", "work" -> FlightReason.Business
        "3", "c", "crew" -> FlightReason.Crew
        "4", "o", "other" -> FlightReason.Other
        else -> null
    }

    /** RFC 4180 CSV: quoted fields may hold commas, quotes ("") and line breaks. Semicolons work too. */
    internal fun csv(text: String): List<List<String>> {
        val firstLine = text.substringBefore('\n')
        val sep = if (firstLine.count { it == ';' } > firstLine.count { it == ',' }) ';' else ','
        val rows = ArrayList<List<String>>()
        var field = StringBuilder()
        var row = ArrayList<String>()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else quoted = false
                } else {
                    field.append(c)
                }
            } else {
                when (c) {
                    '"' -> quoted = true
                    sep -> { row += field.toString(); field = StringBuilder() }
                    '\r' -> Unit
                    '\n' -> { row += field.toString(); field = StringBuilder(); rows += row; row = ArrayList() }
                    else -> field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); rows += row }
        return rows
    }
}

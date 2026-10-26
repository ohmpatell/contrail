package com.contrail.contrail.core.data.logbook

import android.content.Context
import android.net.Uri
import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.data.repository.AirportRepository
import com.contrail.contrail.core.data.repository.PlannedTrip
import com.contrail.contrail.core.data.repository.TripRepository
import com.contrail.contrail.core.data.ticket.AirportDirectory
import com.contrail.contrail.core.data.ticket.ImportSource
import com.contrail.contrail.core.data.ticket.TripAssembler
import com.contrail.contrail.core.data.trip.FlightSegment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/** A read flight log, ready to show before anything is saved. */
data class LogPreview(
    val format: LogFormat,
    /** New flights, oldest first. */
    val flights: List<FlightSegment>,
    /** Already in Contrail (same day and route), left out. */
    val duplicates: Int,
    /** Codes not in the airport list; those rows are left out. */
    val unknownAirports: Set<String>,
    /** Rows that couldn't be read at all, plus rows with unknown airports. */
    val skipped: Int,
    /** Trips the flights will be grouped into. */
    val trips: List<PlannedTrip>,
) {
    val estimated: Int get() = flights.count { it.timesEstimated }
    val years: IntRange? get() = flights.takeIf { it.isNotEmpty() }?.let { f -> f.minOf { it.departureLocal.year }..f.maxOf { it.departureLocal.year } }
}

/** Turns log rows into flights: airports from the bundled list, times from the log or estimated. */
object FlightLogResolver {
    /** Gate-to-gate time when a log has none: 30 minutes of taxi plus about 810 km/h in the air. */
    fun estimatedDuration(distanceKm: Double): Duration = Duration.ofMinutes(30 + (distanceKm / 13.5).toLong())

    /** Noon local is the placeholder departure when only the date is known. */
    private val PLACEHOLDER: LocalTime = LocalTime.NOON

    fun resolve(f: LoggedFlight, airports: AirportDirectory, icaoToIata: (String) -> String?): FlightSegment? {
        val fromCode = f.originIata?.takeIf { airports.find(it) != null } ?: f.originIcao?.let(icaoToIata) ?: return null
        val toCode = f.destinationIata?.takeIf { airports.find(it) != null } ?: f.destinationIcao?.let(icaoToIata) ?: return null
        val from = airports.find(fromCode) ?: return null
        val to = airports.find(toCode) ?: return null
        val distanceKm = Geo.distanceM(from.lat, from.lon, to.lat, to.lon) / 1000

        var estimated = false
        val departure: LocalDateTime = f.departure ?: run { estimated = true; f.date.atTime(PLACEHOLDER) }
        val depInstant = departure.atZone(from.zone).toInstant()
        val arrival: LocalDateTime = when {
            !estimated && f.arrival != null -> f.arrival
            f.duration != null -> depInstant.plus(f.duration).atZone(to.zone).toLocalDateTime()
            !estimated && f.arrivalTime != null ->
                LocalDateTime.of(TripAssembler.arrivalDate(departure.toLocalDate(), departure.toLocalTime(), f.arrivalTime, from.zone, to.zone), f.arrivalTime)
            else -> {
                estimated = true
                depInstant.plus(estimatedDuration(distanceKm)).atZone(to.zone).toLocalDateTime()
            }
        }
        return FlightSegment(
            id = 0, tripId = 0, ordinal = 0,
            airlineIata = f.airlineIata ?: f.airlineIcao ?: "",
            flightNumber = f.flightNumber.orEmpty(),
            origin = from.iata, destination = to.iata,
            departureLocal = departure, arrivalLocal = arrival,
            departureZone = from.zone, arrivalZone = to.zone,
            departureTerminal = f.departureTerminal, arrivalTerminal = f.arrivalTerminal, gate = f.gate,
            cabin = f.cabin, seat = f.seat, aircraftType = f.aircraftType, tail = f.registration,
            airlineName = f.airlineName, seatType = f.seatType, reason = f.reason, note = f.note,
            timesEstimated = estimated,
        )
    }

    /** "2026-10-12|YYZ|HKG": the same route on the same local day is the same flight. */
    fun key(s: FlightSegment): String = "${s.departureLocal.toLocalDate()}|${s.origin}|${s.destination}"

    fun preview(log: ParsedLog, airports: AirportDirectory, icaoToIata: (String) -> String?, existing: Set<String>): LogPreview {
        val unknown = LinkedHashSet<String>()
        var skipped = log.skippedRows
        val seen = HashSet(existing)
        var duplicates = 0
        val flights = ArrayList<FlightSegment>()
        for (f in log.flights) {
            val s = resolve(f, airports, icaoToIata)
            if (s == null) {
                skipped++
                listOf(f.originIata ?: f.originIcao, f.destinationIata ?: f.destinationIcao).forEach { c ->
                    if (c != null && airports.find(c) == null && icaoToIata(c) == null) unknown += c
                }
                continue
            }
            if (!seen.add(key(s))) { duplicates++; continue }
            flights += s
        }
        flights.sortBy { it.departure }
        val splits = TripAssembler.autoSplits(flights, airports)
        val trips = TripAssembler.group(flights, splits).map { g -> PlannedTrip(TripAssembler.title(g, airports), g) }
        return LogPreview(log.format, flights, duplicates, unknown, skipped, trips)
    }
}

@Singleton
class FlightLogImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val airports: AirportRepository,
    private val trips: TripRepository,
) {
    suspend fun read(uri: Uri): LogPreview = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("Couldn't open that file")
        read(text)
    }

    suspend fun read(text: String): LogPreview = withContext(Dispatchers.Default) {
        val log = FlightLogParser.parse(text)
        val dir = airports.directory()
        val icao = airports.icaoResolver()
        FlightLogResolver.preview(log, dir, icao, trips.flightKeys())
    }

    suspend fun save(preview: LogPreview): List<Long> =
        trips.save(preview.trips, pnr = null, passengerName = null, sources = setOf(ImportSource.FlightLog))
}

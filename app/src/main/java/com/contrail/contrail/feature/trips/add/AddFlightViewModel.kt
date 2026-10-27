package com.contrail.contrail.feature.trips.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.data.logbook.FlightLogResolver
import com.contrail.contrail.core.data.logbook.LoggedFlight
import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.core.data.repository.AircraftDetailsRepository
import com.contrail.contrail.core.data.repository.AirportRepository
import com.contrail.contrail.core.data.repository.TripRepository
import com.contrail.contrail.core.data.ticket.AirportDirectory
import com.contrail.contrail.core.data.ticket.AirportInfo
import com.contrail.contrail.core.data.ticket.TripAssembler
import com.contrail.contrail.core.data.trip.Cabin
import com.contrail.contrail.core.data.trip.FlightReason
import com.contrail.contrail.core.data.trip.FlightSegment
import com.contrail.contrail.core.data.trip.SeatType
import com.contrail.contrail.feature.trips.importing.SegmentForm
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class AirportField { From, To }

data class AirportSuggestion(val iata: String, val name: String, val city: String, val countryIso: String)

/** Every field as you typed it, plus what Contrail worked out from it. */
data class AddFlightState(
    val editingId: Long = 0,
    val loading: Boolean = true,
    val flight: String = "",
    val date: LocalDate? = null,
    val from: String = "",
    val to: String = "",
    val departure: String = "",
    val arrival: String = "",
    val aircraft: String = "",
    val tail: String = "",
    val seat: String = "",
    val seatType: SeatType? = null,
    val cabin: Cabin? = null,
    val reason: FlightReason? = null,
    val note: String = "",
    val departureTerminal: String = "",
    val arrivalTerminal: String = "",
    /** Set when the route came from the callsign database, not from you. */
    val routeFromCallsign: Boolean = false,
    val lookingUp: Boolean = false,
    val activeAirport: AirportField? = null,
    val suggestions: List<AirportSuggestion> = emptyList(),
    /** The flight as it will be saved, once the route and date are known. */
    val preview: FlightSegment? = null,
    val places: Map<String, AirportInfo> = emptyMap(),
    val distanceKm: Int? = null,
    val today: LocalDate = LocalDate.now(),
    val savedSegmentId: Long? = null,
    val deleted: Boolean = false,
) {
    val airlineIata: String? get() = parseFlight(flight)?.first
    val canSave: Boolean get() = preview != null

    companion object {
        private val FLIGHT = Regex("^([A-Z][A-Z0-9]|[0-9][A-Z])\\s?0*(\\d{1,4}[A-Z]?)?$")

        /** "EY140", "EY 140" or just "EY" → airline and number. */
        fun parseFlight(raw: String): Pair<String, String>? =
            FLIGHT.matchEntire(raw.trim().uppercase())?.let { it.groupValues[1] to it.groupValues[2] }
    }
}

@HiltViewModel(assistedFactory = AddFlightViewModel.Factory::class)
class AddFlightViewModel @AssistedInject constructor(
    @Assisted private val segmentId: Long,
    private val trips: TripRepository,
    private val airports: AirportRepository,
    private val details: AircraftDetailsRepository,
    private val clock: Clock,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(segmentId: Long): AddFlightViewModel
    }

    private val _state = MutableStateFlow(AddFlightState(editingId = segmentId, today = Instant.ofEpochMilli(clock.nowMs()).atZone(ZoneId.systemDefault()).toLocalDate()))
    val state: StateFlow<AddFlightState> = _state.asStateFlow()
    private var directory: AirportDirectory? = null
    private var routeLookup: Job? = null
    private var search: Job? = null

    init {
        viewModelScope.launch {
            directory = airports.directory()
            if (segmentId != 0L) {
                trips.segment(segmentId).first()?.second?.let { s -> _state.update { it.fill(s) } }
            }
            _state.update { it.copy(loading = false) }
            recompute()
        }
    }

    private fun AddFlightState.fill(s: FlightSegment) = copy(
        flight = s.designator,
        date = s.departureLocal.toLocalDate(),
        from = s.origin,
        to = s.destination,
        departure = if (s.timesEstimated) "" else TIME.format(s.departureLocal),
        arrival = if (s.timesEstimated) "" else TIME.format(s.arrivalLocal),
        aircraft = s.aircraftType.orEmpty(),
        tail = s.tail.orEmpty(),
        seat = s.seat.orEmpty(),
        seatType = s.seatType,
        cabin = s.cabin,
        reason = s.reason,
        note = s.note.orEmpty(),
        departureTerminal = s.departureTerminal.orEmpty(),
        arrivalTerminal = s.arrivalTerminal.orEmpty(),
    )

    fun edit(change: (AddFlightState) -> AddFlightState) {
        val before = _state.value
        val after = change(before)
        _state.update { after }
        recompute()
        if (after.flight != before.flight) lookUpRoute()
    }

    fun setAirport(field: AirportField, text: String) {
        val v = text.take(40)
        edit { if (field == AirportField.From) it.copy(from = v, routeFromCallsign = false) else it.copy(to = v, routeFromCallsign = false) }
        _state.update { it.copy(activeAirport = field) }
        searchAirports(v)
    }

    fun pickAirport(field: AirportField, iata: String) {
        edit { if (field == AirportField.From) it.copy(from = iata) else it.copy(to = iata) }
        _state.update { it.copy(activeAirport = null, suggestions = emptyList()) }
    }

    fun swap() = edit { it.copy(from = it.to, to = it.from, departureTerminal = it.arrivalTerminal, arrivalTerminal = it.departureTerminal) }

    private fun searchAirports(q: String) {
        search?.cancel()
        val query = q.trim()
        if (query.length < 2 || (query.length == 3 && directory?.find(query) != null)) {
            _state.update { it.copy(suggestions = emptyList()) }
            return
        }
        search = viewModelScope.launch {
            delay(150)
            val found = airports.search(query).first()
                .filter { it.iata.isNotBlank() }
                .sortedByDescending { when (it.size) { "L" -> 2; "M" -> 1; else -> 0 } }
                .take(5)
                .map { AirportSuggestion(it.iata, it.name, it.city.substringBefore(" ("), it.countryIso) }
            _state.update { it.copy(suggestions = found) }
        }
    }

    /** A flight number with no route yet fills it in from the callsign database. */
    private fun lookUpRoute() {
        routeLookup?.cancel()
        routeLookup = viewModelScope.launch {
            delay(600)
            val s = _state.value
            if (s.from.isNotBlank() && s.to.isNotBlank() && !s.routeFromCallsign) return@launch
            val (airline, number) = AddFlightState.parseFlight(s.flight) ?: return@launch
            if (number.isEmpty()) return@launch
            val callsign = Airlines.callsignFor(airline, number) ?: return@launch
            _state.update { it.copy(lookingUp = true) }
            runCatching { details.refreshRoute(callsign) }
            val route = details.observeRoute(callsign).first()
            _state.update { it.copy(lookingUp = false) }
            val from = route?.origin?.iata ?: return@launch
            val to = route.destination.iata ?: return@launch
            edit { it.copy(from = from, to = to, routeFromCallsign = true) }
        }
    }

    private fun recompute() {
        val dir = directory ?: return
        val s = _state.value
        val codes = listOf(s.from, s.to).map { it.trim().uppercase() }.filter { it.length == 3 }
        val places = codes.mapNotNull { c -> dir.find(c)?.let { c to it } }.toMap()
        val a = places[s.from.trim().uppercase()]
        val b = places[s.to.trim().uppercase()]
        val distance = if (a != null && b != null) com.contrail.contrail.core.common.Geo.distanceM(a.lat, a.lon, b.lat, b.lon) / 1000 else null
        val preview = if (a != null && b != null && s.date != null && a.iata != b.iata) {
            val (airline, number) = AddFlightState.parseFlight(s.flight) ?: ("" to "")
            val dep = SegmentForm.parseTime(s.departure)
            val arr = SegmentForm.parseTime(s.arrival)
            val known = Airlines.byIata(airline)
            FlightLogResolver.resolve(
                LoggedFlight(
                    row = 0, date = s.date,
                    airlineIata = airline.ifEmpty { null }, airlineName = known?.name, flightNumber = number.ifEmpty { null },
                    originIata = a.iata, destinationIata = b.iata,
                    departure = dep?.let { s.date.atTime(it) },
                    arrivalTime = arr.takeIf { dep != null },
                    aircraftType = s.aircraft.trim().uppercase().ifEmpty { null },
                    registration = s.tail.trim().uppercase().ifEmpty { null },
                    seat = s.seat.trim().uppercase().ifEmpty { null }, seatType = s.seatType, cabin = s.cabin, reason = s.reason,
                    note = s.note.trim().ifEmpty { null },
                    departureTerminal = s.departureTerminal.trim().uppercase().ifEmpty { null },
                    arrivalTerminal = s.arrivalTerminal.trim().uppercase().ifEmpty { null },
                ),
                dir,
            ) { null }
        } else {
            null
        }
        _state.update { it.copy(preview = preview, places = places, distanceKm = distance?.toInt()) }
    }

    fun save() {
        val p = _state.value.preview ?: return
        val dir = directory ?: return
        viewModelScope.launch {
            val id = trips.saveFlight(p, TripAssembler.title(listOf(p), dir), segmentId)
            _state.update { it.copy(savedSegmentId = id) }
        }
    }

    fun delete() {
        if (segmentId == 0L) return
        viewModelScope.launch {
            trips.deleteFlight(segmentId)
            _state.update { it.copy(deleted = true) }
        }
    }

    companion object {
        private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)
    }
}

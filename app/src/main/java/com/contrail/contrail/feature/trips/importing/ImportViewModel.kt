package com.contrail.contrail.feature.trips.importing

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.core.data.repository.AircraftDetailsRepository
import com.contrail.contrail.core.data.repository.AirportRepository
import com.contrail.contrail.core.data.repository.PlannedTrip
import com.contrail.contrail.core.data.repository.TripRepository
import com.contrail.contrail.core.data.ticket.AirportDirectory
import com.contrail.contrail.core.data.ticket.ImportSource
import com.contrail.contrail.core.data.ticket.SegmentDraft
import com.contrail.contrail.core.data.ticket.SharedTicket
import com.contrail.contrail.core.data.ticket.SharedTicketInbox
import com.contrail.contrail.core.data.ticket.TicketDraft
import com.contrail.contrail.core.data.ticket.TicketImporter
import com.contrail.contrail.core.data.ticket.TicketText
import com.contrail.contrail.core.data.ticket.TripAssembler
import com.contrail.contrail.core.data.trip.Cabin
import com.contrail.contrail.core.data.trip.Connection
import com.contrail.contrail.core.data.trip.ConnectionPref
import com.contrail.contrail.core.data.trip.ConnectionRules
import com.contrail.contrail.core.data.trip.FlightSegment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

enum class ImportStage { Choose, Paste, Scan, Working, Review }

/** One segment as editable text, exactly as you see it on the review screen. */
data class SegmentForm(
    val flight: String = "",
    val from: String = "",
    val to: String = "",
    val date: String = "",
    val departure: String = "",
    val arrival: String = "",
    /** Only set when the ticket printed it; otherwise worked out from time zones. */
    val arrivalDate: String = "",
    val departureTerminal: String = "",
    val arrivalTerminal: String = "",
    val gate: String = "",
    val seat: String = "",
    val fareClass: String = "",
    val cabin: Cabin? = null,
    val aircraft: String = "",
    val operatedBy: String = "",
    val hotelAfter: Boolean = false,
) {
    fun toDraft(): SegmentDraft {
        val m = FLIGHT.find(flight.trim().uppercase())
        return SegmentDraft(
            airlineIata = m?.groupValues?.get(1),
            flightNumber = m?.groupValues?.get(2)?.trimStart('0')?.ifEmpty { null },
            origin = from.trim().uppercase().takeIf { it.length == 3 },
            destination = to.trim().uppercase().takeIf { it.length == 3 },
            date = parseDate(date),
            departureTime = parseTime(departure),
            arrivalTime = parseTime(arrival),
            arrivalDate = parseDate(arrivalDate),
            departureTerminal = departureTerminal.trim().uppercase().ifEmpty { null },
            arrivalTerminal = arrivalTerminal.trim().uppercase().ifEmpty { null },
            gate = gate.trim().uppercase().ifEmpty { null },
            cabin = cabin,
            fareClass = fareClass.trim().uppercase().take(1).ifEmpty { null },
            seat = seat.trim().uppercase().ifEmpty { null },
            aircraftType = aircraft.trim().uppercase().ifEmpty { null },
            operatedBy = operatedBy.trim().ifEmpty { null },
            hotelAfter = hotelAfter,
        )
    }

    companion object {
        private val DAY_MONTH_YEAR = Regex("^(\\d{1,2})\\s*([A-Za-z]{3,9})\\.?\\s*(\\d{4})$")
        private val FLIGHT = Regex("^([A-Z][A-Z0-9]|[0-9][A-Z])\\s?0*(\\d{1,4}[A-Z]?)$")
        private val dateOut = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)
        private val timeOut = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)

        fun from(d: SegmentDraft) = SegmentForm(
            flight = listOfNotNull(d.airlineIata, d.flightNumber).joinToString(" "),
            from = d.origin.orEmpty(),
            to = d.destination.orEmpty(),
            date = d.date?.let(dateOut::format).orEmpty(),
            departure = d.departureTime?.let(timeOut::format).orEmpty(),
            arrival = d.arrivalTime?.let(timeOut::format).orEmpty(),
            arrivalDate = d.arrivalDate?.let(dateOut::format).orEmpty(),
            departureTerminal = d.departureTerminal.orEmpty(),
            arrivalTerminal = d.arrivalTerminal.orEmpty(),
            gate = d.gate.orEmpty(),
            seat = d.seat.orEmpty(),
            fareClass = d.fareClass.orEmpty(),
            cabin = d.cabin,
            aircraft = d.aircraftType.orEmpty(),
            operatedBy = d.operatedBy.orEmpty(),
            hotelAfter = d.hotelAfter,
        )

        /** "12 Dec 2026", "2026-12-12", "12/12/2026" or "20261212". */
        fun parseDate(raw: String): LocalDate? {
            val s = raw.trim()
            if (s.isEmpty()) return null
            runCatching { return LocalDate.parse(s) }
            // "12 Dec 2026", any case, "Sep" or "Sept".
            DAY_MONTH_YEAR.matchEntire(s)?.let { m ->
                val month = TicketText.month(m.groupValues[2])
                if (month != null) runCatching { return LocalDate.of(m.groupValues[3].toInt(), month, m.groupValues[1].toInt()) }
            }
            runCatching { return LocalDate.parse(s, DateTimeFormatter.ofPattern("d/M/yyyy")) }
            if (s.length == 8 && s.all { it.isDigit() }) runCatching { return LocalDate.parse(s, DateTimeFormatter.BASIC_ISO_DATE) }
            return null
        }

        /** "22:10" or "2210". */
        fun parseTime(raw: String): LocalTime? {
            val digits = raw.filter { it.isDigit() }
            if (digits.length !in 3..4) return null
            val h = digits.dropLast(2).toInt()
            val m = digits.takeLast(2).toInt()
            return if (h in 0..23 && m in 0..59) LocalTime.of(h, m) else null
        }
    }
}

/** What the reviewed segments become: resolved flights, trip groups and their connections. */
data class ReviewPreview(
    val resolved: List<FlightSegment?>,
    val splits: Set<Int>,
    val connections: Map<Int, Connection>,
    val titles: List<String>,
    val unknownAirports: Set<String>,
) {
    val ready: Boolean get() = resolved.isNotEmpty() && resolved.all { it != null }
}

data class ImportUiState(
    val stage: ImportStage = ImportStage.Choose,
    val working: String? = null,
    val pasted: String = "",
    val pnr: String = "",
    val passenger: String = "",
    val segments: List<SegmentForm> = emptyList(),
    /** Your trip splits; null means the automatic ones. */
    val splits: Set<Int>? = null,
    val sources: Set<ImportSource> = emptySet(),
    val preview: ReviewPreview? = null,
    val message: String? = null,
    val routeHint: Int? = null,
    val savedTripId: Long? = null,
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importer: TicketImporter,
    private val trips: TripRepository,
    private val airports: AirportRepository,
    private val details: AircraftDetailsRepository,
    private val inbox: SharedTicketInbox,
) : ViewModel() {
    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()
    private var directory: AirportDirectory? = null
    private var routeLookup: Job? = null

    init {
        viewModelScope.launch {
            directory = airports.directory()
            // Whatever was shared to Contrail, now or while this screen is open.
            inbox.shared.collect { if (it != null) inbox.take()?.let { t -> readShared(t) } }
        }
    }

    fun choose(stage: ImportStage) {
        if (stage == ImportStage.Review) {
            _state.update { it.copy(stage = ImportStage.Review, segments = listOf(SegmentForm()), sources = setOf(ImportSource.Manual), message = null) }
            recompute()
        } else {
            _state.update { it.copy(stage = stage, message = null) }
        }
    }

    fun back(): Boolean {
        val s = _state.value
        if (s.stage == ImportStage.Choose || s.stage == ImportStage.Working) return false
        _state.update { it.copy(stage = ImportStage.Choose, message = null) }
        return true
    }

    fun setPasted(text: String) = _state.update { it.copy(pasted = text) }

    fun readPasted() {
        val text = _state.value.pasted
        if (text.isBlank()) return
        work("Reading the text") { importer.fromText(text) }
    }

    fun readUri(uri: Uri, mime: String?) {
        val pdf = mime == "application/pdf"
        work(if (pdf) "Reading your PDF" else "Reading the image") { importer.fromUri(uri, mime) }
    }

    /** A boarding-pass barcode from the live scanner. Returns false when it isn't one. */
    fun onBarcode(raw: String): Boolean {
        val draft = importer.fromBarcode(raw) ?: return false
        show(draft)
        return true
    }

    private fun readShared(t: SharedTicket) {
        when {
            t.uris.isNotEmpty() -> work("Reading what you shared") {
                t.uris.fold(TicketDraft()) { acc, uri -> acc.merge(importer.fromUri(uri, t.mime)) }
            }
            !t.text.isNullOrBlank() -> work("Reading what you shared") { importer.fromText(t.text) }
        }
    }

    private fun work(label: String, block: suspend () -> TicketDraft) {
        _state.update { it.copy(stage = ImportStage.Working, working = label, message = null) }
        viewModelScope.launch {
            val result = runCatching { block() }
            val draft = result.getOrNull()
            if (draft == null || draft.segments.isEmpty()) {
                _state.update {
                    it.copy(
                        stage = ImportStage.Choose,
                        message = if (result.isFailure) "Couldn't read that file. Try a screenshot, or paste the text."
                        else "No flights found in that. You can enter them by hand.",
                    )
                }
            } else {
                show(draft)
            }
        }
    }

    private fun show(d: TicketDraft) {
        _state.update {
            it.copy(
                stage = ImportStage.Review,
                pnr = d.pnr.orEmpty(),
                passenger = d.passengerName.orEmpty(),
                segments = d.segments.map(SegmentForm::from),
                sources = d.sources,
                splits = null,
                message = null,
            )
        }
        recompute()
    }

    fun edit(index: Int, change: (SegmentForm) -> SegmentForm) {
        val before = _state.value.segments.getOrNull(index) ?: return
        val after = change(before)
        _state.update { s -> s.copy(segments = s.segments.toMutableList().also { it[index] = after }) }
        recompute()
        if (after.flight != before.flight) lookUpRoute(index)
    }

    fun setPnr(v: String) = _state.update { it.copy(pnr = v.uppercase().take(8)) }
    fun setPassenger(v: String) = _state.update { it.copy(passenger = v) }

    fun addSegment() {
        val last = _state.value.segments.lastOrNull()
        _state.update { s -> s.copy(segments = s.segments + SegmentForm(from = last?.to.orEmpty(), date = last?.arrivalDate?.ifEmpty { last.date }.orEmpty())) }
        recompute()
    }

    fun removeSegment(index: Int) {
        _state.update { s -> s.copy(segments = s.segments.filterIndexed { i, _ -> i != index }, splits = null) }
        recompute()
    }

    /** Starts (or stops) a new trip before segment [index]. */
    fun toggleSplit(index: Int) {
        val current = _state.value.splits ?: _state.value.preview?.splits.orEmpty()
        _state.update { it.copy(splits = if (index in current) current - index else current + index) }
        recompute()
    }

    /** Typing a flight number with no route fills the route in from the callsign database. */
    private fun lookUpRoute(index: Int) {
        routeLookup?.cancel()
        routeLookup = viewModelScope.launch {
            delay(700)
            val form = _state.value.segments.getOrNull(index) ?: return@launch
            if (form.from.isNotBlank() && form.to.isNotBlank()) return@launch
            val d = form.toDraft()
            val callsign = Airlines.callsignFor(d.airlineIata ?: return@launch, d.flightNumber ?: return@launch) ?: return@launch
            runCatching { details.refreshRoute(callsign) }
            val route = details.observeRoute(callsign).first() ?: return@launch
            val from = route.origin.iata ?: return@launch
            val to = route.destination.iata ?: return@launch
            _state.update { s ->
                val cur = s.segments.getOrNull(index) ?: return@update s
                s.copy(
                    segments = s.segments.toMutableList().also { it[index] = cur.copy(from = cur.from.ifBlank { from }, to = cur.to.ifBlank { to }) },
                    routeHint = index,
                )
            }
            recompute()
        }
    }

    private fun recompute() {
        val dir = directory ?: return
        val s = _state.value
        val drafts = s.segments.map { it.toDraft() }
        val resolved = drafts.mapIndexed { i, d -> TripAssembler.resolve(d, dir, i)?.copy(id = i.toLong() + 1) }
        val unknown = drafts.flatMap { listOfNotNull(it.origin, it.destination) }.filter { dir.find(it) == null }.toSet()
        val complete = resolved.filterNotNull()
        val splits = s.splits ?: if (complete.size == resolved.size) TripAssembler.autoSplits(complete, dir) else emptySet()
        val connections = HashMap<Int, Connection>()
        for (i in 0 until resolved.size - 1) {
            if (i + 1 in splits) continue
            val a = resolved[i] ?: continue
            val b = resolved[i + 1] ?: continue
            ConnectionRules.connections(listOf(a, b), mapOf(a.id to ConnectionPref(a.id, null, drafts[i].hotelAfter))).firstOrNull()?.let { connections[i] = it }
        }
        val titles = if (complete.size == resolved.size && complete.isNotEmpty()) {
            TripAssembler.group(complete, splits).map { TripAssembler.title(it, dir) }
        } else {
            emptyList()
        }
        _state.update { it.copy(preview = ReviewPreview(resolved, splits, connections, titles, unknown)) }
    }

    fun save() {
        val s = _state.value
        val p = s.preview
        val dir = directory ?: return
        if (p == null || !p.ready) {
            _state.update { it.copy(message = "Fill in the highlighted fields first.") }
            return
        }
        val segments = p.resolved.filterNotNull().map { it.copy(id = 0) }
        val drafts = s.segments.map { it.toDraft() }
        val groups = TripAssembler.group(segments, p.splits)
        var offset = 0
        val planned = groups.map { g ->
            val hotel = g.indices.filter { drafts[offset + it].hotelAfter }.toSet()
            offset += g.size
            PlannedTrip(TripAssembler.title(g, dir), g, hotel)
        }
        viewModelScope.launch {
            val ids = trips.save(planned, s.pnr.ifBlank { null }, s.passenger.ifBlank { null }, s.sources.ifEmpty { setOf(ImportSource.Manual) })
            _state.update { it.copy(savedTripId = ids.firstOrNull()) }
        }
    }
}

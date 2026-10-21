package com.contrail.contrail.feature.trips.lounge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contrail.contrail.core.data.repository.AirportRepository
import com.contrail.contrail.core.data.repository.Lounge
import com.contrail.contrail.core.data.repository.LoungeAccess
import com.contrail.contrail.core.data.repository.LoungePhase
import com.contrail.contrail.core.data.repository.LoungeRepository
import com.contrail.contrail.core.data.repository.LoungeVisit
import com.contrail.contrail.core.data.repository.TripRepository
import com.contrail.contrail.core.data.trip.Cabin
import com.contrail.contrail.core.data.trip.FlightSegment
import com.contrail.contrail.feature.trips.importing.SegmentForm
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

data class LoungeForm(
    val loungeKey: String? = null,
    val loungeName: String = "",
    val newName: String = "",
    val terminal: String = "",
    val date: String = "",
    val timeIn: String = "",
    val timeOut: String = "",
    val access: LoungeAccess = LoungeAccess.Cabin,
    val rating: Int = 4,
    val note: String = "",
)

data class LoungeUiState(
    val segment: FlightSegment? = null,
    val phase: LoungePhase = LoungePhase.Departure,
    val airport: String = "",
    val city: String? = null,
    val suggestions: List<Lounge> = emptyList(),
    val form: LoungeForm = LoungeForm(),
    val editing: Boolean = false,
    val done: Boolean = false,
) {
    val canSave: Boolean get() = form.loungeName.isNotBlank() && SegmentForm.parseDate(form.date) != null
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = LoungeViewModel.Factory::class)
class LoungeViewModel @AssistedInject constructor(
    @Assisted("segment") private val segmentId: Long,
    @Assisted private val phaseName: String,
    @Assisted("visit") private val visitId: Long,
    trips: TripRepository,
    private val airports: AirportRepository,
    private val lounges: LoungeRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(@Assisted("segment") segmentId: Long, phase: String, @Assisted("visit") visitId: Long): LoungeViewModel
    }

    private val phase = LoungePhase.entries.firstOrNull { it.name == phaseName } ?: LoungePhase.Departure
    private val segment = trips.segment(segmentId).map { it?.second }
    private val form = MutableStateFlow(LoungeForm())
    private val done = MutableStateFlow(false)
    private val city = MutableStateFlow<String?>(null)

    private val airport = segment.filterNotNull().map { if (phase == LoungePhase.Departure) it.origin else it.destination }

    val state: StateFlow<LoungeUiState> = combine(
        segment,
        airport.flatMapLatest { lounges.suggestions(it) },
        form,
        done,
        city,
    ) { s, suggestions, f, d, c ->
        val code = s?.let { if (phase == LoungePhase.Departure) it.origin else it.destination }.orEmpty()
        val term = f.terminal.ifBlank { null }
        LoungeUiState(
            s, phase, code, c,
            // Lounges in your terminal first, then the rest.
            suggestions.sortedBy { l -> if (term != null && l.terminal != null && l.terminal.equals(term, true)) 0 else if (l.terminal == null) 1 else 2 },
            f, visitId != 0L, d,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoungeUiState(phase = phase))

    init {
        viewModelScope.launch {
            val s = segment.filterNotNull().first()
            val code = if (phase == LoungePhase.Departure) s.origin else s.destination
            val info = airports.directory().find(code)
            city.value = info?.city
            info?.let { launch { lounges.refreshOsm(code, it.lat, it.lon) } }
            val existing = if (visitId != 0L) lounges.visit(visitId) else null
            form.value = existing?.let { v ->
                LoungeForm(v.loungeKey, v.loungeName, "", v.terminal.orEmpty(), DATE.format(v.date), v.timeIn?.let(TIME::format).orEmpty(), v.timeOut?.let(TIME::format).orEmpty(), v.access, v.rating, v.note)
            } ?: defaults(s)
        }
    }

    /** Sensible starting times: two hours before departure, or just after landing. */
    private fun defaults(s: FlightSegment): LoungeForm {
        val (date, tIn, tOut, terminal) = if (phase == LoungePhase.Departure) {
            Quad(s.departureLocal.toLocalDate(), s.departureLocal.minusHours(2), s.departureLocal.minusMinutes(40), s.departureTerminal)
        } else {
            Quad(s.arrivalLocal.toLocalDate(), s.arrivalLocal.plusMinutes(20), s.arrivalLocal.plusMinutes(80), s.arrivalTerminal)
        }
        val access = if (s.cabin == Cabin.Business || s.cabin == Cabin.First) LoungeAccess.Cabin else LoungeAccess.Card
        return LoungeForm(terminal = terminal.orEmpty(), date = DATE.format(date), timeIn = TIME.format(tIn), timeOut = TIME.format(tOut), access = access)
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    fun pick(l: Lounge) = form.update { it.copy(loungeKey = l.key, loungeName = l.name, terminal = l.terminal ?: it.terminal) }

    fun edit(change: (LoungeForm) -> LoungeForm) = form.update(change)

    /** Adds a lounge you typed, remembers it for this airport, and selects it. */
    fun addNew() {
        val name = form.value.newName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            val l = lounges.addLounge(state.value.airport, form.value.terminal, name)
            form.update { it.copy(loungeKey = l.key, loungeName = l.name, newName = "") }
        }
    }

    fun toggleFavourite(l: Lounge) {
        viewModelScope.launch { lounges.setFavourite(l, !l.favourite) }
    }

    fun save() {
        val s = state.value
        val f = s.form
        val date = SegmentForm.parseDate(f.date) ?: return
        viewModelScope.launch {
            val key = f.loungeKey ?: lounges.addLounge(s.airport, f.terminal, f.loungeName).key
            lounges.saveVisit(
                LoungeVisit(
                    id = visitId, segmentId = segmentId.takeIf { it != 0L }, phase = phase, loungeKey = key, loungeName = f.loungeName,
                    airport = s.airport, terminal = f.terminal.ifBlank { null }, date = date,
                    timeIn = SegmentForm.parseTime(f.timeIn), timeOut = SegmentForm.parseTime(f.timeOut),
                    access = f.access, rating = f.rating, note = f.note,
                ),
            )
            done.value = true
        }
    }

    fun delete() {
        if (visitId == 0L) return
        viewModelScope.launch {
            lounges.deleteVisit(visitId)
            done.value = true
        }
    }

    private companion object {
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)
    }
}

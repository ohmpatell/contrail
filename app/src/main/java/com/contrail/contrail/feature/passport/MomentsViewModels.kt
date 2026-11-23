package com.contrail.contrail.feature.passport

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.data.hangar.HangarRepository
import com.contrail.contrail.core.data.moments.Moment
import com.contrail.contrail.core.data.moments.MomentKind
import com.contrail.contrail.core.data.moments.MomentRepository
import com.contrail.contrail.core.data.repository.AirportRepository
import com.contrail.contrail.core.data.repository.TripRepository
import com.contrail.contrail.core.data.ticket.AirportInfo
import com.contrail.contrail.core.data.trip.FlightSegment
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Every moment: the ones you wrote, the ones Cabin mode captured and the ones noticed in your
 * logbook, newest first.
 */
class AllMoments @Inject constructor(
    private val moments: MomentRepository,
    private val trips: TripRepository,
    private val airports: AirportRepository,
    private val hangar: HangarRepository,
    private val clock: Clock,
) {
    fun observe() = combine(moments.observeStored(), trips.trips, hangar.observe()) { stored, t, pins ->
        val now = Instant.ofEpochMilli(clock.nowMs())
        val flown = t.flatMap { it.segments }.filter { it.arrival.isBefore(now) }
        val dir = airports.directory()
        val places = flown.flatMap { listOf(it.origin, it.destination) }.toSet().mapNotNull { c -> dir.find(c)?.let { c to it } }.toMap()
        val noticed = NoticedMoments.from(flown, places, pins.map { it.registration }.toSet())
        MomentsData((stored + noticed).sortedWith(compareByDescending<Moment> { it.date }.thenByDescending { it.mine }), flown, places)
    }
}

data class MomentsData(
    val moments: List<Moment> = emptyList(),
    val flown: List<FlightSegment> = emptyList(),
    val places: Map<String, AirportInfo> = emptyMap(),
)

@HiltViewModel
class MomentsViewModel @Inject constructor(all: AllMoments) : ViewModel() {
    val state: StateFlow<MomentsData> = all.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MomentsData())
}

/** A moment being written or edited. */
data class MomentDraft(
    val id: String? = null,
    val kind: MomentKind = MomentKind.Cockpit,
    val title: String = "",
    val note: String = "",
    val date: LocalDate? = null,
    val segmentId: Long? = null,
    val registration: String = "",
    val photo: Uri? = null,
    val existingPhoto: String? = null,
    val recentFlights: List<FlightSegment> = emptyList(),
    val places: Map<String, AirportInfo> = emptyMap(),
    val hangar: List<String> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val saved: Boolean = false,
    val deleted: Boolean = false,
) {
    val canSave: Boolean get() = date != null
}

@HiltViewModel(assistedFactory = AddMomentViewModel.Factory::class)
class AddMomentViewModel @AssistedInject constructor(
    @Assisted("moment") private val momentId: String,
    @Assisted private val segmentId: Long,
    @Assisted("registration") private val registration: String,
    private val moments: MomentRepository,
    private val all: AllMoments,
    hangar: HangarRepository,
    clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(@Assisted("moment") momentId: String, segmentId: Long, @Assisted("registration") registration: String): AddMomentViewModel
    }

    private val today = Instant.ofEpochMilli(clock.nowMs()).atZone(ZoneId.systemDefault()).toLocalDate()
    private val _state = MutableStateFlow(MomentDraft(today = today, date = today, registration = registration))
    val state: StateFlow<MomentDraft> = _state

    init {
        viewModelScope.launch {
            val data = all.observe().first()
            val pins = hangar.observe().first().map { it.registration }
            val recent = data.flown.sortedByDescending { it.departure }.take(20)
            _state.update { it.copy(recentFlights = recent, places = data.places, hangar = pins) }
            if (segmentId != 0L) recent.firstOrNull { it.id == segmentId }?.let { pickFlight(it) }
            if (momentId.isNotEmpty()) {
                data.moments.firstOrNull { it.id == momentId }?.let { m ->
                    _state.update {
                        it.copy(
                            id = m.id, kind = m.kind, title = m.title, note = m.note.orEmpty(), date = m.date,
                            segmentId = m.segmentId, registration = m.registration.orEmpty(), existingPhoto = m.photoPath,
                        )
                    }
                }
            }
        }
    }

    fun edit(change: (MomentDraft) -> MomentDraft) = _state.update(change)

    /** Linking a flight takes its day and its tail. */
    fun pickFlight(s: FlightSegment?) {
        _state.update {
            if (s == null || it.segmentId == s.id) {
                it.copy(segmentId = null)
            } else {
                it.copy(segmentId = s.id, date = s.departureLocal.toLocalDate(), registration = s.tail?.uppercase() ?: it.registration)
            }
        }
    }

    fun save() {
        val d = _state.value
        val date = d.date ?: return
        viewModelScope.launch {
            moments.save(d.id, d.kind, d.title.ifBlank { d.kind.label }, d.note, date, d.segmentId, d.registration, d.photo)
            _state.update { it.copy(saved = true) }
        }
    }

    fun delete() {
        val id = _state.value.id ?: return
        viewModelScope.launch {
            moments.delete(id)
            _state.update { it.copy(deleted = true) }
        }
    }
}

package com.contrail.contrail.feature.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.data.repository.AirportRepository
import com.contrail.contrail.core.data.repository.TripRepository
import com.contrail.contrail.core.data.ticket.AirportInfo
import com.contrail.contrail.core.data.trip.FlightSegment
import com.contrail.contrail.core.data.trip.Trip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject

data class TripsUiState(
    val loading: Boolean = true,
    val upcoming: List<Trip> = emptyList(),
    val past: List<Trip> = emptyList(),
    /** The next flight that hasn't landed, and its trip. */
    val next: Pair<Trip, FlightSegment>? = null,
    val places: Map<String, AirportInfo> = emptyMap(),
) {
    companion object {
        fun from(trips: List<Trip>, now: Instant, places: Map<String, AirportInfo>): TripsUiState {
            val (past, upcoming) = trips.partition { it.isPast(now) }
            val next = upcoming.firstNotNullOfOrNull { t -> t.nextSegment(now)?.let { t to it } }
            return TripsUiState(false, upcoming, past.sortedByDescending { it.start }, next, places)
        }
    }
}

@HiltViewModel
class TripsViewModel @Inject constructor(
    trips: TripRepository,
    private val airports: AirportRepository,
    private val clock: Clock,
) : ViewModel() {
    val state: StateFlow<TripsUiState> = trips.trips.map { list ->
        val dir = airports.directory()
        val codes = list.flatMap { t -> t.segments.flatMap { listOf(it.origin, it.destination) } }.toSet()
        TripsUiState.from(list, Instant.ofEpochMilli(clock.nowMs()), codes.mapNotNull { c -> dir.find(c)?.let { c to it } }.toMap())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripsUiState())
}

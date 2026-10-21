package com.contrail.contrail.feature.passport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.data.repository.AirportRepository
import com.contrail.contrail.core.data.repository.LoungeRepository
import com.contrail.contrail.core.data.repository.TripRepository
import com.contrail.contrail.core.data.ticket.AirportInfo
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject

data class PassportUiState(
    val loading: Boolean = true,
    val stats: PassportStats = PassportStats(),
    val places: Map<String, AirportInfo> = emptyMap(),
    val upcomingFlights: Int = 0,
)

private fun passportFlow(
    trips: TripRepository,
    lounges: LoungeRepository,
    airports: AirportRepository,
    clock: Clock,
    year: Int?,
): Flow<PassportUiState> = combine(trips.trips, lounges.visits, lounges.stored) { t, visits, stored ->
    val dir = airports.directory()
    val codes = t.flatMap { trip -> trip.segments.flatMap { listOf(it.origin, it.destination) } }.toSet()
    val places = codes.mapNotNull { c -> dir.find(c)?.let { c to it } }.toMap()
    val now = Instant.ofEpochMilli(clock.nowMs())
    PassportUiState(
        loading = false,
        stats = PassportCalculator.compute(t, places, visits, stored.filter { it.favourite }.map { it.key }.toSet(), now, year),
        places = places,
        upcomingFlights = t.sumOf { trip -> trip.segments.count { it.arrival.isAfter(now) } },
    )
}

@HiltViewModel
class PassportViewModel @Inject constructor(
    trips: TripRepository,
    lounges: LoungeRepository,
    airports: AirportRepository,
    clock: Clock,
) : ViewModel() {
    val state: StateFlow<PassportUiState> = passportFlow(trips, lounges, airports, clock, null)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PassportUiState())
}

@HiltViewModel(assistedFactory = YearInReviewViewModel.Factory::class)
class YearInReviewViewModel @AssistedInject constructor(
    @Assisted val year: Int,
    trips: TripRepository,
    lounges: LoungeRepository,
    airports: AirportRepository,
    clock: Clock,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(year: Int): YearInReviewViewModel
    }

    val state: StateFlow<PassportUiState> = passportFlow(trips, lounges, airports, clock, year)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PassportUiState())
}

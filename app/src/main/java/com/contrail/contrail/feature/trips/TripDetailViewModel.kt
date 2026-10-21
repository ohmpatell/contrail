package com.contrail.contrail.feature.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contrail.contrail.core.data.repository.AirportInfoRepository
import com.contrail.contrail.core.data.repository.AirportRepository
import com.contrail.contrail.core.data.repository.AirportWeather
import com.contrail.contrail.core.data.repository.LoungeRepository
import com.contrail.contrail.core.data.repository.LoungeVisit
import com.contrail.contrail.core.data.repository.TripRepository
import com.contrail.contrail.core.data.ticket.AirportInfo
import com.contrail.contrail.core.data.trip.Connection
import com.contrail.contrail.core.data.trip.ConnectionKind
import com.contrail.contrail.core.data.trip.ConnectionRules
import com.contrail.contrail.core.data.trip.Trip
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TripDetailUiState(
    val loading: Boolean = true,
    val trip: Trip? = null,
    val places: Map<String, AirportInfo> = emptyMap(),
    /** Lounge visits for this trip's flights, by segment id. */
    val lounges: Map<Long, List<LoungeVisit>> = emptyMap(),
    /** Weather at stopover airports, by IATA code. */
    val weather: Map<String, AirportWeather> = emptyMap(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = TripDetailViewModel.Factory::class)
class TripDetailViewModel @AssistedInject constructor(
    @Assisted private val tripId: Long,
    private val trips: TripRepository,
    private val airports: AirportRepository,
    private val info: AirportInfoRepository,
    loungeRepo: LoungeRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(tripId: Long): TripDetailViewModel
    }

    private val trip = trips.trip(tripId)

    private val places = trip.map { t ->
        val dir = airports.directory()
        t?.segments?.flatMap { listOf(it.origin, it.destination) }?.toSet().orEmpty().mapNotNull { c -> dir.find(c)?.let { c to it } }.toMap()
    }

    private val stopovers = combine(trip, places) { t, p ->
        t?.connections?.filter { it.kind == ConnectionKind.Stopover }?.mapNotNull { c -> p[c.airport]?.let { c.airport to it.icao } }.orEmpty()
    }.distinctUntilChanged()

    private val weather = stopovers.flatMapLatest { list ->
        if (list.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(list.map { (iata, icao) -> info.observeWeather(icao).map { iata to it } }) { pairs ->
                pairs.mapNotNull { (k, v) -> v?.let { k to it } }.toMap()
            }
        }
    }

    val state: StateFlow<TripDetailUiState> = combine(trip, places, loungeRepo.visits, weather) { t, p, visits, wx ->
        val ids = t?.segments?.map { it.id }?.toSet().orEmpty()
        TripDetailUiState(false, t, p, visits.filter { it.segmentId in ids }.groupBy { it.segmentId!! }, wx)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripDetailUiState())

    init {
        viewModelScope.launch {
            stopovers.collect { list -> list.forEach { (_, icao) -> launch { info.refreshWeather(icao) } } }
        }
    }

    /** One tap switches layover ↔ stopover. Switching back to what Contrail guessed returns to automatic. */
    fun toggleKind(c: Connection) {
        val next = if (c.kind == ConnectionKind.Layover) ConnectionKind.Stopover else ConnectionKind.Layover
        val guess = ConnectionRules.guess(c.inbound, c.outbound, c.hotel)
        viewModelScope.launch { trips.setConnectionKind(c.inbound.id, if (next == guess) null else next) }
    }

    fun toggleHotel(c: Connection) {
        viewModelScope.launch { trips.setHotel(c.inbound.id, !c.hotel) }
    }

    fun rename(title: String) {
        viewModelScope.launch { trips.rename(tripId, title) }
    }

    fun delete(done: () -> Unit) {
        viewModelScope.launch {
            trips.delete(tripId)
            done()
        }
    }
}

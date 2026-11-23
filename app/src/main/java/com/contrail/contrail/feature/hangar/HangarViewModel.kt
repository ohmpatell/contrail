package com.contrail.contrail.feature.hangar

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contrail.contrail.core.data.hangar.HangarPlane
import com.contrail.contrail.core.data.hangar.HangarRepository
import com.contrail.contrail.core.data.model.AircraftPhoto
import com.contrail.contrail.core.data.model.FlightRoute
import com.contrail.contrail.core.data.model.LiveAircraft
import com.contrail.contrail.core.data.repository.AircraftDetailsRepository
import com.contrail.contrail.core.data.repository.LiveTrafficRepository
import com.contrail.contrail.core.data.repository.TripRepository
import com.contrail.contrail.core.data.rotation.EarlierLeg
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A pinned plane with what's known right now: live state, its Planespotters photo, its current route claim. */
data class HangarCard(
    val plane: HangarPlane,
    val live: LiveAircraft? = null,
    val photo: AircraftPhoto? = null,
    val route: FlightRoute? = null,
)

data class HangarUiState(val cards: List<HangarCard> = emptyList(), val loading: Boolean = true)

/** Everything on the planes you've pinned. Looks them all up when opened. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HangarViewModel @Inject constructor(
    private val hangar: HangarRepository,
    traffic: LiveTrafficRepository,
    private val details: AircraftDetailsRepository,
) : ViewModel() {
    private val loading = MutableStateFlow(true)

    val state: StateFlow<HangarUiState> = combine(hangar.observe(), traffic.observeTraffic(), loading) { planes, live, l ->
        planes to live to l
    }.flatMapLatest { (pl, l) ->
        val (planes, live) = pl
        if (planes.isEmpty()) return@flatMapLatest flowOf(HangarUiState(emptyList(), l))
        val cards = planes.map { p ->
            val now = live.firstOrNull { it.hex == p.hex || (it.registration != null && it.registration.equals(p.registration, ignoreCase = true)) }
            val photo = p.hex?.let { details.observePhoto(it) } ?: flowOf(null)
            val callsign = now?.callsign?.trim()?.ifEmpty { null }
            val route = callsign?.let { details.observeRoute(it) } ?: flowOf(null)
            combine(photo, route) { ph, r -> HangarCard(p, now, ph, r) }
        }
        combine(cards) { HangarUiState(it.toList(), l) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HangarUiState())

    init {
        viewModelScope.launch {
            runCatching { hangar.refreshLive() }
            loading.value = false
            hangar.observe().map { list -> list.mapNotNull { p -> p.hex?.let { it to p.registration } } }.distinctUntilChanged().collect { list ->
                list.forEach { (hex, reg) -> launch { runCatching { details.refreshPhoto(hex, reg) } } }
            }
        }
    }
}

data class HangarPlaneUiState(
    val card: HangarCard? = null,
    val flights: List<EarlierLeg> = emptyList(),
    val flightsLoading: Boolean = true,
    val flightsError: String? = null,
    val flownOnIt: Int = 0,
    val removed: Boolean = false,
    /** Moments with this plane. */
    val moments: List<com.contrail.contrail.core.data.moments.Moment> = emptyList(),
)

/** One pinned plane: story, your photo, alerts, its last week and your history with it. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = HangarPlaneViewModel.Factory::class)
class HangarPlaneViewModel @AssistedInject constructor(
    @Assisted private val registration: String,
    private val hangar: HangarRepository,
    traffic: LiveTrafficRepository,
    private val details: AircraftDetailsRepository,
    private val trips: TripRepository,
    moments: com.contrail.contrail.feature.passport.AllMoments,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(registration: String): HangarPlaneViewModel
    }

    private val extra = MutableStateFlow(HangarPlaneUiState())

    private val card = combine(hangar.observe(registration), traffic.observeTraffic()) { p, live -> p to live }
        .flatMapLatest { (p, live) ->
            if (p == null) return@flatMapLatest flowOf<HangarCard?>(null)
            val now = live.firstOrNull { it.hex == p.hex || it.registration.equals(p.registration, ignoreCase = true) }
            val photo = p.hex?.let { details.observePhoto(it) } ?: flowOf(null)
            val route = now?.callsign?.trim()?.ifEmpty { null }?.let { details.observeRoute(it) } ?: flowOf(null)
            combine(photo, route) { ph, r -> HangarCard(p, now, ph, r) }
        }

    val state: StateFlow<HangarPlaneUiState> = combine(card, extra, moments.observe()) { c, e, m ->
        e.copy(card = c, moments = m.moments.filter { it.registration.equals(registration, ignoreCase = true) })
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HangarPlaneUiState())

    init {
        viewModelScope.launch {
            runCatching { hangar.refreshLive() }
            val p = hangar.observe(registration).first()
            p?.hex?.let { hex -> launch { runCatching { details.refreshPhoto(hex, registration) } } }
            val flown = runCatching { trips.flightsOnTail(registration) }.getOrDefault(0)
            extra.value = extra.value.copy(flownOnIt = flown)
            if (p != null) {
                val result = runCatching { hangar.recentFlights(p) }
                extra.value = extra.value.copy(
                    flights = result.getOrDefault(emptyList()),
                    flightsLoading = false,
                    flightsError = result.exceptionOrNull()?.let { "Couldn't load its recent flights (${it.message ?: "OpenSky"})." },
                )
            }
        }
    }

    fun setStory(story: String) {
        viewModelScope.launch { hangar.setStory(registration, story) }
    }

    fun setPhoto(uri: Uri) {
        viewModelScope.launch { hangar.setPhoto(registration, uri) }
    }

    fun setAlerts(on: Boolean) {
        viewModelScope.launch { hangar.setAlerts(registration, on) }
    }

    fun remove() {
        viewModelScope.launch {
            hangar.unpin(registration)
            extra.value = extra.value.copy(removed = true)
        }
    }
}

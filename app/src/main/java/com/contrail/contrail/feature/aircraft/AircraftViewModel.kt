package com.contrail.contrail.feature.aircraft

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.data.model.AircraftPhoto
import com.contrail.contrail.core.data.model.FlightRoute
import com.contrail.contrail.core.data.model.LiveAircraft
import com.contrail.contrail.core.data.model.WatchItem
import com.contrail.contrail.core.data.model.WatchKind
import com.contrail.contrail.core.data.model.Watchlist
import com.contrail.contrail.core.data.reference.AircraftTypes
import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.core.data.reference.Rarity
import com.contrail.contrail.core.data.repository.AircraftDetailsRepository
import com.contrail.contrail.core.data.repository.CutoutRepository
import com.contrail.contrail.core.data.repository.LiveTrafficRepository
import com.contrail.contrail.core.data.repository.LogostreamRepository
import com.contrail.contrail.core.data.repository.SightingEntity
import com.contrail.contrail.core.data.repository.SightingRepository
import com.contrail.contrail.core.data.repository.WatchlistRepository
import com.contrail.contrail.core.network.logostream.LsAirline
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** Which floating image the hero shows. */
enum class HeroMode { Livery, Cutout, Photo }

data class AircraftUiState(
    val hex: String,
    val live: LiveAircraft? = null,
    val registration: String? = null,
    val typeCode: String? = null,
    val callsign: String? = null,
    val airlineIcao: String? = null,
    val airline: LsAirline? = null,
    val photo: AircraftPhoto? = null,
    val route: FlightRoute? = null,
    val sightings: List<SightingEntity> = emptyList(),
    val cutout: File? = null,
    val cutoutWorking: Boolean = false,
    val hero: HeroMode = HeroMode.Livery,
    val watchlist: Watchlist = Watchlist(emptyList()),
) {
    val typeName: String? get() = AircraftTypes.nameFor(typeCode)
    val rarity: Rarity get() = AircraftTypes.rarityFor(typeCode, Airlines.byIcao(airlineIcao))
    val airlineName: String? get() = airline?.name ?: Airlines.byIcao(airlineIcao)?.name

    /** Share of the great-circle distance already flown, when live and on a known route. */
    val progress: Float? get() {
        val r = route ?: return null
        val a = live ?: return null
        val total = Geo.distanceM(r.origin.lat, r.origin.lon, r.destination.lat, r.destination.lon)
        val left = Geo.distanceM(a.lat, a.lon, r.destination.lat, r.destination.lon)
        return (1 - left / total).toFloat().coerceIn(0f, 1f).takeIf { total > 0 }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = AircraftViewModel.Factory::class)
class AircraftViewModel @AssistedInject constructor(
    @Assisted private val hex: String,
    traffic: LiveTrafficRepository,
    private val details: AircraftDetailsRepository,
    private val logostream: LogostreamRepository,
    private val sightings: SightingRepository,
    private val cutouts: CutoutRepository,
    private val watchlist: WatchlistRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(hex: String): AircraftViewModel
    }

    private val hero = MutableStateFlow(HeroMode.Livery)
    private val cutout = MutableStateFlow(cutouts.cached(hex))
    private val cutoutWorking = MutableStateFlow(false)

    private val live = traffic.observeTraffic().map { list -> list.firstOrNull { it.hex == hex } }.distinctUntilChanged()
    private val seen = sightings.forAircraft(hex)

    /** Identity from the live feed, else from what we've seen before. */
    private val identity = combine(live, seen) { l, s ->
        val last = s.firstOrNull()
        Identity(
            registration = l?.registration ?: last?.registration,
            typeCode = l?.typeCode ?: last?.typeCode,
            callsign = l?.callsign ?: last?.callsign,
        )
    }.distinctUntilChanged()

    private data class Identity(val registration: String?, val typeCode: String?, val callsign: String?) {
        val airlineIcao: String? get() = Airlines.icaoFromCallsign(callsign)
    }

    private val airline = identity.map { it.airlineIcao }.distinctUntilChanged().flatMapLatest { icao ->
        if (icao == null) flowOf(null) else logostream.observeAirline(icao)
    }
    private val photo = details.observePhoto(hex)
    private val route = identity.map { it.callsign }.distinctUntilChanged().flatMapLatest { cs ->
        if (cs == null) flowOf(null) else details.observeRoute(cs)
    }

    val state: StateFlow<AircraftUiState> = combine(
        combine(live, identity, seen) { l, id, s -> Triple(l, id, s) },
        combine(airline, photo, route) { a, p, r -> Triple(a, p, r) },
        combine(cutout, cutoutWorking, hero) { c, w, h -> Triple(c, w, h) },
        watchlist.observe().map { Watchlist(it) },
    ) { (l, id, s), (a, p, r), (c, w, h), watch ->
        AircraftUiState(
            hex = hex, live = l, registration = id.registration, typeCode = id.typeCode, callsign = id.callsign,
            airlineIcao = id.airlineIcao, airline = a, photo = p, route = r, sightings = s,
            cutout = c, cutoutWorking = w,
            // With no airline to paint, the real aircraft cutout is the better floating image.
            hero = if (h == HeroMode.Livery && id.airlineIcao == null && c != null) HeroMode.Cutout else h,
            watchlist = watch,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AircraftUiState(hex))

    init {
        viewModelScope.launch {
            val id = identity.first()
            launch { runCatching { details.refreshPhoto(hex, id.registration) } }
            id.callsign?.let { cs -> launch { runCatching { details.refreshRoute(cs) } } }
            id.airlineIcao?.let { icao -> launch { logostream.refreshAirline(icao) } }
        }
        // Remember the flight once its route is known.
        viewModelScope.launch {
            combine(identity, route) { id, r -> id to r }.collect { (id, r) ->
                val cs = id.callsign ?: return@collect
                sightings.record(hex, cs, id.registration, id.typeCode, r?.origin?.iata, r?.destination?.iata)
            }
        }
        // Prepare the cutout in the background once there is a photo, so switching is instant.
        viewModelScope.launch { makeCutout() }
    }

    private suspend fun makeCutout() {
        if (cutout.value != null || cutoutWorking.value) return
        val p = photo.filterNotNull().first()
        cutoutWorking.value = true
        cutout.value = runCatching { cutouts.cutout(hex, p.largeUrl) }.getOrNull()
        cutoutWorking.value = false
    }

    fun showHero(mode: HeroMode) {
        hero.value = mode
        // The segmentation model downloads on first use; asking again retries once it's there.
        if (mode == HeroMode.Cutout) viewModelScope.launch { makeCutout() }
    }

    fun toggleWatch(item: WatchItem, on: Boolean) {
        viewModelScope.launch { if (on) watchlist.remove(item.kind, item.value) else watchlist.add(item) }
    }

    companion object {
        fun watchOptions(s: AircraftUiState): List<WatchItem> = buildList {
            s.registration?.let { add(WatchItem(WatchKind.Tail, it, it)) }
            s.typeCode?.let { add(WatchItem(WatchKind.Type, it, s.typeName ?: it)) }
            s.airlineIcao?.let { add(WatchItem(WatchKind.Airline, it, s.airlineName ?: it)) }
        }
    }
}

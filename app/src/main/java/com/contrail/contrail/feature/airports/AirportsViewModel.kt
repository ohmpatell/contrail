package com.contrail.contrail.feature.airports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.airport.RunwayLog
import com.contrail.contrail.core.data.airport.RunwayUse
import com.contrail.contrail.core.data.airport.RunwaysInUse
import com.contrail.contrail.core.data.events.Happening
import com.contrail.contrail.core.data.events.HappeningRepository
import com.contrail.contrail.core.data.hangar.HangarRepository
import com.contrail.contrail.core.data.model.AirportRef
import com.contrail.contrail.core.data.model.FlightRoute
import com.contrail.contrail.core.data.model.LiveAircraft
import com.contrail.contrail.core.data.repository.AircraftDetailsRepository
import com.contrail.contrail.core.data.repository.AirportInfoRepository
import com.contrail.contrail.core.data.repository.AirportRepository
import com.contrail.contrail.core.data.repository.LiveTrafficRepository
import com.contrail.contrail.core.data.route.RouteCheck
import com.contrail.contrail.core.data.spotting.Light
import com.contrail.contrail.core.data.spotting.PhotoLight
import com.contrail.contrail.core.data.spotting.SpotterArrival
import com.contrail.contrail.core.data.spotting.SpotterBoard
import com.contrail.contrail.core.database.AirportWithUser
import com.contrail.contrail.core.network.airport.Metar
import com.contrail.contrail.core.network.airport.windFromDeg
import com.contrail.contrail.core.network.traffic.AircraftLookupSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Which part of Happening now to show. */
enum class HappeningFilter(val label: String) { All("All"), Near("My airports"), World("Worldwide"), Hangar("My Hangar") }

/** One favourite airport, seen through a spotter's eyes. */
data class SpotterBoardState(
    val airport: AirportWithUser,
    val runways: RunwaysInUse? = null,
    val metar: Metar? = null,
    /** The light on the main arrival runway, for photos. */
    val light: PhotoLight? = null,
    val arrivals: List<SpotterArrival> = emptyList(),
    val watching: Boolean = false,
)

data class AirportsUiState(
    val query: String = "",
    val results: List<AirportWithUser> = emptyList(),
    val favourites: List<AirportWithUser> = emptyList(),
    val visited: List<AirportWithUser> = emptyList(),
    val boards: List<SpotterBoardState> = emptyList(),
    val happenings: List<Happening> = emptyList(),
    val filter: HappeningFilter = HappeningFilter.All,
    val nowMs: Long = 0L,
)

/**
 * The Airports tab: search, your airports, Happening now and a Spotter's board for each
 * favourite. While the tab is open, Contrail watches the sky around your favourites (one
 * request per airport every 45 seconds) and asks for the world's emergency squawks every two
 * minutes.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class AirportsViewModel @Inject constructor(
    private val airports: AirportRepository,
    private val info: AirportInfoRepository,
    private val traffic: LiveTrafficRepository,
    private val details: AircraftDetailsRepository,
    private val lookup: AircraftLookupSource,
    private val runwayLog: RunwayLog,
    happenings: HappeningRepository,
    hangar: HangarRepository,
    private val clock: Clock,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(HappeningFilter.All)
    private val active = MutableStateFlow(false)
    /** Routes for planes around your airports, refreshed each look. */
    private val routes = MutableStateFlow<Map<String, FlightRoute?>>(emptyMap())

    private val results = query.debounce(120).flatMapLatest { q ->
        if (q.isBlank()) flowOf(emptyList()) else airports.search(q)
    }

    private val favourites = airports.favourites().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val hangarKeys = hangar.observe().map { list -> list.flatMap { listOfNotNull(it.hex, it.registration) }.toSet() }

    private val boards: Flow<List<SpotterBoardState>> = favourites.map { it.take(MAX_BOARDS) }.distinctUntilChanged().flatMapLatest { favs ->
        if (favs.isEmpty()) return@flatMapLatest flowOf(emptyList())
        val weather = combine(favs.map { f -> info.observeWeather(f.icao).onStart { emit(null) } }) { it.toList() }
        val runwaysFlows = combine(favs.map { f -> airports.runways(f.icao) }) { it.toList() }
        combine(traffic.observeTraffic(), traffic.observeTrails(), routes, combine(weather, runwaysFlows, hangarKeys) { w, r, h -> Triple(w, r, h) }, active) { list, trails, rts, (wx, rws, hk), on ->
            Inputs(list, trails, rts, wx, rws, hk, on)
        }.debounce(1_000).mapLatest { input ->
            withContext(Dispatchers.Default) {
                favs.mapIndexed { i, f -> board(f, input, i) }
            }
        }
    }.onStart { emit(emptyList()) }

    private data class Inputs(
        val traffic: List<LiveAircraft>,
        val trails: Map<String, List<com.contrail.contrail.core.data.model.TrailPoint>>,
        val routes: Map<String, FlightRoute?>,
        val weather: List<com.contrail.contrail.core.data.repository.AirportWeather?>,
        val runways: List<List<com.contrail.contrail.core.database.RunwayEntity>>,
        val hangar: Set<String>,
        val active: Boolean,
    )

    private suspend fun board(f: AirportWithUser, input: Inputs, index: Int): SpotterBoardState {
        val ref = AirportRef(f.iata, f.icao, f.name, f.city, f.countryIso, f.lat, f.lon)
        val around = input.traffic.filter { Geo.distanceM(f.lat, f.lon, it.lat, it.lon) < BOARD_RADIUS_M }
        val metar = input.weather.getOrNull(index)?.metar
        val runways = input.runways.getOrNull(index).orEmpty()
        val near = around.filter { Geo.distanceM(f.lat, f.lon, it.lat, it.lon) < 40_000 }
        val use = runCatching {
            runwayLog.update(f.icao, f.elevationFt, runways, near, input.trails.filterKeys { k -> near.any { it.hex == k } }, metar.windFromDeg, metar?.wspd)
        }.getOrNull()
        val verdicts = around.associate { a ->
            val r = a.callsign?.trim()?.let { input.routes[it] }
            a.hex to RouteCheck.check(r, a, airports.near(LatLon(a.lat, a.lon), 160_000.0))
        }
        val arrivals = SpotterBoard.arrivals(ref, around, verdicts, input.hangar)
        val mainEnd = RunwayUse.ends(runways).firstOrNull { it.ident == use?.arrivals?.firstOrNull() }
        val light = mainEnd?.let { Light.forRunway(it, LatLon(f.lat, f.lon), clock.nowMs()) }
        return SpotterBoardState(f, use, metar, light, arrivals, watching = input.active)
    }

    private val shownHappenings = combine(happenings.observe(), filter) { list, f ->
        when (f) {
            HappeningFilter.All -> list
            HappeningFilter.Near -> list.filter { it.scope == Happening.Scope.Near }
            HappeningFilter.World -> list.filter { it.scope == Happening.Scope.World }
            HappeningFilter.Hangar -> list.filter { it.scope == Happening.Scope.Hangar }
        }
    }

    val state: StateFlow<AirportsUiState> = combine(
        combine(query, results) { q, r -> q to r },
        combine(favourites, airports.visited()) { f, v -> f to v },
        boards,
        combine(shownHappenings, filter) { h, f -> h to f },
    ) { (q, r), (favs, visited), b, (h, f) ->
        AirportsUiState(q, r, favs, visited, b, h, f, clock.nowMs())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AirportsUiState())

    init {
        viewModelScope.launch { airports.ensureImported() }
        viewModelScope.launch { watchLoop() }
        viewModelScope.launch { emergencyLoop() }
    }

    fun setActive(on: Boolean) {
        active.value = on
    }

    fun onQuery(q: String) {
        query.value = q
    }

    fun onFilter(f: HappeningFilter) {
        filter.value = f
    }

    fun toggleFavourite(a: AirportWithUser) {
        viewModelScope.launch { airports.setFavourite(a.icao, a.favourite != true) }
    }

    /** Around each favourite every 45 s while the tab is open: traffic, then routes for planes that might be coming in. */
    private suspend fun watchLoop() {
        combine(active, favourites.map { it.take(MAX_BOARDS) }.distinctUntilChanged()) { a, f -> if (a) f else emptyList() }
            .collectLatest { favs ->
                if (favs.isEmpty()) return@collectLatest
                favs.forEach { f -> launchCatching { info.refreshWeather(f.icao) } }
                while (true) {
                    for (f in favs) {
                        try {
                            traffic.refresh(LatLon(f.lat, f.lon), WATCH_NM)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                        }
                    }
                    refreshRoutes(favs)
                    delay(WATCH_MS)
                }
            }
    }

    private suspend fun refreshRoutes(favs: List<AirportWithUser>) {
        val all = traffic.observeTraffic().first()
        val wanted = all.filter { a -> !a.onGround && favs.any { Geo.distanceM(it.lat, it.lon, a.lat, a.lon) < BOARD_RADIUS_M } }
            .mapNotNull { it.callsign?.trim()?.ifEmpty { null } }.distinct()
        val out = routes.value.toMutableMap()
        // Cached answers are free; new look-ups are capped per pass to stay polite.
        var fetched = 0
        for (cs in wanted) {
            if (cs !in out && fetched < MAX_ROUTE_LOOKUPS) {
                runCatching { details.refreshRoute(cs) }
                fetched++
            }
            out[cs] = details.observeRoute(cs).first()
        }
        routes.value = out.filterKeys { it in wanted }
    }

    /** The world's emergency squawks every two minutes while the tab is open; they reach Happening now through the live data. */
    private suspend fun emergencyLoop() {
        active.collectLatest { on ->
            if (!on) return@collectLatest
            while (true) {
                for (code in listOf("7700", "7600", "7500")) {
                    runCatching { traffic.addFound(lookup.bySquawk(code)) }
                }
                delay(EMERGENCY_MS)
            }
        }
    }

    private fun launchCatching(block: suspend () -> Unit) = viewModelScope.launch { runCatching { block() } }

    companion object {
        const val MAX_BOARDS = 3
        const val WATCH_NM = 150
        const val WATCH_MS = 45_000L
        const val EMERGENCY_MS = 120_000L
        const val BOARD_RADIUS_M = 280_000.0
        const val MAX_ROUTE_LOOKUPS = 25
    }
}

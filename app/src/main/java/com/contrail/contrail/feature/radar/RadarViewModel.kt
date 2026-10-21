package com.contrail.contrail.feature.radar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.data.model.FlightRoute
import com.contrail.contrail.core.data.model.LiveAircraft
import com.contrail.contrail.core.data.model.TrailPoint
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.model.WatchItem
import com.contrail.contrail.core.data.model.WatchKind
import com.contrail.contrail.core.data.model.Watchlist
import com.contrail.contrail.core.data.repository.AircraftDetailsRepository
import com.contrail.contrail.core.data.repository.FlightTrackRepository
import com.contrail.contrail.core.data.repository.WatchlistRepository
import com.contrail.contrail.core.data.repository.LiveTrafficRepository
import com.contrail.contrail.core.data.repository.RadarCamera
import com.contrail.contrail.core.data.repository.RadarLayers
import com.contrail.contrail.core.data.repository.RadarPreferencesRepository
import com.contrail.contrail.core.network.traffic.ReadsbApiSource
import com.contrail.contrail.core.network.weather.WeatherRadarSource
import com.contrail.contrail.core.network.weather.WeatherTiles
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.min

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class RadarViewModel @Inject constructor(
    private val traffic: LiveTrafficRepository,
    private val details: AircraftDetailsRepository,
    private val prefs: RadarPreferencesRepository,
    private val weather: WeatherRadarSource,
    private val tracks: FlightTrackRepository,
    private val watchlist: WatchlistRepository,
    private val clock: Clock,
) : ViewModel() {

    private val viewport = MutableStateFlow<Viewport?>(null)
    private val active = MutableStateFlow(false)
    private val filters = MutableStateFlow(RadarFilters())
    private val selectedHex = MutableStateFlow<String?>(null)
    private val following = MutableStateFlow(false)
    private val panel = MutableStateFlow(RadarPanel.None)
    private val feed = MutableStateFlow<FeedStatus>(FeedStatus.Connecting)
    private var lastSuccessMs: Long? = null
    private var lastRequestMs = 0L

    val layers: StateFlow<RadarLayers> = prefs.layers.stateIn(viewModelScope, SharingStarted.Eagerly, RadarLayers())

    private val allTraffic: StateFlow<List<LiveAircraft>> =
        traffic.observeTraffic().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Everything on the watchlist, as a matcher the filters and map rings share. */
    val watch: StateFlow<Watchlist> = watchlist.observe()
        .map { Watchlist(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Watchlist(emptyList()))

    /** Filtered contacts for the map layers. The selected aircraft always stays visible. */
    val mapTraffic: StateFlow<List<LiveAircraft>> =
        combine(allTraffic, filters, selectedHex, watch) { list, f, sel, w ->
            if (f.isEmpty && f.showGround) list else list.filter { it.hex == sel || f.matches(it, w) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val trails: StateFlow<Map<String, List<TrailPoint>>> =
        combine(traffic.observeTrails(), mapTraffic) { trails, shown ->
            val keep = shown.mapTo(HashSet()) { it.hex }
            trails.filterKeys { it in keep }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val selected: StateFlow<String?> = selectedHex
    val isFollowing: StateFlow<Boolean> = following

    val weatherTiles: StateFlow<WeatherTiles?> = layers
        .map { it.weather }
        .distinctUntilChanged()
        .flatMapLatest { on -> if (on) weatherFrames() else flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val selection: Flow<SelectedAircraft?> = selectedHex.flatMapLatest { hex ->
        if (hex == null) return@flatMapLatest flowOf(null)
        val aircraftFlow = allTraffic.map { list -> list.firstOrNull { it.hex == hex } }.distinctUntilChanged()
        val callsignFlow = aircraftFlow.map { it?.callsign }.distinctUntilChanged()
        val routeFlow = callsignFlow.flatMapLatest { callsign ->
            if (callsign == null) flowOf<Lookup<FlightRoute>>(Lookup.Missing)
            else lookup(details.observeRoute(callsign)) { details.refreshRoute(callsign) }
        }
        val registration = aircraftFlow.map { it?.registration }.distinctUntilChanged()
        val photoFlow = registration.flatMapLatest { reg ->
            lookup(details.observePhoto(hex)) { details.refreshPhoto(hex, reg) }
        }
        val trail = traffic.observeTrail(hex)
        combine(aircraftFlow, routeFlow, photoFlow, trail) { aircraft, route, photo, points ->
            aircraft?.let {
                SelectedAircraft(
                    aircraft = it,
                    route = route,
                    photo = photo,
                    verticalRates = points.mapNotNull { p -> p.verticalRateFpm?.toFloat() },
                    trail = points.map { p -> LatLon(p.lat, p.lon) },
                )
            }
        }
    }

    private val selectionState: StateFlow<SelectedAircraft?> =
        selection.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The flown track for the selected aircraft, refreshed every minute while it is selected. */
    private val selectedTrack: Flow<List<LatLon>?> = selectedHex.flatMapLatest { hex ->
        if (hex == null) {
            flowOf(null)
        } else {
            flow {
                emit(null)
                while (true) {
                    emit(tracks.track(hex)?.points)
                    delay(TRACK_REFRESH_MS)
                }
            }
        }
    }

    /** Whole-route overlay: only when the route is known; otherwise the trail stays as it is. */
    val routeOverlay: StateFlow<RouteOverlay?> = combine(selectionState, selectedTrack) { sel, track ->
        val route = (sel?.route as? Lookup.Found)?.value ?: return@combine null
        val a = sel.aircraft
        RouteOverlay.build(route, track, sel.trail, LatLon(a.lat, a.lon))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val chrome = combine(filters, layers, panel, following, viewport) { f, l, p, follow, vp ->
        Chrome(f, l, p, follow, (vp?.radiusNm ?: 0.0) > ReadsbApiSource.MAX_RADIUS_NM)
    }.combine(watch) { c, w -> c.copy(watch = w) }

    val uiState: StateFlow<RadarUiState> =
        combine(feed, allTraffic, mapTraffic, chrome, selectionState) { feed, all, shown, c, sel ->
            RadarUiState(
                feed = feed,
                shownCount = shown.size,
                totalCount = all.size,
                filters = c.filters,
                facets = FilterFacets.from(all, c.filters),
                layers = c.layers,
                panel = c.panel,
                selected = sel,
                following = c.following && sel != null,
                radiusCapped = c.radiusCapped,
                emergencies = all.filter { it.isEmergency },
                watchlist = c.watch.items,
                watchedCount = if (c.watch.isEmpty) 0 else all.count { c.watch.matches(it) },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RadarUiState())

    private data class Chrome(
        val filters: RadarFilters,
        val layers: RadarLayers,
        val panel: RadarPanel,
        val following: Boolean,
        val radiusCapped: Boolean,
        val watch: Watchlist = Watchlist(emptyList()),
    )

    init {
        viewModelScope.launch { traffic.pruneStale() }
        viewModelScope.launch { pollLoop() }
        // If the selected aircraft drops out of the feed, close its sheet.
        viewModelScope.launch {
            allTraffic.collect { list ->
                val sel = selectedHex.value ?: return@collect
                if (list.isNotEmpty() && list.none { it.hex == sel }) clearSelection()
            }
        }
    }

    suspend fun initialCamera(): RadarCamera = prefs.camera.first()

    // region Events

    fun setActive(isActive: Boolean) {
        active.value = isActive
    }

    fun onViewportChanged(vp: Viewport) {
        viewport.value = vp
        viewModelScope.launch {
            prefs.saveCamera(RadarCamera(vp.center.lat, vp.center.lon, vp.zoom))
        }
    }

    fun onAircraftTapped(hex: String) {
        if (selectedHex.value != hex) following.value = false
        selectedHex.value = hex
        panel.value = RadarPanel.None
    }

    fun clearSelection() {
        selectedHex.value = null
        following.value = false
    }

    fun onMapTapped() {
        if (panel.value != RadarPanel.None) panel.value = RadarPanel.None else clearSelection()
    }

    fun toggleFollow() {
        if (selectedHex.value != null) following.update { !it }
    }

    fun onUserGesture() {
        following.value = false
    }

    fun togglePanel(target: RadarPanel) {
        panel.update { if (it == target) RadarPanel.None else target }
    }

    fun updateFilters(transform: (RadarFilters) -> RadarFilters) = filters.update(transform)

    fun clearFilters() {
        filters.value = RadarFilters()
    }

    fun watch(item: WatchItem) {
        viewModelScope.launch { watchlist.add(item) }
    }

    fun unwatch(kind: WatchKind, value: String) {
        viewModelScope.launch { watchlist.remove(kind, value) }
    }

    fun updateLayers(transform: (RadarLayers) -> RadarLayers) {
        viewModelScope.launch { prefs.saveLayers(transform(layers.value)) }
    }

    // endregion

    /**
     * Polls the live feed while the radar is on screen. A significant pan or zoom restarts
     * the loop immediately; otherwise it refreshes every [POLL_INTERVAL_MS], backing off on errors.
     */
    private suspend fun pollLoop() {
        combine(active, viewport.filterNotNull().debounce(350).distinctUntilChanged(::sameArea)) { a, vp -> if (a) vp else null }
            .distinctUntilChanged()
            .collectLatest { vp ->
                if (vp == null) return@collectLatest
                var failures = 0
                // A pan restarts this loop; never poll more often than MIN_POLL_GAP_MS regardless.
                delay((lastRequestMs + MIN_POLL_GAP_MS - clock.nowMs()).coerceAtLeast(0))
                while (true) {
                    lastRequestMs = clock.nowMs()
                    val current = viewport.value ?: vp
                    val radius = min(current.radiusNm * 1.1, ReadsbApiSource.MAX_RADIUS_NM.toDouble()).toInt().coerceAtLeast(5)
                    try {
                        val result = traffic.refresh(current.center, radius)
                        failures = 0
                        lastSuccessMs = result.receivedAtMs
                        feed.value = FeedStatus.Live(result.source, result.receivedAtMs)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "Live traffic refresh failed", e)
                        failures++
                        feed.value = FeedStatus.Offline(lastSuccessMs)
                        traffic.pruneStale()
                    }
                    delay(if (failures == 0) POLL_INTERVAL_MS else backoff(failures))
                }
            }
    }

    private fun weatherFrames(): Flow<WeatherTiles?> = flow {
        while (true) {
            emit(runCatching { weather.latestTileUrl() }.getOrNull())
            delay(WEATHER_REFRESH_MS)
        }
    }

    private fun <T> lookup(cached: Flow<T?>, refresh: suspend () -> Unit): Flow<Lookup<T>> = channelFlow {
        var refreshing = true
        var latest: T? = null
        var failed = false
        fun current(): Lookup<T> = latest?.let { Lookup.Found(it) } ?: if (refreshing && !failed) Lookup.Loading else Lookup.Missing
        launch {
            try {
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Lookup failed", e)
                failed = true
            }
            refreshing = false
            send(current())
        }
        cached.collect {
            latest = it
            send(current())
        }
    }.distinctUntilChanged()

    companion object {
        private const val TAG = "ContrailRadar"
        /** Polite to adsb.fi (1/s) and to adsb.lol's 500/day backup budget; dead reckoning hides the gap. */
        const val POLL_INTERVAL_MS = 5_000L
        const val MIN_POLL_GAP_MS = 4_000L
        const val WEATHER_REFRESH_MS = 10 * 60_000L
        const val TRACK_REFRESH_MS = 60_000L

        internal fun backoff(failures: Int): Long = min(30_000L, 4_000L * (1L shl (failures - 1).coerceAtMost(3)))

        /** Pans within a fraction of the radius, and small zooms, reuse the current query area. */
        internal fun sameArea(a: Viewport, b: Viewport): Boolean {
            val moved = Geo.distanceM(a.center, b.center) / Geo.METERS_PER_NM
            val radiusRatio = b.radiusNm / a.radiusNm.coerceAtLeast(0.1)
            return moved < a.radiusNm * 0.25 && radiusRatio in 0.75..1.33
        }
    }
}

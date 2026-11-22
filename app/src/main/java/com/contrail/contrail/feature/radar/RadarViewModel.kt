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
import com.contrail.contrail.core.data.repository.LogostreamRepository
import com.contrail.contrail.core.data.reference.Airlines
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
import com.contrail.contrail.core.data.hangar.HangarPlane
import com.contrail.contrail.core.data.hangar.HangarRepository
import com.contrail.contrail.core.data.search.SearchQuery
import com.contrail.contrail.core.data.search.SearchTarget
import com.contrail.contrail.core.network.traffic.AircraftLookupSource
import com.contrail.contrail.core.data.repository.AirportRepository
import com.contrail.contrail.core.data.route.RouteCheck
import com.contrail.contrail.core.data.route.RouteVerdict
import kotlin.math.min

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class RadarViewModel @Inject constructor(
    private val traffic: LiveTrafficRepository,
    private val details: AircraftDetailsRepository,
    private val prefs: RadarPreferencesRepository,
    private val weather: WeatherRadarSource,
    private val tracks: FlightTrackRepository,
    private val logostream: LogostreamRepository,
    private val airports: AirportRepository,
    private val lookup: AircraftLookupSource,
    private val hangarRepo: HangarRepository,
    private val watchlist: WatchlistRepository,
    private val clock: Clock,
) : ViewModel() {

    private val viewport = MutableStateFlow<Viewport?>(null)
    private val active = MutableStateFlow(false)
    private val filters = MutableStateFlow(RadarFilters())
    private val selectedHex = MutableStateFlow<String?>(null)
    private val following = MutableStateFlow(FollowState())
    private val panel = MutableStateFlow(RadarPanel.None)
    private val searchText = MutableStateFlow("")
    private val searchResults = MutableStateFlow(SearchResults())

    /** A camera move the map should make once it's showing (search results, Hangar markers); cleared when done. */
    private val _cameraMove = MutableStateFlow<CameraMove?>(null)
    val cameraMove: StateFlow<CameraMove?> = _cameraMove

    fun cameraMoved() {
        _cameraMove.value = null
    }

    /** Your pinned planes, with where each was last seen. */
    val hangar: StateFlow<List<HangarPlane>> = hangarRepo.observe().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Transponder codes and registrations of pinned planes, for the gold ring on the map. */
    val hangarKeys: StateFlow<Set<String>> = hangar
        .map { list -> list.flatMap { listOfNotNull(it.hex?.lowercase(), it.registration.uppercase()) }.toSet() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val search: StateFlow<SearchUiState> = combine(searchText, searchResults, prefs.recentSearches) { text, results, recent ->
        SearchUiState(
            query = text,
            readings = SearchQuery.targets(text).map { it.label },
            results = results.takeIf { it.query == text.trim().uppercase() } ?: SearchResults(),
            recent = recent,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SearchUiState())
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
    /** True while the camera should track the selected plane (following and not paused by a drag). */
    val isFollowing: StateFlow<Boolean> = following.map { it.on && !it.paused }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

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
        // Airports around the plane, re-read only when it has moved about 10 km.
        val nearbyFlow = aircraftFlow
            .map { a -> a?.let { LatLon(kotlin.math.round(it.lat * 10) / 10, kotlin.math.round(it.lon * 10) / 10) } }
            .distinctUntilChanged()
            .map { p -> p?.let { airports.near(it, NEARBY_AIRPORTS_M) }.orEmpty() }
        val verdictFlow = combine(aircraftFlow, routeFlow, nearbyFlow) { a, r, near ->
            if (a == null || r is Lookup.Loading) null else RouteCheck.check((r as? Lookup.Found)?.value, a, near)
        }
        val airlineFlow = callsignFlow.map { Airlines.icaoFromCallsign(it) }.distinctUntilChanged().flatMapLatest { icao ->
            if (icao == null) {
                flowOf(null)
            } else {
                viewModelScope.launch { logostream.refreshAirline(icao) }
                logostream.observeAirline(icao)
            }
        }
        combine(aircraftFlow, routeFlow, photoFlow, trail, airlineFlow) { aircraft, route, photo, points, airline ->
            aircraft?.let {
                SelectedAircraft(
                    aircraft = it,
                    route = route,
                    photo = photo,
                    verticalRates = points.mapNotNull { p -> p.verticalRateFpm?.toFloat() },
                    trail = points.map { p -> LatLon(p.lat, p.lon) },
                    airlineLogoUrl = airline?.logo?.logoWhite ?: airline?.logo?.logo,
                    tailArtUrl = airline?.logo?.tail,
                    brandColorHex = airline?.colors,
                )
            }
        }.combine(verdictFlow) { sel, verdict -> sel?.copy(verdict = verdict) }
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
        val a = sel?.aircraft ?: return@combine null
        val here = LatLon(a.lat, a.lon)
        // Only a route that fits what the plane is doing gets drawn.
        when (val v = sel.verdict) {
            is RouteVerdict.OnRoute -> RouteOverlay.build(v.from, v.to, track, sel.trail, here)
            is RouteVerdict.Landing -> RouteOverlay.build(null, v.at, track, sel.trail, here)
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val chrome = combine(filters, layers, panel, following, viewport) { f, l, p, follow, vp ->
        Chrome(f, l, p, follow, (vp?.radiusNm ?: 0.0) > ReadsbApiSource.MAX_RADIUS_NM, center = vp?.center)
    }.combine(watch) { c, w -> c.copy(watch = w) }
        .combine(search) { c, s -> c.copy(search = s) }
        .combine(hangar) { c, h -> c.copy(hangar = h) }

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
                following = c.following.on && sel != null,
                followPaused = c.following.paused,
                sheetCollapsed = c.following.collapsed && sel != null,
                radiusCapped = c.radiusCapped,
                emergencies = all.filter { it.isEmergency },
                watchlist = c.watch.items,
                watchedCount = if (c.watch.isEmpty) 0 else all.count { c.watch.matches(it) },
                search = c.search,
                hangar = c.hangar,
                viewCenter = c.center,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RadarUiState())

    private data class Chrome(
        val filters: RadarFilters,
        val layers: RadarLayers,
        val panel: RadarPanel,
        val following: FollowState,
        val radiusCapped: Boolean,
        val watch: Watchlist = Watchlist(emptyList()),
        val search: SearchUiState = SearchUiState(),
        val hangar: List<HangarPlane> = emptyList(),
        val center: LatLon? = null,
    )

    init {
        viewModelScope.launch { traffic.pruneStale() }
        viewModelScope.launch { pollLoop() }
        viewModelScope.launch { searchLoop() }
        viewModelScope.launch { hangarLoop() }
        // Another page asked to show a plane here.
        viewModelScope.launch {
            com.contrail.contrail.navigation.RadarRequests.focus.filterNotNull().collect { f ->
                com.contrail.contrail.navigation.RadarRequests.focus.value = null
                panel.value = RadarPanel.None
                following.value = FollowState()
                f.hex?.let { selectedHex.value = it }
                _cameraMove.value = (CameraMove(LatLon(f.lat, f.lon), zoom = 7.5))
            }
        }
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
        if (selectedHex.value != hex) following.value = FollowState() else following.update { it.copy(collapsed = false) }
        selectedHex.value = hex
        panel.value = RadarPanel.None
    }

    fun clearSelection() {
        selectedHex.value = null
        following.value = FollowState()
    }

    /** A tap on empty map: closes a panel, tucks a followed plane's sheet away, or deselects. */
    fun onMapTapped() {
        when {
            panel.value != RadarPanel.None -> panel.value = RadarPanel.None
            following.value.on -> following.update { it.copy(collapsed = true) }
            else -> clearSelection()
        }
    }

    fun toggleFollow() {
        if (selectedHex.value != null) following.update { if (it.on) FollowState() else FollowState(on = true) }
    }

    /** Dragging the map pauses following; Recentre picks it up again. */
    fun onUserGesture() {
        following.update { if (it.on) it.copy(paused = true) else it }
    }

    fun recentre() {
        following.update { it.copy(paused = false) }
    }

    /** Opens the followed plane's sheet again from the pinned readout. */
    fun expandSheet() {
        following.update { it.copy(collapsed = false) }
    }

    fun togglePanel(target: RadarPanel) {
        panel.update { if (it == target) RadarPanel.None else target }
        if (panel.value != RadarPanel.Search) searchText.value = ""
    }

    // region Search

    fun onSearchText(text: String) {
        searchText.value = text.take(24)
    }

    /** Looks the typed text up now (the keyboard's search key) instead of waiting for the pause. */
    fun submitSearch() {
        viewModelScope.launch { runSearch(searchText.value, remember = true) }
    }

    fun clearRecentSearches() {
        viewModelScope.launch { prefs.clearRecentSearches() }
    }

    /** Opens a live result: it joins the radar, the camera flies to it and its sheet opens. */
    fun openSearchResult(aircraft: LiveAircraft) {
        viewModelScope.launch {
            prefs.addRecentSearch(searchText.value.ifBlank { aircraft.displayCallsign })
            traffic.addFound(listOf(aircraft))
            panel.value = RadarPanel.None
            searchText.value = ""
            following.value = FollowState()
            selectedHex.value = aircraft.hex
            _cameraMove.value = (CameraMove(LatLon(aircraft.lat, aircraft.lon), zoom = 8.5))
        }
    }

    fun openSearchAirport(hit: AirportHit) {
        viewModelScope.launch { prefs.addRecentSearch(hit.code) }
        panel.value = RadarPanel.None
        searchText.value = ""
        _cameraMove.value = (CameraMove(LatLon(hit.lat, hit.lon), zoom = 10.5))
    }

    private suspend fun searchLoop() {
        searchText.debounce(450).distinctUntilChanged().collectLatest { runSearch(it, remember = false) }
    }

    private suspend fun runSearch(raw: String, remember: Boolean) {
        val q = raw.trim().uppercase()
        if (q.length < 2) {
            searchResults.value = SearchResults()
            return
        }
        val targets = SearchQuery.targets(q)
        searchResults.value = SearchResults(q, searching = true)
        val planes = mutableListOf<LiveAircraft>()
        var failed = false
        for (t in targets.take(3)) {
            try {
                planes += when (t) {
                    is SearchTarget.Callsign -> lookup.byCallsign(t.callsign)
                    is SearchTarget.Registration -> lookup.byRegistration(t.registration)
                    is SearchTarget.Hex -> lookup.byHex(t.hex)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failed = true
            }
        }
        val places = if (q.length >= 3) {
            airports.search(q).first().filter { it.iata.isNotBlank() }.take(4).map { AirportHit(it.icao, it.iata, it.name, it.city, it.lat, it.lon) }
        } else {
            emptyList()
        }
        val here = viewport.value?.center
        val found = planes.distinctBy { it.hex }.sortedBy { a -> here?.let { Geo.distanceM(it, LatLon(a.lat, a.lon)) } ?: 0.0 }
        searchResults.value = SearchResults(
            query = q,
            aircraft = found,
            airports = places,
            failed = failed && found.isEmpty(),
            tried = targets.isNotEmpty(),
        )
        if (remember && (found.isNotEmpty() || places.isNotEmpty())) prefs.addRecentSearch(q)
    }

    // endregion

    // region Hangar

    /** Pins or unpins the selected plane. Needs its registration. */
    fun togglePin(aircraft: LiveAircraft) {
        val reg = aircraft.registration ?: return
        viewModelScope.launch {
            if (hangar.value.any { it.registration == reg.uppercase() }) {
                hangarRepo.unpin(reg)
            } else {
                hangarRepo.pin(reg, aircraft.hex, aircraft.typeCode, aircraft.airline?.icao, aircraft.airline?.name)
            }
        }
    }

    /** Pins a registration found by search (it needn't be flying). */
    fun pinRegistration(registration: String) {
        viewModelScope.launch { hangarRepo.pin(registration, null, null, null, null) }
    }

    /** Flies to a pinned plane wherever it is, and opens it. */
    fun openHangarPlane(plane: HangarPlane) {
        val lat = plane.lastLat ?: return
        val lon = plane.lastLon ?: return
        panel.value = RadarPanel.None
        following.value = FollowState()
        plane.hex?.let { selectedHex.value = it }
        _cameraMove.value = (CameraMove(LatLon(lat, lon), zoom = 7.5))
    }

    /** While the radar is open, your pinned planes are looked up worldwide every minute so they're always on it. */
    private suspend fun hangarLoop() {
        combine(active, hangar.map { it.size }.distinctUntilChanged()) { a, n -> a && n > 0 }
            .distinctUntilChanged()
            .collectLatest { on ->
                if (!on) return@collectLatest
                while (true) {
                    runCatching { hangarRepo.refreshLive() }
                    delay(HANGAR_REFRESH_MS)
                }
            }
    }

    // endregion

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
        const val NEARBY_AIRPORTS_M = 160_000.0
        const val HANGAR_REFRESH_MS = 60_000L
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

/** Following the selected plane. [collapsed] tucks its sheet into the top readout; [paused] after a drag. */
data class FollowState(val on: Boolean = false, val paused: Boolean = false, val collapsed: Boolean = false)

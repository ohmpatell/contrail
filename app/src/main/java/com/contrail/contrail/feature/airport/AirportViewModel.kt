package com.contrail.contrail.feature.airport

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.model.FlightRoute
import com.contrail.contrail.core.data.model.LiveAircraft
import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.core.data.repository.AircraftDetailsRepository
import com.contrail.contrail.core.data.repository.AirportInfoRepository
import com.contrail.contrail.core.data.repository.AirportRepository
import com.contrail.contrail.core.data.repository.AirportWeather
import com.contrail.contrail.core.data.repository.LiveTrafficRepository
import com.contrail.contrail.core.data.repository.LogostreamRepository
import com.contrail.contrail.core.database.AirportWithUser
import com.contrail.contrail.core.database.FrequencyEntity
import com.contrail.contrail.core.database.RunwayEntity
import com.contrail.contrail.core.network.airport.OsmResult
import com.contrail.contrail.core.network.logostream.LsAirportStats
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos

/** One airline that flies from here, with how many nonstop routes it serves. */
data class AirlineServed(val icao: String?, val iata: String?, val name: String, val routes: Int, val logoUrl: String?, val accentArgb: Long?)

/** A nonstop destination with coordinates, for the map. */
data class Destination(val iata: String, val city: String, val lat: Double, val lon: Double, val airlines: Int)

enum class BoardKind { Departures, Arrivals }

/** A live movement near the airport, derived from ADS-B. */
data class BoardRow(
    val hex: String,
    val callsign: String,
    val otherEnd: String?,
    val typeCode: String?,
    val status: String,
    /** Minutes until landing for arrivals, since departure (approx.) for departures. */
    val minutes: Int?,
    val altitudeFt: Int?,
)

data class AirportUiState(
    val airport: AirportWithUser? = null,
    val runways: List<RunwayEntity> = emptyList(),
    val frequencies: List<FrequencyEntity> = emptyList(),
    val weather: AirportWeather? = null,
    val geometry: OsmResult? = null,
    val stats: LsAirportStats? = null,
    val airlines: List<AirlineServed> = emptyList(),
    val destinations: List<Destination> = emptyList(),
    val nearby: List<LiveAircraft> = emptyList(),
    val departures: List<BoardRow> = emptyList(),
    val arrivals: List<BoardRow> = emptyList(),
    val board: BoardKind = BoardKind.Departures,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = AirportViewModel.Factory::class)
class AirportViewModel @AssistedInject constructor(
    @Assisted private val icao: String,
    private val airports: AirportRepository,
    private val info: AirportInfoRepository,
    private val logostream: LogostreamRepository,
    private val traffic: LiveTrafficRepository,
    private val details: AircraftDetailsRepository,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(icao: String): AirportViewModel
    }

    private val board = MutableStateFlow(BoardKind.Departures)
    private val airport = airports.observe(icao).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val routesAndAirlines: Flow<Pair<List<AirlineServed>, List<Destination>>> =
        airport.filterNotNull().flatMapLatest { a ->
            logostream.observeRoutesFrom(a.iata).map { routes ->
                routes.orEmpty().let { list ->
                    // logostream's route data reuses IATA codes and sometimes carries the wrong ICAO
                    // (Rouge as CPN), so group by IATA and resolve names through our own table first.
                    val byAirline = list.flatMap { r -> r.airlines.map { it to r } }
                        .groupBy { it.first.iata ?: it.first.icao ?: "?" }
                    val served = byAirline.map { (code, pairs) ->
                        val first = pairs.first().first
                        val local = Airlines.byIata(first.iata) ?: Airlines.byIcao(first.icao)
                        val icao = local?.icao ?: first.icao
                        val profile = icao?.let { logostream.observeAirline(it).first() }
                            ?.takeIf { local != null || it.iata == null || it.iata == first.iata }
                        AirlineServed(
                            icao = icao, iata = first.iata,
                            name = local?.name ?: profile?.name ?: code,
                            routes = pairs.map { it.second.to }.distinct().size,
                            logoUrl = profile?.logo?.logoWhite ?: profile?.logo?.icon,
                            accentArgb = local?.accentArgb,
                        )
                    }.sortedByDescending { it.routes }
                    val coords = airports.byIatas(list.map { it.to })
                    val dest = list.mapNotNull { r ->
                        val ap = coords[r.to] ?: return@mapNotNull null
                        Destination(r.to, ap.city.ifBlank { ap.name }, ap.lat, ap.lon, r.airlines.size)
                    }
                    served to dest
                }
            }
        }

    private val nearbyTraffic: Flow<List<LiveAircraft>> = combine(airport.filterNotNull(), traffic.observeTraffic()) { a, list ->
        list.filter { Geo.distanceM(a.lat, a.lon, it.lat, it.lon) < NEARBY_RADIUS_M }
            .sortedBy { Geo.distanceM(a.lat, a.lon, it.lat, it.lon) }
    }

    private val boards: Flow<Pair<List<BoardRow>, List<BoardRow>>> = combine(airport.filterNotNull(), nearbyTraffic) { a, list ->
        val routes = list.mapNotNull { it.callsign }.distinct().associateWith { cs -> details.observeRoute(cs).first() }
        LiveBoard.build(a, list, routes)
    }

    private val basics = combine(
        airport,
        airports.runways(icao),
        airports.frequencies(icao),
        info.observeWeather(icao),
        info.observeGeometry(icao),
    ) { a, r, f, w, g -> Basics(a, r, f, w, g) }

    private val stats = airport.filterNotNull().flatMapLatest { logostream.observeAirportStats(it.iata) }

    val state: StateFlow<AirportUiState> = combine(basics, stats, routesAndAirlines, combine(nearbyTraffic, boards) { n, b -> n to b }, board) { b, s, (served, dest), (near, bd), kind ->
        AirportUiState(
            airport = b.airport, runways = b.runways, frequencies = b.frequencies, weather = b.weather,
            geometry = b.geometry, stats = s, airlines = served, destinations = dest,
            nearby = near, departures = bd.first, arrivals = bd.second, board = kind,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AirportUiState())

    private data class Basics(
        val airport: AirportWithUser?,
        val runways: List<RunwayEntity>,
        val frequencies: List<FrequencyEntity>,
        val weather: AirportWeather?,
        val geometry: OsmResult?,
    )

    init {
        viewModelScope.launch {
            val a = airport.filterNotNull().first()
            launch { info.refreshWeather(a.icao) }
            launch { info.refreshGeometry(a.icao, a.lat, a.lon) }
            launch { logostream.refreshAirportStats(a.iata) }
            launch {
                logostream.refreshRoutesFrom(a.iata)
                // Profiles (names and logo URLs) for the airlines with the most routes here.
                val routes = logostream.observeRoutesFrom(a.iata).first().orEmpty()
                routes.flatMap { it.airlines }
                    .mapNotNull { al -> (Airlines.byIata(al.iata) ?: Airlines.byIcao(al.icao))?.icao ?: al.icao }
                    .groupingBy { it }.eachCount()
                    .entries.sortedByDescending { it.value }.take(TOP_AIRLINES)
                    .forEach { (code, _) -> logostream.refreshAirline(code) }
            }
            launch { pollNearby(a) }
        }
    }

    /** Live traffic around the airport while the page is open, for the board and mini-radar. */
    private suspend fun pollNearby(a: AirportWithUser) {
        while (true) {
            runCatching { traffic.refresh(LatLon(a.lat, a.lon), NEARBY_QUERY_NM) }
            val callsigns = traffic.observeTraffic().first()
                .filter { Geo.distanceM(a.lat, a.lon, it.lat, it.lon) < NEARBY_RADIUS_M }
                .mapNotNull { it.callsign }.distinct().take(MAX_ROUTE_LOOKUPS)
            callsigns.forEach { runCatching { details.refreshRoute(it) } }
            delay(POLL_MS)
        }
    }

    fun showBoard(kind: BoardKind) {
        board.value = kind
    }

    fun toggleFavourite() {
        val a = airport.value ?: return
        viewModelScope.launch { airports.setFavourite(a.icao, a.favourite != true) }
    }

    fun toggleVisited() {
        val a = airport.value ?: return
        viewModelScope.launch { airports.setVisited(a.icao, a.visited != true) }
    }

    companion object {
        const val NEARBY_RADIUS_M = 60_000.0
        const val NEARBY_QUERY_NM = 40
        const val POLL_MS = 10_000L
        const val MAX_ROUTE_LOOKUPS = 12
        const val TOP_AIRLINES = 16
    }
}

/**
 * Departures and arrivals from live ADS-B: aircraft near the airport that are climbing away
 * or descending towards it, with their other end from the callsign route where known.
 */
object LiveBoard {
    fun build(a: AirportWithUser, traffic: List<LiveAircraft>, routes: Map<String, FlightRoute?>): Pair<List<BoardRow>, List<BoardRow>> {
        val here = LatLon(a.lat, a.lon)
        val departures = ArrayList<BoardRow>()
        val arrivals = ArrayList<BoardRow>()
        for (ac in traffic) {
            val cs = ac.callsign ?: continue
            if (ac.onGround) continue
            val alt = ac.altitudeFt ?: continue
            val pos = LatLon(ac.lat, ac.lon)
            val distM = Geo.distanceM(here, pos)
            val vr = ac.verticalRateFpm ?: 0
            val track = ac.trackDeg?.toDouble() ?: continue
            val bearingIn = Geo.bearingDeg(pos, here)
            val toward = abs(Geo.headingDelta(track, bearingIn)) < 70
            val route = routes[cs]
            val code = a.iata
            when {
                route?.destination?.iata == code || (route == null && toward && vr < -300 && alt < 14_000) -> {
                    val gsMps = (ac.groundSpeedKt ?: 180f) * Geo.MPS_PER_KNOT
                    val minutes = (distM / gsMps / 60).toInt().coerceAtLeast(0)
                    arrivals += BoardRow(ac.hex, cs, route?.origin?.iata, ac.typeCode, if (alt < 3_000) "Final" else "Approach", minutes, alt)
                }
                route?.origin?.iata == code || (route == null && !toward && vr > 300 && alt < 18_000) -> {
                    val minutes = (alt / 2_000.0).toInt()
                    departures += BoardRow(ac.hex, cs, route?.destination?.iata, ac.typeCode, if (alt < 10_000) "Climbing" else "Departed", minutes, alt)
                }
            }
        }
        // Arrivals by how soon they land; departures by how recently they left (lowest first).
        return departures.sortedBy { it.altitudeFt ?: 0 }.take(10) to arrivals.sortedBy { it.minutes ?: 999 }.take(10)
    }

    /** Degrees of crosswind/headwind aside, the runway end most into the wind is likely in use. */
    fun likelyActiveEnd(runways: List<RunwayEntity>, windFromDeg: Double?): String? {
        if (windFromDeg == null || runways.isEmpty()) return null
        var best: String? = null
        var bestComponent = -1.0
        for (r in runways) {
            listOf(r.leIdent to r.leHeadingDeg, r.heIdent to r.heHeadingDeg).forEach { (ident, hdg) ->
                val h = hdg ?: identHeading(ident) ?: return@forEach
                val headwind = cos(Geo.toRad(Geo.headingDelta(h, windFromDeg)))
                if (headwind > bestComponent) {
                    bestComponent = headwind
                    best = ident
                }
            }
        }
        return best
    }

    private fun identHeading(ident: String): Double? = ident.takeWhile { it.isDigit() }.toIntOrNull()?.let { it * 10.0 }
}

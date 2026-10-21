package com.contrail.contrail.feature.trips.flight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.data.model.LiveAircraft
import com.contrail.contrail.core.data.repository.AirportRepository
import com.contrail.contrail.core.data.repository.FlightTrackRepository
import com.contrail.contrail.core.data.repository.LiveFlightRepository
import com.contrail.contrail.core.data.repository.LoungeRepository
import com.contrail.contrail.core.data.repository.LoungeVisit
import com.contrail.contrail.core.data.repository.TripRepository
import com.contrail.contrail.core.data.repository.TripSettings
import com.contrail.contrail.core.data.rotation.Rotation
import com.contrail.contrail.core.data.rotation.RotationRepository
import com.contrail.contrail.core.data.rotation.TurnThresholds
import com.contrail.contrail.core.data.ticket.AirportInfo
import com.contrail.contrail.core.data.trip.FlightSegment
import com.contrail.contrail.core.data.trip.Trip
import com.contrail.contrail.core.network.track.FlightTrack
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import javax.inject.Inject

/** Live state of your flight from ADS-B, refreshed every 15 seconds around flight time. */
data class LiveState(
    val aircraft: LiveAircraft? = null,
    val track: FlightTrack? = null,
    val checkedAtMs: Long? = null,
    /** False outside the window from an hour before departure to an hour after arrival. */
    val inWindow: Boolean = false,
)

data class FlightUiState(
    val loading: Boolean = true,
    val trip: Trip? = null,
    val segment: FlightSegment? = null,
    val places: Map<String, AirportInfo> = emptyMap(),
    val live: LiveState = LiveState(),
    val rotation: Rotation? = null,
    val rotationLoading: Boolean = false,
    val thresholds: TurnThresholds = TurnThresholds(),
    val flightsOnTail: Int = 0,
    val lounges: List<LoungeVisit> = emptyList(),
)

/** One row of the gate-to-gate timeline. Estimates are always labelled as such. */
data class TimelineRow(val label: String, val scheduled: LocalDateTime?, val estimated: LocalDateTime?, val estimateNote: String?)

object FlightTimeline {
    private const val TAXI_OUT_MIN = 15L
    private const val TAXI_IN_MIN = 8L

    /**
     * Gate out and gate in come from the ticket. Takeoff and landing aren't on tickets, so
     * they're estimated: from the ADS-B track and live speed once airborne, else from typical taxi times.
     */
    fun rows(s: FlightSegment, live: LiveState, places: Map<String, AirportInfo>, now: Instant): List<TimelineRow> {
        val ac = live.aircraft?.takeIf { !it.onGround }
        val firstSeen = live.track?.startTimeSec?.takeIf { it > 0 }?.let(Instant::ofEpochSecond)
        val takeoff = firstSeen ?: s.departure.plusSeconds(TAXI_OUT_MIN * 60)
        val dest = places[s.destination]
        val landing = if (ac != null && dest != null && (ac.groundSpeedKt ?: 0f) > 60f) {
            val metres = Geo.distanceM(ac.lat, ac.lon, dest.lat, dest.lon)
            now.plusSeconds((metres / (ac.groundSpeedKt!! * Geo.MPS_PER_KNOT)).toLong())
        } else {
            s.arrival.minusSeconds(TAXI_IN_MIN * 60)
        }
        fun dep(i: Instant) = LocalDateTime.ofInstant(i, s.departureZone)
        fun arr(i: Instant) = LocalDateTime.ofInstant(i, s.arrivalZone)
        return listOf(
            TimelineRow("Gate out", s.departureLocal, firstSeen?.let { dep(it.minusSeconds(TAXI_OUT_MIN * 60)) }, firstSeen?.let { "from ADS-B" }),
            TimelineRow("Takeoff", null, dep(takeoff), if (firstSeen != null) "first seen airborne" else "typical taxi"),
            TimelineRow("Landing", null, arr(landing), if (ac != null) "live speed" else "typical taxi"),
            TimelineRow("Gate in", s.arrivalLocal, if (ac != null) arr(landing.plusSeconds(TAXI_IN_MIN * 60)) else null, if (ac != null) "live speed" else null),
        )
    }

    /** Estimated arrival delay once airborne (positive = late). */
    fun delay(rows: List<TimelineRow>, s: FlightSegment): Duration? {
        val gateIn = rows.lastOrNull()?.estimated ?: return null
        return Duration.between(s.arrivalLocal.atZone(s.arrivalZone), gateIn.atZone(s.arrivalZone))
    }
}

@HiltViewModel(assistedFactory = FlightViewModel.Factory::class)
class FlightViewModel @AssistedInject constructor(
    @Assisted private val segmentId: Long,
    private val trips: TripRepository,
    private val airports: AirportRepository,
    private val liveFlights: LiveFlightRepository,
    private val tracks: FlightTrackRepository,
    private val rotations: RotationRepository,
    settings: TripSettings,
    loungeRepo: LoungeRepository,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(segmentId: Long): FlightViewModel
    }

    private val found = trips.segment(segmentId)
    private val live = MutableStateFlow(LiveState())
    private val rotation = MutableStateFlow<Rotation?>(null)
    private val rotationLoading = MutableStateFlow(false)
    private val tailCount = MutableStateFlow(0)

    private val places = found.map { f ->
        val dir = airports.directory()
        f?.first?.segments?.flatMap { listOf(it.origin, it.destination) }?.toSet().orEmpty().mapNotNull { c -> dir.find(c)?.let { c to it } }.toMap()
    }

    val state: StateFlow<FlightUiState> = combine(
        combine(found, places) { f, p -> f to p },
        combine(live, rotation, rotationLoading) { l, r, rl -> Triple(l, r, rl) },
        combine(settings.thresholds, tailCount) { t, c -> t to c },
        loungeRepo.visits.map { v -> v.filter { it.segmentId == segmentId } },
    ) { (f, p), (l, r, rl), (t, c), lounges ->
        FlightUiState(false, f?.first, f?.second, p, l, r, rl, t, c, lounges)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FlightUiState())

    init {
        viewModelScope.launch { pollLive() }
        // The rotation follows the tail: entering or changing it reloads the panel.
        viewModelScope.launch {
            found.filterNotNull().map { it.second.tail }.distinctUntilChanged().collect { tail ->
                tailCount.value = tail?.let { trips.flightsOnTail(it) } ?: 0
                loadRotation()
            }
        }
    }

    private suspend fun pollLive() {
        val seg = found.filterNotNull().first().second
        while (true) {
            val now = Instant.ofEpochMilli(clock.nowMs())
            val inWindow = now.isAfter(seg.departure.minus(Duration.ofHours(1))) && now.isBefore(seg.arrival.plus(Duration.ofHours(1)))
            if (!inWindow) {
                live.update { it.copy(inWindow = false) }
                return
            }
            val ac = runCatching { liveFlights.find(seg) }.getOrNull()
            val track = if (ac != null && !ac.onGround && live.value.track == null) runCatching { tracks.track(ac.hex) }.getOrNull() else live.value.track
            live.value = LiveState(ac, track, clock.nowMs(), inWindow = true)
            delay(15_000)
        }
    }

    fun refreshRotation() {
        viewModelScope.launch { loadRotation() }
    }

    private suspend fun loadRotation() {
        val seg = state.value.segment ?: found.filterNotNull().first().second
        rotationLoading.value = true
        rotation.value = runCatching { rotations.rotation(seg) }.getOrNull()
        rotationLoading.value = false
    }

    fun setTail(tail: String) {
        viewModelScope.launch { trips.setTail(segmentId, tail) }
    }
}

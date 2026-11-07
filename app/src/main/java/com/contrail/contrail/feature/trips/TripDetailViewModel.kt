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
    val signedIn: Boolean = false,
    /** People you've shared this trip with. */
    val sharedWith: List<com.contrail.contrail.core.data.sync.SentShare> = emptyList(),
    val sharing: Boolean = false,
    val shareMessage: String? = null,
    val shareError: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = TripDetailViewModel.Factory::class)
class TripDetailViewModel @AssistedInject constructor(
    @Assisted private val tripId: Long,
    private val trips: TripRepository,
    private val airports: AirportRepository,
    private val info: AirportInfoRepository,
    loungeRepo: LoungeRepository,
    private val account: com.contrail.contrail.core.data.sync.AccountRepository,
    private val shares: com.contrail.contrail.core.data.sync.SharesRepository,
    private val db: com.contrail.contrail.core.database.ContrailDatabase,
) : ViewModel() {
    /** Sending, then a message or an error, for the share sheet. */
    private val shareStatus = kotlinx.coroutines.flow.MutableStateFlow(Triple(false, null as String?, null as String?))


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
    }.combine(account.signedIn) { s, signed -> s.copy(signedIn = signed) }
        .combine(shares.sent) { s, sent ->
            val uid = db.syncDao().trips().firstOrNull { it.id == tripId }?.uid
            s.copy(sharedWith = sent.filter { it.tripUid == uid })
        }
        .combine(shareStatus) { s, (busy, msg, err) -> s.copy(sharing = busy, shareMessage = msg, shareError = err) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripDetailUiState())

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

    fun share(username: String) {
        viewModelScope.launch {
            shareStatus.value = Triple(true, null, null)
            val r = runCatching { account.shareTrip(tripId, username) }
            shareStatus.value = Triple(false, r.getOrNull()?.let { "Shared with @${username.trim().lowercase()}." }, r.exceptionOrNull()?.message)
        }
    }

    fun clearShareStatus() {
        shareStatus.value = Triple(false, null, null)
    }

    fun unshare(s: com.contrail.contrail.core.data.sync.SentShare) {
        viewModelScope.launch { runCatching { account.unshareTrip(s.tripUid, s.userId) } }
    }

    /** "I'm on this trip too": a followed trip becomes yours and counts in your Passport. */
    fun join() {
        viewModelScope.launch { shares.join(tripId) }
    }

    fun unfollow(done: () -> Unit) {
        viewModelScope.launch {
            shares.unfollow(tripId)
            done()
        }
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

package com.contrail.contrail.feature.sky

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.location.DeviceLocation
import com.contrail.contrail.core.data.model.Watchlist
import com.contrail.contrail.core.data.repository.AirportRepository
import com.contrail.contrail.core.data.repository.LiveTrafficRepository
import com.contrail.contrail.core.data.repository.WatchlistRepository
import com.contrail.contrail.core.data.sky.AlertRules
import com.contrail.contrail.core.data.sky.SkyContact
import com.contrail.contrail.core.data.sky.SkyGeometry
import com.contrail.contrail.core.data.sky.SkySettingsRepository
import com.contrail.contrail.feature.sky.alerts.ProximityAlerts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.ceil

data class SkyUiState(
    val permission: Boolean = false,
    val locating: Boolean = false,
    val me: LatLon? = null,
    /** "Toronto (YYZ)": the nearest airport, to say where you are. */
    val near: String? = null,
    val radiusKm: Int = 25,
    val contacts: List<SkyContact> = emptyList(),
    val updatedAtMs: Long? = null,
    val source: String? = null,
    val error: String? = null,
    val alerts: AlertRules = AlertRules(),
    val watchlist: Watchlist = Watchlist(emptyList()),
    val nowMs: Long = 0,
) {
    val closest: SkyContact? get() = contacts.firstOrNull()
}

private data class Live(val me: LatLon? = null, val near: String? = null, val locating: Boolean = false, val permission: Boolean = false, val updatedAtMs: Long? = null, val source: String? = null, val error: String? = null)

@HiltViewModel
class SkyViewModel @Inject constructor(
    private val location: DeviceLocation,
    private val traffic: LiveTrafficRepository,
    private val settings: SkySettingsRepository,
    private val airports: AirportRepository,
    private val alertsScheduler: ProximityAlerts,
    watchlist: WatchlistRepository,
    private val clock: Clock,
) : ViewModel() {
    private val live = MutableStateFlow(Live(permission = location.hasPermission()))
    private var polling: Job? = null

    /** A once-a-second tick so countdowns and dead-reckoned positions move between updates. */
    private val ticker = flow { while (true) { emit(clock.nowMs()); delay(1_000) } }

    val state: StateFlow<SkyUiState> = combine(live, settings.settings, traffic.observeTraffic(), watchlist.observe(), ticker) { l, s, t, w, now ->
        SkyUiState(
            permission = l.permission,
            locating = l.locating,
            me = l.me,
            near = l.near,
            radiusKm = s.radiusKm,
            contacts = l.me?.let { SkyGeometry.contacts(it, t, s.radiusKm.toDouble(), now) }.orEmpty(),
            updatedAtMs = l.updatedAtMs,
            source = l.source,
            error = l.error,
            alerts = s.alerts,
            watchlist = Watchlist(w),
            nowMs = now,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SkyUiState())

    /** The Sky tab is on screen: find you, then poll the feed every few seconds. */
    fun setActive(active: Boolean) {
        polling?.cancel()
        if (!active) return
        polling = viewModelScope.launch {
            locate()
            while (isActive) {
                val me = live.value.me
                if (me != null) {
                    val radius = settings.current().radiusKm
                    runCatching { traffic.refresh(me, ceil(radius / 1.852 + 5).toInt()) }
                        .onSuccess { r -> live.update { it.copy(updatedAtMs = r.receivedAtMs, source = r.source, error = null) } }
                        .onFailure { e -> live.update { it.copy(error = e.message ?: "Live feed unavailable") } }
                }
                delay(POLL_MS)
            }
        }
    }

    fun onPermissionResult() {
        live.update { it.copy(permission = location.hasPermission()) }
        if (location.hasPermission()) viewModelScope.launch { locate() }
    }

    private suspend fun locate() {
        if (!location.hasPermission()) {
            live.update { it.copy(permission = false) }
            // Without permission, use the last place you allowed (if any).
            settings.current().spot?.let { spot -> live.update { it.copy(me = spot, near = settings.current().spotName) } }
            return
        }
        live.update { it.copy(permission = true, locating = it.me == null) }
        val me = location.current()
        if (me == null) {
            live.update { it.copy(locating = false, error = "Couldn't get your location. Is location turned on?") }
            return
        }
        val near = airports.nearest(me)?.let { "${it.city} (${it.iata})" }
        live.update { it.copy(me = me, near = near, locating = false) }
        settings.setSpot(me, near)
    }

    fun setRadius(km: Int) = viewModelScope.launch { settings.setRadius(km) }

    fun setAlerts(rules: AlertRules) = viewModelScope.launch {
        settings.setAlerts(rules)
        alertsScheduler.schedule(rules.enabled)
    }

    companion object {
        const val POLL_MS = 6_000L
    }
}

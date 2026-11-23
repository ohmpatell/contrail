package com.contrail.contrail.feature.cabin

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.common.Sun
import com.contrail.contrail.core.data.cabin.Bumpiness
import com.contrail.contrail.core.data.cabin.CabinMath
import com.contrail.contrail.core.data.cabin.FlightPack
import com.contrail.contrail.core.data.cabin.FlightPackRepository
import com.contrail.contrail.core.data.cabin.Side
import com.contrail.contrail.core.data.cabin.SunEvent
import com.contrail.contrail.core.data.cabin.Upcoming
import com.contrail.contrail.core.data.model.LiveAircraft
import com.contrail.contrail.core.data.moments.MomentKind
import com.contrail.contrail.core.data.moments.MomentRepository
import com.contrail.contrail.core.data.reference.AircraftTypes
import com.contrail.contrail.core.data.reference.Silhouette
import com.contrail.contrail.core.data.repository.AirportRepository
import com.contrail.contrail.core.data.repository.LiveFlightRepository
import com.contrail.contrail.core.data.repository.TripRepository
import com.contrail.contrail.core.data.ticket.AirportInfo
import com.contrail.contrail.core.data.trip.FlightSegment
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import kotlin.math.sqrt

/** Where the position on screen comes from, most trustworthy first. */
enum class PositionSource(val label: String) { Gps("GPS"), Adsb("ADS-B via the plane's Wi-Fi"), Estimated("Estimated from the clock") }

/** Before the wheels leave the ground, in the air, or down. */
enum class CabinPhase { Boarding, Airborne, Landed }

data class CabinState(
    val segment: FlightSegment? = null,
    val origin: AirportInfo? = null,
    val destination: AirportInfo? = null,
    val pack: FlightPack? = null,
    val route: List<LatLon> = emptyList(),
    val flown: List<LatLon> = emptyList(),
    val phase: CabinPhase = CabinPhase.Boarding,
    val position: LatLon? = null,
    val headingDeg: Double = 0.0,
    val fraction: Double = 0.0,
    val source: PositionSource = PositionSource.Estimated,
    val gpsAgeSec: Long? = null,
    val satellites: Int? = null,
    val groundSpeedKt: Double? = null,
    val altitudeFt: Int? = null,
    val cabinAltitudeFt: Int? = null,
    val bumpiness: Bumpiness = Bumpiness.Smooth,
    val roughestToday: Bumpiness = Bumpiness.Smooth,
    val minutesLeft: Int? = null,
    val landingMs: Long? = null,
    val upcoming: List<Upcoming> = emptyList(),
    val nextBorder: Pair<String, Int>? = null,
    val sunEvent: SunEvent? = null,
    val sunSide: Side? = null,
    val sunElevationDeg: Double? = null,
    val window: Side? = null,
    val captured: List<String> = emptyList(),
    val nowMs: Long = 0,
)

/**
 * Cabin mode: your flight from the inside, with or without internet. Position comes from the
 * phone's GPS (it works in airplane mode, best by a window), from ADS-B if the plane has Wi-Fi, or
 * from the clock along the planned route, and always says which. Sensors stop when you leave.
 */
@HiltViewModel(assistedFactory = CabinViewModel.Factory::class)
class CabinViewModel @AssistedInject constructor(
    @Assisted private val segmentId: Long,
    @ApplicationContext private val context: Context,
    private val trips: TripRepository,
    private val airports: AirportRepository,
    private val packs: FlightPackRepository,
    private val live: LiveFlightRepository,
    private val moments: MomentRepository,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(segmentId: Long): CabinViewModel
    }

    private val _state = MutableStateFlow(CabinState())
    val state: StateFlow<CabinState> = _state

    private var lastFix: Location? = null
    private var lastAdsb: LiveAircraft? = null
    private var pressure: Float? = null
    private val accel = ArrayDeque<Float>()
    private var takeoffMs: Long? = null
    private var landingMs: Long? = null
    private val captured = LinkedHashSet<String>()
    private var lastSunElevation: Double? = null
    private var lastRecordedFix = 0L
    private var running = false

    private val sensors = context.getSystemService(SensorManager::class.java)
    private val locations = context.getSystemService(LocationManager::class.java)

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            when (e.sensor.type) {
                Sensor.TYPE_PRESSURE -> pressure = e.values[0]
                Sensor.TYPE_ACCELEROMETER -> {
                    val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]) - SensorManager.GRAVITY_EARTH
                    accel.addLast(g)
                    while (accel.size > 150) accel.removeFirst()
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val locationListener = LocationListener { loc -> lastFix = loc }

    init {
        viewModelScope.launch {
            val (_, seg) = trips.segment(segmentId).filterNotNull().first()
            val dir = airports.directory()
            _state.update { it.copy(segment = seg, origin = dir.find(seg.origin), destination = dir.find(seg.destination)) }
            takeoffMs = seg.actualDeparture?.toEpochMilli()
            landingMs = seg.actualArrival?.toEpochMilli()
            packs.observe(segmentId).collect { p -> _state.update { it.copy(pack = p) } }
        }
        viewModelScope.launch { packs.observeTrack(segmentId).collect { t -> _state.update { s -> s.copy(flown = t.map { LatLon(it.lat, it.lon) }) } } }
        viewModelScope.launch { tick() }
        viewModelScope.launch { adsbLoop() }
    }

    /** Starts the GPS and sensors while the screen is showing. */
    @SuppressLint("MissingPermission")
    fun setActive(on: Boolean) {
        if (on == running) return
        running = on
        if (on) {
            sensors?.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let { sensors.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL) }
            sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensors.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME) }
            runCatching {
                // GPS straight from the receiver: it needs no network, so it works in airplane mode.
                locations?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5_000L, 0f, locationListener, Looper.getMainLooper())
            }
        } else {
            sensors?.unregisterListener(sensorListener)
            runCatching { locations?.removeUpdates(locationListener) }
        }
    }

    override fun onCleared() {
        setActive(false)
    }

    /** If the plane has Wi-Fi, the real ADS-B position for your flight. */
    private suspend fun adsbLoop() {
        while (true) {
            val seg = _state.value.segment
            if (seg != null) lastAdsb = runCatching { live.find(seg) }.getOrNull() ?: lastAdsb?.takeIf { clock.nowMs() - it.fetchedAtMs < 120_000 }
            delay(30_000)
        }
    }

    private suspend fun tick() {
        while (true) {
            runCatching { update() }
            delay(1_000)
        }
    }

    private suspend fun update() {
        val s = _state.value
        val seg = s.segment ?: return
        val from = s.origin ?: return
        val to = s.destination ?: return
        val now = clock.nowMs()
        val route = s.pack?.route?.takeIf { it.size >= 2 } ?: Geo.greatCircle(LatLon(from.lat, from.lon), LatLon(to.lat, to.lon), 96)
        val fix = lastFix?.takeIf { now - it.time < 120_000 }
        val gpsSpeedKt = fix?.takeIf { it.hasSpeed() }?.speed?.toDouble()?.div(Geo.MPS_PER_KNOT)
        val gpsAltFt = fix?.takeIf { it.hasAltitude() }?.altitude?.times(3.28084)
        val adsb = lastAdsb?.takeIf { now - it.fetchedAtMs < 120_000 }

        // Phase from what we can see: GPS speed, ADS-B, or the timetable.
        val airborneNow = when {
            fix != null -> CabinMath.airborne(gpsSpeedKt, gpsAltFt, from.let { 0.0 })
            adsb != null -> !adsb.onGround
            else -> null
        }
        when {
            s.phase == CabinPhase.Boarding && airborneNow == true -> {
                takeoffMs = takeoffMs ?: now
                trips.recordGpsTimes(segmentId, takeoffMs, null)
                capture("takeoff", MomentKind.Takeoff, "Wheels up from ${from.city}", fix?.let { LatLon(it.latitude, it.longitude) })
            }
            s.phase == CabinPhase.Airborne && airborneNow == false && (gpsSpeedKt ?: 0.0) < 60 -> {
                landingMs = now
                trips.recordGpsTimes(segmentId, null, now)
                capture("landing", MomentKind.Landing, "Touchdown in ${to.city}", fix?.let { LatLon(it.latitude, it.longitude) })
            }
        }
        val schedDep = seg.expectedDeparture.toEpochMilli()
        val schedArr = seg.expectedArrival.toEpochMilli()
        val phase = when {
            landingMs != null -> CabinPhase.Landed
            takeoffMs != null || airborneNow == true -> CabinPhase.Airborne
            airborneNow == null && now in (schedDep + 10 * 60_000)..schedArr -> CabinPhase.Airborne
            airborneNow == null && now > schedArr -> CabinPhase.Landed
            else -> CabinPhase.Boarding
        }
        val flightTime = (schedArr - schedDep).coerceAtLeast(30 * 60_000L)
        val start = takeoffMs ?: (schedDep + 10 * 60_000)
        val end = start + flightTime - 10 * 60_000

        val (pos, source) = when {
            fix != null -> LatLon(fix.latitude, fix.longitude) to PositionSource.Gps
            adsb != null -> LatLon(adsb.lat, adsb.lon) to PositionSource.Adsb
            phase == CabinPhase.Boarding -> LatLon(from.lat, from.lon) to PositionSource.Estimated
            phase == CabinPhase.Landed -> LatLon(to.lat, to.lon) to PositionSource.Estimated
            else -> CabinMath.pointAt(route, CabinMath.estimatedFraction(now, start, end)).first to PositionSource.Estimated
        }
        val fraction = if (source == PositionSource.Estimated) {
            when (phase) {
                CabinPhase.Boarding -> 0.0
                CabinPhase.Landed -> 1.0
                CabinPhase.Airborne -> CabinMath.estimatedFraction(now, start, end)
            }
        } else {
            CabinMath.fractionOf(route, pos)
        }
        val heading = fix?.takeIf { it.hasBearing() && (gpsSpeedKt ?: 0.0) > 50 }?.bearing?.toDouble()
            ?: adsb?.trackDeg?.toDouble()
            ?: CabinMath.pointAt(route, fraction).second
        val speed = gpsSpeedKt ?: adsb?.groundSpeedKt?.toDouble() ?: if (phase == CabinPhase.Airborne) TYPICAL_CRUISE_KT else null
        val altitude = gpsAltFt?.toInt() ?: adsb?.altitudeFt ?: if (phase == CabinPhase.Airborne && source == PositionSource.Estimated) null else null
        if (fix != null && phase == CabinPhase.Airborne && now - lastRecordedFix > 30_000) {
            lastRecordedFix = now
            packs.recordFix(segmentId, fix.time, pos, gpsAltFt?.toInt(), gpsSpeedKt?.toFloat())
        }

        // Time left: by speed and distance once moving, by the timetable otherwise.
        val total = CabinMath.length(route)
        val remainingM = (1 - fraction) * total
        val landAt = when {
            phase == CabinPhase.Landed -> landingMs ?: schedArr
            speed != null && speed > 150 && phase == CabinPhase.Airborne -> now + (remainingM / (speed * Geo.MPS_PER_KNOT) * 1000).toLong() + 8 * 60_000
            else -> schedArr
        }

        val pack = s.pack
        val upcoming = if (phase == CabinPhase.Airborne && pack != null) CabinMath.upcoming(route, pack.packPlaces, fraction, speed ?: TYPICAL_CRUISE_KT) else emptyList()
        val border = pack?.crossings?.firstOrNull { it.along > fraction }?.let { b ->
            val minutes = (((b.along - fraction) * total) / ((speed ?: TYPICAL_CRUISE_KT) * Geo.MPS_PER_KNOT) / 60).toInt()
            (b.countryName ?: b.countryIso) to minutes
        }
        // Crossed a border since last time: a moment.
        pack?.crossings?.lastOrNull { it.along <= fraction && phase == CabinPhase.Airborne }?.let { b ->
            capture("border-${b.countryIso}", MomentKind.Border, "Into ${b.countryName ?: b.countryIso}", pos)
        }

        // The sun, as seen from up here.
        val altM = (altitude ?: if (phase == CabinPhase.Airborne) 11_000 * 3 else 0) / 3.28084
        val sunEl = Sun.elevationDeg(now, pos.lat, pos.lon)
        val horizon = -0.833 - CabinMath.horizonDipDeg(altM)
        lastSunElevation?.let { prev ->
            if (phase == CabinPhase.Airborne && prev >= horizon && sunEl < horizon) capture("sunset-${now / 3_600_000}", MomentKind.Sunset, "Sunset at ${altitude?.let { "%,d ft".format(it) } ?: "cruising height"}", pos)
            if (phase == CabinPhase.Airborne && prev <= horizon && sunEl > horizon) capture("sunrise-${now / 3_600_000}", MomentKind.Sunrise, "Sunrise at ${altitude?.let { "%,d ft".format(it) } ?: "cruising height"}", pos)
        }
        lastSunElevation = sunEl
        val sunEvent = if (phase == CabinPhase.Airborne && now % 30_000 < 1_000 || s.sunEvent == null) {
            CabinMath.nextSunEvent(route, fraction, now, landAt, altM, start)
        } else {
            s.sunEvent?.takeIf { it.atMs > now }
        }

        val bump = CabinMath.bumpiness(accel.toList())
        val widebody = AircraftTypes.silhouetteFor(seg.aircraftType, null).let { it == Silhouette.Widebody || it == Silhouette.Jumbo || it == Silhouette.Quad || it == Silhouette.WideTri }
        _state.update {
            it.copy(
                route = route,
                phase = phase,
                position = pos,
                headingDeg = heading,
                fraction = fraction,
                source = source,
                gpsAgeSec = lastFix?.let { f -> (now - f.time) / 1000 },
                satellites = lastFix?.extras?.getInt("satellites")?.takeIf { n -> n > 0 },
                groundSpeedKt = speed,
                altitudeFt = altitude,
                cabinAltitudeFt = pressure?.let(CabinMath::pressureAltitudeFt),
                bumpiness = bump,
                roughestToday = maxOf(it.roughestToday, bump),
                minutesLeft = ((landAt - now) / 60_000).toInt().takeIf { phase != CabinPhase.Landed },
                landingMs = landAt,
                upcoming = upcoming.take(6),
                nextBorder = border,
                sunEvent = sunEvent,
                sunSide = if (sunEl > horizon) CabinMath.sideOf(pos, heading, Sun.subSolarPoint(now)) else null,
                sunElevationDeg = sunEl,
                window = CabinMath.windowSide(seg.seat, widebody),
                captured = captured.toList(),
                nowMs = now,
            )
        }
    }

    /** Saves a moment once: wheels up, a sunset aloft, a new country below. */
    private suspend fun capture(key: String, kind: MomentKind, title: String, at: LatLon?) {
        if (!captured.add(key)) return
        val seg = _state.value.segment ?: return
        val existing = moments.observeStored().first().any { it.segmentId == segmentId && it.title == title }
        if (existing) return
        val date = Instant.ofEpochMilli(clock.nowMs()).atZone(ZoneId.systemDefault()).toLocalDate()
        moments.save(null, kind, title, "${seg.designator} · captured in Cabin mode", date, segmentId, seg.tail, null, source = "cabin", lat = at?.lat, lon = at?.lon)
    }

    private companion object {
        /** When there's no speed at all: a typical airliner cruise. */
        const val TYPICAL_CRUISE_KT = 470.0
    }
}

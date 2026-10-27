package com.contrail.contrail.core.data.sky

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.model.LiveAircraft
import com.contrail.contrail.core.data.model.Watchlist
import com.contrail.contrail.core.data.reference.Rarity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** When a plane will pass closest to you, and how close. */
data class Approach(val inSeconds: Long, val minDistanceKm: Double)

/** One aircraft as seen from where you stand. */
data class SkyContact(
    val aircraft: LiveAircraft,
    val distanceKm: Double,
    /** Compass bearing from you to it, 0 = north. */
    val bearingDeg: Double,
    /** Angle above the horizon. */
    val elevationDeg: Double,
    /** Null when it's moving away or not moving. */
    val approach: Approach?,
    /** Unit vector toward it: east, north, up. */
    val east: Double,
    val north: Double,
    val up: Double,
) {
    val worthLookingUp: Boolean get() = aircraft.rarity < Rarity.Common || aircraft.military || aircraft.isEmergency
}

object SkyGeometry {
    private const val FT_TO_M = 0.3048
    private const val KT_TO_KMS = 1.852 / 3600.0

    /**
     * Where [a] sits in your sky from [me]: distance along the ground, compass bearing, and
     * the angle above the horizon (with the Earth's curvature taken off at long range).
     */
    fun contact(me: LatLon, a: LiveAircraft, nowMs: Long = a.positionTimeMs): SkyContact {
        // Dead-reckon to now so a label points where the plane is, not where it was.
        val p = project(a, nowMs)
        val groundM = Geo.distanceM(me, p)
        val bearing = Geo.bearingDeg(me, p)
        val altM = (a.geometricAltitudeFt ?: a.altitudeFt ?: 0) * FT_TO_M
        val drop = groundM * groundM / (2 * Geo.EARTH_RADIUS_M)
        val heightM = (altM - drop).coerceAtLeast(-groundM)
        val elevation = Math.toDegrees(atan2(heightM, groundM.coerceAtLeast(1.0)))
        val b = Math.toRadians(bearing)
        val e = Math.toRadians(elevation)
        return SkyContact(
            aircraft = a,
            distanceKm = hypot(groundM, heightM.coerceAtLeast(0.0)) / 1000,
            bearingDeg = bearing,
            elevationDeg = elevation,
            approach = approach(me, a, p),
            east = cos(e) * sin(b),
            north = cos(e) * cos(b),
            up = sin(e),
        )
    }

    /** Where [a] is now, moved along its track at its ground speed since its last position. */
    fun project(a: LiveAircraft, nowMs: Long): LatLon {
        val here = LatLon(a.lat, a.lon)
        val speed = a.groundSpeedKt ?: return here
        val track = a.trackDeg ?: return here
        val dt = ((nowMs - a.positionTimeMs) / 1000.0).coerceIn(0.0, 60.0)
        if (dt <= 0 || a.onGround) return here
        return Geo.destination(here, track.toDouble(), speed * KT_TO_KMS * 1000 * dt)
    }

    /** Closest point of approach on a straight track, within the next 30 minutes. */
    fun approach(me: LatLon, a: LiveAircraft, at: LatLon = LatLon(a.lat, a.lon)): Approach? {
        val speed = a.groundSpeedKt ?: return null
        val track = a.trackDeg ?: return null
        if (speed < 30 || a.onGround) return null
        // Flat east/north kilometres around you are plenty for 30 minutes of flight.
        val km = Geo.distanceM(me, at) / 1000
        val brg = Math.toRadians(Geo.bearingDeg(me, at))
        val px = km * sin(brg)
        val py = km * cos(brg)
        val v = speed * KT_TO_KMS
        val vx = v * sin(Math.toRadians(track.toDouble()))
        val vy = v * cos(Math.toRadians(track.toDouble()))
        val t = -(px * vx + py * vy) / (vx * vx + vy * vy)
        if (t <= 0 || t > 1_800) return null
        val dx = px + vx * t
        val dy = py + vy * t
        return Approach(t.toLong(), sqrt(dx * dx + dy * dy))
    }

    fun contacts(me: LatLon, traffic: List<LiveAircraft>, radiusKm: Double, nowMs: Long): List<SkyContact> =
        traffic.asSequence()
            .filterNot { it.onGround }
            .map { contact(me, it, nowMs) }
            .filter { it.distanceKm <= radiusKm }
            .sortedBy { it.distanceKm }
            .toList()
}

/** What's worth a notification. */
data class AlertRules(
    val enabled: Boolean = false,
    val rare: Boolean = true,
    val military: Boolean = true,
    val emergency: Boolean = true,
    val watchlist: Boolean = true,
    /** Anything below this many feet, or null to skip. */
    val lowerThanFt: Int? = null,
) {
    /** Why [c] deserves an alert, or null when it doesn't. */
    fun reason(c: SkyContact, watch: Watchlist): String? {
        val a = c.aircraft
        return when {
            emergency && a.isEmergency -> LiveAircraft.emergencyLabel(a.squawk) ?: "Emergency"
            watchlist && watch.matches(a) -> "On your watchlist"
            rare && a.rarity < Rarity.Common -> "${a.rarity.label} aircraft"
            military && a.military -> "Military"
            lowerThanFt != null && (a.altitudeFt ?: Int.MAX_VALUE) < lowerThanFt -> "Low and close"
            else -> null
        }
    }
}

data class SkySettings(
    val radiusKm: Int = 25,
    val alerts: AlertRules = AlertRules(),
    /** Where you last opened Sky: alerts and the overhead widget look here, so no background location is needed. */
    val spot: LatLon? = null,
    val spotName: String? = null,
)

@Singleton
class SkySettingsRepository @Inject constructor(@Named("sky") private val store: DataStore<Preferences>) {
    private val radius = intPreferencesKey("radius")
    private val enabled = booleanPreferencesKey("alerts.enabled")
    private val rare = booleanPreferencesKey("alerts.rare")
    private val military = booleanPreferencesKey("alerts.military")
    private val emergency = booleanPreferencesKey("alerts.emergency")
    private val watch = booleanPreferencesKey("alerts.watch")
    private val low = intPreferencesKey("alerts.low")
    private val lat = doublePreferencesKey("spot.lat")
    private val lon = doublePreferencesKey("spot.lon")
    private val spotName = stringPreferencesKey("spot.name")
    private val notified = stringPreferencesKey("alerts.notified")
    private val notifiedAt = longPreferencesKey("alerts.notifiedAt")

    val settings: Flow<SkySettings> = store.data.map { p ->
        SkySettings(
            radiusKm = p[radius] ?: 25,
            alerts = AlertRules(p[enabled] ?: false, p[rare] ?: true, p[military] ?: true, p[emergency] ?: true, p[watch] ?: true, p[low]?.takeIf { it > 0 }),
            spot = p[lat]?.let { la -> p[lon]?.let { lo -> LatLon(la, lo) } },
            spotName = p[spotName],
        )
    }

    suspend fun current(): SkySettings = settings.first()

    suspend fun setRadius(km: Int) = store.edit { it[radius] = km.coerceIn(5, 100) }

    suspend fun setAlerts(a: AlertRules) = store.edit {
        it[enabled] = a.enabled; it[rare] = a.rare; it[military] = a.military; it[emergency] = a.emergency; it[watch] = a.watchlist
        it[low] = a.lowerThanFt ?: 0
    }

    suspend fun setSpot(p: LatLon, name: String?) = store.edit {
        it[lat] = p.lat; it[lon] = p.lon
        if (name != null) it[spotName] = name
    }

    /** Hex codes already notified in the last few hours, so one plane alerts once. */
    suspend fun recentlyNotified(nowMs: Long): Set<String> {
        val p = store.data.first()
        if (nowMs - (p[notifiedAt] ?: 0) > 3 * 3_600_000L) return emptySet()
        return p[notified]?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty()
    }

    suspend fun markNotified(hexes: Set<String>, nowMs: Long) = store.edit { p ->
        val keep = if (nowMs - (p[notifiedAt] ?: 0) > 3 * 3_600_000L) emptySet() else p[notified]?.split(',')?.toSet().orEmpty()
        p[notified] = (keep + hexes).toList().takeLast(200).joinToString(",")
        p[notifiedAt] = nowMs
    }
}

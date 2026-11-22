package com.contrail.contrail.core.data.events

import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.model.AirportRef
import com.contrail.contrail.core.data.model.LiveAircraft
import com.contrail.contrail.core.data.reference.Rarity
import com.contrail.contrail.core.data.repository.AircraftDetailsRepository
import com.contrail.contrail.core.data.repository.AirportRepository
import com.contrail.contrail.core.data.repository.LiveTrafficRepository
import com.contrail.contrail.core.data.route.RouteCheck
import com.contrail.contrail.core.data.route.RouteVerdict
import com.contrail.contrail.core.database.ContrailDatabase
import com.contrail.contrail.core.database.HappeningEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** One item in Happening now. */
data class Happening(
    val id: String,
    val kind: HappeningKind,
    /** Near one of your airports, somewhere in the world, or one of your Hangar planes. */
    val scope: Scope,
    val hex: String,
    val callsign: String?,
    val registration: String?,
    val typeCode: String?,
    val title: String,
    val detail: String,
    val airportIcao: String?,
    val lat: Double,
    val lon: Double,
    val timeMs: Long,
) {
    enum class Scope(val key: String) { Near("near"), World("world"), Hangar("hangar") }
}

@Singleton
class HappeningRepository @Inject constructor(db: ContrailDatabase, private val clock: Clock) {
    private val dao = db.happeningDao()

    /** The last [hours] hours, newest first. */
    fun observe(hours: Int = 12): Flow<List<Happening>> =
        dao.observeSince(clock.nowMs() - hours * 3_600_000L).map { rows -> rows.mapNotNull { it.toModel() } }

    suspend fun record(events: List<Happening>) {
        if (events.isEmpty()) return
        dao.insert(events.map { HappeningEntity(it.id, it.kind.key, it.scope.key, it.hex, it.callsign, it.registration, it.typeCode, it.title, it.detail, it.airportIcao, it.lat, it.lon, it.timeMs) })
    }

    suspend fun prune() = dao.prune(clock.nowMs() - 48 * 3_600_000L)

    private fun HappeningEntity.toModel(): Happening? {
        val k = HappeningKind.of(kind) ?: return null
        val s = Happening.Scope.entries.firstOrNull { it.key == scope } ?: Happening.Scope.World
        return Happening(id, k, s, hex, callsign, registration, typeCode, title, detail, airportIcao, lat, lon, timeMs)
    }
}

/**
 * Watches whatever live traffic the app already has (the radar, the Airports tab's watch on your
 * favourite airports, Sky, worldwide emergency squawks) and writes what's worth knowing to
 * Happening now. Costs no requests of its own, apart from route look-ups for planes coming down
 * near your favourite airports.
 */
@Singleton
class EventMonitor @Inject constructor(
    private val traffic: LiveTrafficRepository,
    private val airports: AirportRepository,
    private val details: AircraftDetailsRepository,
    private val happenings: HappeningRepository,
    private val clock: Clock,
) {
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            happenings.prune()
            combine(traffic.observeTraffic(), traffic.observeTrails(), airports.favourites()) { list, trails, favs -> Triple(list, trails, favs) }
                .debounce(4_000)
                .collectLatest { (list, trails, favs) -> runCatching { scan(list, trails, favs.map { it.toRef() to (it.elevationFt ?: 0) }) } }
        }
    }

    private suspend fun scan(
        list: List<LiveAircraft>,
        trails: Map<String, List<com.contrail.contrail.core.data.model.TrailPoint>>,
        favourites: List<Pair<AirportRef, Int>>,
    ) {
        val now = clock.nowMs()
        val out = mutableListOf<Happening>()
        fun nearFavourite(a: LiveAircraft, withinM: Double) = favourites.firstOrNull { (ap, _) -> Geo.distanceM(a.lat, a.lon, ap.lat, ap.lon) < withinM }
        fun scopeFor(a: LiveAircraft) = if (nearFavourite(a, 150_000.0) != null) Happening.Scope.Near else Happening.Scope.World
        suspend fun placeName(a: LiveAircraft): String? = airports.near(LatLon(a.lat, a.lon), 250_000.0).firstOrNull()?.let { it.city ?: it.name }

        for (a in list) {
            val trail = trails[a.hex].orEmpty()
            val name = a.typeName ?: a.typeCode ?: "aircraft"
            // Emergencies, anywhere Contrail sees them.
            if (a.isEmergency) {
                val where = placeName(a)?.let { "near $it" } ?: "over ${String.format(Locale.US, "%.1f, %.1f", a.lat, a.lon)}"
                out += event(HappeningKind.Emergency, scopeFor(a), a, now, bucketMs = 3_600_000L,
                    title = "${a.displayCallsign} is squawking ${a.squawk}",
                    detail = "${LiveAircraft.emergencyLabel(a.squawk) ?: "Emergency"} · $name · $where")
            }
            val alt = a.altitudeFt ?: continue
            // Go-arounds: low and close to an airport, then climbing away.
            if (!a.onGround && alt < 6_000 && trail.size >= 3) {
                val airport = airports.near(LatLon(a.lat, a.lon), 15_000.0).firstOrNull()
                if (airport != null) {
                    val elevation = favourites.firstOrNull { it.first.icao == airport.icao }?.second ?: 0
                    if (EventDetector.isGoAround(trail, a, airport, elevation)) {
                        out += event(HappeningKind.GoAround, scopeFor(a), a, now, bucketMs = 30 * 60_000L, airport = airport,
                            title = "${a.displayCallsign} went around at ${airport.city ?: airport.code}",
                            detail = "$name · climbing away after coming down to ${String.format(Locale.US, "%,d", trail.mapNotNull { it.altitudeFt }.minOrNull() ?: alt)} ft")
                    }
                }
            }
            // Holding: circling while level.
            if (!a.onGround && alt in 4_000..30_000 && EventDetector.isHolding(trail, a)) {
                val airport = airports.near(LatLon(a.lat, a.lon), 120_000.0).firstOrNull()
                out += event(HappeningKind.Holding, scopeFor(a), a, now, bucketMs = 3_600_000L, airport = airport,
                    title = "${a.displayCallsign} is holding" + (airport?.let { " near ${it.city ?: it.code}" } ?: ""),
                    detail = "$name · circling at ${String.format(Locale.US, "%,d", alt)} ft")
            }
        }

        // Near your favourite airports: rare visitors and diversions.
        for ((ap, _) in favourites) {
            val around = list.filter { !it.onGround && Geo.distanceM(it.lat, it.lon, ap.lat, ap.lon) < 150_000 }
            for (a in around) {
                if (a.rarity == Rarity.Legendary || a.rarity == Rarity.Rare) {
                    out += event(HappeningKind.RareVisitor, Happening.Scope.Near, a, now, bucketMs = 12 * 3_600_000L, airport = ap,
                        title = "${a.typeName ?: a.typeCode} near ${ap.city ?: ap.code}",
                        detail = listOfNotNull(a.displayCallsign, a.registration, a.airline?.name, "${a.rarity.label.lowercase()} type").joinToString(" · "))
                }
                // Coming down: does its route still fit? Only routes already looked up (the Spotter's board fetches them).
                val alt = a.altitudeFt ?: continue
                val cs = a.callsign?.trim()?.ifEmpty { null } ?: continue
                if (alt > 12_000 || (a.verticalRateFpm ?: 0) > -400) continue
                val route = details.observeRoute(cs).first() ?: continue
                val v = RouteCheck.check(route, a, airports.near(LatLon(a.lat, a.lon), 160_000.0))
                if (v is RouteVerdict.Landing && v.diverting) {
                    out += event(HappeningKind.Diversion, Happening.Scope.Near, a, now, bucketMs = 12 * 3_600_000L, airport = v.at,
                        title = "${a.displayCallsign} may be diverting to ${v.at.city ?: v.at.code}",
                        detail = "Its callsign says ${route.origin.code} → ${route.destination.code} · ${a.typeName ?: a.typeCode ?: "aircraft"} · estimated")
                }
            }
        }
        happenings.record(out)
    }

    private fun event(
        kind: HappeningKind,
        scope: Happening.Scope,
        a: LiveAircraft,
        now: Long,
        bucketMs: Long,
        title: String,
        detail: String,
        airport: AirportRef? = null,
    ) = Happening(
        id = "${kind.key}:${a.hex}:${airport?.icao.orEmpty()}:${now / bucketMs}",
        kind = kind, scope = scope, hex = a.hex, callsign = a.callsign?.trim(), registration = a.registration, typeCode = a.typeCode,
        title = title, detail = detail, airportIcao = airport?.icao, lat = a.lat, lon = a.lon, timeMs = now,
    )

    private fun com.contrail.contrail.core.database.AirportWithUser.toRef() = AirportRef(iata, icao, name, city, countryIso, lat, lon)
}

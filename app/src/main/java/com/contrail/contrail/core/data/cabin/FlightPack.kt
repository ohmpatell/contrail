package com.contrail.contrail.core.data.cabin

import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.repository.AirportInfoRepository
import com.contrail.contrail.core.data.repository.AirportRepository
import com.contrail.contrail.core.data.repository.TripRepository
import com.contrail.contrail.core.data.rotation.RotationRepository
import com.contrail.contrail.core.database.CabinTrackEntity
import com.contrail.contrail.core.database.ContrailDatabase
import com.contrail.contrail.core.database.FlightPackEntity
import com.contrail.contrail.core.network.wiki.WikipediaSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything Cabin mode needs with no internet, gathered on the ground: the route (as flown
 * recently, or the great circle), places along it with a line from Wikipedia, the countries it
 * crosses, and the weather where you're going. Map tiles are stored separately by MapLibre.
 */
@Serializable
data class FlightPack(
    val segmentId: Long,
    val path: List<Pt>,
    /** "recent" when it's the path this flight actually flew lately, "great-circle" otherwise. */
    val pathSource: String,
    val places: List<Place>,
    val borders: List<Border>,
    val weather: String? = null,
    val forecast: String? = null,
    val createdAtMs: Long,
    val mapReady: Boolean = false,
) {
    @Serializable data class Pt(val lat: Double, val lon: Double)
    @Serializable data class Place(val name: String, val iso: String? = null, val lat: Double, val lon: Double, val blurb: String? = null, val along: Double)
    @Serializable data class Border(val iso: String, val name: String? = null, val along: Double)

    val route: List<LatLon> get() = path.map { LatLon(it.lat, it.lon) }
    val packPlaces: List<PackPlace> get() = places.map { PackPlace(it.name, it.iso, it.lat, it.lon, it.blurb, it.along) }
    val crossings: List<BorderCrossing> get() = borders.map { BorderCrossing(it.iso, it.name, it.along) }
}

@Singleton
class FlightPackRepository @Inject constructor(
    db: ContrailDatabase,
    private val trips: TripRepository,
    private val airports: AirportRepository,
    private val info: AirportInfoRepository,
    private val rotation: RotationRepository,
    private val wiki: WikipediaSource,
    private val clock: Clock,
) {
    private val dao = db.cabinDao()
    private val json = Json { ignoreUnknownKeys = true }

    fun observe(segmentId: Long): Flow<FlightPack?> = dao.observePack(segmentId).map { row ->
        row?.let { runCatching { json.decodeFromString<FlightPack>(it.json) }.getOrNull() }
    }

    suspend fun get(segmentId: Long): FlightPack? = dao.pack(segmentId)?.let { runCatching { json.decodeFromString<FlightPack>(it.json) }.getOrNull() }

    suspend fun markMapReady(segmentId: Long) {
        val p = get(segmentId) ?: return
        save(p.copy(mapReady = true))
    }

    /**
     * Builds the pack while you're online. [progress] gets a short line for each step. Wikipedia
     * look-ups are capped so a long-haul pack stays quick and polite.
     */
    suspend fun build(segmentId: Long, progress: (String) -> Unit = {}): FlightPack? {
        val seg = trips.segment(segmentId).first()?.second ?: return null
        val dir = airports.directory()
        val from = dir.find(seg.origin) ?: return null
        val to = dir.find(seg.destination) ?: return null
        val a = LatLon(from.lat, from.lon)
        val b = LatLon(to.lat, to.lon)

        progress("Finding the route it really flies…")
        val recent = seg.icaoCallsign?.let { cs -> runCatching { rotation.recentPath(cs, to.icao) }.getOrNull() }
        val route = recent?.takeIf { it.size >= 8 }?.let { listOf(a) + it + b } ?: Geo.greatCircle(a, b, 128)
        val source = if (recent != null && recent.size >= 8) "recent" else "great-circle"

        progress("Looking for places along the way…")
        val total = CabinMath.length(route).coerceAtLeast(1.0)
        val step = (total / 90).coerceIn(40_000.0, 120_000.0)
        val samples = mutableListOf<Pair<Double, LatLon>>()
        var d = 0.0
        while (d <= total) {
            samples += (d / total) to CabinMath.pointAt(route, d / total).first
            d += step
        }
        val seen = HashSet<String>()
        val found = mutableListOf<FlightPack.Place>()
        val countries = mutableListOf<Pair<Double, String?>>()
        for ((along, p) in samples) {
            val near = airports.majorNear(p, 300_000.0)
            countries += along to near.firstOrNull { Geo.distanceM(p.lat, p.lon, it.lat, it.lon) < 250_000 }?.countryIso
            near.filter { Geo.distanceM(p.lat, p.lon, it.lat, it.lon) < 120_000 }.take(2).forEach { ap ->
                val city = ap.city.substringBefore(" (").ifBlank { return@forEach }
                if (city.equals(from.city, ignoreCase = true) || city.equals(to.city, ignoreCase = true)) return@forEach
                if (seen.add(city.lowercase())) found += FlightPack.Place(city, ap.countryIso, ap.lat, ap.lon, null, CabinMath.fractionOf(route, LatLon(ap.lat, ap.lon)))
            }
        }
        // Thin to about one place every 150 km, keeping the spread.
        val spaced = found.sortedBy { it.along }.fold(mutableListOf<FlightPack.Place>()) { acc, p ->
            if (acc.isEmpty() || (p.along - acc.last().along) * total > 150_000) acc += p
            acc
        }.take(MAX_PLACES)

        progress("Reading up on ${spaced.size} places…")
        val withBlurbs = spaced.mapIndexed { i, p ->
            if (i >= MAX_WIKI) return@mapIndexed p
            val countryName = p.iso?.let { Locale("", it).getDisplayCountry(Locale.UK) }
            val summary = runCatching { wiki.summary(p.name) }.getOrNull()
                ?: countryName?.let { runCatching { wiki.summary("${p.name}, $it") }.getOrNull() }
            p.copy(blurb = summary?.let { listOfNotNull(it.description?.replaceFirstChar { c -> c.uppercase() }, it.extract).joinToString(". ").take(320) })
        }
        val borders = CabinMath.borders(countries).map { (along, iso) -> FlightPack.Border(iso, Locale("", iso).getDisplayCountry(Locale.UK), along) }

        progress("Checking the weather at ${to.city}…")
        runCatching { info.refreshWeather(to.icao) }
        val wx = info.observeWeather(to.icao).first()
        val metar = wx?.metar
        val weather = metar?.let { m ->
            listOfNotNull(m.temp?.let { "${it.toInt()}°C" }, m.wxString, m.fltCat).joinToString(" · ").ifBlank { null }
        }
        val pack = FlightPack(
            segmentId = segmentId,
            path = thin(route).map { FlightPack.Pt(it.lat, it.lon) },
            pathSource = source,
            places = withBlurbs,
            borders = borders,
            weather = weather,
            forecast = wx?.taf?.rawTAF,
            createdAtMs = clock.nowMs(),
            mapReady = get(segmentId)?.mapReady == true,
        )
        save(pack)
        return pack
    }

    suspend fun delete(segmentId: Long) = dao.deletePack(segmentId)

    /** Records one GPS fix: the real path you flew. */
    suspend fun recordFix(segmentId: Long, at: Long, p: LatLon, altitudeFt: Int?, speedKt: Float?) =
        dao.insertFix(CabinTrackEntity(segmentId, at, p.lat, p.lon, altitudeFt, speedKt))

    fun observeTrack(segmentId: Long): Flow<List<CabinTrackEntity>> = dao.observeTrack(segmentId)

    private suspend fun save(p: FlightPack) = dao.upsertPack(FlightPackEntity(p.segmentId, json.encodeToString(FlightPack.serializer(), p), p.createdAtMs))

    /** At most a few hundred points: plenty for drawing and maths. */
    private fun thin(points: List<LatLon>): List<LatLon> {
        if (points.size <= 400) return points
        val every = points.size / 400.0
        return (0 until 400).map { points[(it * every).toInt()] } + points.last()
    }

    private companion object {
        const val MAX_PLACES = 60
        const val MAX_WIKI = 40
    }
}

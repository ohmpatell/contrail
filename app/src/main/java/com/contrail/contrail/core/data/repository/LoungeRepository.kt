package com.contrail.contrail.core.data.repository

import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.data.reference.Lounges
import com.contrail.contrail.core.database.CachedResponseEntity
import com.contrail.contrail.core.database.ContrailDatabase
import com.contrail.contrail.core.database.LoungeEntity
import com.contrail.contrail.core.database.LoungeVisitEntity
import com.contrail.contrail.core.network.airport.OverpassParser
import com.contrail.contrail.core.network.airport.OverpassSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

enum class LoungeSource(val label: String) { Curated("Contrail list"), OpenStreetMap("OpenStreetMap"), Yours("Added by you") }

data class Lounge(
    val key: String,
    val airport: String,
    val terminal: String?,
    val name: String,
    val source: LoungeSource,
    val favourite: Boolean = false,
)

enum class LoungeAccess(val label: String) {
    Cabin("Cabin"),
    Status("Status"),
    Card("Card"),
    Paid("Paid"),
}

enum class LoungePhase { Departure, Arrival }

data class LoungeVisit(
    val id: Long,
    val segmentId: Long?,
    val phase: LoungePhase,
    val loungeKey: String,
    val loungeName: String,
    val airport: String,
    val terminal: String?,
    val date: LocalDate,
    val timeIn: LocalTime?,
    val timeOut: LocalTime?,
    val access: LoungeAccess,
    val rating: Int,
    val note: String,
) {
    /** Time spent, crossing midnight if you left after it. */
    val duration: Duration?
        get() {
            val a = timeIn ?: return null
            val b = timeOut ?: return null
            val d = Duration.between(a, b)
            return if (d.isNegative) d.plusHours(24) else d
        }
}

/**
 * Lounge suggestions (curated list, OpenStreetMap, and lounges you've added) and your visits.
 * OSM results are cached for 30 days per airport.
 */
@Singleton
class LoungeRepository @Inject constructor(
    db: ContrailDatabase,
    private val overpass: OverpassSource,
    private val clock: Clock,
) {
    private val dao = db.loungeDao()
    private val cache = db.cacheDao()

    val visits: Flow<List<LoungeVisit>> = dao.observeVisits().map { rows -> rows.map { it.toDomain() } }

    val stored: Flow<List<LoungeEntity>> = dao.observeAll()

    /** Lounges at [airport]: yours first, then the curated list, then OpenStreetMap, without duplicates. */
    fun suggestions(airport: String): Flow<List<Lounge>> {
        val code = airport.uppercase()
        return combine(dao.forAirport(code), cache.observe(osmKey(code))) { mine, osm ->
            val favourites = mine.filter { it.favourite }.map { it.key }.toSet()
            val out = LinkedHashMap<String, Lounge>()
            mine.filter { it.userAdded }.forEach { out[it.key] = Lounge(it.key, code, it.terminal, it.name, LoungeSource.Yours) }
            Lounges.at(code).forEach { c ->
                val key = Lounges.key(code, c.name)
                out.putIfAbsent(key, Lounge(key, code, c.terminal, c.name, LoungeSource.Curated))
            }
            osm?.body?.let { osmLounges(code, it) }?.forEach { l -> out.putIfAbsent(l.key, l) }
            out.values.map { it.copy(favourite = it.key in favourites) }
        }
    }

    suspend fun refreshOsm(airport: String, lat: Double, lon: Double) {
        val key = osmKey(airport)
        val cached = cache.get(key)
        if (cached != null && clock.nowMs() - cached.fetchedAtMs < OSM_TTL_MS) return
        val body = runCatching { overpass.lounges(lat, lon) }.getOrNull() ?: return
        cache.put(CachedResponseEntity(key, body, clock.nowMs()))
    }

    /** Remembers a lounge you typed in, so it's suggested next time. */
    suspend fun addLounge(airport: String, terminal: String?, name: String): Lounge {
        val key = Lounges.key(airport, name)
        val existing = dao.get(key)
        dao.upsert(
            LoungeEntity(key, airport.uppercase(), terminal?.ifBlank { null }, name.trim(), userAdded = true, favourite = existing?.favourite ?: false, addedAtMs = clock.nowMs()),
        )
        return Lounge(key, airport.uppercase(), terminal, name.trim(), LoungeSource.Yours)
    }

    suspend fun setFavourite(lounge: Lounge, favourite: Boolean) {
        val existing = dao.get(lounge.key)
        dao.upsert(
            existing?.copy(favourite = favourite)
                ?: LoungeEntity(lounge.key, lounge.airport, lounge.terminal, lounge.name, userAdded = false, favourite = favourite, addedAtMs = clock.nowMs()),
        )
    }

    suspend fun saveVisit(v: LoungeVisit): Long {
        val entity = LoungeVisitEntity(
            id = v.id, segmentId = v.segmentId, phase = v.phase.name, loungeKey = v.loungeKey, loungeName = v.loungeName,
            airport = v.airport, terminal = v.terminal, date = v.date.toString(), timeIn = v.timeIn?.toString(), timeOut = v.timeOut?.toString(),
            access = v.access.name, rating = v.rating.coerceIn(1, 5), note = v.note.trim(), createdAtMs = clock.nowMs(),
        )
        return if (v.id == 0L) dao.insertVisit(entity) else { dao.updateVisit(entity); v.id }
    }

    suspend fun visit(id: Long): LoungeVisit? = dao.visit(id)?.toDomain()

    suspend fun deleteVisit(id: Long) = dao.deleteVisit(id)

    companion object {
        const val OSM_TTL_MS = 30 * 86_400_000L
        private fun osmKey(airport: String) = "osm-lounges:${airport.uppercase()}"

        /** Named OSM lounges that aren't bus, limousine or TV waiting areas. */
        fun osmLounges(airport: String, body: String): List<Lounge> {
            val skip = Regex("(?i)bus|limousine|tv lounge|ground transport|smoking|prayer|waiting|nursing|baby")
            return OverpassParser.parse(body).elements.mapNotNull { e ->
                val name = (e.tags["name:en"] ?: e.tags["name"])?.trim() ?: return@mapNotNull null
                if (skip.containsMatchIn(name) || e.tags["access"] == "no") return@mapNotNull null
                Lounge(Lounges.key(airport, name), airport, null, name, LoungeSource.OpenStreetMap)
            }.distinctBy { it.key }
        }

        fun LoungeVisitEntity.toDomain() = LoungeVisit(
            id = id, segmentId = segmentId,
            phase = LoungePhase.entries.firstOrNull { it.name == phase } ?: LoungePhase.Departure,
            loungeKey = loungeKey, loungeName = loungeName, airport = airport, terminal = terminal,
            date = LocalDate.parse(date), timeIn = timeIn?.let(LocalTime::parse), timeOut = timeOut?.let(LocalTime::parse),
            access = LoungeAccess.entries.firstOrNull { it.name == access } ?: LoungeAccess.Cabin,
            rating = rating, note = note,
        )
    }
}

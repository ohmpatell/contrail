package com.contrail.contrail.core.data.repository

import androidx.room.withTransaction
import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.database.AirportEntity
import com.contrail.contrail.core.database.AirportUserEntity
import com.contrail.contrail.core.database.AirportWithUser
import com.contrail.contrail.core.database.ContrailDatabase
import com.contrail.contrail.core.database.FrequencyEntity
import com.contrail.contrail.core.database.RunwayEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.contrail.contrail.core.data.ticket.AirportCodes
import com.contrail.contrail.core.data.ticket.AirportDirectory
import com.contrail.contrail.core.data.ticket.AirportInfo
import java.io.InputStream
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/** Opens the bundled airports.json (an asset in the app, a file in tests). */
fun interface AirportsAsset {
    fun open(): InputStream
}

/**
 * Every airport with scheduled service, bundled from OurAirports and imported into Room the
 * first time it's needed, so search works offline and instantly.
 */
@Singleton
class AirportRepository @Inject constructor(
    private val db: ContrailDatabase,
    private val asset: AirportsAsset,
    private val clock: Clock,
) {
    private val dao = db.airportDao()
    private val importLock = Mutex()
    @Volatile private var imported = false

    /** Imports the bundled dataset if the table is empty. Safe to call repeatedly. */
    suspend fun ensureImported() {
        if (imported) return
        importLock.withLock {
            if (imported) return
            withContext(Dispatchers.IO) {
                if (dao.count() == 0) import()
            }
            imported = true
        }
    }

    fun search(query: String): Flow<List<AirportWithUser>> = flow {
        ensureImported()
        val q = query.trim()
        emitAll(dao.search(code = q.uppercase(), prefix = "$q%", contains = "%$q%"))
    }

    fun observe(icao: String): Flow<AirportWithUser?> = flow {
        ensureImported()
        emitAll(dao.observe(icao.uppercase()))
    }

    fun favourites(): Flow<List<AirportWithUser>> = flow {
        ensureImported()
        emitAll(dao.observeMine(favourites = true, visited = false))
    }

    fun visited(): Flow<List<AirportWithUser>> = flow {
        ensureImported()
        emitAll(dao.observeMine(favourites = false, visited = true))
    }

    fun runways(icao: String): Flow<List<RunwayEntity>> = dao.runways(icao.uppercase())

    fun frequencies(icao: String): Flow<List<FrequencyEntity>> = dao.frequencies(icao.uppercase())

    suspend fun byIata(iata: String): AirportEntity? {
        ensureImported()
        return dao.byIata(iata.uppercase())
    }

    suspend fun byIatas(iatas: Collection<String>): Map<String, AirportEntity> {
        ensureImported()
        return iatas.map { it.uppercase() }.distinct().chunked(500)
            .flatMap { dao.byIatas(it) }
            .associateBy { it.iata }
    }

    @Volatile private var directoryCache: Map<String, AirportInfo>? = null

    /** Every airport by IATA code, in memory, for ticket import and trip maths. */
    suspend fun directory(): AirportDirectory {
        val map = directoryCache ?: run {
            ensureImported()
            withContext(Dispatchers.IO) {
                dao.allWithIata()
                    // Several entries can share a code; the biggest airport wins.
                    .sortedByDescending { when (it.size) { "L" -> 2; "M" -> 1; else -> 0 } }
                    .distinctBy { it.iata }
                    .associate { a ->
                        a.iata to AirportInfo(
                            a.iata, a.icao, a.city.substringBefore(" (").ifBlank { a.name }, a.countryIso,
                            runCatching { ZoneId.of(a.timezone) }.getOrDefault(ZoneOffset.UTC), a.lat, a.lon,
                        )
                    }
            }.also { directoryCache = it }
        }
        return AirportDirectory { map[it.uppercase()] }
    }

    /** IATA code for an ICAO code (OpenSky reports ICAO), from the same in-memory table. */
    suspend fun iataForIcao(icao: String?): String? {
        if (icao == null) return null
        directory()
        val byIcao = icaoIndex ?: directoryCache.orEmpty().values.associate { it.icao to it.iata }.also { icaoIndex = it }
        return byIcao[icao.uppercase()]
    }

    @Volatile private var icaoIndex: Map<String, String>? = null

    /** The nearest airport with scheduled service to [p], for naming where you are. */
    suspend fun nearest(p: com.contrail.contrail.core.common.LatLon): AirportInfo? {
        directory()
        return directoryCache.orEmpty().values.minByOrNull { com.contrail.contrail.core.common.Geo.distanceM(p.lat, p.lon, it.lat, it.lon) }
    }

    /** ICAO → IATA as a plain function, for parsers that run off the main thread. */
    suspend fun icaoResolver(): (String) -> String? {
        directory()
        val byIcao = icaoIndex ?: directoryCache.orEmpty().values.associate { it.icao to it.iata }.also { icaoIndex = it }
        return { byIcao[it.uppercase()] }
    }

    suspend fun codes(): AirportCodes {
        val dir = directory()
        return AirportCodes { dir.find(it) != null }
    }

    suspend fun setFavourite(icao: String, favourite: Boolean) = updateUser(icao) { it.copy(favourite = favourite) }

    suspend fun setVisited(icao: String, visited: Boolean) = updateUser(icao) { it.copy(visited = visited) }

    private suspend fun updateUser(icao: String, change: (AirportUserEntity) -> AirportUserEntity) {
        val key = icao.uppercase()
        val current = dao.user(key) ?: AirportUserEntity(key, favourite = false, visited = false, updatedAtMs = 0)
        dao.upsertUser(change(current).copy(updatedAtMs = clock.nowMs()))
    }

    private suspend fun import() {
        val root = asset.open().bufferedReader().use { Json.parseToJsonElement(it.readText()) }.jsonObject
        val countries = root["countries"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content }.orEmpty()
        val airports = root.getValue("airports").jsonArray.map { e ->
            val a = e.jsonArray
            AirportEntity(
                icao = a.str(0), iata = a.str(1), name = a.str(2), city = a.str(3), countryIso = a.str(4),
                countryName = countries[a.str(4)] ?: a.str(4),
                lat = a.dbl(5) ?: 0.0, lon = a.dbl(6) ?: 0.0, elevationFt = a.int(7),
                size = a.str(8), scheduled = a.int(9) == 1, timezone = a.str(10),
            )
        }
        val runways = root.getValue("runways").jsonArray.mapNotNull { e ->
            val r = e.jsonArray
            val le = r.str(1).ifBlank { return@mapNotNull null }
            RunwayEntity(
                airportIcao = r.str(0), leIdent = le, heIdent = r.str(2), lengthFt = r.int(3), widthFt = r.int(4),
                surface = r.str(5), leLat = r.dbl(6), leLon = r.dbl(7), heLat = r.dbl(8), heLon = r.dbl(9),
                leHeadingDeg = r.dbl(10), heHeadingDeg = r.dbl(11),
            )
        }
        val frequencies = root.getValue("frequencies").jsonArray.mapNotNull { e ->
            val f = e.jsonArray
            FrequencyEntity(f.str(0), f.str(1), f.str(2), f.dbl(3) ?: return@mapNotNull null)
        }
        db.withTransaction {
            dao.insertAirports(airports)
            dao.insertRunways(runways)
            dao.insertFrequencies(frequencies)
        }
    }

    private fun JsonArray.at(i: Int): JsonElement? = getOrNull(i)?.takeUnless { it is JsonNull }
    private fun JsonArray.str(i: Int): String = at(i)?.jsonPrimitive?.content.orEmpty()
    private fun JsonArray.dbl(i: Int): Double? = at(i)?.jsonPrimitive?.doubleOrNull
    private fun JsonArray.int(i: Int): Int? = at(i)?.jsonPrimitive?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }
}

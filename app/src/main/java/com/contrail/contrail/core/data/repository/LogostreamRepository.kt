package com.contrail.contrail.core.data.repository

import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.data.settings.ApiKeys
import com.contrail.contrail.core.data.settings.ApiService
import com.contrail.contrail.core.data.settings.UsageTracker
import com.contrail.contrail.core.database.CachedResponseEntity
import com.contrail.contrail.core.database.ContrailDatabase
import com.contrail.contrail.core.network.logostream.LogostreamParser
import com.contrail.contrail.core.network.logostream.LogostreamResponse
import com.contrail.contrail.core.network.logostream.LogostreamSource
import com.contrail.contrail.core.network.logostream.LsAirline
import com.contrail.contrail.core.network.logostream.LsAirportStats
import com.contrail.contrail.core.network.logostream.LsRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * logostream data, cached in Room the way you asked and its terms allow: airline profiles
 * and airport stats permanently, routes for a week. Logo *images* are never cached on disk
 * (free-plan terms); only the URLs from the airline profile are stored.
 */
@Singleton
class LogostreamRepository @Inject constructor(
    db: ContrailDatabase,
    private val source: LogostreamSource,
    private val keys: ApiKeys,
    private val usage: UsageTracker,
    private val clock: Clock,
) {
    private val cache = db.cacheDao()

    fun observeAirline(icao: String): Flow<LsAirline?> =
        cache.observe(airlineKey(icao)).map { row -> row?.body?.let { runCatching { LogostreamParser.airline(it) }.getOrNull() } }

    fun observeAirportStats(iata: String): Flow<LsAirportStats?> =
        cache.observe(statsKey(iata)).map { row -> row?.body?.let { runCatching { LogostreamParser.stats(it) }.getOrNull() } }

    fun observeRoutesFrom(iata: String): Flow<List<LsRoute>?> =
        cache.observe(routesKey(iata)).map { row -> row?.body?.let { runCatching { LogostreamParser.decodeRoutes(it) }.getOrNull() } }

    /** Fetches the airline once; it never changes enough to refresh. */
    suspend fun refreshAirline(icao: String) {
        val key = airlineKey(icao)
        if (cache.get(key) != null) return
        val response = call { source.airline(icao, it) } ?: return
        if (response.status == 200 && LogostreamParser.airline(response.body) != null) {
            cache.put(CachedResponseEntity(key, response.body, clock.nowMs()))
        }
    }

    suspend fun refreshAirportStats(iata: String) {
        val key = statsKey(iata)
        if (cache.get(key) != null) return
        val response = call { source.airportStats(iata, it) } ?: return
        if (response.status == 200) cache.put(CachedResponseEntity(key, response.body, clock.nowMs()))
    }

    /** Every nonstop route from an airport, all pages, refreshed weekly. */
    suspend fun refreshRoutesFrom(iata: String) {
        val key = routesKey(iata)
        val cached = cache.get(key)
        if (cached != null && clock.nowMs() - cached.fetchedAtMs < ROUTES_TTL_MS) return
        val all = ArrayList<LsRoute>()
        var page = 1
        while (page <= MAX_ROUTE_PAGES) {
            val response = call { source.routesFrom(iata, page, it) } ?: return
            if (response.status != 200) break
            val (routes, more) = LogostreamParser.routes(response.body)
            all += routes
            if (!more || routes.isEmpty()) break
            page++
        }
        if (all.isNotEmpty()) cache.put(CachedResponseEntity(key, LogostreamParser.encodeRoutes(all), clock.nowMs()))
    }

    /** Runs one request if there is a key and quota left, and records the quota headers. */
    private suspend fun call(block: suspend (String) -> LogostreamResponse): LogostreamResponse? {
        val apiKey = keys.get(ApiService.Logostream) ?: return null
        if (usage.current().exhausted) return null
        val response = runCatching { block(apiKey) }.getOrNull() ?: return null
        val now = clock.nowMs()
        val month = Instant.ofEpochMilli(now).atOffset(ZoneOffset.UTC).let { "%04d-%02d".format(it.year, it.monthValue) }
        usage.recordLogostream(response.limit, response.remaining, response.reset, month, now)
        return response
    }

    private fun airlineKey(icao: String) = "ls:airline:${icao.uppercase()}"
    private fun statsKey(iata: String) = "ls:stats:${iata.uppercase()}"
    private fun routesKey(iata: String) = "ls:routes:${iata.uppercase()}"

    companion object {
        const val ROUTES_TTL_MS = 7 * 86_400_000L
        const val MAX_ROUTE_PAGES = 8
    }
}

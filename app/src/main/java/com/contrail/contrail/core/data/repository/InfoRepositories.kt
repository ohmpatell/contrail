package com.contrail.contrail.core.data.repository

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Upsert
import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.database.CachedResponseEntity
import com.contrail.contrail.core.database.ContrailDatabase
import com.contrail.contrail.core.database.NewsItemEntity
import com.contrail.contrail.core.network.airport.AviationWeatherSource
import com.contrail.contrail.core.network.airport.Metar
import com.contrail.contrail.core.network.airport.OsmResult
import com.contrail.contrail.core.network.airport.OverpassParser
import com.contrail.contrail.core.network.airport.OverpassSource
import com.contrail.contrail.core.network.airport.Taf
import com.contrail.contrail.core.network.airport.WeatherParser
import com.contrail.contrail.core.network.news.NewsArticle
import com.contrail.contrail.core.network.news.NewsFeed
import com.contrail.contrail.core.network.news.RssNewsSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Aviation news, cached in Room and refreshed at most once an hour per feed. */
@Singleton
class NewsRepository @Inject constructor(
    db: ContrailDatabase,
    private val source: RssNewsSource,
    private val clock: Clock,
) {
    private val news = db.newsDao()
    private val cache = db.cacheDao()

    fun observe(feed: NewsFeed): Flow<List<NewsArticle>> = news.observe(feed.key).map { rows ->
        rows.map { NewsArticle(it.title, it.summary, it.link, it.source, it.imageUrl, it.publishedAtMs) }
    }

    /** Returns false when the refresh was skipped or failed. */
    suspend fun refresh(feed: NewsFeed, force: Boolean = false): Boolean {
        val stamp = "news:fetched:${feed.key}"
        val last = cache.get(stamp)?.fetchedAtMs ?: 0
        val now = clock.nowMs()
        if (!force && now - last < NEWS_TTL_MS) return false
        val articles = runCatching { source.fetch(feed) }.getOrNull() ?: return false
        if (articles.isEmpty()) return false
        news.upsertAll(
            articles.take(80).map {
                NewsItemEntity(it.link, feed.key, it.title, it.summary, it.source, it.imageUrl, it.publishedAtMs.takeIf { t -> t > 0 } ?: now, now)
            },
        )
        cache.put(CachedResponseEntity(stamp, "", now))
        news.deleteOlderThan(now - 14 * 86_400_000L)
        return true
    }

    companion object {
        const val NEWS_TTL_MS = 3_600_000L
    }
}

data class AirportWeather(val metar: Metar?, val taf: Taf?, val fetchedAtMs: Long)

/** Weather (10-minute cache) and OpenStreetMap diagram geometry (30-day cache) for airports. */
@Singleton
class AirportInfoRepository @Inject constructor(
    db: ContrailDatabase,
    private val weather: AviationWeatherSource,
    private val overpass: OverpassSource,
    private val clock: Clock,
) {
    private val cache = db.cacheDao()

    fun observeWeather(icao: String): Flow<AirportWeather?> = cache.observe("wx:${icao.uppercase()}").map { row ->
        row ?: return@map null
        val (m, t) = row.body.split(SEP, limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        AirportWeather(WeatherParser.metar(m), WeatherParser.taf(t), row.fetchedAtMs)
    }

    suspend fun refreshWeather(icao: String) {
        val key = "wx:${icao.uppercase()}"
        val cached = cache.get(key)
        if (cached != null && clock.nowMs() - cached.fetchedAtMs < WEATHER_TTL_MS) return
        val metar = runCatching { weather.metar(icao) }.getOrNull() ?: return
        val taf = runCatching { weather.taf(icao) }.getOrNull().orEmpty()
        cache.put(CachedResponseEntity(key, metar + SEP + taf, clock.nowMs()))
    }

    fun observeGeometry(icao: String): Flow<OsmResult?> =
        cache.observe("osm:${icao.uppercase()}").map { row -> row?.body?.let(OverpassParser::parse) }

    suspend fun refreshGeometry(icao: String, lat: Double, lon: Double) {
        val key = "osm:${icao.uppercase()}"
        val cached = cache.get(key)
        if (cached != null && clock.nowMs() - cached.fetchedAtMs < GEOMETRY_TTL_MS) return
        val body = runCatching { overpass.airportGeometry(lat, lon) }.getOrNull() ?: return
        cache.put(CachedResponseEntity(key, body, clock.nowMs()))
    }

    companion object {
        const val WEATHER_TTL_MS = 10 * 60_000L
        const val GEOMETRY_TTL_MS = 30 * 86_400_000L
        private const val SEP = "\u0000"
    }
}

/** A flight Contrail saw: one row per aircraft, callsign and day. */
@Entity(tableName = "sighting", primaryKeys = ["hex", "callsign", "day"])
data class SightingEntity(
    val hex: String,
    val callsign: String,
    /** UTC day, "2026-10-20". */
    val day: String,
    val registration: String?,
    val typeCode: String?,
    val originIata: String?,
    val destinationIata: String?,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
)

@Dao
interface SightingDao {
    @Query("SELECT * FROM sighting WHERE hex = :hex ORDER BY lastSeenMs DESC LIMIT 20")
    fun forAircraft(hex: String): Flow<List<SightingEntity>>

    @Query("SELECT * FROM sighting WHERE hex = :hex AND callsign = :callsign AND day = :day")
    suspend fun get(hex: String, callsign: String, day: String): SightingEntity?

    @Upsert
    suspend fun upsert(s: SightingEntity)
}

@Singleton
class SightingRepository @Inject constructor(db: ContrailDatabase, private val clock: Clock) {
    private val dao = db.sightingDao()

    fun forAircraft(hex: String): Flow<List<SightingEntity>> = dao.forAircraft(hex.lowercase())

    suspend fun record(hex: String, callsign: String, registration: String?, typeCode: String?, origin: String?, destination: String?) {
        val now = clock.nowMs()
        val day = java.time.Instant.ofEpochMilli(now).atOffset(java.time.ZoneOffset.UTC).toLocalDate().toString()
        val existing = dao.get(hex.lowercase(), callsign, day)
        dao.upsert(
            SightingEntity(
                hex.lowercase(), callsign, day, registration, typeCode,
                origin ?: existing?.originIata, destination ?: existing?.destinationIata,
                existing?.firstSeenMs ?: now, now,
            ),
        )
    }
}

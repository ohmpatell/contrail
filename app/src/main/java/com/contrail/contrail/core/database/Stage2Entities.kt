package com.contrail.contrail.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Bundled OurAirports reference data, imported from assets on first use. */
@Entity(tableName = "airport", indices = [Index("iata"), Index("name"), Index("city")])
data class AirportEntity(
    @PrimaryKey val icao: String,
    val iata: String,
    val name: String,
    val city: String,
    val countryIso: String,
    val countryName: String,
    val lat: Double,
    val lon: Double,
    val elevationFt: Int?,
    /** L, M or S (large, medium, small). */
    val size: String,
    val scheduled: Boolean,
    val timezone: String,
)

@Entity(tableName = "runway", primaryKeys = ["airportIcao", "leIdent"], indices = [Index("airportIcao")])
data class RunwayEntity(
    val airportIcao: String,
    val leIdent: String,
    val heIdent: String,
    val lengthFt: Int?,
    val widthFt: Int?,
    val surface: String,
    val leLat: Double?,
    val leLon: Double?,
    val heLat: Double?,
    val heLon: Double?,
    val leHeadingDeg: Double?,
    val heHeadingDeg: Double?,
)

@Entity(tableName = "frequency", primaryKeys = ["airportIcao", "type", "mhz"], indices = [Index("airportIcao")])
data class FrequencyEntity(
    val airportIcao: String,
    val type: String,
    val description: String,
    val mhz: Double,
)

/** Your relationship with an airport. */
@Entity(tableName = "airport_user")
data class AirportUserEntity(
    @PrimaryKey val icao: String,
    val favourite: Boolean,
    val visited: Boolean,
    val updatedAtMs: Long,
)

@Entity(tableName = "watch_item", primaryKeys = ["kind", "value"])
data class WatchItemEntity(
    val kind: String,
    val value: String,
    val label: String,
    val addedAtMs: Long,
)

@Entity(tableName = "news_item", indices = [Index("publishedAtMs"), Index("feedKey")])
data class NewsItemEntity(
    /** The article link: stable and unique. */
    @PrimaryKey val link: String,
    val feedKey: String,
    val title: String,
    val summary: String,
    val source: String,
    val imageUrl: String?,
    val publishedAtMs: Long,
    val fetchedAtMs: Long,
)

/**
 * Small JSON response cache keyed by request (logostream data, Overpass geometry, weather).
 * Each caller decides its own freshness.
 */
@Entity(tableName = "response_cache")
data class CachedResponseEntity(
    @PrimaryKey val key: String,
    val body: String,
    val fetchedAtMs: Long,
)

data class AirportWithUser(
    val icao: String,
    val iata: String,
    val name: String,
    val city: String,
    val countryIso: String,
    val countryName: String,
    val lat: Double,
    val lon: Double,
    val elevationFt: Int?,
    val size: String,
    val scheduled: Boolean,
    val timezone: String,
    val favourite: Boolean?,
    val visited: Boolean?,
)

@Dao
interface AirportDao {
    @Query("SELECT COUNT(*) FROM airport")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAirports(items: List<AirportEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRunways(items: List<RunwayEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFrequencies(items: List<FrequencyEntity>)

    /** Code matches first, then name and city prefixes, then anywhere; big airports first. */
    @Query(
        """
        SELECT a.*, u.favourite AS favourite, u.visited AS visited FROM airport a
        LEFT JOIN airport_user u ON u.icao = a.icao
        WHERE a.iata = :code OR a.icao = :code
           OR a.name LIKE :contains OR a.city LIKE :contains OR a.countryName LIKE :prefix
        ORDER BY
          CASE WHEN a.iata = :code OR a.icao = :code THEN 0
               WHEN a.city LIKE :prefix OR a.name LIKE :prefix THEN 1 ELSE 2 END,
          CASE a.size WHEN 'L' THEN 0 WHEN 'M' THEN 1 ELSE 2 END,
          a.name
        LIMIT :limit
        """,
    )
    fun search(code: String, prefix: String, contains: String, limit: Int = 40): Flow<List<AirportWithUser>>

    @Query("SELECT a.*, u.favourite AS favourite, u.visited AS visited FROM airport a LEFT JOIN airport_user u ON u.icao = a.icao WHERE a.icao = :icao")
    fun observe(icao: String): Flow<AirportWithUser?>

    @Query("SELECT * FROM airport WHERE iata = :iata ORDER BY size LIMIT 1")
    suspend fun byIata(iata: String): AirportEntity?

    @Query("SELECT * FROM airport WHERE iata != ''")
    suspend fun allWithIata(): List<AirportEntity>

    @Query("SELECT * FROM airport WHERE iata IN (:iatas)")
    suspend fun byIatas(iatas: List<String>): List<AirportEntity>

    @Query(
        """
        SELECT a.*, u.favourite AS favourite, u.visited AS visited FROM airport a
        JOIN airport_user u ON u.icao = a.icao
        WHERE (:favourites AND u.favourite) OR (:visited AND u.visited)
        ORDER BY u.updatedAtMs DESC
        """,
    )
    fun observeMine(favourites: Boolean, visited: Boolean): Flow<List<AirportWithUser>>

    @Query("SELECT * FROM runway WHERE airportIcao = :icao ORDER BY lengthFt DESC")
    fun runways(icao: String): Flow<List<RunwayEntity>>

    @Query("SELECT * FROM frequency WHERE airportIcao = :icao ORDER BY type, mhz")
    fun frequencies(icao: String): Flow<List<FrequencyEntity>>

    @Query("SELECT * FROM airport_user WHERE icao = :icao")
    suspend fun user(icao: String): AirportUserEntity?

    @Upsert
    suspend fun upsertUser(entity: AirportUserEntity)

    /** Nearest airports to a point, by a cheap bounding-box prefilter. */
    @Query(
        """
        SELECT * FROM airport WHERE lat BETWEEN :minLat AND :maxLat AND lon BETWEEN :minLon AND :maxLon
        ORDER BY CASE size WHEN 'L' THEN 0 WHEN 'M' THEN 1 ELSE 2 END LIMIT 60
        """,
    )
    suspend fun within(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<AirportEntity>
}

@Dao
interface WatchDao {
    @Query("SELECT * FROM watch_item ORDER BY addedAtMs DESC")
    fun observe(): Flow<List<WatchItemEntity>>

    @Upsert
    suspend fun upsert(item: WatchItemEntity)

    @Query("DELETE FROM watch_item WHERE kind = :kind AND value = :value")
    suspend fun delete(kind: String, value: String)
}

@Dao
interface NewsDao {
    @Query("SELECT * FROM news_item WHERE feedKey = :feedKey ORDER BY publishedAtMs DESC LIMIT :limit")
    fun observe(feedKey: String, limit: Int = 60): Flow<List<NewsItemEntity>>

    @Upsert
    suspend fun upsertAll(items: List<NewsItemEntity>)

    @Query("DELETE FROM news_item WHERE fetchedAtMs < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface CacheDao {
    @Query("SELECT * FROM response_cache WHERE `key` = :key")
    suspend fun get(key: String): CachedResponseEntity?

    @Query("SELECT * FROM response_cache WHERE `key` = :key")
    fun observe(key: String): Flow<CachedResponseEntity?>

    @Upsert
    suspend fun put(entity: CachedResponseEntity)
}

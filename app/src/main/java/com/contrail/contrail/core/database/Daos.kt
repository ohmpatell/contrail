package com.contrail.contrail.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LiveAircraftDao {
    @Query("SELECT * FROM live_aircraft WHERE fetchedAtMs >= :since")
    fun observeSince(since: Long): Flow<List<LiveAircraftEntity>>

    @Query("SELECT * FROM live_aircraft WHERE fetchedAtMs >= :since")
    suspend fun allSince(since: Long): List<LiveAircraftEntity>

    @Upsert
    suspend fun upsertAll(items: List<LiveAircraftEntity>)

    @Query("DELETE FROM live_aircraft WHERE fetchedAtMs < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface TrailDao {
    @Query("SELECT * FROM trail_point WHERE timeMs >= :since ORDER BY hex, timeMs")
    fun observeSince(since: Long): Flow<List<TrailPointEntity>>

    @Query("SELECT * FROM trail_point WHERE hex = :hex AND timeMs >= :since ORDER BY timeMs")
    fun observeForHex(hex: String, since: Long): Flow<List<TrailPointEntity>>

    @Upsert
    suspend fun upsertAll(points: List<TrailPointEntity>)

    @Query("DELETE FROM trail_point WHERE timeMs < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface RouteDao {
    @Query("SELECT * FROM route_cache WHERE callsign = :callsign")
    fun observe(callsign: String): Flow<RouteEntity?>

    @Query("SELECT * FROM route_cache WHERE callsign = :callsign")
    suspend fun get(callsign: String): RouteEntity?

    @Upsert
    suspend fun upsert(entity: RouteEntity)
}

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photo_cache WHERE hex = :hex")
    fun observe(hex: String): Flow<PhotoEntity?>

    @Query("SELECT * FROM photo_cache WHERE hex = :hex")
    suspend fun get(hex: String): PhotoEntity?

    @Upsert
    suspend fun upsert(entity: PhotoEntity)
}

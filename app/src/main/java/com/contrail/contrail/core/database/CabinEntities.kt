package com.contrail.contrail.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Everything Cabin mode needs offline for one flight, as JSON ([com.contrail.contrail.core.data.cabin.FlightPack]). */
@Entity(tableName = "flight_pack")
data class FlightPackEntity(
    @PrimaryKey val segmentId: Long,
    val json: String,
    val createdAtMs: Long,
)

/** One GPS fix from your phone during a flight: the real path you flew. Kept for good. */
@Entity(tableName = "cabin_track", primaryKeys = ["segmentId", "timeMs"])
data class CabinTrackEntity(
    val segmentId: Long,
    val timeMs: Long,
    val lat: Double,
    val lon: Double,
    val altitudeFt: Int?,
    val speedKt: Float?,
)

@Dao
interface CabinDao {
    @Query("SELECT * FROM flight_pack WHERE segmentId = :segmentId")
    fun observePack(segmentId: Long): Flow<FlightPackEntity?>

    @Query("SELECT * FROM flight_pack WHERE segmentId = :segmentId")
    suspend fun pack(segmentId: Long): FlightPackEntity?

    @Upsert
    suspend fun upsertPack(p: FlightPackEntity)

    @Query("DELETE FROM flight_pack WHERE segmentId = :segmentId")
    suspend fun deletePack(segmentId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFix(f: CabinTrackEntity)

    @Query("SELECT * FROM cabin_track WHERE segmentId = :segmentId ORDER BY timeMs")
    fun observeTrack(segmentId: Long): Flow<List<CabinTrackEntity>>

    @Query("SELECT * FROM cabin_track WHERE segmentId = :segmentId ORDER BY timeMs")
    suspend fun track(segmentId: Long): List<CabinTrackEntity>
}

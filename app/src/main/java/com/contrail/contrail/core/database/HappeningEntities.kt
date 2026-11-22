package com.contrail.contrail.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Something worth knowing about that happened in the sky: a go-around, a hold, a diversion, an
 * emergency, a rare visitor, one of your pinned planes moving. [id] is the kind, the plane and a
 * time bucket, so the same event is stored once.
 */
@Entity(tableName = "happening", indices = [Index("timeMs")])
data class HappeningEntity(
    @PrimaryKey val id: String,
    val kind: String,
    /** "near" (your airports), "world" or "hangar". */
    val scope: String,
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
)

@Dao
interface HappeningDao {
    @Query("SELECT * FROM happening WHERE timeMs >= :since ORDER BY timeMs DESC")
    fun observeSince(since: Long): Flow<List<HappeningEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(rows: List<HappeningEntity>)

    @Query("DELETE FROM happening WHERE timeMs < :before")
    suspend fun prune(before: Long)
}

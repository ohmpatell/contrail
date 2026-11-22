package com.contrail.contrail.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A plane pinned to My Hangar: one that means something to you. Found live anywhere in the
 * world, always on the radar, with your own story. [photoPath] is your own photo, kept on this
 * device only; everything else here syncs.
 */
@Entity(tableName = "hangar_plane")
data class HangarPlaneEntity(
    @PrimaryKey val registration: String,
    val hex: String? = null,
    val typeCode: String? = null,
    val airlineIcao: String? = null,
    /** Airline or owner name, as known when pinned. */
    val operator: String? = null,
    val story: String? = null,
    val photoPath: String? = null,
    val addedAtMs: Long,
    val alerts: Boolean = true,
)

/** The last time a pinned plane was seen live. Changes every minute or so, so it's kept apart and never synced. */
@Entity(tableName = "hangar_seen")
data class HangarSeenEntity(
    @PrimaryKey val registration: String,
    val hex: String,
    val seenMs: Long,
    val lat: Double,
    val lon: Double,
    val callsign: String?,
    val airborne: Boolean,
)

data class HangarRow(
    val registration: String,
    val hex: String?,
    val typeCode: String?,
    val airlineIcao: String?,
    val operator: String?,
    val story: String?,
    val photoPath: String?,
    val addedAtMs: Long,
    val alerts: Boolean,
    val seenMs: Long?,
    val lat: Double?,
    val lon: Double?,
    val callsign: String?,
    val airborne: Boolean?,
)

@Dao
interface HangarDao {
    @Query(
        """
        SELECT p.registration, COALESCE(p.hex, s.hex) AS hex, p.typeCode, p.airlineIcao, p.operator, p.story, p.photoPath,
               p.addedAtMs, p.alerts, s.seenMs, s.lat, s.lon, s.callsign, s.airborne
        FROM hangar_plane p LEFT JOIN hangar_seen s ON s.registration = p.registration ORDER BY p.addedAtMs
        """,
    )
    fun observeAll(): Flow<List<HangarRow>>

    @Query("SELECT * FROM hangar_plane ORDER BY addedAtMs")
    suspend fun all(): List<HangarPlaneEntity>

    @Query("SELECT * FROM hangar_plane WHERE registration = :registration")
    suspend fun get(registration: String): HangarPlaneEntity?

    @Query("SELECT * FROM hangar_seen WHERE registration = :registration")
    suspend fun seen(registration: String): HangarSeenEntity?

    @Upsert
    suspend fun upsert(plane: HangarPlaneEntity)

    @Upsert
    suspend fun upsertSeen(seen: HangarSeenEntity)

    @Query("DELETE FROM hangar_plane WHERE registration = :registration")
    suspend fun delete(registration: String)

    @Query("DELETE FROM hangar_seen WHERE registration = :registration")
    suspend fun deleteSeen(registration: String)
}

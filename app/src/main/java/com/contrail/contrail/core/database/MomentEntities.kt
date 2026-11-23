package com.contrail.contrail.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A moment you want to remember: a cockpit visit, an upgrade, a special livery, a sunset at
 * 38,000 ft. Written by you, or captured by Cabin mode in flight. May point at one of your flights
 * (by its cross-device uid) and at a plane (by registration). [photoPath] stays on this device.
 */
@Entity(tableName = "moment", indices = [Index("registration"), Index("segmentUid")])
data class MomentEntity(
    @PrimaryKey val uid: String,
    val kind: String,
    val title: String,
    val note: String?,
    /** Local date, ISO (2026-12-19). */
    val date: String,
    val segmentUid: String?,
    val registration: String?,
    val photoPath: String?,
    /** "you" (written by you) or "cabin" (captured in flight). */
    val source: String,
    val lat: Double?,
    val lon: Double?,
    val createdAtMs: Long,
)

@Dao
interface MomentDao {
    @Query("SELECT * FROM moment ORDER BY date DESC, createdAtMs DESC")
    fun observeAll(): Flow<List<MomentEntity>>

    @Query("SELECT * FROM moment")
    suspend fun all(): List<MomentEntity>

    @Query("SELECT * FROM moment WHERE uid = :uid")
    suspend fun get(uid: String): MomentEntity?

    @Upsert
    suspend fun upsert(m: MomentEntity)

    @Query("DELETE FROM moment WHERE uid = :uid")
    suspend fun delete(uid: String)
}

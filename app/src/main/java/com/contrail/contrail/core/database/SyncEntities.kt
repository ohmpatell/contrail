package com.contrail.contrail.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * What this device last sent or received for each synced record, so the next sync knows what
 * changed here ([hash] differs) and what disappeared (a row here, no record locally).
 */
@Entity(tableName = "sync_state", primaryKeys = ["kind", "id"])
data class SyncStateEntity(
    val kind: String,
    val id: String,
    val hash: String,
    val updatedAtMs: Long,
    val deleted: Boolean,
)

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_state")
    suspend fun all(): List<SyncStateEntity>

    @Upsert
    suspend fun upsert(rows: List<SyncStateEntity>)

    @Query("DELETE FROM sync_state")
    suspend fun clear()

    @Query("SELECT * FROM trip")
    suspend fun trips(): List<TripEntity>

    @Query("SELECT * FROM segment")
    suspend fun segments(): List<SegmentEntity>

    @Query("SELECT * FROM connection_pref")
    suspend fun prefs(): List<ConnectionPrefEntity>

    @Query("SELECT * FROM lounge_visit")
    suspend fun visits(): List<LoungeVisitEntity>

    @Query("SELECT * FROM lounge WHERE userAdded = 1 OR favourite = 1")
    suspend fun lounges(): List<LoungeEntity>

    @Query("SELECT * FROM watch_item")
    suspend fun watch(): List<WatchItemEntity>

    @Query("SELECT * FROM airport_user")
    suspend fun airportUser(): List<AirportUserEntity>

    // ---- applying what other devices sent

    @Query("SELECT * FROM trip WHERE uid = :uid")
    suspend fun tripByUid(uid: String): TripEntity?

    @Insert
    suspend fun insertTrip(t: TripEntity): Long

    @Update
    suspend fun updateTrip(t: TripEntity)

    @Query("DELETE FROM trip WHERE id = :id")
    suspend fun deleteTrip(id: Long)

    @Query("SELECT * FROM segment WHERE tripId = :tripId")
    suspend fun segmentsOf(tripId: Long): List<SegmentEntity>

    @Query("SELECT * FROM segment WHERE uid = :uid")
    suspend fun segmentByUid(uid: String): SegmentEntity?

    @Insert
    suspend fun insertSegment(s: SegmentEntity): Long

    @Update
    suspend fun updateSegment(s: SegmentEntity)

    @Query("DELETE FROM segment WHERE id IN (:ids)")
    suspend fun deleteSegments(ids: List<Long>)

    @Query("DELETE FROM connection_pref WHERE inboundSegmentId IN (:segmentIds)")
    suspend fun deletePrefs(segmentIds: List<Long>)

    @Upsert
    suspend fun upsertPrefs(prefs: List<ConnectionPrefEntity>)

    @Query("UPDATE lounge_visit SET segmentId = NULL WHERE segmentId IN (:segmentIds)")
    suspend fun detachVisits(segmentIds: List<Long>)

    @Query("SELECT * FROM lounge_visit WHERE uid = :uid")
    suspend fun visitByUid(uid: String): LoungeVisitEntity?

    @Upsert
    suspend fun upsertVisit(v: LoungeVisitEntity)

    @Query("DELETE FROM lounge_visit WHERE uid = :uid")
    suspend fun deleteVisit(uid: String)

    @Upsert
    suspend fun upsertLounge(l: LoungeEntity)

    @Query("DELETE FROM lounge WHERE `key` = :key")
    suspend fun deleteLounge(key: String)

    @Upsert
    suspend fun upsertWatch(w: WatchItemEntity)

    @Query("DELETE FROM watch_item WHERE kind = :kind AND value = :value")
    suspend fun deleteWatch(kind: String, value: String)

    @Upsert
    suspend fun upsertAirportUser(a: AirportUserEntity)

    @Query("DELETE FROM airport_user WHERE icao = :icao")
    suspend fun deleteAirportUser(icao: String)
}

/** Gives every existing trip, flight and lounge visit its cross-device id. */
class FillUids : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        for (table in listOf("trip", "segment", "lounge_visit")) {
            db.execSQL(
                "UPDATE $table SET uid = lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' || " +
                    "substr('89ab', 1 + (abs(random()) % 4), 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6))) WHERE uid = ''",
            )
        }
    }
}

package com.contrail.contrail.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A journey made of one or more flight segments. */
@Entity(tableName = "trip")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val pnr: String?,
    val passengerName: String?,
    /** How it was added (ImportSource names, comma-separated). */
    val source: String,
    val createdAtMs: Long,
)

/** One flight. Local times are ISO strings as printed on the ticket; the UTC columns are for sorting. */
@Entity(
    tableName = "segment",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId"), Index("departureUtcMs"), Index("tail")],
)
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val ordinal: Int,
    val airlineIata: String,
    val flightNumber: String,
    val origin: String,
    val destination: String,
    val departureLocal: String,
    val arrivalLocal: String,
    val departureZone: String,
    val arrivalZone: String,
    val departureUtcMs: Long,
    val arrivalUtcMs: Long,
    val departureTerminal: String?,
    val arrivalTerminal: String?,
    val gate: String?,
    val cabin: String?,
    val fareClass: String?,
    val seat: String?,
    val aircraftType: String?,
    val tail: String?,
    val operatedBy: String?,
    val airlineName: String? = null,
    val seatType: String? = null,
    val reason: String? = null,
    val note: String? = null,
    @ColumnInfo(defaultValue = "0") val timesEstimated: Boolean = false,
)

/** Your layover/stopover choice and hotel flag for the connection after a segment. */
@Entity(
    tableName = "connection_pref",
    foreignKeys = [ForeignKey(entity = SegmentEntity::class, parentColumns = ["id"], childColumns = ["inboundSegmentId"], onDelete = ForeignKey.CASCADE)],
)
data class ConnectionPrefEntity(
    @PrimaryKey val inboundSegmentId: Long,
    val kind: String?,
    val hotel: Boolean,
)

/** A lounge you added by hand, or a favourite flag on any lounge. Keyed "AIRPORT:normalised name". */
@Entity(tableName = "lounge", indices = [Index("airport")])
data class LoungeEntity(
    @PrimaryKey val key: String,
    val airport: String,
    val terminal: String?,
    val name: String,
    val userAdded: Boolean,
    val favourite: Boolean,
    val addedAtMs: Long,
)

/**
 * A lounge visit. [segmentId] is the flight it belongs to (kept as a plain id, not a foreign key,
 * so your lounge history survives deleting a trip). [phase] is "Departure" (before that flight,
 * at its origin, which covers connections) or "Arrival" (after it lands).
 */
@Entity(tableName = "lounge_visit", indices = [Index("segmentId"), Index("loungeKey")])
data class LoungeVisitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val segmentId: Long?,
    val phase: String,
    val loungeKey: String,
    val loungeName: String,
    val airport: String,
    val terminal: String?,
    val date: String,
    val timeIn: String?,
    val timeOut: String?,
    val access: String,
    val rating: Int,
    val note: String,
    val createdAtMs: Long,
)

@Dao
interface TripDao {
    @Query("SELECT * FROM trip")
    fun observeTrips(): Flow<List<TripEntity>>

    @Query("SELECT * FROM segment ORDER BY departureUtcMs")
    fun observeSegments(): Flow<List<SegmentEntity>>

    @Query("SELECT * FROM connection_pref")
    fun observePrefs(): Flow<List<ConnectionPrefEntity>>

    @Query("SELECT * FROM segment WHERE id = :id")
    suspend fun segment(id: Long): SegmentEntity?

    @Query("SELECT * FROM segment")
    suspend fun allSegments(): List<SegmentEntity>

    @Query("DELETE FROM segment WHERE id = :id")
    suspend fun deleteSegment(id: Long)

    @Query("SELECT COUNT(*) FROM segment WHERE tripId = :tripId")
    suspend fun segmentCount(tripId: Long): Int

    @Query("SELECT * FROM segment WHERE tail = :tail")
    suspend fun segmentsWithTail(tail: String): List<SegmentEntity>

    @Insert
    suspend fun insertTrip(trip: TripEntity): Long

    @Insert
    suspend fun insertSegments(segments: List<SegmentEntity>): List<Long>

    @Update
    suspend fun updateSegment(segment: SegmentEntity)

    @Update
    suspend fun updateTrip(trip: TripEntity)

    @Query("SELECT * FROM trip WHERE id = :id")
    suspend fun trip(id: Long): TripEntity?

    @Upsert
    suspend fun upsertPref(pref: ConnectionPrefEntity)

    @Query("SELECT * FROM connection_pref WHERE inboundSegmentId = :id")
    suspend fun pref(id: Long): ConnectionPrefEntity?

    @Query("DELETE FROM trip WHERE id = :id")
    suspend fun deleteTrip(id: Long)

    @Query("SELECT id FROM segment WHERE tripId = :tripId")
    suspend fun segmentIds(tripId: Long): List<Long>

    @Transaction
    suspend fun insertTripWithSegments(trip: TripEntity, segments: List<SegmentEntity>, hotelAfter: Set<Int>): Long {
        val id = insertTrip(trip)
        val ids = insertSegments(segments.map { it.copy(tripId = id) })
        hotelAfter.forEach { i -> ids.getOrNull(i)?.let { upsertPref(ConnectionPrefEntity(it, null, hotel = true)) } }
        return id
    }
}

@Dao
interface LoungeDao {
    @Query("SELECT * FROM lounge WHERE airport = :airport")
    fun forAirport(airport: String): Flow<List<LoungeEntity>>

    @Query("SELECT * FROM lounge")
    fun observeAll(): Flow<List<LoungeEntity>>

    @Query("SELECT * FROM lounge WHERE `key` = :key")
    suspend fun get(key: String): LoungeEntity?

    @Upsert
    suspend fun upsert(lounge: LoungeEntity)

    @Query("SELECT * FROM lounge_visit ORDER BY date DESC, timeIn DESC")
    fun observeVisits(): Flow<List<LoungeVisitEntity>>

    @Insert
    suspend fun insertVisit(visit: LoungeVisitEntity): Long

    @Update
    suspend fun updateVisit(visit: LoungeVisitEntity)

    @Query("SELECT * FROM lounge_visit WHERE id = :id")
    suspend fun visit(id: Long): LoungeVisitEntity?

    @Query("DELETE FROM lounge_visit WHERE id = :id")
    suspend fun deleteVisit(id: Long)

    @Query("UPDATE lounge_visit SET segmentId = NULL WHERE segmentId IN (:segmentIds)")
    suspend fun detach(segmentIds: List<Long>)
}

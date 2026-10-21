package com.contrail.contrail.core.data.repository

import androidx.room.withTransaction
import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.data.ticket.ImportSource
import com.contrail.contrail.core.data.trip.Cabin
import com.contrail.contrail.core.data.trip.ConnectionKind
import com.contrail.contrail.core.data.trip.ConnectionPref
import com.contrail.contrail.core.data.trip.ConnectionRules
import com.contrail.contrail.core.data.trip.FlightSegment
import com.contrail.contrail.core.data.trip.Trip
import com.contrail.contrail.core.database.ConnectionPrefEntity
import com.contrail.contrail.core.database.ContrailDatabase
import com.contrail.contrail.core.database.SegmentEntity
import com.contrail.contrail.core.database.TripEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** A trip ready to save: segments in order, plus the connections the ticket says have a hotel. */
data class PlannedTrip(
    val title: String,
    val segments: List<FlightSegment>,
    val hotelAfter: Set<Int> = emptySet(),
)

/** Your trips. Everything is local: trips only ever come from your own tickets or typing. */
@Singleton
class TripRepository @Inject constructor(
    private val db: ContrailDatabase,
    private val clock: Clock,
) {
    private val dao = db.tripDao()

    /** Every trip, soonest first, with its connections classified. */
    val trips: Flow<List<Trip>> = combine(dao.observeTrips(), dao.observeSegments(), dao.observePrefs()) { trips, segments, prefs ->
        val prefMap = prefs.associate { it.inboundSegmentId to it.toDomain() }
        val byTrip = segments.groupBy { it.tripId }
        trips.mapNotNull { t ->
            val segs = byTrip[t.id].orEmpty().sortedBy { it.departureUtcMs }.map { it.toDomain() }
            if (segs.isEmpty()) return@mapNotNull null
            Trip(t.id, t.title, t.pnr, t.passengerName, segs, ConnectionRules.connections(segs, prefMap))
        }.sortedBy { it.start }
    }.distinctUntilChanged()

    fun trip(id: Long): Flow<Trip?> = trips.map { list -> list.firstOrNull { it.id == id } }.distinctUntilChanged()

    /** A segment with its trip, for the flight detail page. */
    fun segment(id: Long): Flow<Pair<Trip, FlightSegment>?> = trips.map { list ->
        list.firstNotNullOfOrNull { t -> t.segments.firstOrNull { it.id == id }?.let { t to it } }
    }.distinctUntilChanged()

    suspend fun save(trips: List<PlannedTrip>, pnr: String?, passengerName: String?, sources: Set<ImportSource>): List<Long> =
        db.withTransaction {
            trips.map { p ->
                dao.insertTripWithSegments(
                    TripEntity(title = p.title, pnr = pnr, passengerName = passengerName, source = sources.joinToString(",") { it.name }, createdAtMs = clock.nowMs()),
                    p.segments.mapIndexed { i, s -> s.copy(ordinal = i).toEntity() },
                    p.hotelAfter,
                )
            }
        }

    suspend fun setConnectionKind(inboundSegmentId: Long, kind: ConnectionKind?) {
        val current = dao.pref(inboundSegmentId)
        dao.upsertPref(ConnectionPrefEntity(inboundSegmentId, kind?.name, current?.hotel ?: false))
    }

    suspend fun setHotel(inboundSegmentId: Long, hotel: Boolean) {
        val current = dao.pref(inboundSegmentId)
        dao.upsertPref(ConnectionPrefEntity(inboundSegmentId, current?.kind, hotel))
    }

    suspend fun setTail(segmentId: Long, tail: String?) {
        val s = dao.segment(segmentId) ?: return
        dao.updateSegment(s.copy(tail = tail?.uppercase()?.trim()?.ifEmpty { null }))
    }

    suspend fun rename(tripId: Long, title: String) {
        val t = dao.trip(tripId) ?: return
        dao.updateTrip(t.copy(title = title.trim().ifEmpty { t.title }))
    }

    /** Deletes a trip. Its lounge visits stay in your Passport, unlinked from the flight. */
    suspend fun delete(tripId: Long) = db.withTransaction {
        db.loungeDao().detach(dao.segmentIds(tripId))
        dao.deleteTrip(tripId)
    }

    /** How many of your flights were on this registration. */
    suspend fun flightsOnTail(tail: String): Int = dao.segmentsWithTail(tail.uppercase()).size

    companion object {
        fun SegmentEntity.toDomain() = FlightSegment(
            id = id, tripId = tripId, ordinal = ordinal,
            airlineIata = airlineIata, flightNumber = flightNumber, origin = origin, destination = destination,
            departureLocal = LocalDateTime.parse(departureLocal), arrivalLocal = LocalDateTime.parse(arrivalLocal),
            departureZone = ZoneId.of(departureZone), arrivalZone = ZoneId.of(arrivalZone),
            departureTerminal = departureTerminal, arrivalTerminal = arrivalTerminal, gate = gate,
            cabin = cabin?.let { c -> Cabin.entries.firstOrNull { it.name == c } },
            fareClass = fareClass, seat = seat, aircraftType = aircraftType, tail = tail, operatedBy = operatedBy,
        )

        fun FlightSegment.toEntity() = SegmentEntity(
            id = id, tripId = tripId, ordinal = ordinal,
            airlineIata = airlineIata, flightNumber = flightNumber, origin = origin, destination = destination,
            departureLocal = departureLocal.toString(), arrivalLocal = arrivalLocal.toString(),
            departureZone = departureZone.id, arrivalZone = arrivalZone.id,
            departureUtcMs = departure.toEpochMilli(), arrivalUtcMs = arrival.toEpochMilli(),
            departureTerminal = departureTerminal, arrivalTerminal = arrivalTerminal, gate = gate,
            cabin = cabin?.name, fareClass = fareClass, seat = seat, aircraftType = aircraftType, tail = tail, operatedBy = operatedBy,
        )

        fun ConnectionPrefEntity.toDomain() = ConnectionPref(
            inboundSegmentId, kind?.let { k -> ConnectionKind.entries.firstOrNull { it.name == k } }, hotel,
        )
    }
}

package com.contrail.contrail.core.data.sync

import androidx.room.withTransaction
import com.contrail.contrail.core.database.AirportUserEntity
import com.contrail.contrail.core.database.ConnectionPrefEntity
import com.contrail.contrail.core.database.ContrailDatabase
import com.contrail.contrail.core.database.LoungeEntity
import com.contrail.contrail.core.database.LoungeVisitEntity
import com.contrail.contrail.core.database.SegmentEntity
import com.contrail.contrail.core.database.SyncDao
import com.contrail.contrail.core.database.SyncStateEntity
import com.contrail.contrail.core.database.TripEntity
import com.contrail.contrail.core.database.WatchItemEntity
import com.contrail.contrail.core.network.sync.RemoteRecord
import com.contrail.contrail.core.network.sync.Session
import com.contrail.contrail.core.network.sync.SyncBackend
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.security.MessageDigest

/** The record kinds that sync, in the order they're applied (flights before lounge visits that point at them). */
enum class RecordKind(val key: String) {
    Trip("trip"), Lounge("lounge"), Visit("visit"), Watch("watch"), Airport("airport");

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key }
    }
}

data class LocalRecord(val kind: RecordKind, val id: String, val data: JsonObject) {
    val hash: String get() = SyncCodec.hash(data)
}

/**
 * Turns the on-phone database into sync records and back. Records never carry local row ids,
 * which differ per device; they use the cross-device uids instead.
 */
class SyncCodec(private val json: Json) {

    @Serializable
    data class TripDoc(
        val title: String,
        val pnr: String? = null,
        val passengerName: String? = null,
        val source: String = "",
        val createdAtMs: Long = 0,
        val segments: List<SegmentDoc> = emptyList(),
        val prefs: List<PrefDoc> = emptyList(),
        val following: Boolean = false,
        val sharedBy: String? = null,
    )

    @Serializable
    data class SegmentDoc(
        val uid: String, val ordinal: Int, val airlineIata: String, val flightNumber: String,
        val origin: String, val destination: String, val departureLocal: String, val arrivalLocal: String,
        val departureZone: String, val arrivalZone: String, val departureUtcMs: Long, val arrivalUtcMs: Long,
        val departureTerminal: String? = null, val arrivalTerminal: String? = null, val gate: String? = null,
        val cabin: String? = null, val fareClass: String? = null, val seat: String? = null,
        val aircraftType: String? = null, val tail: String? = null, val operatedBy: String? = null,
        val airlineName: String? = null, val seatType: String? = null, val reason: String? = null,
        val note: String? = null, val timesEstimated: Boolean = false,
        val revisedDepartureUtcMs: Long? = null, val revisedArrivalUtcMs: Long? = null,
        val actualDepartureUtcMs: Long? = null, val actualArrivalUtcMs: Long? = null, val actualSource: String? = null,
        val liveStatus: String? = null,
    )

    @Serializable
    data class PrefDoc(val segmentUid: String, val kind: String? = null, val hotel: Boolean = false)

    @Serializable
    data class VisitDoc(
        val segmentUid: String? = null, val phase: String, val loungeKey: String, val loungeName: String,
        val airport: String, val terminal: String? = null, val date: String, val timeIn: String? = null,
        val timeOut: String? = null, val access: String, val rating: Int, val note: String, val createdAtMs: Long,
    )

    @Serializable
    data class LoungeDoc(val airport: String, val terminal: String? = null, val name: String, val userAdded: Boolean, val favourite: Boolean, val addedAtMs: Long)

    @Serializable
    data class WatchDoc(val kind: String, val value: String, val label: String, val addedAtMs: Long)

    @Serializable
    data class AirportDoc(val favourite: Boolean, val visited: Boolean, val updatedAtMs: Long)

    private inline fun <reified T> obj(v: T): JsonObject = json.encodeToJsonElement(v).jsonObject

    /** Everything on this phone that syncs. */
    suspend fun snapshot(dao: SyncDao): List<LocalRecord> {
        val segments = dao.segments()
        val segUid = segments.associate { it.id to it.uid }
        val prefs = dao.prefs()
        val trips = dao.trips().map { t ->
            val segs = segments.filter { it.tripId == t.id }.sortedBy { it.ordinal }
            val ids = segs.map { it.id }.toSet()
            val doc = TripDoc(
                t.title, t.pnr, t.passengerName, t.source, t.createdAtMs,
                segs.map { it.toDoc() },
                prefs.filter { it.inboundSegmentId in ids }.mapNotNull { p -> segUid[p.inboundSegmentId]?.let { PrefDoc(it, p.kind, p.hotel) } }.sortedBy { it.segmentUid },
                t.following, t.sharedBy,
            )
            LocalRecord(RecordKind.Trip, t.uid, obj(doc))
        }
        val visits = dao.visits().map { v ->
            LocalRecord(RecordKind.Visit, v.uid, obj(VisitDoc(v.segmentId?.let(segUid::get), v.phase, v.loungeKey, v.loungeName, v.airport, v.terminal, v.date, v.timeIn, v.timeOut, v.access, v.rating, v.note, v.createdAtMs)))
        }
        val lounges = dao.lounges().map { LocalRecord(RecordKind.Lounge, it.key, obj(LoungeDoc(it.airport, it.terminal, it.name, it.userAdded, it.favourite, it.addedAtMs))) }
        val watch = dao.watch().map { LocalRecord(RecordKind.Watch, "${it.kind}:${it.value}", obj(WatchDoc(it.kind, it.value, it.label, it.addedAtMs))) }
        val airports = dao.airportUser().map { LocalRecord(RecordKind.Airport, it.icao, obj(AirportDoc(it.favourite, it.visited, it.updatedAtMs))) }
        return trips + visits + lounges + watch + airports
    }

    /** Writes one record from another device into the database (or removes it). */
    suspend fun apply(dao: SyncDao, kind: RecordKind, id: String, data: JsonObject, deleted: Boolean) {
        when (kind) {
            RecordKind.Trip -> applyTrip(dao, id, if (deleted) null else json.decodeFromJsonElement<TripDoc>(data))
            RecordKind.Visit -> if (deleted) {
                dao.deleteVisit(id)
            } else {
                val d = json.decodeFromJsonElement<VisitDoc>(data)
                val existing = dao.visitByUid(id)
                dao.upsertVisit(
                    LoungeVisitEntity(
                        id = existing?.id ?: 0, uid = id, segmentId = d.segmentUid?.let { dao.segmentByUid(it)?.id },
                        phase = d.phase, loungeKey = d.loungeKey, loungeName = d.loungeName, airport = d.airport, terminal = d.terminal,
                        date = d.date, timeIn = d.timeIn, timeOut = d.timeOut, access = d.access, rating = d.rating, note = d.note, createdAtMs = d.createdAtMs,
                    ),
                )
            }
            RecordKind.Lounge -> if (deleted) {
                dao.deleteLounge(id)
            } else {
                val d = json.decodeFromJsonElement<LoungeDoc>(data)
                dao.upsertLounge(LoungeEntity(id, d.airport, d.terminal, d.name, d.userAdded, d.favourite, d.addedAtMs))
            }
            RecordKind.Watch -> {
                val (k, v) = id.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                if (deleted) dao.deleteWatch(k, v) else json.decodeFromJsonElement<WatchDoc>(data).let { dao.upsertWatch(WatchItemEntity(it.kind, it.value, it.label, it.addedAtMs)) }
            }
            RecordKind.Airport -> if (deleted) {
                dao.deleteAirportUser(id)
            } else {
                json.decodeFromJsonElement<AirportDoc>(data).let { dao.upsertAirportUser(AirportUserEntity(id, it.favourite, it.visited, it.updatedAtMs)) }
            }
        }
    }

    private suspend fun applyTrip(dao: SyncDao, uid: String, doc: TripDoc?) {
        val local = dao.tripByUid(uid)
        if (doc == null) {
            if (local != null) {
                dao.detachVisits(dao.segmentsOf(local.id).map { it.id })
                dao.deleteTrip(local.id)
            }
            return
        }
        val tripId = if (local == null) {
            dao.insertTrip(TripEntity(uid = uid, title = doc.title, pnr = doc.pnr, passengerName = doc.passengerName, source = doc.source, createdAtMs = doc.createdAtMs, following = doc.following, sharedBy = doc.sharedBy))
        } else {
            dao.updateTrip(local.copy(title = doc.title, pnr = doc.pnr, passengerName = doc.passengerName, source = doc.source, createdAtMs = doc.createdAtMs, following = doc.following, sharedBy = doc.sharedBy))
            local.id
        }
        val existing = dao.segmentsOf(tripId)
        val keep = doc.segments.map { it.uid }.toSet()
        val gone = existing.filter { it.uid !in keep }.map { it.id }
        if (gone.isNotEmpty()) {
            dao.detachVisits(gone)
            dao.deleteSegments(gone)
        }
        val idByUid = HashMap<String, Long>()
        doc.segments.forEach { s ->
            // A flight can move between trips on another device, so look it up anywhere.
            val found = dao.segmentByUid(s.uid)
            val entity = s.toEntity(id = found?.id ?: 0, tripId = tripId)
            idByUid[s.uid] = if (found == null) dao.insertSegment(entity) else entity.id.also { dao.updateSegment(entity) }
        }
        dao.deletePrefs(idByUid.values.toList())
        dao.upsertPrefs(doc.prefs.mapNotNull { p -> idByUid[p.segmentUid]?.let { ConnectionPrefEntity(it, p.kind, p.hotel) } })
    }

    private fun SegmentEntity.toDoc() = SegmentDoc(
        uid, ordinal, airlineIata, flightNumber, origin, destination, departureLocal, arrivalLocal, departureZone, arrivalZone,
        departureUtcMs, arrivalUtcMs, departureTerminal, arrivalTerminal, gate, cabin, fareClass, seat, aircraftType, tail, operatedBy,
        airlineName, seatType, reason, note, timesEstimated,
        revisedDepartureUtcMs, revisedArrivalUtcMs, actualDepartureUtcMs, actualArrivalUtcMs, actualSource, liveStatus,
    )

    private fun SegmentDoc.toEntity(id: Long, tripId: Long) = SegmentEntity(
        id = id, uid = uid, tripId = tripId, ordinal = ordinal, airlineIata = airlineIata, flightNumber = flightNumber,
        origin = origin, destination = destination, departureLocal = departureLocal, arrivalLocal = arrivalLocal,
        departureZone = departureZone, arrivalZone = arrivalZone, departureUtcMs = departureUtcMs, arrivalUtcMs = arrivalUtcMs,
        departureTerminal = departureTerminal, arrivalTerminal = arrivalTerminal, gate = gate, cabin = cabin, fareClass = fareClass,
        seat = seat, aircraftType = aircraftType, tail = tail, operatedBy = operatedBy, airlineName = airlineName,
        seatType = seatType, reason = reason, note = note, timesEstimated = timesEstimated,
        revisedDepartureUtcMs = revisedDepartureUtcMs, revisedArrivalUtcMs = revisedArrivalUtcMs,
        actualDepartureUtcMs = actualDepartureUtcMs, actualArrivalUtcMs = actualArrivalUtcMs, actualSource = actualSource, liveStatus = liveStatus,
    )

    companion object {
        fun hash(data: JsonObject): String =
            MessageDigest.getInstance("SHA-256").digest(data.toString().toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
    }
}

/** What one sync did, for the Settings line and tests. */
data class SyncResult(val pulled: Int, val applied: Int, val pushed: Int, val cursor: Long)

/**
 * Pull, then push. Rules:
 * - A record changed on this phone since the last sync ("dirty") is kept and sent; otherwise
 *   the server's version wins. With one person per account, that's last-sync-wins.
 * - Deleting here sends a tombstone, so the other devices delete it too.
 * - The server numbers every write, so pulling "after N" never misses one because of a clock.
 */
class SyncEngine(
    private val db: ContrailDatabase,
    private val backend: SyncBackend,
    private val codec: SyncCodec,
    private val now: () -> Long,
) {
    private val dao get() = db.syncDao()

    suspend fun sync(session: Session, cursor: Long): SyncResult {
        var seq = cursor
        var pulled = 0
        var applied = 0
        while (true) {
            val page = backend.pull(session, seq)
            if (page.isEmpty()) break
            pulled += page.size
            seq = maxOf(seq, page.maxOf { it.seq })
            applied += applyPage(page)
            if (page.size < 500) break
        }
        val pushed = push(session)
        return SyncResult(pulled, applied, pushed, seq)
    }

    private suspend fun applyPage(page: List<RemoteRecord>): Int = db.withTransaction {
        val state = dao.all().associateBy { it.kind to it.id }
        val local = codec.snapshot(dao).associateBy { it.kind.key to it.id }
        var n = 0
        val newState = mutableListOf<SyncStateEntity>()
        for (r in page.sortedBy { RecordKind.of(it.kind)?.ordinal ?: Int.MAX_VALUE }) {
            val kind = RecordKind.of(r.kind) ?: continue
            val key = r.kind to r.id
            val st = state[key]
            val remoteHash = SyncCodec.hash(r.data)
            // Already have exactly this (usually our own write coming back).
            if (st != null && st.hash == remoteHash && st.deleted == r.deleted) continue
            val mine = local[key]
            val dirty = when {
                mine == null -> st != null && !st.deleted // deleted here, not yet sent
                st == null -> false // never synced: take the server's
                else -> mine.hash != st.hash
            }
            if (dirty) continue
            codec.apply(dao, kind, r.id, r.data, r.deleted)
            newState += SyncStateEntity(r.kind, r.id, remoteHash, r.updatedAt, r.deleted)
            n++
        }
        dao.upsert(newState)
        n
    }

    private suspend fun push(session: Session): Int {
        val state = dao.all().associateBy { it.kind to it.id }
        val local = codec.snapshot(dao)
        val t = now()
        val changed = local.filter { r -> state[r.kind.key to r.id]?.let { it.deleted || it.hash != r.hash } ?: true }
            .map { RemoteRecord(it.kind.key, it.id, it.data, t) }
        val present = local.map { it.kind.key to it.id }.toSet()
        val tombstones = state.values.filter { !it.deleted && (it.kind to it.id) !in present }
            .map { RemoteRecord(it.kind, it.id, JsonObject(emptyMap()), t, deleted = true) }
        val out = changed + tombstones
        if (out.isEmpty()) return 0
        backend.push(session, out)
        dao.upsert(out.map { SyncStateEntity(it.kind, it.id, SyncCodec.hash(it.data), t, it.deleted) })
        return out.size
    }
}

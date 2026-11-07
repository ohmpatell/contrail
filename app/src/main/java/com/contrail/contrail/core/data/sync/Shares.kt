package com.contrail.contrail.core.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.withTransaction
import com.contrail.contrail.core.database.ContrailDatabase
import com.contrail.contrail.core.network.sync.AccountException
import com.contrail.contrail.core.network.sync.OutgoingShare
import com.contrail.contrail.core.network.sync.Session
import com.contrail.contrail.core.network.sync.SupabaseBackend
import com.contrail.contrail.core.network.sync.SyncBackend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Someone you've shared a trip with. */
data class SentShare(val tripUid: String, val userId: String, val username: String, val hash: String)

/**
 * Sharing trips between accounts.
 *
 * - You share a trip with a username. Their Contrail shows it under Following: a live view of
 *   your trip that updates when you edit it, with its own Live Update and a "landed" alert.
 * - "I'm on this trip too" turns it into their own trip: it counts in their Passport, both
 *   Passports show the flights as flown together, and your later edits stop changing it.
 * - Unsharing, or deleting the trip, removes it from their Following.
 */
@Singleton
class SharesRepository @Inject constructor(
    private val db: ContrailDatabase,
    private val backend: SyncBackend,
    private val json: Json,
    @Named("trips") private val store: DataStore<Preferences>,
) {
    private val sentKey = stringPreferencesKey("shares.sent")
    private val cursorKey = longPreferencesKey("shares.cursor")

    val sent: Flow<List<SentShare>> = store.data.map { decode(it[sentKey]) }

    /** Shares one of your trips with [username]. */
    suspend fun share(session: Session, me: String, tripId: Long, username: String) {
        val them = SupabaseBackend.normalise(username)
        if (them == me) throw AccountException("That's you.")
        val userId = backend.userId(session, them) ?: throw AccountException("No Contrail account called @$them.")
        val trip = db.syncDao().trips().firstOrNull { it.id == tripId } ?: throw AccountException("That trip is gone.")
        if (trip.following) throw AccountException("You can only share your own trips.")
        val doc = doc(trip.uid) ?: throw AccountException("That trip is gone.")
        backend.share(session, OutgoingShare(userId, me, "trip", trip.uid, doc, System.currentTimeMillis()))
        val list = sent.first().filterNot { it.tripUid == trip.uid && it.userId == userId } + SentShare(trip.uid, userId, them, SyncCodec.hash(doc))
        store.edit { it[sentKey] = encode(list) }
    }

    suspend fun unshare(session: Session, me: String, tripUid: String, userId: String) {
        backend.share(session, OutgoingShare(userId, me, "trip", tripUid, JsonObject(emptyMap()), System.currentTimeMillis(), deleted = true))
        store.edit { p -> p[sentKey] = encode(decode(p[sentKey]).filterNot { it.tripUid == tripUid && it.userId == userId }) }
    }

    /** Runs with every sync: re-sends trips you've changed, then takes in what was shared with you. */
    suspend fun exchange(session: Session, me: String) {
        val now = System.currentTimeMillis()
        val updated = sent.first().map { s ->
            val doc = doc(s.tripUid)
            when {
                doc == null -> {
                    backend.share(session, OutgoingShare(s.userId, me, "trip", s.tripUid, JsonObject(emptyMap()), now, deleted = true))
                    null
                }
                SyncCodec.hash(doc) != s.hash -> {
                    backend.share(session, OutgoingShare(s.userId, me, "trip", s.tripUid, doc, now))
                    s.copy(hash = SyncCodec.hash(doc))
                }
                else -> s
            }
        }.filterNotNull()
        store.edit { it[sentKey] = encode(updated) }

        val cursor = store.data.first()[cursorKey] ?: 0
        val incoming = backend.incoming(session, cursor)
        if (incoming.isEmpty()) return
        val codec = SyncCodec(json)
        val dao = db.syncDao()
        db.withTransaction {
            incoming.filter { it.kind == "trip" }.forEach { s ->
                val uid = followUid(s.fromUsername, s.ref)
                val local = dao.tripByUid(uid)
                // Joined trips are yours now; the sender's edits no longer change them.
                if (local != null && !local.following) return@forEach
                if (s.deleted) {
                    codec.apply(dao, RecordKind.Trip, uid, JsonObject(emptyMap()), deleted = true)
                } else {
                    val data = JsonObject(s.data + mapOf("following" to JsonPrimitive(true), "sharedBy" to JsonPrimitive(s.fromUsername)))
                    // Flights get uids of their own on this side, so they never collide with the sender's.
                    codec.apply(dao, RecordKind.Trip, uid, rekey(data, uid), deleted = false)
                }
            }
        }
        store.edit { it[cursorKey] = incoming.maxOf { it.seq } }
    }

    /** "I'm on this trip too": a followed trip becomes yours. */
    suspend fun join(tripId: Long) {
        val dao = db.syncDao()
        val t = dao.trips().firstOrNull { it.id == tripId } ?: return
        dao.updateTrip(t.copy(following = false))
    }

    /** Stops following: the trip leaves this phone (it was never yours). */
    suspend fun unfollow(tripId: Long) = db.withTransaction {
        val dao = db.syncDao()
        dao.detachVisits(dao.segmentsOf(tripId).map { it.id })
        dao.deleteTrip(tripId)
    }

    private suspend fun doc(tripUid: String): JsonObject? =
        SyncCodec(json).snapshot(db.syncDao()).firstOrNull { it.kind == RecordKind.Trip && it.id == tripUid }?.data

    private fun rekey(data: JsonObject, uid: String): JsonObject {
        val segs = (data["segments"] as? kotlinx.serialization.json.JsonArray)?.map { el ->
            val o = el.jsonObject
            val segUid = (o["uid"] as? JsonPrimitive)?.content.orEmpty()
            JsonObject(o + ("uid" to JsonPrimitive("$uid:$segUid")))
        }
        val prefs = (data["prefs"] as? kotlinx.serialization.json.JsonArray)?.map { el ->
            val o = el.jsonObject
            val segUid = (o["segmentUid"] as? JsonPrimitive)?.content.orEmpty()
            JsonObject(o + ("segmentUid" to JsonPrimitive("$uid:$segUid")))
        }
        return JsonObject(
            data + listOfNotNull(
                segs?.let { "segments" to kotlinx.serialization.json.JsonArray(it) },
                prefs?.let { "prefs" to kotlinx.serialization.json.JsonArray(it) },
            ),
        )
    }

    companion object {
        fun followUid(from: String, ref: String) = "shared:$from:$ref"

        internal fun encode(list: List<SentShare>) = list.joinToString("\n") { "${it.tripUid}\t${it.userId}\t${it.username}\t${it.hash}" }

        internal fun decode(s: String?): List<SentShare> = s.orEmpty().lines().filter { it.isNotBlank() }.mapNotNull { line ->
            val f = line.split("\t")
            if (f.size < 4) null else SentShare(f[0], f[1], f[2], f[3])
        }
    }
}

package com.contrail.contrail.core.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.contrail.contrail.core.data.sync.SharesRepository
import com.contrail.contrail.core.database.ContrailDatabase
import com.contrail.contrail.core.database.SegmentEntity
import com.contrail.contrail.core.database.TripEntity
import com.contrail.contrail.core.network.ContrailHttp
import com.contrail.contrail.core.network.sync.IncomingShare
import com.contrail.contrail.core.network.sync.OutgoingShare
import com.contrail.contrail.core.network.sync.RemoteRecord
import com.contrail.contrail.core.network.sync.Session
import com.contrail.contrail.core.network.sync.SyncBackend
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** The shares table in memory, for two accounts: "ohm" (u1) and "mia" (u2). */
private class FakeShareServer : SyncBackend {
    private val rows = linkedMapOf<List<String>, Pair<String, IncomingShare>>()
    private var seq = 0L
    override val configured = true
    override suspend fun signUp(username: String, password: String) = error("unused")
    override suspend fun signIn(username: String, password: String) = error("unused")
    override suspend fun refresh(refreshToken: String) = error("unused")
    override suspend fun push(session: Session, records: List<RemoteRecord>) = Unit
    override suspend fun pull(session: Session, afterSeq: Long, limit: Int) = emptyList<RemoteRecord>()
    override suspend fun userId(session: Session, username: String) = mapOf("ohm" to "u1", "mia" to "u2")[username]
    override suspend fun share(session: Session, share: OutgoingShare) {
        rows[listOf(session.userId, share.toUser, share.kind, share.ref)] =
            share.toUser to IncomingShare(share.fromUsername, share.kind, share.ref, share.data, share.updatedAt, share.deleted, ++seq)
    }
    override suspend fun incoming(session: Session, afterSeq: Long) =
        rows.values.filter { it.first == session.userId && it.second.seq > afterSeq }.map { it.second }.sortedBy { it.seq }
}

@RunWith(AndroidJUnit4::class)
class SharesTest {
    @get:Rule val tmp = TemporaryFolder()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val ohmDb = Room.inMemoryDatabaseBuilder(context, ContrailDatabase::class.java).allowMainThreadQueries().build()
    private val miaDb = Room.inMemoryDatabaseBuilder(context, ContrailDatabase::class.java).allowMainThreadQueries().build()
    private val server = FakeShareServer()
    private val ohm = Session("u1", "a", "r", Long.MAX_VALUE)
    private val mia = Session("u2", "a", "r", Long.MAX_VALUE)

    private fun repo(db: ContrailDatabase, name: String) =
        SharesRepository(db, server, ContrailHttp.json, PreferenceDataStoreFactory.create { tmp.newFile("$name.preferences_pb") })

    private val ohmShares by lazy { repo(ohmDb, "ohm") }
    private val miaShares by lazy { repo(miaDb, "mia") }

    @After fun close() {
        ohmDb.close()
        miaDb.close()
    }

    private suspend fun ohmTrip(): Long {
        val id = ohmDb.tripDao().insertTrip(TripEntity(title = "Toronto to Abu Dhabi", pnr = null, passengerName = null, source = "Manual", createdAtMs = 1))
        ohmDb.tripDao().insertSegments(
            listOf(
                SegmentEntity(
                    tripId = id, ordinal = 0, airlineIata = "EY", flightNumber = "140", origin = "YYZ", destination = "AUH",
                    departureLocal = "2026-11-12T22:10", arrivalLocal = "2026-11-13T19:55", departureZone = "America/Toronto", arrivalZone = "Asia/Dubai",
                    departureUtcMs = 1, arrivalUtcMs = 2, departureTerminal = "3", arrivalTerminal = null, gate = null, cabin = null,
                    fareClass = null, seat = "7K", aircraftType = "B789", tail = null, operatedBy = null,
                ),
            ),
        )
        return id
    }

    @Test fun sharedTripsArriveFollowAndJoin() = runTest {
        val tripId = ohmTrip()
        ohmShares.share(ohm, "ohm", tripId, "mia")
        miaShares.exchange(mia, "mia")

        val followed = miaDb.syncDao().trips().single()
        assertTrue(followed.following)
        assertEquals("ohm", followed.sharedBy)
        assertEquals(listOf("YYZ"), miaDb.syncDao().segmentsOf(followed.id).map { it.origin })

        // Ohm renames it: Mia's copy follows.
        ohmDb.syncDao().updateTrip(ohmDb.syncDao().trips().single().copy(title = "Abu Dhabi with Mia"))
        ohmShares.exchange(ohm, "ohm")
        miaShares.exchange(mia, "mia")
        assertEquals("Abu Dhabi with Mia", miaDb.syncDao().trips().single().title)

        // Mia joins: it's hers now, and Ohm's later edits don't change it.
        miaShares.join(followed.id)
        ohmDb.syncDao().updateTrip(ohmDb.syncDao().trips().single().copy(title = "Changed again"))
        ohmShares.exchange(ohm, "ohm")
        miaShares.exchange(mia, "mia")
        val joined = miaDb.syncDao().trips().single()
        assertFalse(joined.following)
        assertEquals("ohm", joined.sharedBy)
        assertEquals("Abu Dhabi with Mia", joined.title)
    }

    @Test fun unsharingRemovesAFollowedTrip() = runTest {
        val tripId = ohmTrip()
        ohmShares.share(ohm, "ohm", tripId, "mia")
        miaShares.exchange(mia, "mia")
        assertEquals(1, miaDb.syncDao().trips().size)
        val uid = ohmDb.syncDao().trips().single().uid
        ohmShares.unshare(ohm, "ohm", uid, "u2")
        miaShares.exchange(mia, "mia")
        assertTrue(miaDb.syncDao().trips().isEmpty())
    }
}

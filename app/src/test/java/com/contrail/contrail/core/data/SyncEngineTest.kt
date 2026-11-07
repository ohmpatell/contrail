package com.contrail.contrail.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.contrail.contrail.core.data.sync.SyncCodec
import com.contrail.contrail.core.data.sync.SyncEngine
import com.contrail.contrail.core.database.ContrailDatabase
import com.contrail.contrail.core.database.LoungeVisitEntity
import com.contrail.contrail.core.database.SegmentEntity
import com.contrail.contrail.core.database.TripEntity
import com.contrail.contrail.core.database.WatchItemEntity
import com.contrail.contrail.core.network.ContrailHttp
import com.contrail.contrail.core.network.sync.RemoteRecord
import com.contrail.contrail.core.network.sync.Session
import com.contrail.contrail.core.network.sync.SyncBackend
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A server in memory: numbers every write, like the real trigger. */
private class FakeServer : SyncBackend {
    val rows = linkedMapOf<Pair<String, String>, RemoteRecord>()
    private var seq = 0L
    override val configured = true
    override suspend fun signUp(username: String, password: String) = error("unused")
    override suspend fun signIn(username: String, password: String) = error("unused")
    override suspend fun refresh(refreshToken: String) = error("unused")
    override suspend fun push(session: Session, records: List<RemoteRecord>) {
        records.forEach { rows[it.kind to it.id] = it.copy(seq = ++seq) }
    }
    override suspend fun pull(session: Session, afterSeq: Long, limit: Int) =
        rows.values.filter { it.seq > afterSeq }.sortedBy { it.seq }.take(limit)
}

@RunWith(AndroidJUnit4::class)
class SyncEngineTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val phone = Room.inMemoryDatabaseBuilder(context, ContrailDatabase::class.java).allowMainThreadQueries().build()
    private val tablet = Room.inMemoryDatabaseBuilder(context, ContrailDatabase::class.java).allowMainThreadQueries().build()
    private val server = FakeServer()
    private val session = Session("u", "a", "r", Long.MAX_VALUE)
    private var clock = 1_000L
    private val cursors = mutableMapOf<ContrailDatabase, Long>()

    private suspend fun sync(db: ContrailDatabase) {
        clock += 1_000
        val r = SyncEngine(db, server, SyncCodec(ContrailHttp.json), { clock }).sync(session, cursors[db] ?: 0)
        cursors[db] = r.cursor
    }

    private fun seg(tripId: Long, ordinal: Int, from: String, to: String) = SegmentEntity(
        tripId = tripId, ordinal = ordinal, airlineIata = "EY", flightNumber = "14$ordinal", origin = from, destination = to,
        departureLocal = "2026-11-12T22:10", arrivalLocal = "2026-11-13T19:55", departureZone = "America/Toronto", arrivalZone = "Asia/Dubai",
        departureUtcMs = 1_000L + ordinal, arrivalUtcMs = 2_000L + ordinal, departureTerminal = "3", arrivalTerminal = null, gate = null,
        cabin = "Business", fareClass = null, seat = "7K", aircraftType = "B789", tail = null, operatedBy = null,
    )

    @After fun close() {
        phone.close()
        tablet.close()
    }

    @Test fun tripsTravelBetweenDevicesWithTheirFlightsAndLounges() = runTest {
        val dao = phone.tripDao()
        val tripId = dao.insertTrip(TripEntity(title = "Toronto to Delhi", pnr = "K7Q2LM", passengerName = null, source = "Manual", createdAtMs = 1))
        val segIds = dao.insertSegments(listOf(seg(tripId, 0, "YYZ", "AUH"), seg(tripId, 1, "AUH", "DEL")))
        phone.loungeDao().insertVisit(LoungeVisitEntity(segmentId = segIds[1], phase = "Departure", loungeKey = "AUH:first", loungeName = "First Lounge", airport = "AUH", terminal = "A", date = "2026-11-13", timeIn = null, timeOut = null, access = "Ticket", rating = 5, note = "", createdAtMs = 1))
        phone.syncDao().upsertWatch(WatchItemEntity("type", "A388", "A380", 1))

        sync(phone)
        sync(tablet)

        val t = tablet.syncDao()
        val trips = t.trips()
        assertEquals(listOf("Toronto to Delhi"), trips.map { it.title })
        val segs = t.segmentsOf(trips.single().id).sortedBy { it.ordinal }
        assertEquals(listOf("YYZ-AUH", "AUH-DEL"), segs.map { "${it.origin}-${it.destination}" })
        // The lounge visit points at the tablet's own row for the second flight.
        assertEquals(segs[1].id, t.visits().single().segmentId)
        assertEquals(listOf("A388"), t.watch().map { it.value })
    }

    @Test fun editsAndDeletesFollow() = runTest {
        val tripId = phone.tripDao().insertTrip(TripEntity(title = "Weekend", pnr = null, passengerName = null, source = "Manual", createdAtMs = 1))
        phone.tripDao().insertSegments(listOf(seg(tripId, 0, "YYZ", "YVR")))
        sync(phone)
        sync(tablet)

        // Rename on the tablet; the phone picks it up.
        val onTablet = tablet.syncDao().trips().single()
        tablet.syncDao().updateTrip(onTablet.copy(title = "Vancouver weekend"))
        sync(tablet)
        sync(phone)
        assertEquals("Vancouver weekend", phone.syncDao().trips().single().title)

        // Delete on the phone; the tablet deletes it too.
        phone.syncDao().deleteTrip(phone.syncDao().trips().single().id)
        sync(phone)
        sync(tablet)
        assertTrue(tablet.syncDao().trips().isEmpty())
        assertTrue(server.rows.values.single().deleted)
    }

    @Test fun unsyncedLocalEditsAreNotOverwritten() = runTest {
        val tripId = phone.tripDao().insertTrip(TripEntity(title = "A", pnr = null, passengerName = null, source = "Manual", createdAtMs = 1))
        phone.tripDao().insertSegments(listOf(seg(tripId, 0, "YYZ", "YVR")))
        sync(phone)
        sync(tablet)
        tablet.syncDao().updateTrip(tablet.syncDao().trips().single().copy(title = "From tablet"))
        sync(tablet)
        // The phone edits before it hears about the tablet's change: its edit is kept and sent.
        phone.syncDao().updateTrip(phone.syncDao().trips().single().copy(title = "From phone"))
        sync(phone)
        assertEquals("From phone", phone.syncDao().trips().single().title)
        sync(tablet)
        assertEquals("From phone", tablet.syncDao().trips().single().title)
        assertNull(server.rows.values.firstOrNull { it.deleted })
    }
}

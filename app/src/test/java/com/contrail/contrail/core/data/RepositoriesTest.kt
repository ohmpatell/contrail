package com.contrail.contrail.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.model.AircraftPhoto
import com.contrail.contrail.core.data.model.AirportRef
import com.contrail.contrail.core.data.model.FlightRoute
import com.contrail.contrail.core.data.repository.DefaultAircraftDetailsRepository
import com.contrail.contrail.core.data.repository.DefaultLiveTrafficRepository
import com.contrail.contrail.core.database.ContrailDatabase
import com.contrail.contrail.core.network.photo.PhotoSource
import com.contrail.contrail.core.network.route.RouteSource
import com.contrail.contrail.core.network.traffic.LiveSnapshot
import com.contrail.contrail.core.network.traffic.LiveTrafficSource
import com.contrail.contrail.testing.aircraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveTrafficRepositoryTest {
    private lateinit var db: ContrailDatabase
    private var now = 1_000_000L
    private val clock = Clock { now }
    private var next = LiveSnapshot(emptyList(), 0, "fake")
    private val source = object : LiveTrafficSource {
        override val name = "fake"
        override suspend fun fetchAround(lat: Double, lon: Double, radiusNm: Int) = next
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), ContrailDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `refresh stores traffic that screens observe`() = runTest {
        val repo = DefaultLiveTrafficRepository(db, source, clock)
        next = LiveSnapshot(listOf(aircraft(hex = "a", positionTimeMs = now, fetchedAtMs = now)), now, "fake")
        assertEquals(1, repo.refresh(LatLon(43.0, -79.0), 50).count)
        assertEquals(listOf("a"), repo.observeTraffic().first().map { it.hex })
    }

    @Test
    fun `aircraft not seen for a while are pruned`() = runTest {
        val repo = DefaultLiveTrafficRepository(db, source, clock)
        next = LiveSnapshot(listOf(aircraft(hex = "old", fetchedAtMs = now, positionTimeMs = now)), now, "fake")
        repo.refresh(LatLon(0.0, 0.0), 10)
        now += DefaultLiveTrafficRepository.STALE_AFTER_MS + 1
        next = LiveSnapshot(listOf(aircraft(hex = "new", fetchedAtMs = now, positionTimeMs = now)), now, "fake")
        repo.refresh(LatLon(0.0, 0.0), 10)
        assertEquals(listOf("new"), repo.observeTraffic().first().map { it.hex })
    }

    @Test
    fun `trails keep turns and gaps but thin straight lines`() = runTest {
        val repo = DefaultLiveTrafficRepository(db, source, clock)
        fun step(t: Long, lon: Double, track: Float) {
            now = t
            next = LiveSnapshot(listOf(aircraft(hex = "a", lon = lon, trackDeg = track, positionTimeMs = t, fetchedAtMs = t)), t, "fake")
        }
        val t0 = now
        step(t0, -79.00, 90f); repo.refresh(LatLon(0.0, 0.0), 10)
        step(t0 + 4_000, -78.99, 90f); repo.refresh(LatLon(0.0, 0.0), 10) // straight: thinned out
        step(t0 + 8_000, -78.98, 120f); repo.refresh(LatLon(0.0, 0.0), 10) // turned: kept
        step(t0 + 30_000, -78.90, 120f); repo.refresh(LatLon(0.0, 0.0), 10) // gap: kept
        assertEquals(listOf(t0, t0 + 8_000, t0 + 30_000), repo.observeTrail("a").first().map { it.timeMs })
    }
}

@RunWith(AndroidJUnit4::class)
class AircraftDetailsRepositoryTest {
    private lateinit var db: ContrailDatabase
    private var now = 0L
    private var routeCalls = 0
    private var photoCalls = 0
    private var route: FlightRoute? = FlightRoute(
        "ETD141",
        AirportRef("AUH", "OMAA", "Zayed International", "Abu Dhabi", "AE", 24.43, 54.65),
        AirportRef("YYZ", "CYYZ", "Pearson", "Toronto", "CA", 43.68, -79.63),
        "Etihad Airways",
    )

    private val repo by lazy {
        DefaultAircraftDetailsRepository(
            db,
            object : RouteSource {
                override suspend fun routeForCallsign(callsign: String): FlightRoute? { routeCalls++; return route }
            },
            object : PhotoSource {
                override suspend fun photoFor(hex: String, registration: String?): AircraftPhoto? { photoCalls++; return null }
            },
            Clock { now },
        )
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), ContrailDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `routes are cached until their TTL runs out`() = runTest {
        repo.refreshRoute("etd141")
        repo.refreshRoute("ETD141")
        assertEquals(1, routeCalls)
        assertEquals("AUH", repo.observeRoute("ETD141").first()?.origin?.code)
        now += DefaultAircraftDetailsRepository.ROUTE_HIT_TTL + 1
        repo.refreshRoute("ETD141")
        assertEquals(2, routeCalls)
    }

    @Test
    fun `misses are cached too, so unknown callsigns are not hammered`() = runTest {
        route = null
        repo.refreshRoute("ZZZ1")
        repo.refreshRoute("ZZZ1")
        assertEquals(1, routeCalls)
        assertNull(repo.observeRoute("ZZZ1").first())
        repo.refreshPhoto("abc", null)
        repo.refreshPhoto("abc", null)
        assertEquals(1, photoCalls)
    }
}

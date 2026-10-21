package com.contrail.contrail.feature

import com.contrail.contrail.core.data.repository.LoungeAccess
import com.contrail.contrail.core.data.repository.LoungePhase
import com.contrail.contrail.core.data.repository.LoungeRepository
import com.contrail.contrail.core.data.repository.LoungeSource
import com.contrail.contrail.core.data.repository.LoungeVisit
import com.contrail.contrail.core.data.rotation.TurnBadge
import com.contrail.contrail.core.data.rotation.TurnThresholds
import com.contrail.contrail.core.data.rotation.TurnaroundDay
import com.contrail.contrail.core.data.rotation.TurnaroundEstimate
import com.contrail.contrail.core.data.trip.ConnectionKind
import com.contrail.contrail.feature.passport.PassportCalculator
import com.contrail.contrail.feature.trips.TripsUiState
import com.contrail.contrail.feature.trips.flight.FlightTimeline
import com.contrail.contrail.feature.trips.flight.LiveState
import com.contrail.contrail.feature.trips.importing.SegmentForm
import com.contrail.contrail.testing.TripFixtures
import com.contrail.contrail.testing.aircraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class Stage3LogicTest {
    private val now = TripFixtures.now
    private val places = TripFixtures.placesFor(TripFixtures.all)

    @Test
    fun `abu dhabi with a hotel is a stopover, hong kong a layover`() {
        assertEquals(ConnectionKind.Stopover, TripFixtures.delhi.connections.single().kind)
        assertEquals(ConnectionKind.Layover, TripFixtures.manila.connections.single().kind)
    }

    @Test
    fun `trips tab splits upcoming and past and finds the next flight`() {
        val s = TripsUiState.from(TripFixtures.all, now, places)
        assertEquals(listOf(2L, 1L), s.upcoming.map { it.id })
        assertEquals(listOf(6L, 5L, 4L, 3L), s.past.map { it.id })
        assertEquals("EY 140", s.next?.second?.designator)
    }

    @Test
    fun `passport counts layovers as transited and stopovers as visited`() {
        val later = Instant.parse("2027-01-10T00:00:00Z")
        val all = TripFixtures.all
        val s = PassportCalculator.compute(all, TripFixtures.placesFor(all), emptyList(), emptySet(), later)
        assertEquals(9, s.flights)
        assertTrue("AUH stopover", "AUH" in s.visited)
        assertTrue("HKG was a destination in August", "HKG" in s.visited)
        assertTrue("LHR only a layover", "LHR" in s.transited)
        assertFalse("visited wins over transited", "HKG" in s.transited)
        assertEquals(setOf("YYZ", "YVR", "BOM", "HKG", "AUH", "DEL", "MNL"), s.visited.toSet())
        assertEquals(setOf("CA", "IN", "HK", "AE", "PH"), s.countries)
        assertEquals("CX 827", s.longest?.designator)
        assertEquals(2, s.topRoute?.second)
        assertTrue(s.distanceKm > 50_000)
    }

    @Test
    fun `passport only counts flights that have landed`() {
        val s = PassportCalculator.compute(TripFixtures.all, places, emptyList(), emptySet(), now)
        assertEquals(5, s.flights)
        assertFalse("not flown yet", "MNL" in s.visited)
        val collected = s.collection.filter { it.flights > 0 }.map { it.code }.toSet()
        assertEquals(setOf("B789", "A333", "B77W", "B788"), collected)
        assertEquals("AC", s.collection.first { it.code == "B789" }.firstAirlineIcao?.let { "AC" })
    }

    @Test
    fun `year in review filters to that year`() {
        val s = PassportCalculator.compute(TripFixtures.all, places, emptyList(), emptySet(), Instant.parse("2027-02-01T00:00:00Z"), year = 2026)
        assertEquals(9, s.flights)
        val none = PassportCalculator.compute(TripFixtures.all, places, emptyList(), emptySet(), Instant.parse("2027-02-01T00:00:00Z"), year = 2025)
        assertEquals(0, none.flights)
        assertEquals(listOf(2026), s.years)
    }

    @Test
    fun `lounge stats total time across midnight and rank favourites first`() {
        fun v(id: Long, key: String, name: String, inT: String, outT: String, rating: Int) = LoungeVisit(
            id, null, LoungePhase.Departure, key, name, "HKG", "1", LocalDate.of(2026, 8, 11), LocalTime.parse(inT), LocalTime.parse(outT), LoungeAccess.Cabin, rating, "",
        )
        val visits = listOf(v(1, "a", "The Pier", "23:30", "01:00", 5), v(2, "b", "The Wing", "10:00", "11:00", 4), v(3, "a", "The Pier", "06:00", "07:00", 4))
        val l = PassportCalculator.loungeStats(visits, favourites = setOf("b"))
        assertEquals(3, l.visits)
        assertEquals(2, l.lounges)
        assertEquals(90L + 60 + 60, l.minutes)
        assertEquals("The Wing", l.collection.first().name)
        assertEquals(setOf("The Wing", "The Pier"), l.favourites.map { it.name }.toSet())
    }

    @Test
    fun `turnaround badge from the median ground time`() {
        fun day(m: Int?, stayed: Boolean = false) = TurnaroundDay("2026-10-1${(m ?: 0) % 10}", "abc", 0, m, stayed)
        val t = TurnThresholds()
        assertEquals(TurnBadge.Tight, TurnaroundEstimate(listOf(day(45), day(50), day(40))).badge(t))
        assertEquals(TurnBadge.Maybe, TurnaroundEstimate(listOf(day(45), day(95), day(100))).badge(t))
        assertEquals(TurnBadge.Relaxed, TurnaroundEstimate(listOf(day(160), day(150), day(200))).badge(t))
        val stays = TurnaroundEstimate(listOf(day(null, true), day(null, true), day(150)))
        assertTrue(stays.usuallyStays)
        assertEquals(TurnBadge.Relaxed, stays.badge(t))
        assertNull(TurnaroundEstimate(listOf(TurnaroundDay("2026-10-10"))).badge(t))
        assertEquals(TurnBadge.Maybe, TurnaroundEstimate(listOf(day(100))).badge(TurnThresholds(relaxedMinutes = 150, maybeMinutes = 90)))
    }

    @Test
    fun `review form reads typed dates and times`() {
        assertEquals(LocalDate.of(2026, 12, 12), SegmentForm.parseDate("12 Dec 2026"))
        assertEquals(LocalDate.of(2026, 12, 12), SegmentForm.parseDate("12 dec 2026"))
        assertEquals(LocalDate.of(2026, 12, 12), SegmentForm.parseDate("2026-12-12"))
        assertEquals(LocalDate.of(2026, 12, 12), SegmentForm.parseDate("12/12/2026"))
        assertEquals(LocalDate.of(2026, 12, 12), SegmentForm.parseDate("20261212"))
        assertNull(SegmentForm.parseDate("next week"))
        assertEquals(LocalTime.of(22, 10), SegmentForm.parseTime("22:10"))
        assertEquals(LocalTime.of(7, 5), SegmentForm.parseTime("0705"))
        assertNull(SegmentForm.parseTime("25:00"))
        val d = SegmentForm(flight = "ey0140", from = "yyz", to = "auh").toDraft()
        assertEquals("EY", d.airlineIata)
        assertEquals("140", d.flightNumber)
        assertEquals("YYZ", d.origin)
    }

    @Test
    fun `timeline estimates takeoff from taxi time and landing from live speed`() {
        val s = TripFixtures.delhi.segments.first()
        val rows = FlightTimeline.rows(s, LiveState(), places, now)
        assertEquals(listOf("Gate out", "Takeoff", "Landing", "Gate in"), rows.map { it.label })
        assertEquals(s.departureLocal, rows[0].scheduled)
        assertEquals(s.departureLocal.plusMinutes(15), rows[1].estimated)
        assertNull(rows[3].estimated)

        val airborneAt = s.departure.plusSeconds(6 * 3_600)
        val auh = places.getValue("AUH")
        val live = LiveState(aircraft(lat = auh.lat + 4.5, lon = auh.lon, groundSpeedKt = 480f, onGround = false))
        val liveRows = FlightTimeline.rows(s, live, places, airborneAt)
        assertNotNull(liveRows[3].estimated)
        val delay = FlightTimeline.delay(liveRows, s)!!
        assertTrue("about 500 km out at 480 kt lands well ahead of schedule", delay.isNegative)
    }

    @Test
    fun `osm lounges skip bus and tv waiting rooms`() {
        val body = """{"elements":[
            {"type":"node","tags":{"amenity":"lounge","name":"The Pier"}},
            {"type":"node","tags":{"amenity":"lounge","name":"Bonded Bus Waiting Lounge"}},
            {"type":"node","tags":{"amenity":"lounge","name":"TV Lounge"}},
            {"type":"node","tags":{"amenity":"lounge","name:en":"Plaza Premium Lounge","name":"環亞機場貴賓室"}},
            {"type":"node","tags":{"amenity":"lounge"}}
        ]}"""
        val l = LoungeRepository.osmLounges("HKG", body)
        assertEquals(listOf("The Pier", "Plaza Premium Lounge"), l.map { it.name })
        assertTrue(l.all { it.source == LoungeSource.OpenStreetMap })
    }
}

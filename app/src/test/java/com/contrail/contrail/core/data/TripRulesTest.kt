package com.contrail.contrail.core.data

import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.core.data.trip.ConnectionKind
import com.contrail.contrail.core.data.trip.ConnectionLength
import com.contrail.contrail.core.data.trip.ConnectionPref
import com.contrail.contrail.core.data.trip.ConnectionRules
import com.contrail.contrail.core.data.trip.FlightSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

class TripRulesTest {
    private val toronto = ZoneId.of("America/Toronto")
    private val hk = ZoneId.of("Asia/Hong_Kong")
    private val dubai = ZoneId.of("Asia/Dubai")
    private val manila = ZoneId.of("Asia/Manila")
    private val india = ZoneId.of("Asia/Kolkata")

    private fun seg(id: Long, from: String, to: String, dep: String, depZ: ZoneId, arr: String, arrZ: ZoneId, depT: String? = null, arrT: String? = null) =
        FlightSegment(id, 1, id.toInt(), "XX", "$id", from, to, LocalDateTime.parse(dep), LocalDateTime.parse(arr), depZ, arrZ, depT, arrT)

    private val cx829 = seg(1, "YYZ", "HKG", "2026-12-19T01:15", toronto, "2026-12-20T05:00", hk, arrT = "1")
    private val cx907 = seg(2, "HKG", "MNL", "2026-12-20T08:35", hk, "2026-12-20T10:45", manila, depT = "1")
    private val ey140 = seg(3, "YYZ", "AUH", "2026-12-12T22:10", toronto, "2026-12-13T19:55", dubai, arrT = "A")
    private val ey218 = seg(4, "AUH", "DEL", "2026-12-15T02:35", dubai, "2026-12-15T07:50", india, depT = "A")

    @Test
    fun `length badges`() {
        assertEquals(ConnectionLength.Tight, ConnectionRules.length(Duration.ofMinutes(55)))
        assertEquals(ConnectionLength.Comfortable, ConnectionRules.length(Duration.ofMinutes(90)))
        assertEquals(ConnectionLength.Comfortable, ConnectionRules.length(Duration.ofHours(4)))
        assertEquals(ConnectionLength.Long, ConnectionRules.length(Duration.ofHours(4).plusMinutes(1)))
    }

    @Test
    fun `hong kong on the way to manila is a layover`() {
        val c = ConnectionRules.connections(listOf(cx829, cx907), emptyMap()).single()
        assertEquals(ConnectionKind.Layover, c.kind)
        assertTrue(c.guessed)
        assertEquals(Duration.ofMinutes(215), c.duration)
        assertEquals(ConnectionLength.Comfortable, c.length)
        assertNull("same terminal", c.terminalChange)
    }

    @Test
    fun `abu dhabi for 30 hours is a stopover`() {
        val c = ConnectionRules.connections(listOf(ey140, ey218), emptyMap()).single()
        assertEquals(ConnectionKind.Stopover, c.kind)
        assertEquals(ConnectionLength.Long, c.length)
    }

    @Test
    fun `overnight with a hotel is a stopover, without one a layover`() {
        val inbound = seg(5, "YYZ", "LHR", "2026-12-01T21:00", toronto, "2026-12-02T09:00", ZoneId.of("Europe/London"))
        val night = seg(6, "LHR", "DEL", "2026-12-02T21:00", ZoneId.of("Europe/London"), "2026-12-03T11:00", india)
        val overnightIn = seg(7, "LHR", "DEL", "2026-12-03T07:00", ZoneId.of("Europe/London"), "2026-12-03T21:00", india)
        assertFalse(ConnectionRules.isOvernight(inbound.arrivalLocal, night.departureLocal))
        assertTrue(ConnectionRules.isOvernight(inbound.arrivalLocal, overnightIn.departureLocal))
        assertEquals(ConnectionKind.Layover, ConnectionRules.guess(inbound, overnightIn, hotel = false))
        assertEquals(ConnectionKind.Stopover, ConnectionRules.guess(inbound, overnightIn, hotel = true))
        assertEquals(ConnectionKind.Layover, ConnectionRules.guess(inbound, night, hotel = true))
    }

    @Test
    fun `your tap overrides the guess`() {
        val prefs = mapOf(1L to ConnectionPref(1, ConnectionKind.Stopover, hotel = false))
        val c = ConnectionRules.connections(listOf(cx829, cx907), prefs).single()
        assertEquals(ConnectionKind.Stopover, c.kind)
        assertFalse(c.guessed)
    }

    @Test
    fun `terminal change`() {
        val a = cx829.copy(arrivalTerminal = "1")
        val b = cx907.copy(departureTerminal = "2")
        assertEquals("1" to "2", ConnectionRules.connections(listOf(a, b), emptyMap()).single().terminalChange)
    }

    @Test
    fun `icao callsigns from flight numbers`() {
        assertEquals("ETD141", Airlines.callsignFor("EY", "141"))
        assertEquals("ACA15", Airlines.callsignFor("AC", "015"))
        assertEquals("CPA907", Airlines.callsignFor("CX", "907"))
        assertNull(Airlines.callsignFor("ZZ", "1"))
    }
}

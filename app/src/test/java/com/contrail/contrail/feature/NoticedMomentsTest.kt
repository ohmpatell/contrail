package com.contrail.contrail.feature

import com.contrail.contrail.core.data.moments.MomentKind
import com.contrail.contrail.feature.passport.NoticedMoments
import com.contrail.contrail.testing.PassportFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NoticedMomentsTest {
    private val now = Instant.parse("2026-10-21T12:00:00Z")
    private val flown = PassportFixtures.trips.flatMap { it.segments }.filter { it.arrival.isBefore(now) }

    @Test
    fun `firsts, milestones and crossings come from the logbook`() {
        val m = NoticedMoments.from(flown, PassportFixtures.places, hangar = emptySet())
        assertEquals(1, m.count { it.kind == MomentKind.FirstFlight })
        assertTrue(m.any { it.kind == MomentKind.Milestone && it.title == "Flight number 10" })
        assertTrue(m.any { it.kind == MomentKind.Crossing && it.title.contains("equator") })
        assertTrue(m.any { it.kind == MomentKind.FirstType })
        // Noticed moments are Contrail's, not yours: never editable, ids stable.
        assertTrue(m.none { it.mine })
        assertEquals(m.size, m.map { it.id }.distinct().size)
    }

    @Test
    fun `a flight on a pinned plane is a moment`() {
        val tail = flown.firstNotNullOf { it.tail }
        val m = NoticedMoments.from(flown, PassportFixtures.places, hangar = setOf(tail.uppercase()))
        assertTrue(m.any { it.kind == MomentKind.HangarFlight && it.title.contains(tail.uppercase()) })
    }
}

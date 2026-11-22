package com.contrail.contrail.core.data

import com.contrail.contrail.core.data.search.SearchQuery
import com.contrail.contrail.core.data.search.SearchTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryTest {
    @Test
    fun `a flight number becomes its ATC callsign`() {
        val t = SearchQuery.targets("cx 888")
        val cs = t.filterIsInstance<SearchTarget.Callsign>().map { it.callsign }
        assertTrue(cs.toString(), "CPA888" in cs)
    }

    @Test
    fun `leading zeros are dropped like ATC does`() {
        val cs = SearchQuery.targets("AC015").filterIsInstance<SearchTarget.Callsign>().map { it.callsign }
        assertTrue(cs.toString(), "ACA15" in cs)
    }

    @Test
    fun `a typed callsign is searched as it is`() {
        val t = SearchQuery.targets("UAL286")
        assertEquals(listOf("UAL286"), t.filterIsInstance<SearchTarget.Callsign>().map { it.callsign })
    }

    @Test
    fun `registrations with and without a dash`() {
        for (reg in listOf("B-LXP", "C-FHFY", "9V-SMA", "N123AB", "JA731J", "HL7782")) {
            val regs = SearchQuery.targets(reg.lowercase()).filterIsInstance<SearchTarget.Registration>().map { it.registration }
            assertEquals(reg, listOf(reg), regs)
        }
    }

    @Test
    fun `six hex digits can be a transponder code and a callsign`() {
        val t = SearchQuery.targets("ABC123")
        assertTrue(t.any { it is SearchTarget.Hex && it.hex == "abc123" })
        assertTrue(t.any { it is SearchTarget.Callsign && it.callsign == "ABC123" })
    }

    @Test
    fun `too short or meaningless text reads as nothing`() {
        assertTrue(SearchQuery.targets("ab").isEmpty())
        assertTrue(SearchQuery.targets("hello world").isEmpty())
    }
}

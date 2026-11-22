package com.contrail.contrail.core.data.search

import com.contrail.contrail.core.data.reference.Airlines

/** One way of reading what was typed into the radar's search box. */
sealed interface SearchTarget {
    /** Shown under the box so it's clear how the text was read. */
    val label: String

    /** An ATC callsign: typed as one (CPA888) or worked out from a flight number (CX 888). */
    data class Callsign(val callsign: String, override val label: String) : SearchTarget

    /** A registration (tail number): B-LXP, C-FHFY, N123AB, JA731J. */
    data class Registration(val registration: String, override val label: String) : SearchTarget

    /** A 24-bit transponder address, six hex digits. */
    data class Hex(val hex: String, override val label: String) : SearchTarget
}

/** Reads a search entry as callsigns, flight numbers, registrations or transponder codes. */
object SearchQuery {
    private val REG_DASH = Regex("^[A-Z0-9]{1,2}-[A-Z0-9]{2,5}$")
    private val REG_NO_DASH = Regex("^(N[1-9][0-9]{0,4}[A-Z]{0,2}|JA[0-9A-Z]{4}|HL[0-9]{4}|B[0-9]{4,5})$")
    private val FLIGHT = Regex("^([A-Z][A-Z0-9]|[0-9][A-Z]) ?0*([0-9]{1,4}[A-Z]?)$")
    private val CALLSIGN = Regex("^[A-Z]{3}[0-9]{1,4}[A-Z]{0,2}$")
    private val HEX = Regex("^[0-9A-F]{6}$")

    /** Every plausible reading, most likely first. Empty when nothing fits. */
    fun targets(raw: String): List<SearchTarget> {
        val q = raw.trim().uppercase().replace(Regex("\\s+"), " ")
        if (q.length < 3) return emptyList()
        val compact = q.replace(" ", "")
        val out = mutableListOf<SearchTarget>()
        if (REG_DASH.matches(compact) || REG_NO_DASH.matches(compact)) {
            out += SearchTarget.Registration(compact, "Registration $compact")
        }
        FLIGHT.matchEntire(q)?.let { m ->
            val airline = m.groupValues[1]
            val number = m.groupValues[2]
            Airlines.callsignFor(airline, number)?.let { cs ->
                val name = Airlines.byIata(airline)?.name
                out += SearchTarget.Callsign(cs, "Flight $airline $number" + (name?.let { " · $it" } ?: "") + " · callsign $cs")
            }
        }
        if (CALLSIGN.matches(compact)) {
            val name = Airlines.byIcao(compact.take(3))?.name
            out += SearchTarget.Callsign(compact, "Callsign $compact" + (name?.let { " · $it" } ?: ""))
        }
        if (HEX.matches(compact)) out += SearchTarget.Hex(compact.lowercase(), "Transponder $compact")
        return out.distinctBy {
            when (it) {
                is SearchTarget.Callsign -> "c:" + it.callsign
                is SearchTarget.Registration -> "r:" + it.registration
                is SearchTarget.Hex -> "h:" + it.hex
            }
        }
    }
}

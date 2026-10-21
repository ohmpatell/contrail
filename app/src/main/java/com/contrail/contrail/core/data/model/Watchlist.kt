package com.contrail.contrail.core.data.model

import com.contrail.contrail.core.data.reference.Airlines

enum class WatchKind { Tail, Type, Airline }

/** One thing you're watching for: a registration, an ICAO type code or an airline ICAO code. */
data class WatchItem(val kind: WatchKind, val value: String, val label: String)

/** Matches live aircraft against everything on the watchlist. */
class Watchlist(val items: List<WatchItem>) {
    private val tails = items.filter { it.kind == WatchKind.Tail }.mapTo(HashSet()) { normalizeTail(it.value) }
    private val types = items.filter { it.kind == WatchKind.Type }.mapTo(HashSet()) { it.value.uppercase() }
    private val airlines = items.filter { it.kind == WatchKind.Airline }.mapTo(HashSet()) { it.value.uppercase() }

    val isEmpty: Boolean get() = items.isEmpty()

    fun matches(a: LiveAircraft): Boolean =
        (a.registration != null && normalizeTail(a.registration) in tails) ||
            a.hex.uppercase() in tails ||
            (a.typeCode != null && a.typeCode.uppercase() in types) ||
            Airlines.icaoFromCallsign(a.callsign) in airlines

    fun contains(kind: WatchKind, value: String): Boolean = when (kind) {
        WatchKind.Tail -> normalizeTail(value) in tails
        WatchKind.Type -> value.uppercase() in types
        WatchKind.Airline -> value.uppercase() in airlines
    }

    companion object {
        /** "c-fiua", "CFIUA" and "C-FIUA" are the same tail. */
        fun normalizeTail(raw: String): String = raw.uppercase().filter { it.isLetterOrDigit() }
    }
}

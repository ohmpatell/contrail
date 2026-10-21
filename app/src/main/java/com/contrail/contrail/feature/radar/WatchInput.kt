package com.contrail.contrail.feature.radar

import com.contrail.contrail.core.data.model.WatchItem
import com.contrail.contrail.core.data.model.WatchKind
import com.contrail.contrail.core.data.reference.AircraftTypes
import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.core.data.reference.TypeDesignators

/** Works out what a typed watchlist entry means: airline code, aircraft type, or tail. */
object WatchInput {
    fun parse(raw: String): WatchItem? {
        val v = raw.trim().uppercase()
        if (v.length < 2) return null
        Airlines.byIcao(v)?.let { return WatchItem(WatchKind.Airline, v, it.name) }
        if (v.length in 2..4 && v.none { it == '-' } && (AircraftTypes.byIcao(v) != null || TypeDesignators.lookup(v) != null)) {
            return WatchItem(WatchKind.Type, v, AircraftTypes.nameFor(v) ?: v)
        }
        if (v.length < 3) return null
        return WatchItem(WatchKind.Tail, v, v)
    }
}

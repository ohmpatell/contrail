package com.contrail.contrail.core.data.repository

import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.data.model.LiveAircraft
import com.contrail.contrail.core.data.trip.FlightSegment
import com.contrail.contrail.core.network.traffic.AircraftLookupSource
import javax.inject.Inject
import javax.inject.Singleton

/** Finds your flight live on adsb.fi (adsb.lol as backup) by its ICAO callsign, e.g. EY 141 → ETD141. */
@Singleton
class LiveFlightRepository @Inject constructor(
    private val lookup: AircraftLookupSource,
    private val airports: AirportRepository,
) {
    /**
     * The aircraft flying your flight right now, or null. When a callsign is in use by more
     * than one aircraft, the one nearest your route wins.
     */
    suspend fun find(segment: FlightSegment): LiveAircraft? {
        val callsign = segment.icaoCallsign ?: return null
        val found = lookup.byCallsign(callsign).filter { it.callsign?.trim().equals(callsign, ignoreCase = true) }
        if (found.size <= 1) return found.firstOrNull()
        val dir = airports.directory()
        val a = dir.find(segment.origin) ?: return found.first()
        val b = dir.find(segment.destination) ?: return found.first()
        return found.minByOrNull { ac ->
            Geo.distanceM(ac.lat, ac.lon, a.lat, a.lon) + Geo.distanceM(ac.lat, ac.lon, b.lat, b.lon)
        }
    }
}

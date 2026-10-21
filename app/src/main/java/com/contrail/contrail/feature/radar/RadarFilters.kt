package com.contrail.contrail.feature.radar

import com.contrail.contrail.core.data.model.LiveAircraft
import com.contrail.contrail.core.data.model.Watchlist
import com.contrail.contrail.core.data.reference.AircraftTypes
import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.core.data.reference.Rarity

/** Altitude bands offered in the filter panel. Upper bounds are exclusive. */
enum class AltitudeBand(val label: String, val sampleAltitudeFt: Int?) {
    Ground("Ground", null),
    Low("Below 10k", 5_000),
    Mid("10–25k", 18_000),
    Cruise("25–40k", 34_000),
    High("Above 40k", 42_000),
    ;

    companion object {
        fun of(aircraft: LiveAircraft): AltitudeBand {
            val alt = aircraft.altitudeFt
            return when {
                aircraft.onGround || alt == null -> Ground
                alt < 10_000 -> Low
                alt < 25_000 -> Mid
                alt < 40_000 -> Cruise
                else -> High
            }
        }
    }
}

/**
 * Radar filters. Empty sets mean "any". Within a facet the choices are OR-ed; across
 * facets they are AND-ed.
 */
data class RadarFilters(
    val airlines: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val bands: Set<AltitudeBand> = emptySet(),
    val militaryOnly: Boolean = false,
    val rareOnly: Boolean = false,
    val watchlistOnly: Boolean = false,
    /** On: parked and taxiing aircraft appear once zoomed into an airport. Off: never. */
    val showGround: Boolean = true,
) {
    val activeCount: Int
        get() = listOf(
            airlines.isNotEmpty(), types.isNotEmpty(), bands.isNotEmpty(),
            militaryOnly, rareOnly, watchlistOnly, !showGround,
        ).count { it }

    /** True when nothing narrows the traffic (ground visibility aside, it's the default). */
    val isEmpty: Boolean get() = activeCount == 0

    fun matches(aircraft: LiveAircraft, watchlist: Watchlist = Watchlist(emptyList())): Boolean {
        if (!showGround && aircraft.onGround) return false
        if (watchlistOnly && !watchlist.matches(aircraft)) return false
        if (militaryOnly && !aircraft.military && aircraft.type?.military != true) return false
        if (rareOnly && aircraft.rarity > Rarity.Rare) return false
        if (bands.isNotEmpty() && AltitudeBand.of(aircraft) !in bands) return false
        if (airlines.isNotEmpty() && Airlines.icaoFromCallsign(aircraft.callsign) !in airlines) return false
        if (types.isNotEmpty() && aircraft.typeCode !in types) return false
        return true
    }

    fun toggleAirline(icao: String) = copy(airlines = airlines.toggle(icao))
    fun toggleType(code: String) = copy(types = types.toggle(code))
    fun toggleBand(band: AltitudeBand) = copy(bands = bands.toggle(band))

    private fun <T> Set<T>.toggle(value: T) = if (value in this) this - value else this + value
}

data class Facet(val key: String, val label: String, val count: Int)

/** What's actually on screen, so the filter panel only offers choices that exist. */
data class FilterFacets(
    val airlines: List<Facet> = emptyList(),
    val types: List<Facet> = emptyList(),
    val militaryCount: Int = 0,
    val rareCount: Int = 0,
) {
    companion object {
        const val MAX_CHIPS = 14

        fun from(traffic: List<LiveAircraft>, selected: RadarFilters): FilterFacets {
            val airlines = traffic
                .mapNotNull { a -> Airlines.icaoFromCallsign(a.callsign) }
                .groupingBy { it }.eachCount()
            val types = traffic.mapNotNull { it.typeCode }.groupingBy { it }.eachCount()
            fun <K> top(counts: Map<String, Int>, keep: Set<String>, label: (String) -> String): List<Facet> {
                val chosen = counts.entries.sortedByDescending { it.value }.take(MAX_CHIPS).map { it.key }.toSet()
                return (chosen + keep).map { Facet(it, label(it), counts[it] ?: 0) }
                    .sortedWith(compareByDescending<Facet> { it.count }.thenBy { it.label })
            }
            return FilterFacets(
                airlines = top<String>(airlines, selected.airlines) { Airlines.byIcao(it)?.name ?: it },
                types = top<String>(types, selected.types) { AircraftTypes.byIcao(it)?.name ?: it },
                militaryCount = traffic.count { it.military || it.type?.military == true },
                rareCount = traffic.count { it.rarity <= Rarity.Rare },
            )
        }
    }
}

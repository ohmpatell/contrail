package com.contrail.contrail.feature.radar

import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.model.AirportRef
import com.contrail.contrail.core.data.model.AircraftPhoto
import com.contrail.contrail.core.data.model.FlightRoute
import com.contrail.contrail.core.data.model.LiveAircraft
import com.contrail.contrail.core.data.model.WatchItem
import com.contrail.contrail.core.data.repository.RadarLayers
import com.contrail.contrail.core.data.route.RouteVerdict
import com.contrail.contrail.core.data.hangar.HangarPlane

/** What the map is looking at. [radiusNm] is centre-to-corner. */
data class Viewport(val center: LatLon, val radiusNm: Double, val zoom: Double)

sealed interface FeedStatus {
    data object Connecting : FeedStatus
    data class Live(val source: String, val updatedAtMs: Long) : FeedStatus
    /** The last refresh failed; [lastSuccessMs] is when data was last good, if ever. */
    data class Offline(val lastSuccessMs: Long?) : FeedStatus
}

enum class RadarPanel { None, Filters, Layers, Search }

/** An airport offered by the radar search. */
data class AirportHit(val icao: String, val code: String, val name: String, val city: String, val lat: Double, val lon: Double)

/** What one search found, for the text it was run on ([query], upper case). */
data class SearchResults(
    val query: String = "",
    val searching: Boolean = false,
    val aircraft: List<LiveAircraft> = emptyList(),
    val airports: List<AirportHit> = emptyList(),
    /** Every lookup failed (offline, or the feeds are down). */
    val failed: Boolean = false,
    /** The text could be read as a flight, callsign, registration or transponder code. */
    val tried: Boolean = false,
)

data class SearchUiState(
    val query: String = "",
    /** How the text is being read, e.g. "Flight CX 888 · callsign CPA888". */
    val readings: List<String> = emptyList(),
    val results: SearchResults = SearchResults(),
    val recent: List<String> = emptyList(),
)

/** A camera move the map should make once: to a search result or a pinned plane. */
data class CameraMove(val to: LatLon, val zoom: Double)

sealed interface Lookup<out T> {
    data object Loading : Lookup<Nothing>
    data object Missing : Lookup<Nothing>
    data class Found<T>(val value: T) : Lookup<T>
}

data class SelectedAircraft(
    val aircraft: LiveAircraft,
    val route: Lookup<FlightRoute>,
    val photo: Lookup<AircraftPhoto>,
    /** Vertical rate history (ft/min), oldest first, for the sparkline. */
    val verticalRates: List<Float>,
    /** Recent positions from our own polling, oldest first. */
    val trail: List<LatLon> = emptyList(),
    /** logostream airline profile: logo and tail-art URLs (never cached on disk) and brand colour. */
    val airlineLogoUrl: String? = null,
    val tailArtUrl: String? = null,
    val brandColorHex: String? = null,
    /** The route on file checked against what the plane is doing; null while the route is loading. */
    val verdict: RouteVerdict? = null,
)

/** The selected aircraft's whole journey: flown so far, then the rest to its destination. */
data class RouteOverlay(
    /** Null when only the destination is known (landing, worked out from the approach). */
    val origin: AirportRef?,
    val destination: AirportRef,
    /** From the origin to the latest known position. */
    val flown: List<LatLon>,
    /** True when [flown] is a real track rather than an origin-to-trail estimate. */
    val tracked: Boolean,
) {
    companion object {
        /**
         * Uses the provider's track when there is one; otherwise draws a great circle from the
         * origin to where our trail begins, then the trail itself.
         */
        fun build(from: AirportRef?, to: AirportRef, track: List<LatLon>?, trail: List<LatLon>, current: LatLon): RouteOverlay {
            val flown = if (!track.isNullOrEmpty()) {
                track + current
            } else if (from != null) {
                val origin = LatLon(from.lat, from.lon)
                val start = trail.firstOrNull() ?: current
                val lead = if (Geo.distanceM(origin, start) > 20_000) Geo.greatCircle(origin, start, 48) else listOf(origin)
                lead + trail + current
            } else {
                trail + current
            }
            return RouteOverlay(from, to, unwrap(flown), tracked = !track.isNullOrEmpty())
        }

        /** Keeps consecutive longitudes within 180 degrees so lines cross the antimeridian cleanly. */
        fun unwrap(points: List<LatLon>): List<LatLon> {
            if (points.isEmpty()) return points
            var prev = points.first().lon
            return points.map { p ->
                var lon = p.lon
                while (lon - prev > 180) lon -= 360
                while (lon - prev < -180) lon += 360
                prev = lon
                p.copy(lon = lon)
            }
        }
    }
}

data class RadarUiState(
    val feed: FeedStatus = FeedStatus.Connecting,
    val shownCount: Int = 0,
    val totalCount: Int = 0,
    val filters: RadarFilters = RadarFilters(),
    val facets: FilterFacets = FilterFacets(),
    val layers: RadarLayers = RadarLayers(),
    val panel: RadarPanel = RadarPanel.None,
    val selected: SelectedAircraft? = null,
    val following: Boolean = false,
    /** Following, but the user dragged away: show Recentre. */
    val followPaused: Boolean = false,
    /** Following with the sheet tucked away into the top readout. */
    val sheetCollapsed: Boolean = false,
    /** True when zoomed out past what one query covers. */
    val radiusCapped: Boolean = false,
    val emergencies: List<LiveAircraft> = emptyList(),
    val watchlist: List<WatchItem> = emptyList(),
    /** How many contacts on screen match the watchlist. */
    val watchedCount: Int = 0,
    /** Middle of the map, for "how far away" in search results. */
    val viewCenter: LatLon? = null,
    val search: SearchUiState = SearchUiState(),
    /** Your pinned planes. */
    val hangar: List<HangarPlane> = emptyList(),
) {
    /** True when the selected plane is pinned to My Hangar. */
    val selectedPinned: Boolean
        get() = selected?.aircraft?.let { a -> hangar.any { it.registration == a.registration?.uppercase() || it.hex == a.hex } } == true
}

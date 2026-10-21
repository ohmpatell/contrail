package com.contrail.contrail.feature.radar

import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.model.AirportRef
import com.contrail.contrail.core.data.model.AircraftPhoto
import com.contrail.contrail.core.data.model.FlightRoute
import com.contrail.contrail.core.data.model.LiveAircraft
import com.contrail.contrail.core.data.model.WatchItem
import com.contrail.contrail.core.data.repository.RadarLayers

/** What the map is looking at. [radiusNm] is centre-to-corner. */
data class Viewport(val center: LatLon, val radiusNm: Double, val zoom: Double)

sealed interface FeedStatus {
    data object Connecting : FeedStatus
    data class Live(val source: String, val updatedAtMs: Long) : FeedStatus
    /** The last refresh failed; [lastSuccessMs] is when data was last good, if ever. */
    data class Offline(val lastSuccessMs: Long?) : FeedStatus
}

enum class RadarPanel { None, Filters, Layers }

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
)

/** The selected aircraft's whole journey: flown so far, then the rest to its destination. */
data class RouteOverlay(
    val origin: AirportRef,
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
        fun build(route: FlightRoute, track: List<LatLon>?, trail: List<LatLon>, current: LatLon): RouteOverlay {
            val origin = LatLon(route.origin.lat, route.origin.lon)
            val flown = if (!track.isNullOrEmpty()) {
                track + current
            } else {
                val start = trail.firstOrNull() ?: current
                val lead = if (Geo.distanceM(origin, start) > 20_000) Geo.greatCircle(origin, start, 48) else listOf(origin)
                lead + trail + current
            }
            return RouteOverlay(route.origin, route.destination, unwrap(flown), tracked = !track.isNullOrEmpty())
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
    /** True when zoomed out past what one query covers. */
    val radiusCapped: Boolean = false,
    val emergencies: List<LiveAircraft> = emptyList(),
    val watchlist: List<WatchItem> = emptyList(),
    /** How many contacts on screen match the watchlist. */
    val watchedCount: Int = 0,
)

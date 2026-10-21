package com.contrail.contrail.feature.radar

import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.model.AircraftPhoto
import com.contrail.contrail.core.data.model.FlightRoute
import com.contrail.contrail.core.data.model.LiveAircraft
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
)

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
)

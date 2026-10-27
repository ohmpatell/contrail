package com.contrail.contrail.feature.passport

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.data.reference.AircraftTypes
import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.core.data.repository.AirportRepository
import com.contrail.contrail.core.data.repository.TripRepository
import com.contrail.contrail.core.data.ticket.AirportInfo
import com.contrail.contrail.core.data.trip.FlightSegment
import com.contrail.contrail.core.designsystem.component.AuroraBackground
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.GlassIconButton
import com.contrail.contrail.core.designsystem.component.GlassSurface
import com.contrail.contrail.core.designsystem.component.LocalBackdropHaze
import com.contrail.contrail.core.designsystem.component.pressScale
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.feature.trips.TripFormat
import com.contrail.contrail.feature.trips.airlineAccent
import com.contrail.contrail.navigation.LocalNavigator
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class LogbookState(
    val loading: Boolean = true,
    /** Newest first. */
    val flown: List<FlightSegment> = emptyList(),
    val upcoming: List<FlightSegment> = emptyList(),
    val places: Map<String, AirportInfo> = emptyMap(),
)

@HiltViewModel
class LogbookViewModel @Inject constructor(trips: TripRepository, airports: AirportRepository, clock: Clock) : ViewModel() {
    val state: StateFlow<LogbookState> = trips.trips.map { list ->
        val dir = airports.directory()
        val now = Instant.ofEpochMilli(clock.nowMs())
        val all = list.flatMap { it.segments }
        val codes = all.flatMap { listOf(it.origin, it.destination) }.toSet()
        LogbookState(
            loading = false,
            flown = all.filter { it.arrival.isBefore(now) }.sortedByDescending { it.departure },
            upcoming = all.filter { !it.arrival.isBefore(now) }.sortedBy { it.departure },
            places = codes.mapNotNull { c -> dir.find(c)?.let { c to it } }.toMap(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogbookState())
}

@Composable
fun LogbookRoute(viewModel: LogbookViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    LogbookScreen(state, onBack = nav::back, onFlight = nav::openFlight, onAdd = { nav.openAddFlight() }, onImport = nav::openLogImport)
}

/** True when [q] matches a flight's codes, cities, airline, number, type or tail. */
fun FlightSegment.matches(q: String, places: Map<String, AirportInfo>): Boolean {
    if (q.isBlank()) return true
    val n = q.trim().lowercase()
    val haystack = listOfNotNull(
        origin, destination, places[origin]?.city, places[destination]?.city, designator, designator.replace(" ", ""),
        Airlines.byIata(airlineIata)?.name, airlineName, aircraftType, AircraftTypes.nameFor(aircraftType), tail, departureLocal.year.toString(),
    )
    return haystack.any { it.lowercase().contains(n) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogbookScreen(state: LogbookState, onBack: () -> Unit, onFlight: (Long) -> Unit, onAdd: () -> Unit, onImport: () -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    var query by rememberSaveable { mutableStateOf("") }
    val flown = remember(state.flown, query) { state.flown.filter { it.matches(query, state.places) } }
    val upcoming = remember(state.upcoming, query) { state.upcoming.filter { it.matches(query, state.places) } }
    val byYear = remember(flown) { flown.groupBy { it.departureLocal.year } }
    val totalKm = remember(flown, state.places) { flown.sumOf { PassportInsights.distanceKm(it, state.places) } }
    val backdrop = rememberHazeState()
    androidx.compose.runtime.CompositionLocalProvider(LocalBackdropHaze provides backdrop) {
        Box(Modifier.fillMaxSize()) {
            AuroraBackground(Modifier.fillMaxSize().hazeSource(backdrop))
            LazyColumn(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
                contentPadding = PaddingValues(start = spacing.gutter, end = spacing.gutter, bottom = spacing.xxxl + 40.dp),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                item {
                    Row(Modifier.fillMaxWidth().padding(vertical = spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        GlassIconButton(ContrailIcons.Back, "Back", onBack)
                        Box(Modifier.weight(1f))
                        GlassIconButton(ContrailIcons.Import, "Import a flight log", onImport)
                        GlassIconButton(ContrailIcons.Plus, "Add a flight", onAdd)
                    }
                }
                item {
                    Column(Modifier.padding(top = spacing.sm, bottom = spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Eyebrow(String.format(Locale.US, "%,d flights · %,d km", flown.size, totalKm), color = colors.passport)
                        Text("Logbook", style = ContrailTheme.type.displayLarge, color = colors.textPrimary)
                    }
                }
                item { SearchField(query) { query = it } }
                if (upcoming.isNotEmpty()) {
                    stickyHeader(key = "upcoming") { YearHeader("Upcoming", "${upcoming.size} flight${if (upcoming.size == 1) "" else "s"}", colors.accent) }
                    items(upcoming, key = { "u${it.id}" }) { s -> LogbookRow(s, state.places, onClick = { onFlight(s.id) }, upcoming = true) }
                }
                byYear.forEach { (year, list) ->
                    val km = list.sumOf { PassportInsights.distanceKm(it, state.places) }
                    stickyHeader(key = "y$year") { YearHeader("$year", String.format(Locale.US, "%d flight%s · %,d km", list.size, if (list.size == 1) "" else "s", km), colors.passport) }
                    items(list, key = { it.id }) { s -> LogbookRow(s, state.places, onClick = { onFlight(s.id) }, modifier = Modifier.animateItem()) }
                }
                if (!state.loading && flown.isEmpty() && upcoming.isEmpty()) {
                    item {
                        Text(
                            if (query.isBlank()) "No flights yet. Add one, or import your myFlightradar24 log." else "Nothing matches “$query”.",
                            style = ContrailTheme.type.body, color = colors.textTertiary, modifier = Modifier.padding(vertical = spacing.xl),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    GlassSurface(Modifier.fillMaxWidth(), shape = ContrailTheme.shapes.chip) {
        Row(Modifier.padding(horizontal = spacing.lg, vertical = spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Icon(ContrailIcons.Search, null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text("Airport, airline, aircraft, tail or year", style = ContrailTheme.type.body, color = colors.textDisabled, maxLines = 1)
                BasicTextField(value, onChange, singleLine = true, textStyle = ContrailTheme.type.body.copy(color = colors.textPrimary), cursorBrush = SolidColor(colors.passport), modifier = Modifier.fillMaxWidth())
            }
            if (value.isNotEmpty()) Icon(ContrailIcons.Close, "Clear", tint = colors.textTertiary, modifier = Modifier.size(18.dp).clickable(role = Role.Button) { onChange("") })
        }
    }
}

/** A frosted pill that sticks to the top while you scroll through its year. */
@Composable
private fun YearHeader(title: String, sub: String, tint: Color) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    Box(Modifier.fillMaxWidth().padding(top = spacing.md, bottom = spacing.xs)) {
        GlassSurface(Modifier.fillMaxWidth(), shape = ContrailTheme.shapes.chip, glow = tint.copy(alpha = 0.5f)) {
            Row(Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = ContrailTheme.type.headline, color = colors.textPrimary, modifier = Modifier.weight(1f))
                Text(sub, style = ContrailTheme.type.numericSmall, color = tint)
            }
        }
    }
}

private val DAY = DateTimeFormatter.ofPattern("d", Locale.UK)
private val MONTH = DateTimeFormatter.ofPattern("MMM", Locale.UK)

/** One flight as a slim ticket: date block, codes and cities, then airline, aircraft and time. */
@Composable
fun LogbookRow(s: FlightSegment, places: Map<String, AirportInfo>, onClick: () -> Unit, modifier: Modifier = Modifier, upcoming: Boolean = false) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val accent = airlineAccent(s.airlineIata)
    val interaction = remember { MutableInteractionSource() }
    val km = PassportInsights.distanceKm(s, places)
    GlassSurface(
        modifier.fillMaxWidth().pressScale(interaction).clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
        shape = ContrailTheme.shapes.medium,
        glow = if (upcoming) accent.copy(alpha = 0.6f) else null,
    ) {
        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.3f)))))
            Column(Modifier.width(54.dp).padding(vertical = spacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(DAY.format(s.departureLocal), style = ContrailTheme.type.displaySmall, color = colors.textPrimary)
                Text(MONTH.format(s.departureLocal).uppercase(), style = ContrailTheme.type.eyebrow, color = accent)
            }
            Column(Modifier.weight(1f).padding(vertical = spacing.md).padding(end = spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text(s.origin, style = ContrailTheme.type.headline, color = colors.textPrimary)
                    Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(colors.hairline, accent.copy(alpha = 0.6f), colors.hairline))))
                    Icon(ContrailIcons.Plane, null, tint = accent, modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = 90f })
                    Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(colors.hairline, accent.copy(alpha = 0.6f), colors.hairline))))
                    Text(s.destination, style = ContrailTheme.type.headline, color = colors.textPrimary)
                }
                Row {
                    Text(TripFormat.city(s.origin, places), style = ContrailTheme.type.bodySmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(TripFormat.city(s.destination, places), style = ContrailTheme.type.bodySmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        listOfNotNull(
                            s.designator.ifBlank { null },
                            AircraftTypes.nameFor(s.aircraftType)?.removePrefix("Airbus ")?.removePrefix("Boeing ") ?: s.aircraftType,
                            TripFormat.duration(s.duration) + if (s.timesEstimated) "*" else "",
                        ).joinToString(" · "),
                        style = ContrailTheme.type.numericSmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (km > 0) Text(String.format(Locale.US, "%,d km", km), style = ContrailTheme.type.numericSmall, color = colors.textTertiary, maxLines = 1)
                }
            }
        }
    }
}

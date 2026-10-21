package com.contrail.contrail.feature.airport

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.designsystem.component.ContrailChip
import com.contrail.contrail.core.designsystem.component.DetailScaffold
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.GlassIconButton
import com.contrail.contrail.core.designsystem.component.GlassSurface
import com.contrail.contrail.core.designsystem.component.LiveLogo
import com.contrail.contrail.core.designsystem.component.SectionCard
import com.contrail.contrail.core.designsystem.component.SplitFlapText
import com.contrail.contrail.core.designsystem.component.StatTile
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.AltitudeRamp
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.core.network.airport.Metar
import com.contrail.contrail.feature.airports.AirportMonogram
import com.contrail.contrail.feature.airports.rememberLocalTime
import com.contrail.contrail.feature.airports.utcOffset
import com.contrail.contrail.navigation.LocalNavigator
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

@Composable
fun AirportRoute(icao: String) {
    val viewModel = hiltViewModel<AirportViewModel, AirportViewModel.Factory>(key = "airport-$icao") { it.create(icao) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    AirportScreen(
        state = state,
        onBack = nav::back,
        onBoard = viewModel::showBoard,
        onToggleFavourite = viewModel::toggleFavourite,
        onToggleVisited = viewModel::toggleVisited,
        onOpenAircraft = nav::openAircraft,
    )
}

@Composable
fun AirportScreen(
    state: AirportUiState,
    onBack: () -> Unit,
    onBoard: (BoardKind) -> Unit,
    onToggleFavourite: () -> Unit,
    onToggleVisited: () -> Unit,
    onOpenAircraft: (String) -> Unit,
) {
    val a = state.airport
    val colors = ContrailTheme.colors
    val category = state.weather?.metar?.fltCat
    DetailScaffold(
        title = a?.name ?: "Airport",
        eyebrow = a?.let { listOf(it.city, it.countryName).filter { s -> s.isNotBlank() }.joinToString(" · ") },
        onBack = onBack,
        actions = {
            GlassIconButton(
                if (a?.favourite == true) ContrailIcons.StarFilled else ContrailIcons.Star,
                if (a?.favourite == true) "Remove favourite" else "Add favourite",
                onToggleFavourite,
                active = a?.favourite == true,
            )
            GlassIconButton(ContrailIcons.Check, if (a?.visited == true) "Mark not visited" else "Mark visited", onToggleVisited, active = a?.visited == true)
        },
        hero = { if (a != null) Hero(a.iata, a.icao, a.timezone, category) },
    ) {
        if (a == null) return@DetailScaffold
        StatsStrip(state)
        WeatherCard(state.weather?.metar, state.weather?.taf?.rawTAF)
        BoardCard(state, onBoard, onOpenAircraft)
        val activeEnd = LiveBoard.likelyActiveEnd(state.runways, windFrom(state.weather?.metar))
        SectionCard("Airfield", trailing = { Eyebrow("© OpenStreetMap") }) {
            RunwayDiagram(LatLon(a.lat, a.lon), state.runways, state.geometry, activeEnd)
            if (activeEnd != null) {
                Text("Runway $activeEnd is likely in use, into the wind.", style = ContrailTheme.type.bodySmall, color = colors.statusOnTime)
            }
            state.runways.take(6).forEach { r ->
                Row {
                    Text("${r.leIdent}/${r.heIdent}", style = ContrailTheme.type.numeric, color = colors.textPrimary, modifier = Modifier.width(96.dp))
                    Text(
                        listOfNotNull(r.lengthFt?.let { String.format(Locale.US, "%,d ft", it) }, r.surface.takeIf { it.isNotBlank() }).joinToString(" · "),
                        style = ContrailTheme.type.bodySmall, color = colors.textTertiary,
                    )
                }
            }
        }
        SectionCard("Nearby traffic", trailing = { Eyebrow("${state.nearby.size} within 60 km") }) {
            NearbyScope(LatLon(a.lat, a.lon), state.nearby, AirportViewModel.NEARBY_RADIUS_M)
        }
        if (state.airlines.isNotEmpty()) AirlinesCard(state.airlines)
        if (state.destinations.isNotEmpty()) {
            SectionCard("Nonstop destinations", trailing = { Eyebrow("${state.destinations.size} routes") }) {
                DestinationsMap(LatLon(a.lat, a.lon), state.destinations)
            }
        }
        FrequenciesCard(state, a.icao)
    }
}

@Composable
private fun Hero(iata: String, icao: String, timezone: String, category: String?) {
    val spacing = ContrailTheme.spacing
    val colors = ContrailTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = spacing.gutter, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        AirportMonogram(iata, size = 84.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            SplitFlapText(icao, style = ContrailTheme.type.flap)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Icon(ContrailIcons.Clock, null, tint = colors.textTertiary, modifier = Modifier.size(ContrailTheme.sizes.iconSmall))
                Text(rememberLocalTime(timezone, "HH:mm:ss"), style = ContrailTheme.type.numeric, color = colors.textPrimary)
                Text(utcOffset(timezone), style = ContrailTheme.type.numericSmall, color = colors.textTertiary)
            }
        }
        if (category != null) FlightCategoryBadge(category)
    }
}

/** VFR green, MVFR blue, IFR red, LIFR magenta: the standard aviation colours. */
@Composable
fun FlightCategoryBadge(category: String) {
    val color = when (category.uppercase()) {
        "VFR" -> ContrailTheme.colors.statusOnTime
        "MVFR" -> ContrailTheme.colors.statusAirborne
        "IFR" -> ContrailTheme.colors.statusCancelled
        else -> AltitudeRamp.colorFor(42_000)
    }
    Box(
        Modifier
            .clip(ContrailTheme.shapes.chip)
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = ContrailTheme.spacing.md, vertical = ContrailTheme.spacing.sm),
    ) {
        Text(category.uppercase(), style = ContrailTheme.type.numeric, color = color)
    }
}

@Composable
private fun StatsStrip(state: AirportUiState) {
    val s = state.stats?.stats
    GlassSurface(Modifier.fillMaxWidth(), shape = ContrailTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(ContrailTheme.spacing.lg), horizontalArrangement = Arrangement.SpaceBetween) {
            StatTile("Destinations", (s?.destinations ?: state.destinations.size.takeIf { it > 0 })?.toString() ?: "—")
            StatTile("Airlines", (s?.airlines ?: state.airlines.size.takeIf { it > 0 })?.toString() ?: "—")
            StatTile("Countries", s?.countries?.toString() ?: "—")
            StatTile("Elevation", state.airport?.elevationFt?.let { String.format(Locale.US, "%,d", it) } ?: "—", unit = "ft")
        }
    }
}

private fun windFrom(m: Metar?): Double? = m?.wdir?.jsonPrimitive?.doubleOrNull?.takeIf { (m.wspd ?: 0) > 0 }

@Composable
private fun WeatherCard(m: Metar?, taf: String?) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    SectionCard("Weather", trailing = { if (m?.fltCat != null) FlightCategoryBadge(m.fltCat) }) {
        if (m == null) {
            Text("Fetching the latest METAR…", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
            return@SectionCard
        }
        val dir = m.wdir?.jsonPrimitive?.content
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            WindTile(dir, m.wspd, m.wgst)
            StatTile("Visibility", m.visib?.jsonPrimitive?.content?.let { if (it.endsWith("+")) it else "$it" } ?: "—", unit = "SM")
            StatTile("Temp", m.temp?.let { "%.0f°".format(it) } ?: "—", unit = m.dewp?.let { "dew %.0f°".format(it) })
            StatTile("QNH", m.altim?.let { "%.0f".format(it) } ?: "—", unit = "hPa")
        }
        val clouds = m.clouds.joinToString("  ") { c -> listOfNotNull(c.cover, c.base?.let { "${it / 100}".padStart(3, '0') }).joinToString("") }
        Text(if (clouds.isBlank()) "Sky clear" else "Clouds  $clouds", style = ContrailTheme.type.bodySmall, color = colors.textSecondary)
        m.rawOb?.let { Text(it, style = ContrailTheme.type.numericSmall, color = colors.textTertiary) }
        taf?.let {
            Eyebrow("Forecast (TAF)")
            Text(it, style = ContrailTheme.type.numericSmall, color = colors.textTertiary, maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
        Spacer(spacing.xxs)
    }
}

@Composable
private fun Spacer(h: androidx.compose.ui.unit.Dp) = Box(Modifier.height(h))

@Composable
private fun WindTile(dir: String?, speed: Int?, gust: Int?) {
    val colors = ContrailTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.xs)) {
        Eyebrow("Wind")
        Row(verticalAlignment = Alignment.CenterVertically) {
            val deg = dir?.toFloatOrNull()
            if (deg != null && (speed ?: 0) > 0) {
                // The arrow points where the wind blows *to*.
                Icon(ContrailIcons.ArrowRight, null, tint = colors.accent, modifier = Modifier.size(ContrailTheme.sizes.iconSmall).graphicsLayer { rotationZ = deg + 90f })
            }
            Text(
                when {
                    speed == null -> "—"
                    speed == 0 -> "Calm"
                    else -> "${dir ?: "VRB"}°/$speed" + (gust?.let { "G$it" } ?: "")
                },
                style = ContrailTheme.type.numeric,
                color = colors.textPrimary,
            )
        }
    }
}

@Composable
private fun BoardCard(state: AirportUiState, onBoard: (BoardKind) -> Unit, onOpenAircraft: (String) -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val rows = if (state.board == BoardKind.Departures) state.departures else state.arrivals
    SectionCard("Live board", trailing = { Eyebrow("from ADS-B · route from callsign") }) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            ContrailChip("Departures · ${state.departures.size}", state.board == BoardKind.Departures, onClick = { onBoard(BoardKind.Departures) })
            ContrailChip("Arrivals · ${state.arrivals.size}", state.board == BoardKind.Arrivals, onClick = { onBoard(BoardKind.Arrivals) })
        }
        if (rows.isEmpty()) {
            Text(
                if (state.board == BoardKind.Departures) "No departures climbing out right now." else "Nothing on approach right now.",
                style = ContrailTheme.type.bodySmall, color = colors.textTertiary,
            )
        }
        rows.forEach { r ->
            Row(
                Modifier.fillMaxWidth().clip(ContrailTheme.shapes.small).clickable(role = Role.Button) { onOpenAircraft(r.hex) }.padding(vertical = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                SplitFlapText(r.callsign.take(7).padEnd(7), style = ContrailTheme.type.numeric, tiles = true, gap = 2.dp)
                Text(r.otherEnd ?: "···", style = ContrailTheme.type.numeric, color = colors.textPrimary, modifier = Modifier.width(44.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.status, style = ContrailTheme.type.label, color = if (r.status == "Final") colors.statusOnTime else colors.textSecondary)
                    Text(r.typeCode ?: "", style = ContrailTheme.type.numericSmall, color = colors.textTertiary)
                }
                Text(
                    r.minutes?.let { if (state.board == BoardKind.Arrivals) "${it}m" else "" } ?: "",
                    style = ContrailTheme.type.numeric, color = colors.accent,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AirlinesCard(airlines: List<AirlineServed>) {
    val spacing = ContrailTheme.spacing
    val colors = ContrailTheme.colors
    SectionCard("Airlines served", trailing = { Eyebrow("${airlines.size}") }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            airlines.take(24).forEach { al ->
                val accent = al.accentArgb?.let { Color(it) } ?: colors.accent
                Row(
                    Modifier
                        .clip(ContrailTheme.shapes.chip)
                        .background(accent.copy(alpha = 0.12f))
                        .padding(horizontal = spacing.md, vertical = spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    if (al.logoUrl != null) {
                        LiveLogo(al.logoUrl, al.name, Modifier.height(18.dp).width(54.dp))
                    } else {
                        Text(al.iata ?: al.icao ?: "", style = ContrailTheme.type.numeric, color = accent)
                    }
                    Text("${al.name} · ${al.routes}", style = ContrailTheme.type.label, color = colors.textSecondary, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun FrequenciesCard(state: AirportUiState, icao: String) {
    val colors = ContrailTheme.colors
    val uri = LocalUriHandler.current
    SectionCard(
        "Radio",
        trailing = {
            Row(
                Modifier.clip(ContrailTheme.shapes.chip).clickable(role = Role.Button) { uri.openUri("https://www.liveatc.net/search/?icao=$icao") }
                    .padding(horizontal = ContrailTheme.spacing.sm, vertical = ContrailTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.xs),
            ) {
                Icon(ContrailIcons.Headset, null, tint = colors.accent, modifier = Modifier.size(ContrailTheme.sizes.iconSmall))
                Text("Listen on LiveATC", style = ContrailTheme.type.label, color = colors.accent)
            }
        },
    ) {
        val wanted = listOf("ATIS", "TWR", "GND", "APP", "DEP", "CLD", "DEL")
        val freqs = state.frequencies.sortedBy { f -> wanted.indexOf(f.type).let { if (it < 0) 99 else it } }.take(8)
        if (freqs.isEmpty()) Text("No published frequencies.", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
        freqs.forEach { f ->
            Row {
                Text(f.type, style = ContrailTheme.type.eyebrow, color = colors.textTertiary, modifier = Modifier.width(56.dp))
                Text(String.format(Locale.US, "%.3f", f.mhz), style = ContrailTheme.type.numeric, color = colors.textPrimary, modifier = Modifier.width(96.dp))
                Text(f.description, style = ContrailTheme.type.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

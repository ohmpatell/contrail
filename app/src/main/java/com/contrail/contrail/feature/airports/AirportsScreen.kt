package com.contrail.contrail.feature.airports

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contrail.contrail.core.data.events.Happening
import com.contrail.contrail.core.data.events.HappeningKind
import com.contrail.contrail.core.data.spotting.Highlight
import com.contrail.contrail.core.data.spotting.Light
import com.contrail.contrail.core.data.spotting.PhotoLight
import com.contrail.contrail.core.data.spotting.SpotterArrival
import com.contrail.contrail.core.database.AirportWithUser
import com.contrail.contrail.core.designsystem.component.ContrailChip
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.GlassIconButton
import com.contrail.contrail.core.designsystem.component.GlassSurface
import com.contrail.contrail.core.designsystem.component.LivePulse
import com.contrail.contrail.core.designsystem.component.RiseIn
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.core.designsystem.theme.RarityPalette
import com.contrail.contrail.core.network.airport.windFromDeg
import com.contrail.contrail.feature.airport.RunwaysInUseText
import com.contrail.contrail.navigation.LocalNavigator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AirportsRoute(viewModel: AirportsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    DisposableEffect(Unit) {
        viewModel.setActive(true)
        onDispose { viewModel.setActive(false) }
    }
    AirportsScreen(
        state = state,
        actions = AirportsActions(
            onQuery = viewModel::onQuery,
            onOpen = { nav.openAirport(it.icao) },
            onToggleFavourite = viewModel::toggleFavourite,
            onFilter = viewModel::onFilter,
            onSettings = nav::openSettings,
            onShowOnRadar = { hex, lat, lon -> nav.showOnRadar(hex, lat, lon) },
        ),
    )
}

/** Everything the Airports tab can ask for. */
data class AirportsActions(
    val onQuery: (String) -> Unit = {},
    val onOpen: (AirportWithUser) -> Unit = {},
    val onToggleFavourite: (AirportWithUser) -> Unit = {},
    val onFilter: (HappeningFilter) -> Unit = {},
    val onSettings: () -> Unit = {},
    val onShowOnRadar: (hex: String?, lat: Double, lon: Double) -> Unit = { _, _, _ -> },
)

@Composable
fun AirportsScreen(state: AirportsUiState, actions: AirportsActions, modifier: Modifier = Modifier, nowMs: Long = state.nowMs.takeIf { it > 0 } ?: System.currentTimeMillis()) {
    val spacing = ContrailTheme.spacing
    val colors = ContrailTheme.colors
    BoxWithConstraints(modifier.fillMaxSize()) {
        val twoColumn = maxWidth >= ContrailTheme.layout.twoColumnMin
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = spacing.gutter)
                .padding(top = spacing.lg, bottom = spacing.navBarClearance),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        LivePulse(color = colors.accent)
                        Eyebrow("${state.favourites.size} favourites · ${state.visited.size} visited", color = colors.accent)
                    }
                    Text("Airports", style = ContrailTheme.type.displayLarge, color = colors.textPrimary)
                }
                GlassIconButton(ContrailIcons.Settings, "Settings", actions.onSettings)
            }

            SearchField(state.query, actions.onQuery)

            if (state.query.isNotBlank()) {
                if (state.results.isEmpty()) {
                    Text("No airports match “${state.query}”", style = ContrailTheme.type.body, color = colors.textTertiary)
                }
                state.results.forEachIndexed { i, a ->
                    RiseIn(i.coerceAtMost(6)) { AirportListRow(a, actions) }
                }
                return@Column
            }

            val boards: @Composable () -> Unit = {
                if (state.favourites.isEmpty()) {
                    EmptyFavourites()
                } else {
                    state.boards.forEachIndexed { i, b -> RiseIn(i) { SpotterBoardCard(b, nowMs, actions) } }
                }
            }
            val happening: @Composable () -> Unit = { HappeningNow(state, nowMs, actions) }
            val yours: @Composable () -> Unit = { YourAirports(state, actions) }
            if (twoColumn) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.lg), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
                        boards()
                        yours()
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.lg)) { happening() }
                }
            } else {
                boards()
                happening()
                yours()
            }
        }
    }
}

// region Spotter's board

@Composable
private fun SpotterBoardCard(b: SpotterBoardState, nowMs: Long, actions: AirportsActions) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val a = b.airport
    var all by rememberSaveable(a.icao) { mutableStateOf(false) }
    GlassSurface(Modifier.fillMaxWidth(), shape = ContrailTheme.shapes.large, glow = colors.accent.copy(alpha = 0.5f)) {
        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Row(
                Modifier.clip(ContrailTheme.shapes.medium).clickable(role = Role.Button, onClickLabel = "Open ${a.iata}") { actions.onOpen(a) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                AirportMonogram(a.iata, size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(a.city.substringBefore(" (").ifBlank { a.name }, style = ContrailTheme.type.headline, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Eyebrow("Spotter's board · ${rememberLocalTime(a.timezone)} local")
                }
                if (b.watching) LivePulse(color = colors.statusOnTime)
                Icon(ContrailIcons.ChevronRight, null, tint = colors.textTertiary, modifier = Modifier.size(ContrailTheme.sizes.iconSmall))
            }
            b.runways?.let { RunwaysInUseText(it) }
            Conditions(b, nowMs)
            val worth = b.arrivals.filter { it.worthSeeing }
            val horizon = (b.arrivals.maxOfOrNull { it.etaMinutes } ?: 0).coerceAtLeast(10)
            Box(Modifier.fillMaxWidth().height(ContrailTheme.sizes.hairline).background(colors.hairline))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("Worth seeing · next $horizon min · estimated", Modifier.weight(1f), color = RarityPalette.Legendary.first())
                if (b.arrivals.isNotEmpty()) {
                    Text(
                        if (all) "Fewer" else "All ${b.arrivals.size}",
                        style = ContrailTheme.type.label,
                        color = colors.textSecondary,
                        modifier = Modifier.clip(ContrailTheme.shapes.chip).clickable(role = Role.Button) { all = !all }.padding(horizontal = spacing.sm, vertical = spacing.xxs),
                    )
                }
            }
            when {
                b.arrivals.isEmpty() && !b.watching -> Text("Open this tab to watch the sky around ${a.iata}.", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
                b.arrivals.isEmpty() -> Text("Looking for arrivals… planes show up as their routes are checked.", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
                worth.isEmpty() && !all -> Text(
                    "Nothing special inbound. ${b.arrivals.size} ${if (b.arrivals.size == 1) "arrival" else "arrivals"} in the next $horizon minutes.",
                    style = ContrailTheme.type.bodySmall,
                    color = colors.textTertiary,
                )
            }
            val rows = if (all) b.arrivals.take(15) else worth.take(6)
            rows.forEach { arr -> ArrivalRow(arr) { actions.onShowOnRadar(arr.aircraft.hex, arr.aircraft.lat, arr.aircraft.lon) } }
        }
    }
}

/** Wind, the sun and where to stand for front-lit shots. */
@Composable
private fun Conditions(b: SpotterBoardState, nowMs: Long) {
    val colors = ContrailTheme.colors
    val small = ContrailTheme.type.bodySmall
    Column(verticalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.xxs)) {
        b.metar?.let { m ->
            val dir = m.windFromDeg?.let { String.format(Locale.US, "%03.0f°", it) } ?: if ((m.wspd ?: 0) == 0) null else "variable"
            val text = if (dir == null) "Wind calm" else "Wind $dir at ${m.wspd} kt" + (m.wgst?.let { ", gusting $it" } ?: "")
            Text(text, style = small, color = colors.textSecondary)
        }
        b.light?.let { l -> LightText(l, b.airport.timezone, nowMs) }
    }
}

@Composable
private fun LightText(l: PhotoLight, timezone: String, nowMs: Long) {
    val colors = ContrailTheme.colors
    val gold = RarityPalette.Legendary.first()
    val time = DateTimeFormatter.ofPattern("HH:mm", Locale.UK).withZone(runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault()))
    val sun = when {
        l.night -> "After dark: lights and silhouettes only."
        l.golden -> "Golden light" + (l.sunsetMs?.let { " · sun sets in ${((it - nowMs) / 60_000).coerceAtLeast(0)} min" } ?: "")
        else -> "Sun in the ${Light.compassWord(l.sunAzimuthDeg)}, ${l.sunElevationDeg.toInt()}° up" + (l.sunsetMs?.let { " · sets ${time.format(Instant.ofEpochMilli(it))}" } ?: "")
    }
    Text(sun, style = ContrailTheme.type.bodySmall, color = if (l.golden) gold else colors.textSecondary)
    if (!l.night && l.standSide != null) {
        Text(
            "Landing on ${l.runway}: stand on the ${l.standSide} side with the sun behind you. The ${l.litSide} side of each plane is lit.",
            style = ContrailTheme.type.bodySmall,
            color = colors.textPrimary,
        )
    } else if (!l.night) {
        Text("Landing on ${l.runway}: the sun is along the approach, so neither side is better lit.", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
    }
}

@Composable
private fun ArrivalRow(arr: SpotterArrival, onClick: () -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val a = arr.aircraft
    val big = Highlight.Widebody in arr.highlights || Highlight.Jumbo in arr.highlights
    Row(
        Modifier.fillMaxWidth().clip(ContrailTheme.shapes.small).clickable(role = Role.Button, onClickLabel = "Show on radar", onClick = onClick).padding(vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Column(Modifier.widthIn(min = 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${arr.etaMinutes}", style = ContrailTheme.type.numeric, color = colors.textPrimary)
            Text("min", style = ContrailTheme.type.eyebrow, color = colors.textTertiary)
        }
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(a.typeName ?: a.typeCode, a.airline?.name).joinToString(" · ").ifBlank { a.displayCallsign },
                style = ContrailTheme.type.title.copy(fontWeight = if (big) FontWeight.SemiBold else FontWeight.Normal),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(a.displayCallsign, a.registration, arr.from?.let { "from ${it.code}" }).joinToString(" · "),
                style = ContrailTheme.type.bodySmall,
                color = colors.textTertiary,
                maxLines = 1,
            )
        }
        arr.highlights.firstOrNull()?.let { HighlightBadge(it) }
    }
}

@Composable
private fun HighlightBadge(h: Highlight) {
    val colors = ContrailTheme.colors
    val tint = when (h) {
        Highlight.Hangar, Highlight.Legendary -> RarityPalette.Legendary.first()
        Highlight.Rare -> RarityPalette.Rare.first()
        Highlight.Emergency -> colors.emergency
        Highlight.Military -> colors.statusDelayed
        else -> colors.accent
    }
    Text(
        h.label.uppercase(Locale.ROOT),
        style = ContrailTheme.type.eyebrow,
        color = tint,
        maxLines = 1,
        modifier = Modifier
            .clip(ContrailTheme.shapes.chip)
            .background(tint.copy(alpha = 0.14f))
            .border(ContrailTheme.sizes.hairline, tint.copy(alpha = 0.6f), ContrailTheme.shapes.chip)
            .padding(horizontal = ContrailTheme.spacing.sm, vertical = ContrailTheme.spacing.xxs),
    )
}

// endregion

// region Happening now

@Composable
private fun HappeningNow(state: AirportsUiState, nowMs: Long, actions: AirportsActions) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            LivePulse(color = colors.emergency)
            Text("Happening now", style = ContrailTheme.type.headline, color = colors.textPrimary)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            HappeningFilter.entries.forEach { f -> ContrailChip(f.label, state.filter == f, onClick = { actions.onFilter(f) }) }
        }
        AnimatedVisibility(state.happenings.isEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            GlassSurface(Modifier.fillMaxWidth(), shape = ContrailTheme.shapes.medium) {
                Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text("Quiet right now", style = ContrailTheme.type.title, color = colors.textPrimary)
                    Text(
                        "Contrail is watching your favourite airports for go-arounds, holds, diversions and rare visitors, the world for emergency squawks, and your Hangar planes. It's all worked out from live ADS-B, so it fills in while the app is open.",
                        style = ContrailTheme.type.bodySmall,
                        color = colors.textTertiary,
                    )
                }
            }
        }
        state.happenings.take(30).forEachIndexed { i, h ->
            RiseIn(i.coerceAtMost(6)) { HappeningRow(h, nowMs) { actions.onShowOnRadar(h.hex, h.lat, h.lon) } }
        }
    }
}

@Composable
private fun HappeningRow(h: Happening, nowMs: Long, onClick: () -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val (icon, tint, turn) = look(h.kind)
    GlassSurface(
        Modifier.fillMaxWidth().clip(ContrailTheme.shapes.medium).clickable(role = Role.Button, onClickLabel = "Show on radar", onClick = onClick),
        shape = ContrailTheme.shapes.medium,
        glow = if (h.kind == HappeningKind.Emergency) colors.emergency.copy(alpha = 0.6f) else null,
    ) {
        Row(Modifier.padding(spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = turn })
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(h.title, style = ContrailTheme.type.title, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(h.detail, style = ContrailTheme.type.bodySmall, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Eyebrow(
                    "${age(nowMs, h.timeMs)} · " + when (h.scope) {
                        Happening.Scope.Near -> "near your airports"
                        Happening.Scope.World -> "worldwide"
                        Happening.Scope.Hangar -> "My Hangar"
                    },
                    color = colors.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun look(kind: HappeningKind): Triple<ImageVector, Color, Float> {
    val colors = ContrailTheme.colors
    val gold = RarityPalette.Legendary.first()
    return when (kind) {
        HappeningKind.Emergency -> Triple(ContrailIcons.Alert, colors.emergency, 0f)
        HappeningKind.GoAround -> Triple(ContrailIcons.Swap, colors.statusDelayed, 90f)
        HappeningKind.Holding -> Triple(ContrailIcons.Compass, colors.statusAirborne, 0f)
        HappeningKind.Diversion -> Triple(ContrailIcons.ArrowRight, colors.accent, -30f)
        HappeningKind.RareVisitor -> Triple(ContrailIcons.Sparkle, gold, 0f)
        HappeningKind.HangarTookOff -> Triple(ContrailIcons.StarFilled, gold, 0f)
        HappeningKind.HangarLanded -> Triple(ContrailIcons.StarFilled, gold, 0f)
    }
}

// endregion

@Composable
private fun YourAirports(state: AirportsUiState, actions: AirportsActions) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        if (state.favourites.isNotEmpty()) {
            Eyebrow("Favourites")
            state.favourites.forEach { a -> AirportListRow(a, actions) }
        }
        if (state.visited.isNotEmpty()) {
            Eyebrow("Visited", Modifier.padding(top = spacing.sm))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                state.visited.forEach { a ->
                    ContrailChip(a.iata, selected = false, onClick = { actions.onOpen(a) }, leading = {
                        Icon(ContrailIcons.Check, null, Modifier.size(ContrailTheme.sizes.iconSmall), tint = colors.statusOnTime)
                    })
                }
            }
        }
    }
}

@Composable
private fun AirportListRow(a: AirportWithUser, actions: AirportsActions) {
    AirportRow(
        iata = a.iata,
        name = a.name,
        place = listOf(a.city, a.countryName).filter { it.isNotBlank() }.joinToString(", "),
        timezone = a.timezone,
        favourite = a.favourite == true,
        onClick = { actions.onOpen(a) },
        onToggleFavourite = { actions.onToggleFavourite(a) },
    )
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val spacing = ContrailTheme.spacing
    val colors = ContrailTheme.colors
    GlassSurface(Modifier.fillMaxWidth(), shape = ContrailTheme.shapes.chip) {
        Row(Modifier.padding(horizontal = spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Icon(ContrailIcons.Search, null, tint = colors.textTertiary, modifier = Modifier.size(ContrailTheme.sizes.icon))
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = ContrailTheme.type.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.weight(1f).padding(horizontal = spacing.md, vertical = spacing.lg),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Search by code, name or city", style = ContrailTheme.type.body, color = colors.textTertiary)
                    inner()
                },
            )
            if (query.isNotEmpty()) {
                Icon(
                    ContrailIcons.Close, "Clear", tint = colors.textSecondary,
                    modifier = Modifier.size(ContrailTheme.sizes.icon).clip(ContrailTheme.shapes.chip).clickable(role = Role.Button) { onQuery("") },
                )
            }
        }
    }
}

@Composable
private fun EmptyFavourites() {
    val colors = ContrailTheme.colors
    GlassSurface(Modifier.fillMaxWidth(), shape = ContrailTheme.shapes.medium, glow = colors.accent) {
        Row(Modifier.padding(ContrailTheme.spacing.lg), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.md)) {
            AirportMonogram("YYZ", size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text("Star the airports you spot at", style = ContrailTheme.type.title, color = colors.textPrimary)
                Text(
                    "Each gets a Spotter's board: runways in use, where the light is, and what's worth seeing in the next 40 minutes.",
                    style = ContrailTheme.type.bodySmall,
                    color = colors.textTertiary,
                )
            }
        }
    }
}

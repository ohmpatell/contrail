package com.contrail.contrail.feature.trips

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contrail.contrail.core.data.reference.AircraftTypes
import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.core.data.reference.Rarity
import com.contrail.contrail.core.data.ticket.AirportInfo
import com.contrail.contrail.core.data.trip.FlightSegment
import com.contrail.contrail.core.data.trip.Trip
import com.contrail.contrail.core.designsystem.component.AccentButton
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.GlassIconButton
import com.contrail.contrail.core.designsystem.component.GlassSurface
import com.contrail.contrail.core.designsystem.component.LivePulse
import com.contrail.contrail.core.designsystem.component.RarityBadge
import com.contrail.contrail.core.designsystem.component.RiseIn
import com.contrail.contrail.core.designsystem.component.SplitFlapText
import com.contrail.contrail.core.designsystem.component.pressScale
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.livery.Liveries
import com.contrail.contrail.core.designsystem.livery.LiveryRender
import com.contrail.contrail.core.designsystem.livery.Profiles
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.core.designsystem.theme.RarityPalette
import com.contrail.contrail.navigation.LocalNavigator
import java.time.Duration
import java.time.Instant

@Composable
fun TripsRoute(viewModel: TripsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    TripsScreen(state, onImport = nav::openImport, onTrip = nav::openTrip, onFlight = nav::openFlight)
}

@Composable
fun TripsScreen(
    state: TripsUiState,
    onImport: () -> Unit,
    onTrip: (Long) -> Unit,
    onFlight: (Long) -> Unit,
    modifier: Modifier = Modifier,
    fixedNow: Instant? = null,
) {
    val spacing = ContrailTheme.spacing
    val now by rememberNow(fixedNow)
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = spacing.gutter)
            .padding(top = spacing.xl, bottom = spacing.navBarClearance),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Header(state, now, onImport)
        when {
            state.loading -> Unit
            state.upcoming.isEmpty() && state.past.isEmpty() -> RiseIn(0) { EmptyTrips(onImport) }
            else -> {
                state.next?.let { (trip, seg) ->
                    RiseIn(0) { NextFlightCard(trip, seg, state.places, now, onClick = { onFlight(seg.id) }) }
                }
                if (state.upcoming.isNotEmpty()) {
                    Eyebrow("Upcoming trips", Modifier.padding(top = spacing.xs))
                    state.upcoming.forEachIndexed { i, t ->
                        RiseIn(i + 1) { TripCard(t, state.places, onClick = { onTrip(t.id) }) }
                    }
                }
                if (state.past.isNotEmpty()) PastTrips(state.past, onTrip)
            }
        }
    }
}

@Composable
private fun Header(state: TripsUiState, now: Instant, onImport: () -> Unit) {
    val spacing = ContrailTheme.spacing
    val colors = ContrailTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            val next = state.next?.second
            val accent = airlineAccent(next?.airlineIata)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                LivePulse(color = if (next != null) accent else colors.textTertiary)
                Eyebrow(
                    when {
                        next == null -> "Your journeys"
                        next.departure.isBefore(now) -> "In the air: ${next.designator}"
                        else -> "Next flight in ${relative(Duration.between(now, next.departure))}"
                    },
                    color = if (next != null) accent else colors.textTertiary,
                )
            }
            Text("Trips", style = ContrailTheme.type.displayLarge, color = colors.textPrimary)
        }
        GlassIconButton(ContrailIcons.Plus, "Add a trip", onImport)
    }
}

private fun relative(d: Duration): String = when {
    d.toDays() >= 2 -> "${d.toDays()} days"
    d.toHours() >= 1 -> "${d.toHours()} h ${d.toMinutesPart()} min"
    else -> "${d.toMinutes().coerceAtLeast(0)} min"
}

/** The hero: your next flight, with its aircraft floating in its airline's livery. */
@Composable
private fun NextFlightCard(trip: Trip, seg: FlightSegment, places: Map<String, AirportInfo>, now: Instant, onClick: () -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val accent = airlineAccent(seg.airlineIata)
    val airline = Airlines.byIata(seg.airlineIata)
    val rarity = AircraftTypes.rarityFor(seg.aircraftType, airline)
    val interaction = remember { MutableInteractionSource() }
    val airborne = seg.departure.isBefore(now)
    GlassSurface(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
        glow = accent.copy(alpha = 0.75f),
    ) {
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.18f), Color.Transparent))))
        Column(Modifier.padding(spacing.xl), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Eyebrow(airline?.name ?: seg.airlineIata, color = accent)
                    Text(seg.designator, style = ContrailTheme.type.displaySmall, color = colors.textPrimary)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    if (rarity < Rarity.Common) {
                        RarityBadge(rarity.label, when (rarity) { Rarity.Legendary -> RarityPalette.Legendary; Rarity.Rare -> RarityPalette.Rare; else -> RarityPalette.Uncommon })
                    }
                    Text(AircraftTypes.nameFor(seg.aircraftType) ?: "Aircraft to be confirmed", style = ContrailTheme.type.bodySmall, color = colors.textSecondary)
                    seg.tail?.let { Text(it, style = ContrailTheme.type.numericSmall, color = colors.textTertiary) }
                }
            }
            LiveryRender(
                Profiles.forType(seg.aircraftType ?: "B789"),
                Liveries.forAirline(airline?.icao, accentArgb = airline?.accentArgb),
                Modifier.fillMaxWidth().padding(horizontal = spacing.md),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                AirportFlap(seg.origin, TripFormat.city(seg.origin, places), time = TripFormat.time(seg.departureLocal))
                val progress = if (airborne) {
                    (Duration.between(seg.departure, now).seconds.toFloat() / seg.duration.seconds.coerceAtLeast(1)).coerceIn(0f, 1f)
                } else {
                    null
                }
                RouteArc(accent, Modifier.weight(1f).height(56.dp), progress = progress)
                AirportFlap(seg.destination, TripFormat.city(seg.destination, places), alignEnd = true, time = TripFormat.time(seg.arrivalLocal) + (TripFormat.dayShift(seg) ?: ""))
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Eyebrow(if (airborne) "Lands in" else "Departs in")
                    val target = if (airborne) seg.arrival else seg.departure
                    Text(TripFormat.countdown(Duration.between(now, target)), style = ContrailTheme.type.numericLarge, color = accent)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Eyebrow(TripFormat.day(seg.departureLocal))
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                        seg.departureTerminal?.let { MiniFlap("Terminal", it) }
                        seg.gate?.let { MiniFlap("Gate", it) }
                        seg.seat?.let { MiniFlap("Seat", it) }
                    }
                }
            }
            if (trip.segments.size > 1) {
                Text(
                    "${trip.title} · flight ${seg.ordinal + 1} of ${trip.segments.size}",
                    style = ContrailTheme.type.bodySmall,
                    color = colors.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun MiniFlap(label: String, value: String) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.xxs)) {
        Eyebrow(label)
        SplitFlapText(value, style = ContrailTheme.type.numeric)
    }
}

/** A trip as a journey: its flights stacked, with each connection between them. */
@Composable
fun TripCard(trip: Trip, places: Map<String, AirportInfo>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val accent = airlineAccent(trip.first.airlineIata)
    val interaction = remember { MutableInteractionSource() }
    GlassSurface(
        modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
        shape = ContrailTheme.shapes.medium,
    ) {
        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(trip.title, style = ContrailTheme.type.headline, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${TripFormat.range(trip)} · ${trip.segments.size} flight${if (trip.segments.size == 1) "" else "s"}",
                        style = ContrailTheme.type.bodySmall,
                        color = colors.textTertiary,
                    )
                }
                trip.pnr?.let { Tag(it, accent) }
            }
            trip.segments.forEach { s ->
                SegmentRow(s)
                trip.connectionAfter(s.id)?.let { ConnectionPill(it, places) }
            }
        }
    }
}

@Composable
private fun PastTrips(past: List<Trip>, onTrip: (Long) -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    var open by rememberSaveable { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 90f else 0f, ContrailTheme.motion.snappy(), label = "chevron")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ContrailTheme.shapes.small)
            .clickable(role = Role.Button) { open = !open }
            .padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Eyebrow("Past trips · ${past.size}", Modifier.weight(1f))
        Icon(ContrailIcons.ChevronRight, if (open) "Hide" else "Show", tint = colors.textTertiary, modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = turn })
    }
    AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            past.forEach { t ->
                GlassSurface(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { onTrip(t.id) },
                    shape = ContrailTheme.shapes.small,
                ) {
                    Row(Modifier.padding(spacing.md), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.title, style = ContrailTheme.type.title, color = colors.textSecondary)
                            Text(
                                (listOf(t.first.origin) + t.segments.map { it.destination }).joinToString(" → "),
                                style = ContrailTheme.type.numericSmall,
                                color = colors.textTertiary,
                            )
                        }
                        Text(TripFormat.range(t), style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
                    }
                }
            }
        }
    }
}

/** No trips yet: a blank boarding pass waiting to be filled in. */
@Composable
private fun EmptyTrips(onImport: () -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val accent = colors.accent
    GlassSurface(Modifier.fillMaxWidth(), glow = accent.copy(alpha = 0.6f)) {
        Column(Modifier.padding(spacing.xl), verticalArrangement = Arrangement.spacedBy(spacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width * 0.86f
                    val h = size.height * 0.86f
                    val tl = Offset((size.width - w) / 2, (size.height - h) / 2)
                    val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
                    drawRoundRect(accent.copy(alpha = 0.08f), tl, Size(w, h), CornerRadius(18.dp.toPx()))
                    drawRoundRect(accent.copy(alpha = 0.5f), tl, Size(w, h), CornerRadius(18.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = dash))
                    val stubX = tl.x + w * 0.72f
                    drawLine(accent.copy(alpha = 0.4f), Offset(stubX, tl.y + 10.dp.toPx()), Offset(stubX, tl.y + h - 10.dp.toPx()), 1.5.dp.toPx(), pathEffect = dash)
                    drawCircle(colors.canvas, 10.dp.toPx(), Offset(stubX, tl.y))
                    drawCircle(colors.canvas, 10.dp.toPx(), Offset(stubX, tl.y + h))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 60.dp)) {
                    SplitFlapText("YYZ", style = ContrailTheme.type.flap, color = colors.textSecondary)
                    Icon(ContrailIcons.Plane, null, tint = accent, modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = 90f })
                    SplitFlapText("???", style = ContrailTheme.type.flap, color = colors.textSecondary)
                }
            }
            Text("Where to next?", style = ContrailTheme.type.displaySmall, color = colors.textPrimary)
            Text(
                "Import a ticket and Contrail fills in every flight, connection, seat and terminal. It all stays on your phone.",
                style = ContrailTheme.type.body,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            AccentButton("Import a ticket", onImport, icon = ContrailIcons.Plus)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Icon(ContrailIcons.Sparkle, null, tint = colors.textTertiary, modifier = Modifier.size(14.dp))
                Text(
                    "Or share a PDF or boarding pass screenshot to Contrail from any app.",
                    style = ContrailTheme.type.bodySmall,
                    color = colors.textTertiary,
                )
            }
            Spacer(Modifier.height(spacing.xxs))
        }
    }
}

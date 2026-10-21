package com.contrail.contrail.feature.trips

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.reference.AircraftTypes
import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.core.data.repository.AirportWeather
import com.contrail.contrail.core.data.repository.LoungePhase
import com.contrail.contrail.core.data.repository.LoungeVisit
import com.contrail.contrail.core.data.ticket.AirportInfo
import com.contrail.contrail.core.data.trip.Connection
import com.contrail.contrail.core.data.trip.ConnectionKind
import com.contrail.contrail.core.data.trip.FlightSegment
import com.contrail.contrail.core.data.trip.Trip
import com.contrail.contrail.core.designsystem.component.AccentButton
import com.contrail.contrail.core.designsystem.component.ContrailChip
import com.contrail.contrail.core.designsystem.component.DetailScaffold
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.GlassSurface
import com.contrail.contrail.core.designsystem.component.RiseIn
import com.contrail.contrail.core.designsystem.component.SectionCard
import com.contrail.contrail.core.designsystem.component.drawRouteArc
import com.contrail.contrail.core.designsystem.component.pressScale
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.feature.trips.importing.FormField
import com.contrail.contrail.feature.trips.maps.FlightMap
import com.contrail.contrail.feature.trips.maps.rememberMapMarkers
import com.contrail.contrail.navigation.LocalNavigator
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TripDetailRoute(tripId: Long) {
    val viewModel = hiltViewModel<TripDetailViewModel, TripDetailViewModel.Factory>(key = "trip-$tripId") { it.create(tripId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    TripDetailScreen(
        state = state,
        onBack = nav::back,
        onFlight = nav::openFlight,
        onToggleKind = viewModel::toggleKind,
        onToggleHotel = viewModel::toggleHotel,
        onLogLounge = { seg, phase -> nav.openLounge(seg, phase.name) },
        onOpenVisit = { v -> nav.openLounge(v.segmentId ?: 0, v.phase.name, v.id) },
        onRename = viewModel::rename,
        onDelete = { viewModel.delete { nav.back() } },
    )
}

@Composable
fun TripDetailScreen(
    state: TripDetailUiState,
    onBack: () -> Unit,
    onFlight: (Long) -> Unit,
    onToggleKind: (Connection) -> Unit,
    onToggleHotel: (Connection) -> Unit,
    onLogLounge: (Long, LoungePhase) -> Unit,
    onOpenVisit: (LoungeVisit) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    fixedNow: Instant? = null,
) {
    val trip = state.trip
    val now by rememberNow(fixedNow)
    val accent = airlineAccent(trip?.first?.airlineIata)
    DetailScaffold(
        title = trip?.title ?: "",
        eyebrow = trip?.let { listOfNotNull(it.pnr, TripFormat.range(it)).joinToString(" · ") },
        accent = accent,
        onBack = onBack,
        hero = { trip?.let { TripMap(it, state.places) } },
    ) {
        if (trip == null) return@DetailScaffold
        trip.segments.forEachIndexed { i, s ->
            val departureLounges = state.lounges[s.id].orEmpty().filter { it.phase == LoungePhase.Departure }
            if (i == 0) {
                RiseIn(i) { LoungeStrip(s.origin, state.places, departureLounges, onOpenVisit, onLog = { onLogLounge(s.id, LoungePhase.Departure) }) }
            }
            RiseIn(i + 1) { SegmentCard(s, state.places, onClick = { onFlight(s.id) }) }
            trip.connectionAfter(s.id)?.let { c ->
                val next = trip.segments[i + 1]
                RiseIn(i + 2) {
                    ConnectionCard(
                        c, state.places, state.weather[c.airport], now,
                        lounges = state.lounges[next.id].orEmpty().filter { it.phase == LoungePhase.Departure },
                        onToggleKind = { onToggleKind(c) },
                        onToggleHotel = { onToggleHotel(c) },
                        onLogLounge = { onLogLounge(next.id, LoungePhase.Departure) },
                        onOpenVisit = onOpenVisit,
                    )
                }
            }
        }
        TripActions(trip, onRename, onDelete)
    }
}

/** Every leg of the trip drawing itself in turn over the night map. */
@Composable
private fun TripMap(trip: Trip, places: Map<String, AirportInfo>) {
    val colors = ContrailTheme.colors
    val motion = ContrailTheme.motion
    val legs = trip.segments.mapNotNull { s ->
        val a = places[s.origin] ?: return@mapNotNull null
        val b = places[s.destination] ?: return@mapNotNull null
        Triple(s, LatLon(a.lat, a.lon), LatLon(b.lat, b.lon))
    }
    val arcs = remember(legs) { legs.map { (_, a, b) -> Geo.greatCircle(a, b, 64) } }
    val drawn = remember { Animatable(if (motion.reduced) legs.size.toFloat() else 0f) }
    LaunchedEffect(legs.size) {
        if (!motion.reduced) drawn.animateTo(legs.size.toFloat(), tween(1_300 * legs.size.coerceAtLeast(1)))
    }
    val markers = rememberMapMarkers()
    FlightMap(
        focus = arcs.flatten(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ContrailTheme.spacing.gutter)
            .aspectRatio(1.5f)
            .clip(ContrailTheme.shapes.large),
    ) { fit ->
        arcs.forEachIndexed { i, arc ->
            val color = airlineAccentRaw(legs[i].first.airlineIata) ?: colors.accent
            val pts = arc.map { fit.project(it) }
            drawRouteArc(pts, color, progress = (drawn.value - i).coerceIn(0f, 1f), dashedRest = colors.hairline)
        }
        legs.forEachIndexed { i, (s, a, b) ->
            val color = airlineAccentRaw(s.airlineIata) ?: colors.accent
            val pa = fit.project(a)
            val pb = fit.project(b)
            // Each code sits on the side away from the airport it's connected to, so close pairs don't collide.
            if (i == 0) markers.drawCode(this, s.origin, pa, color, pa.y > pb.y)
            markers.drawCode(this, s.destination, pb, color, pb.y >= pa.y)
        }
    }
}

private fun airlineAccentRaw(iata: String?): Color? = Airlines.byIata(iata)?.let { Color(it.accentArgb) }

@Composable
private fun SegmentCard(s: FlightSegment, places: Map<String, AirportInfo>, onClick: () -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val accent = airlineAccent(s.airlineIata)
    val interaction = remember { MutableInteractionSource() }
    GlassSurface(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
        shape = ContrailTheme.shapes.medium,
        glow = accent.copy(alpha = 0.55f),
    ) {
        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Eyebrow(Airlines.byIata(s.airlineIata)?.name ?: s.airlineIata, color = accent)
                    Text(s.designator, style = ContrailTheme.type.displaySmall, color = colors.textPrimary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(TripFormat.day(s.departureLocal), style = ContrailTheme.type.label, color = colors.textSecondary)
                    Text(AircraftTypes.nameFor(s.aircraftType) ?: "Aircraft TBA", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                AirportFlap(s.origin, TripFormat.city(s.origin, places), time = TripFormat.time(s.departureLocal))
                RouteArc(accent, Modifier.weight(1f).height(44.dp))
                AirportFlap(s.destination, TripFormat.city(s.destination, places), alignEnd = true, time = TripFormat.time(s.arrivalLocal) + (TripFormat.dayShift(s) ?: ""))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.lg)) {
                val terminals = if (s.departureTerminal == null && s.arrivalTerminal == null) null else "${s.departureTerminal ?: "?"} → ${s.arrivalTerminal ?: "?"}"
                Field("Terminals", terminals, Modifier.weight(1.3f))
                Field("Seat", s.seat, Modifier.weight(0.9f))
                Field("Class", s.fareClass, Modifier.weight(0.8f))
                Field("Cabin", s.cabin?.label, Modifier.weight(1.5f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${TripFormat.duration(s.duration)} flight", style = ContrailTheme.type.bodySmall, color = colors.textTertiary, modifier = Modifier.weight(1f))
                Text("Flight details", style = ContrailTheme.type.label, color = accent)
                Icon(ContrailIcons.ChevronRight, null, tint = accent, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    c: Connection,
    places: Map<String, AirportInfo>,
    weather: AirportWeather?,
    now: Instant,
    lounges: List<LoungeVisit>,
    onToggleKind: () -> Unit,
    onToggleHotel: () -> Unit,
    onLogLounge: () -> Unit,
    onOpenVisit: (LoungeVisit) -> Unit,
) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val tint = colors.kind(c.kind)
    Row(Modifier.fillMaxWidth()) {
        // The journey's spine: a dashed thread from one flight to the next.
        Box(Modifier.width(spacing.xl).padding(top = spacing.sm), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.width(2.dp).height(36.dp).background(tint.copy(alpha = 0.4f), ContrailTheme.shapes.chip))
        }
        GlassSurface(Modifier.weight(1f), shape = ContrailTheme.shapes.medium, glow = tint.copy(alpha = 0.55f), tint = tint.copy(alpha = 0.07f)) {
            Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    Icon(if (c.kind == ConnectionKind.Stopover) ContrailIcons.Bed else ContrailIcons.Clock, null, tint = tint, modifier = Modifier.size(ContrailTheme.sizes.icon))
                    Column(Modifier.weight(1f)) {
                        Eyebrow("In ${TripFormat.city(c.airport, places)}")
                        Text(TripFormat.duration(c.duration), style = ContrailTheme.type.numericLarge, color = colors.textPrimary)
                    }
                    Tag(c.length.label, colors.length(c.length), filled = true)
                }
                KindToggle(c.kind, onToggleKind)
                Text(
                    (if (c.kind == ConnectionKind.Stopover) "Leaving the airport. " else "Staying airside. ") +
                        (if (c.guessed) "Guessed from ${reason(c)}; tap to switch." else "Set by you; tap to switch."),
                    style = ContrailTheme.type.bodySmall,
                    color = colors.textTertiary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    ContrailChip("Hotel", c.hotel, onToggleHotel, accent = colors.passport, leading = {
                        Icon(ContrailIcons.Bed, null, tint = colors.passport, modifier = Modifier.size(14.dp))
                    })
                    c.terminalChange?.let { (a, b) -> Tag("Terminal $a → $b", colors.statusDelayed, icon = ContrailIcons.Swap) }
                    if (c.changesAirport) Tag("${c.inbound.destination} → ${c.outbound.origin}", colors.statusCancelled, icon = ContrailIcons.Alert)
                }
                AnimatedVisibility(c.kind == ConnectionKind.Stopover, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Stopover(c, places[c.airport], weather, now)
                }
                LoungeStrip(c.airport, places, lounges, onOpenVisit, onLogLounge, inset = true)
            }
        }
    }
}

private fun reason(c: Connection): String = when {
    c.duration > Duration.ofHours(24) -> "over 24 hours on the ground"
    c.kind == ConnectionKind.Stopover -> "overnight with a hotel"
    c.hotel -> "a hotel, but not overnight"
    else -> "under 24 hours with no hotel"
}

/** Layover | Stopover, with a sliding pill. */
@Composable
private fun KindToggle(kind: ConnectionKind, onToggle: () -> Unit) {
    val colors = ContrailTheme.colors
    val pos by animateFloatAsState(if (kind == ConnectionKind.Stopover) 1f else 0f, ContrailTheme.motion.bouncy(), label = "kind")
    val interaction = remember { MutableInteractionSource() }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .pressScale(interaction)
            .clip(ContrailTheme.shapes.chip)
            .background(colors.canvasSunken.copy(alpha = 0.6f))
            .border(ContrailTheme.sizes.hairline, colors.hairline, ContrailTheme.shapes.chip)
            .clickable(interaction, indication = null, role = Role.Switch, onClick = onToggle),
    ) {
        val half = maxWidth / 2
        val tint = if (pos > 0.5f) colors.passport else colors.accent
        Box(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset((half * pos).roundToPx(), 0) }
                .width(half)
                .fillMaxHeight()
                .padding(3.dp)
                .clip(ContrailTheme.shapes.chip)
                .background(tint.copy(alpha = 0.28f))
                .border(ContrailTheme.sizes.hairline, tint.copy(alpha = 0.8f), ContrailTheme.shapes.chip),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            listOf("Layover", "Stopover").forEachIndexed { i, label ->
                val on = (i == 1) == (kind == ConnectionKind.Stopover)
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = ContrailTheme.type.title, color = if (on) colors.textPrimary else colors.textTertiary)
                }
            }
        }
    }
}

/** A stopover's own mini-section: when you land and leave, the city's time and weather, and a countdown. */
@Composable
private fun Stopover(c: Connection, place: AirportInfo?, weather: AirportWeather?, now: Instant) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val local = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.UK)
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            Field("You land", "${TripFormat.day(c.inbound.arrivalLocal)} ${TripFormat.time(c.inbound.arrivalLocal)}", Modifier.weight(1f))
            Field("You leave", "${TripFormat.day(c.outbound.departureLocal)} ${TripFormat.time(c.outbound.departureLocal)}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.Bottom) {
            Field(
                "Local time in ${place?.city ?: c.airport}",
                place?.let { local.format(ZonedDateTime.ofInstant(now, it.zone)) },
                Modifier.weight(1f),
            )
            val m = weather?.metar
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Eyebrow("Weather now")
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Text(m?.temp?.let { "${it.toInt()}°C" } ?: "—", style = ContrailTheme.type.numeric, color = colors.textPrimary)
                    m?.fltCat?.let { Tag(it, if (it == "VFR") colors.statusOnTime else colors.statusDelayed) }
                }
                m?.let { Text(sky(it.clouds.firstOrNull()?.cover, it.wxString), style = ContrailTheme.type.bodySmall, color = colors.textTertiary) }
            }
        }
        val untilLanding = Duration.between(now, c.inbound.arrival)
        val untilNext = Duration.between(now, c.outbound.departure)
        if (!untilNext.isNegative) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Eyebrow(if (untilLanding.isNegative) "Next flight in" else "Stopover starts in")
                Text(
                    TripFormat.countdown(if (untilLanding.isNegative) untilNext else untilLanding),
                    style = ContrailTheme.type.numericLarge,
                    color = colors.passport,
                )
            }
        }
    }
}

private fun sky(cover: String?, wx: String?): String {
    val clouds = when (cover) {
        "SKC", "CLR", "NSC", "CAVOK", null -> "Clear skies"
        "FEW" -> "A few clouds"
        "SCT" -> "Scattered clouds"
        "BKN" -> "Mostly cloudy"
        "OVC" -> "Overcast"
        else -> cover
    }
    val weather = when {
        wx == null -> null
        "TS" in wx -> "thunderstorms"
        "RA" in wx || "DZ" in wx -> "rain"
        "SN" in wx -> "snow"
        "FG" in wx || "BR" in wx -> "mist"
        "HZ" in wx || "DU" in wx -> "haze"
        else -> null
    }
    return listOfNotNull(clouds, weather).joinToString(", ")
}

/** Lounge visits at an airport with a "Log a lounge" button. */
@Composable
fun LoungeStrip(airport: String, places: Map<String, AirportInfo>, visits: List<LoungeVisit>, onOpenVisit: (LoungeVisit) -> Unit, onLog: () -> Unit, inset: Boolean = false) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        visits.forEach { v -> LoungeVisitRow(v, onClick = { onOpenVisit(v) }) }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(ContrailTheme.shapes.small)
                .clickable(role = Role.Button, onClick = onLog)
                .padding(vertical = spacing.xs, horizontal = if (inset) 0.dp else spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Icon(ContrailIcons.Lounge, null, tint = colors.textTertiary, modifier = Modifier.size(16.dp))
            Text("Log a lounge at ${TripFormat.city(airport, places)}", style = ContrailTheme.type.label, color = colors.textTertiary, modifier = Modifier.weight(1f))
            Icon(ContrailIcons.Plus, null, tint = colors.textTertiary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun LoungeVisitRow(v: LoungeVisit, onClick: () -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ContrailTheme.shapes.small)
            .background(colors.glassTint.copy(alpha = 0.08f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Icon(ContrailIcons.Lounge, null, tint = colors.statusDelayed, modifier = Modifier.size(ContrailTheme.sizes.icon))
        Column(Modifier.weight(1f)) {
            Text(v.loungeName, style = ContrailTheme.type.title, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(v.airport, v.terminal?.let { "T$it" }, v.duration?.let(TripFormat::duration), v.access.label).joinToString(" · "),
                style = ContrailTheme.type.bodySmall,
                color = colors.textTertiary,
            )
        }
        Stars(v.rating)
    }
}

@Composable
fun Stars(rating: Int, size: androidx.compose.ui.unit.Dp = 14.dp, onRate: ((Int) -> Unit)? = null) {
    val colors = ContrailTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..5) {
            val on = i <= rating
            val pop by animateFloatAsState(if (on) 1f else 0.82f, ContrailTheme.motion.bouncy(), label = "star")
            Icon(
                if (on) ContrailIcons.StarFilled else ContrailIcons.Star,
                "$i star${if (i > 1) "s" else ""}",
                tint = if (on) colors.statusDelayed else colors.textDisabled,
                modifier = Modifier
                    .size(size * pop)
                    .then(if (onRate != null) Modifier.clickable(role = Role.Button) { onRate(i) } else Modifier),
            )
        }
    }
}

@Composable
private fun TripActions(trip: Trip, onRename: (String) -> Unit, onDelete: () -> Unit) {
    val colors = ContrailTheme.colors
    var name by rememberSaveable(trip.id) { mutableStateOf(trip.title) }
    var confirm by remember { mutableStateOf(false) }
    SectionCard("This trip") {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.sm)) {
            FormField("Name", name, { name = it }, Modifier.weight(1f), mono = false)
            AccentButton("Rename", { onRename(name) }, secondary = true, enabled = name.isNotBlank() && name != trip.title)
        }
        trip.passengerName?.let { Text("Passenger: $it", style = ContrailTheme.type.bodySmall, color = colors.textTertiary) }
        AccentButton(
            if (confirm) "Tap again to delete" else "Delete trip",
            { if (confirm) onDelete() else confirm = true },
            icon = ContrailIcons.Trash,
            accent = colors.statusCancelled,
            secondary = !confirm,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

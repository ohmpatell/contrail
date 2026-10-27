package com.contrail.contrail.feature.trips.flight

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.reference.AircraftTypes
import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.core.data.reference.Rarity
import com.contrail.contrail.core.data.repository.LoungePhase
import com.contrail.contrail.core.data.repository.LoungeVisit
import com.contrail.contrail.core.data.trip.FlightSegment
import com.contrail.contrail.core.designsystem.component.AccentButton
import com.contrail.contrail.core.designsystem.component.DetailScaffold
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.GlassSurface
import com.contrail.contrail.core.designsystem.component.GlassIconButton
import com.contrail.contrail.core.designsystem.component.LivePulse
import com.contrail.contrail.core.designsystem.component.RarityBadge
import com.contrail.contrail.core.designsystem.component.SectionCard
import com.contrail.contrail.core.designsystem.component.StatTile
import com.contrail.contrail.core.designsystem.component.arcTip
import com.contrail.contrail.core.designsystem.component.drawRouteArc
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.livery.Liveries
import com.contrail.contrail.core.designsystem.livery.LiveryRender
import com.contrail.contrail.core.designsystem.livery.Profiles
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.core.designsystem.theme.RarityPalette
import com.contrail.contrail.feature.trips.Field
import com.contrail.contrail.feature.trips.LoungeStrip
import com.contrail.contrail.feature.trips.Tag
import com.contrail.contrail.feature.trips.TripFormat
import com.contrail.contrail.feature.trips.airlineAccent
import com.contrail.contrail.feature.trips.BoardingPass
import com.contrail.contrail.feature.trips.PassData
import com.contrail.contrail.feature.trips.importing.FormField
import com.contrail.contrail.feature.trips.maps.FlightMap
import com.contrail.contrail.feature.trips.maps.rememberMapMarkers
import com.contrail.contrail.feature.trips.rememberNow
import com.contrail.contrail.navigation.LocalNavigator
import java.time.Duration
import java.time.Instant
import java.util.Locale

@Composable
fun FlightRoute(segmentId: Long) {
    val viewModel = hiltViewModel<FlightViewModel, FlightViewModel.Factory>(key = "flight-$segmentId") { it.create(segmentId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    FlightScreen(
        state = state,
        onBack = nav::back,
        onSetTail = viewModel::setTail,
        onRefreshRotation = viewModel::refreshRotation,
        onOpenSettings = nav::openSettings,
        onOpenAircraft = nav::openAircraft,
        onLogLounge = { phase -> nav.openLounge(segmentId, phase.name) },
        onOpenVisit = { v -> nav.openLounge(segmentId, v.phase.name, v.id) },
        onEdit = { nav.openAddFlight(segmentId) },
    )
}

@Composable
fun FlightScreen(
    state: FlightUiState,
    onBack: () -> Unit,
    onSetTail: (String) -> Unit,
    onRefreshRotation: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAircraft: (String) -> Unit,
    onLogLounge: (LoungePhase) -> Unit,
    onOpenVisit: (LoungeVisit) -> Unit,
    fixedNow: Instant? = null,
    onEdit: () -> Unit = {},
) {
    val s = state.segment
    val now by rememberNow(fixedNow)
    val accent = airlineAccent(s?.airlineIata)
    val airline = Airlines.byIata(s?.airlineIata)
    DetailScaffold(
        title = s?.designator ?: "",
        eyebrow = s?.let { listOfNotNull(airline?.name, "${TripFormat.city(it.origin, state.places)} → ${TripFormat.city(it.destination, state.places)}").joinToString(" · ") },
        accent = accent,
        onBack = onBack,
        actions = { GlassIconButton(ContrailIcons.Edit, "Edit flight", onEdit) },
        hero = { s?.let { FlightHero(it, state, now, accent) } },
    ) {
        if (s == null) return@DetailScaffold
        val flown = s.arrival.isBefore(now) && state.live.aircraft == null
        if (flown) {
            FlownCard(s, state, now, accent)
            BoardingPass(PassData.of(s, state.places), flap = false)
        } else {
            StatusStrip(s, state, now, accent)
            Timeline(s, state, now)
        }
        AircraftCard(s, state, accent, onSetTail, onOpenAircraft)
        if (!flown) RotationPanel(s, state.rotation, state.rotationLoading, state.thresholds, state.places, onRefreshRotation, onOpenSettings)
        BookingCard(s, state)
        NotesCard(s)
        SectionCard("Lounges") {
            val dep = state.lounges.filter { it.phase == LoungePhase.Departure }
            val arr = state.lounges.filter { it.phase == LoungePhase.Arrival }
            LoungeStrip(s.origin, state.places, dep, onOpenVisit, onLog = { onLogLounge(LoungePhase.Departure) }, inset = true)
            LoungeStrip(s.destination, state.places, arr, onOpenVisit, onLog = { onLogLounge(LoungePhase.Arrival) }, inset = true)
        }
    }
}

/** The route over the night map: drawing itself before departure, flown part solid and the plane live once airborne. */
@Composable
private fun FlightHero(s: FlightSegment, state: FlightUiState, now: Instant, accent: Color) {
    val colors = ContrailTheme.colors
    val motion = ContrailTheme.motion
    val a = state.places[s.origin]
    val b = state.places[s.destination]
    val draw = remember { Animatable(if (motion.reduced) 1f else 0f) }
    LaunchedEffect(Unit) { if (!motion.reduced) draw.animateTo(1f, tween(1_600)) }
    val markers = rememberMapMarkers()
    val live = state.live.aircraft
    Box(Modifier.fillMaxWidth().padding(horizontal = ContrailTheme.spacing.gutter)) {
        if (a != null && b != null) {
            val arc = remember(a, b) { Geo.greatCircle(LatLon(a.lat, a.lon), LatLon(b.lat, b.lon), 96) }
            val total = Geo.distanceM(a.lat, a.lon, b.lat, b.lon)
            // Live: real progress from position. Otherwise by the clock while in the air, or a full self-drawing arc.
            val progress = when {
                live != null && !live.onGround -> (1 - Geo.distanceM(live.lat, live.lon, b.lat, b.lon) / total).toFloat().coerceIn(0f, 1f)
                now.isAfter(s.departure) && now.isBefore(s.arrival) -> (Duration.between(s.departure, now).seconds.toFloat() / s.duration.seconds).coerceIn(0f, 1f)
                now.isAfter(s.arrival) -> 1f
                else -> null
            }
            FlightMap(
                focus = arc,
                modifier = Modifier.fillMaxWidth().aspectRatio(1.35f).clip(ContrailTheme.shapes.large),
            ) { fit ->
                val pts = arc.map { fit.project(it) }
                drawRouteArc(pts, accent, progress = (progress ?: 1f) * draw.value, dashedRest = colors.textTertiary.copy(alpha = 0.5f), width = 2.6f)
                markers.drawCode(this, s.origin, pts.first(), accent, true)
                markers.drawCode(this, s.destination, pts.last(), accent, true)
                if (live != null && !live.onGround) {
                    markers.drawPlane(this, fit.project(live.lat, live.lon), live.trackDeg ?: 0f, accent, 26f)
                } else {
                    arcTip(pts, (progress ?: 1f) * draw.value)?.let { (tip, heading) ->
                        if (progress != null && progress < 1f || draw.value < 1f) markers.drawPlane(this, tip, heading, accent, 22f)
                    }
                }
            }
        }
        // The aircraft floats over the bottom edge of the map.
        Box(Modifier.align(Alignment.BottomCenter).offset(y = 36.dp).fillMaxWidth(0.82f)) {
            val airline = Airlines.byIata(s.airlineIata)
            LiveryRender(Profiles.forType(s.aircraftType ?: "B789"), Liveries.forAirline(airline?.icao, accentArgb = airline?.accentArgb))
        }
    }
    Box(Modifier.size(36.dp))
}

@Composable
private fun StatusStrip(s: FlightSegment, state: FlightUiState, now: Instant, accent: Color) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val live = state.live.aircraft
    val airborne = live != null && !live.onGround
    GlassSurface(Modifier.fillMaxWidth(), shape = ContrailTheme.shapes.medium, glow = (if (airborne) colors.statusAirborne else accent).copy(alpha = 0.6f)) {
        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                val (label, color) = when {
                    airborne -> "Airborne" to colors.statusAirborne
                    live != null -> "On the ground" to colors.statusOnTime
                    now.isAfter(s.arrival) -> "Landed" to colors.textTertiary
                    now.isAfter(s.departure) -> "Should be in the air" to colors.statusAirborne
                    else -> "Scheduled" to colors.statusOnTime
                }
                LivePulse(color = color)
                Text(label, style = ContrailTheme.type.headline, color = colors.textPrimary, modifier = Modifier.weight(1f))
                when {
                    now.isBefore(s.departure) -> Text("in ${TripFormat.countdown(Duration.between(now, s.departure))}", style = ContrailTheme.type.numeric, color = accent)
                    now.isBefore(s.arrival) -> Text("lands in ${TripFormat.countdown(Duration.between(now, s.arrival))}", style = ContrailTheme.type.numeric, color = accent)
                }
            }
            if (airborne) {
                val dest = state.places[s.destination]
                Row(Modifier.fillMaxWidth()) {
                    StatTile("Altitude", live!!.altitudeFt?.let { String.format(Locale.US, "%,d", it) } ?: "—", Modifier.weight(1f), unit = "ft")
                    StatTile("Speed", live.groundSpeedKt?.toInt()?.toString() ?: "—", Modifier.weight(1f), unit = "kt")
                    StatTile(
                        "To go",
                        dest?.let { String.format(Locale.US, "%,d", (Geo.distanceM(live.lat, live.lon, it.lat, it.lon) / 1000).toInt()) } ?: "—",
                        Modifier.weight(1f),
                        unit = "km",
                    )
                }
                Text("Live from ${live.source} as ${live.displayCallsign}", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
            } else if (!state.live.inWindow && now.isBefore(s.departure)) {
                Text(
                    "Live tracking starts an hour before departure, as ${s.icaoCallsign ?: s.designator} on adsb.fi.",
                    style = ContrailTheme.type.bodySmall,
                    color = colors.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun Timeline(s: FlightSegment, state: FlightUiState, now: Instant) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val rows = FlightTimeline.rows(s, state.live, state.places, now)
    val delay = if (state.live.aircraft?.onGround == false) FlightTimeline.delay(rows, s) else null
    SectionCard("Timeline", trailing = { delay?.let { d ->
        val late = d.toMinutes()
        Tag(if (late > 5) "+$late min (est.)" else if (late < -5) "${-late} min early (est.)" else "On time (est.)", if (late > 15) colors.statusDelayed else colors.statusOnTime)
    } }) {
        Row {
            Box(Modifier.weight(1.1f))
            Eyebrow("Scheduled", Modifier.weight(1f))
            Eyebrow("Estimated", Modifier.weight(1f))
        }
        rows.forEachIndexed { i, r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1.1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Canvas(Modifier.size(10.dp)) {
                        drawCircle(if (i < 2) colors.accent else colors.statusAirborne, size.minDimension / 2, style = if (r.scheduled != null) androidx.compose.ui.graphics.drawscope.Fill else Stroke(1.5.dp.toPx()))
                    }
                    Text(r.label, style = ContrailTheme.type.title, color = colors.textPrimary)
                }
                Text(r.scheduled?.let(TripFormat::time) ?: "—", style = ContrailTheme.type.numeric, color = if (r.scheduled != null) colors.textPrimary else colors.textDisabled, modifier = Modifier.weight(1f))
                Column(Modifier.weight(1f)) {
                    Text(r.estimated?.let(TripFormat::time) ?: "—", style = ContrailTheme.type.numeric, color = if (r.estimated != null) colors.statusAirborne else colors.textDisabled)
                    r.estimateNote?.takeIf { r.estimated != null }?.let { Text(it, style = ContrailTheme.type.numericSmall, color = colors.textTertiary) }
                }
            }
        }
        Text("Gate times are from your ticket. Takeoff and landing are estimates.", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
    }
}

@Composable
private fun AircraftCard(s: FlightSegment, state: FlightUiState, accent: Color, onSetTail: (String) -> Unit, onOpenAircraft: (String) -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val uri = LocalUriHandler.current
    val airline = Airlines.byIata(s.airlineIata)
    val rarity = AircraftTypes.rarityFor(s.aircraftType, airline)
    var tail by rememberSaveable(s.tail) { mutableStateOf(s.tail.orEmpty()) }
    SectionCard("Aircraft", trailing = {
        if (rarity < Rarity.Common) RarityBadge(rarity.label, when (rarity) { Rarity.Legendary -> RarityPalette.Legendary; Rarity.Rare -> RarityPalette.Rare; else -> RarityPalette.Uncommon })
    }) {
        Row(Modifier.fillMaxWidth()) {
            Field("Type", AircraftTypes.nameFor(s.aircraftType) ?: s.aircraftType, Modifier.weight(2f))
            Field("Code", s.aircraftType, Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            FormField("Tail number", tail, { tail = it.uppercase().take(10) }, Modifier.weight(1f), placeholder = "A6-BLT", caps = true)
            AccentButton("Save", { onSetTail(tail) }, accent = accent, secondary = true, enabled = tail != s.tail.orEmpty())
        }
        if (s.tail != null) {
            Text(
                if (state.flightsOnTail <= 1) "Your first flight on ${s.tail}." else "Your flight number ${state.flightsOnTail} on ${s.tail}.",
                style = ContrailTheme.type.bodySmall,
                color = accent,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            val hex = state.rotation?.hex ?: state.live.aircraft?.hex
            if (hex != null) AccentButton("Aircraft page", { onOpenAircraft(hex) }, icon = ContrailIcons.Plane, secondary = true, accent = accent)
            AccentButton("Seat map", {
                uri.openUri("https://www.google.com/search?q=" + java.net.URLEncoder.encode("${airline?.name ?: s.airlineIata} ${AircraftTypes.nameFor(s.aircraftType) ?: ""} seat map", "UTF-8"))
            }, icon = ContrailIcons.ExternalLink, secondary = true, accent = colors.textSecondary)
        }
    }
}

/** A flight you've taken: when, how far, and how long ago. */
@Composable
private fun FlownCard(s: FlightSegment, state: FlightUiState, now: Instant, accent: Color) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val a = state.places[s.origin]
    val b = state.places[s.destination]
    val km = if (a != null && b != null) (Geo.distanceM(a.lat, a.lon, b.lat, b.lon) / 1000).toInt() else null
    val days = Duration.between(s.arrival, now).toDays()
    val ago = when {
        days < 1 -> "today"
        days < 2 -> "yesterday"
        days < 60 -> "$days days ago"
        days < 730 -> "${days / 30} months ago"
        else -> "${days / 365} years ago"
    }
    GlassSurface(Modifier.fillMaxWidth(), shape = ContrailTheme.shapes.medium, glow = accent.copy(alpha = 0.5f)) {
        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Icon(ContrailIcons.Stamp, null, tint = accent, modifier = Modifier.size(20.dp))
                Text("Flown $ago", style = ContrailTheme.type.headline, color = colors.textPrimary, modifier = Modifier.weight(1f))
                Tag(TripFormat.day(s.departureLocal), accent)
            }
            Row(Modifier.fillMaxWidth()) {
                StatTile("Distance", km?.let { String.format(Locale.US, "%,d", it) } ?: "—", Modifier.weight(1f), unit = "km")
                StatTile("Time", TripFormat.duration(s.duration), Modifier.weight(1f))
                StatTile("Speed", km?.let { String.format(Locale.US, "%,d", (it / (s.duration.toMinutes().coerceAtLeast(1) / 60.0)).toInt()) } ?: "—", Modifier.weight(1f), unit = "km/h")
            }
            if (s.timesEstimated) {
                Text("No times were given, so they're estimated from the distance. Tap edit to add the real ones.", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
            }
        }
    }
}

/** Your seat, why you flew and anything you wrote about it. */
@Composable
private fun NotesCard(s: FlightSegment) {
    if (s.note == null && s.reason == null && s.seatType == null) return
    SectionCard("Notes") {
        Row(Modifier.fillMaxWidth()) {
            Field("Seat type", s.seatType?.label, Modifier.weight(1f))
            Field("Reason", s.reason?.label, Modifier.weight(1f))
        }
        s.note?.let { Text(it, style = ContrailTheme.type.body, color = ContrailTheme.colors.textSecondary) }
    }
}

@Composable
private fun BookingCard(s: FlightSegment, state: FlightUiState) {
    SectionCard("Your booking") {
        Row(Modifier.fillMaxWidth()) {
            Field("Booking", state.trip?.pnr, Modifier.weight(1f))
            Field("Seat", s.seat, Modifier.weight(1f))
            Field("Class", s.fareClass, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth()) {
            Field("Cabin", s.cabin?.label, Modifier.weight(1.4f))
            Field("Terminal", s.departureTerminal, Modifier.weight(1f))
            Field("Gate", s.gate, Modifier.weight(1f))
        }
        s.operatedBy?.let { Text("Operated by $it", style = ContrailTheme.type.bodySmall, color = ContrailTheme.colors.textTertiary) }
        Text(
            "${TripFormat.day(s.departureLocal)} ${TripFormat.time(s.departureLocal)} → ${TripFormat.day(s.arrivalLocal)} ${TripFormat.time(s.arrivalLocal)} · local times · ${TripFormat.duration(s.duration)}",
            style = ContrailTheme.type.numericSmall,
            color = ContrailTheme.colors.textTertiary,
        )
    }
}

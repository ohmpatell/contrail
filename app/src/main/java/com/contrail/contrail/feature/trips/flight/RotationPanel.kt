package com.contrail.contrail.feature.trips.flight

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.contrail.contrail.core.data.reference.AircraftTypes
import com.contrail.contrail.core.data.rotation.Rotation
import com.contrail.contrail.core.data.rotation.TurnThresholds
import com.contrail.contrail.core.data.ticket.AirportInfo
import com.contrail.contrail.core.data.trip.FlightSegment
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.LivePulse
import com.contrail.contrail.core.designsystem.component.SectionCard
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.feature.trips.Tag
import com.contrail.contrail.feature.trips.TripFormat
import com.contrail.contrail.feature.trips.turn
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The cockpit-visit planner: the typical turnaround at your destination (last 7 days of the
 * same flight number), your aircraft right now, and where it flew before. Everything is an
 * estimate from free ADS-B history, and says so.
 */
@Composable
fun RotationPanel(
    segment: FlightSegment,
    rotation: Rotation?,
    loading: Boolean,
    thresholds: TurnThresholds,
    places: Map<String, AirportInfo>,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    SectionCard("Cockpit visit planner", trailing = {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            if (rotation?.sample == true) Tag("Sample data", colors.passport, filled = true)
            Tag("Estimated", colors.textTertiary)
        }
    }) {
        if (rotation?.sample == true) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(ContrailTheme.shapes.small)
                    .background(colors.passport.copy(alpha = 0.1f))
                    .clickable(role = Role.Button, onClick = onOpenSettings)
                    .padding(spacing.md),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(ContrailIcons.Sparkle, null, tint = colors.passport, modifier = Modifier.size(16.dp))
                Text(
                    "Turnaround history and earlier legs are sample data until your free OpenSky account is in Settings.",
                    style = ContrailTheme.type.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Turnaround(segment, rotation, loading, thresholds, places)
        AircraftNow(segment, rotation, places)
        rotation?.error?.let { Text(it, style = ContrailTheme.type.bodySmall, color = colors.statusDelayed) }
        Row(
            Modifier.clip(ContrailTheme.shapes.small).clickable(role = Role.Button, onClick = onRefresh).padding(vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Icon(ContrailIcons.Signal, null, tint = colors.textTertiary, modifier = Modifier.size(14.dp))
            Text(if (loading) "Checking…" else "Check again", style = ContrailTheme.type.label, color = colors.textTertiary)
        }
    }
}

@Composable
private fun Turnaround(segment: FlightSegment, rotation: Rotation?, loading: Boolean, thresholds: TurnThresholds, places: Map<String, AirportInfo>) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val est = rotation?.turnaround
    val badge = est?.badge(thresholds)
    val city = TripFormat.city(segment.destination, places)
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Eyebrow("Typical turnaround in $city")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            val big = when {
                est == null -> if (loading) "…" else "—"
                est.usuallyStays -> "Overnight"
                est.typicalMinutes != null -> TripFormat.duration(Duration.ofMinutes(est.typicalMinutes!!.toLong()))
                else -> "No data"
            }
            Text(big, style = ContrailTheme.type.displayMedium, color = badge?.let { colors.turn(it) } ?: colors.textPrimary)
            badge?.let { Tag(it.label, colors.turn(it), filled = true) }
        }
        badge?.let { Text(it.advice + ".", style = ContrailTheme.type.body, color = colors.textSecondary) }
        if (est != null) {
            TurnBars(est.days.map { it.groundMinutes to it.stayed }, est.days.map { it.seen }, est.days.map { it.localDate.dayOfWeek.name.take(1) }, thresholds)
            val seen = est.seenDays.size
            Text(
                if (seen == 0) "${segment.icaoCallsign ?: segment.designator} wasn't seen arriving in the last 7 days."
                else "Ground time after ${segment.icaoCallsign} landed, on $seen of the last 7 days (ADS-B, estimated).",
                style = ContrailTheme.type.bodySmall,
                color = colors.textTertiary,
            )
            est.usualNextLeg?.let { (cs, to) ->
                Text(
                    "Then usually continues as $cs${to?.let { " to ${TripFormat.city(it, places)}" }.orEmpty()}.",
                    style = ContrailTheme.type.bodySmall,
                    color = colors.textSecondary,
                )
            }
        } else if (segment.icaoCallsign == null) {
            Text("Contrail can't map ${segment.airlineIata} to its ICAO callsign yet, so there's no history.", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
        }
    }
}

/** Seven bars, one per past day: ground time, a moon when it stayed overnight, a dashed slot when unseen. */
@Composable
private fun TurnBars(values: List<Pair<Int?, Boolean>>, seen: List<Boolean>, labels: List<String>, t: TurnThresholds) {
    val colors = ContrailTheme.colors
    val motion = ContrailTheme.motion
    val grow = remember { Animatable(if (motion.reduced) 1f else 0f) }
    LaunchedEffect(values) { if (!motion.reduced) { grow.snapTo(0f); grow.animateTo(1f, tween(900)) } }
    val maxMin = 240f
    Column(verticalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.xs)) {
        Canvas(Modifier.fillMaxWidth().height(64.dp)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val w = slot * 0.56f
            val relaxedY = size.height * (1 - t.relaxedMinutes / maxMin)
            drawLine(colors.hairline, Offset(0f, relaxedY), Offset(size.width, relaxedY), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())))
            values.forEachIndexed { i, (m, stayed) ->
                // Oldest day on the left.
                val x = size.width - (i + 1) * slot + (slot - w) / 2
                if (!seen[i]) {
                    drawRoundRect(colors.hairline, Offset(x, size.height * 0.55f), Size(w, size.height * 0.45f), CornerRadius(4.dp.toPx()), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))
                    return@forEachIndexed
                }
                val minutes = if (stayed || m == null) maxMin else m.toFloat().coerceAtMost(maxMin)
                val h = size.height * (minutes / maxMin) * grow.value
                val c = when {
                    stayed || (m ?: 0) >= t.relaxedMinutes -> colors.statusOnTime
                    (m ?: 0) >= t.maybeMinutes -> colors.statusDelayed
                    else -> colors.statusCancelled
                }
                drawRoundRect(Brush.verticalGradient(listOf(c, c.copy(alpha = 0.35f)), startY = size.height - h, endY = size.height), Offset(x, size.height - h), Size(w, h), CornerRadius(4.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.reversed().forEach { l -> Text(l, style = ContrailTheme.type.numericSmall, color = colors.textTertiary, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
    }
}

@Composable
private fun AircraftNow(segment: FlightSegment, rotation: Rotation?, places: Map<String, AirportInfo>) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val time = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.UK)
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
    if (segment.tail == null) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Eyebrow("Your aircraft")
            Text("Not assigned yet", style = ContrailTheme.type.title, color = colors.textPrimary)
            Text(
                "${AircraftTypes.nameFor(segment.aircraftType) ?: "Type to be confirmed"} on your ticket. Add the tail number below when the airline shows it, and Contrail finds the aircraft and its inbound flight.",
                style = ContrailTheme.type.bodySmall,
                color = colors.textTertiary,
            )
        }
        return
    }
    val now = rotation?.now
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Eyebrow("${segment.tail} right now")
        when {
            now == null -> Text("Not seen on ADS-B right now. It may be parked, or out of coverage.", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
            now.isInbound -> Row(
                Modifier
                    .fillMaxWidth()
                    .clip(ContrailTheme.shapes.small)
                    .background(colors.statusAirborne.copy(alpha = 0.1f))
                    .border(ContrailTheme.sizes.hairline, colors.statusAirborne.copy(alpha = 0.5f), ContrailTheme.shapes.small)
                    .padding(spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                LivePulse(color = colors.statusAirborne)
                Column(Modifier.weight(1f)) {
                    Text("Your inbound: ${now.callsign} from ${now.from?.let { TripFormat.city(it, places) } ?: "?"}", style = ContrailTheme.type.title, color = colors.textPrimary)
                    Text(
                        now.etaMs?.let { "Lands about ${time.format(Instant.ofEpochMilli(it).atZone(segment.departureZone))} local (estimated)" } ?: "Airborne",
                        style = ContrailTheme.type.bodySmall,
                        color = colors.statusAirborne,
                    )
                }
            }
            now.aircraft.onGround -> Text("On the ground${now.callsign?.let { ", last as $it" }.orEmpty()}.", style = ContrailTheme.type.body, color = colors.textSecondary)
            else -> Text(
                "Flying ${now.callsign ?: "a flight"}${if (now.from != null || now.to != null) " · ${now.from ?: "?"} → ${now.to ?: "?"} (route from callsign)" else ""}",
                style = ContrailTheme.type.body,
                color = colors.textSecondary,
            )
        }
        val legs = rotation?.earlierLegs.orEmpty()
        if (legs.isNotEmpty()) {
            Eyebrow("Where it flew before")
            val zone = ZoneId.systemDefault()
            legs.forEach { l ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text(l.callsign ?: "—", style = ContrailTheme.type.numeric, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    Text("${l.from ?: "?"} → ${l.to ?: "?"}", style = ContrailTheme.type.numeric, color = colors.textSecondary)
                    Text(time.format(Instant.ofEpochSecond(l.departedSec).atZone(zone)), style = ContrailTheme.type.numericSmall, color = colors.textTertiary)
                }
            }
            Text("Times in your phone's time zone.", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
        }
    }
}

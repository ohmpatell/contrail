package com.contrail.contrail.feature.airport

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.contrail.contrail.core.data.repository.BoardRefresh
import com.contrail.contrail.core.designsystem.component.AccentButton
import com.contrail.contrail.core.designsystem.component.ContrailChip
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.SectionCard
import com.contrail.contrail.core.designsystem.component.SplitFlapText
import com.contrail.contrail.core.designsystem.theme.ContrailColors
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.core.network.schedule.AeroDataBoxSource
import com.contrail.contrail.core.network.schedule.BoardRow
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

/**
 * The airport's real departures and arrivals (AeroDataBox): times, delays, gates and status.
 * Loading costs 2 of the month's units, so it waits for a tap unless a fresh copy exists.
 */
@Composable
fun ScheduleBoardCard(state: AirportUiState, nowMs: Long, onBoard: (BoardKind) -> Unit, onLoad: () -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val uri = LocalUriHandler.current
    val zone = state.airport?.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
    val board = state.schedule
    SectionCard(if (state.board == BoardKind.Departures) "Departures" else "Arrivals", trailing = { Eyebrow("airline data", color = colors.accent) }) {
        if (board == null) {
            Text(
                "The airport's timetable with delays, gates and status. Loading uses 2 of this month's free AeroDataBox units" +
                    (state.unitsLeft?.let { " ($it left)" } ?: "") + ".",
                style = ContrailTheme.type.bodySmall, color = colors.textSecondary,
            )
            if (state.scheduleNote == BoardRefresh.Unavailable && state.unitsLeft != null && state.unitsLeft <= 60) {
                Text("Saving the last units for your own flights this month.", style = ContrailTheme.type.bodySmall, color = colors.statusDelayed)
            } else {
                AccentButton(if (state.scheduleLoading) "Loading…" else "Load the board", onLoad, enabled = !state.scheduleLoading)
            }
            return@SectionCard
        }
        val all = if (state.board == BoardKind.Departures) board.departures else board.arrivals
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            ContrailChip("Departures · ${board.departures.size}", state.board == BoardKind.Departures, onClick = { onBoard(BoardKind.Departures) })
            ContrailChip("Arrivals · ${board.arrivals.size}", state.board == BoardKind.Arrivals, onClick = { onBoard(BoardKind.Arrivals) })
        }
        val now = Instant.ofEpochMilli(nowMs)
        val rows = all.filter { it.best.isAfter(now - Duration.ofMinutes(20)) }.take(14)
        if (rows.isEmpty()) Text("Nothing more in the next few hours.", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
        rows.forEach { ScheduleRow(it, zone, colors) }
        val age = Duration.ofMillis((nowMs - board.fetchedAtMs).coerceAtLeast(0)).toMinutes()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            Column(Modifier.weight(1f)) {
                Text(
                    AeroDataBoxSource.ATTRIBUTION,
                    style = ContrailTheme.type.label, color = colors.accent,
                    modifier = Modifier.clickable(role = Role.Button) { uri.openUri(AeroDataBoxSource.ATTRIBUTION_URL) },
                )
                Text(
                    (if (age < 1) "Updated just now" else "Updated $age min ago") +
                        (if (state.scheduleNote == BoardRefresh.Shared) " · shared" else "") +
                        (state.unitsLeft?.let { " · $it units left" } ?: ""),
                    style = ContrailTheme.type.bodySmall, color = colors.textTertiary,
                )
            }
            AccentButton(if (state.scheduleLoading) "…" else "Refresh", onLoad, secondary = true, enabled = !state.scheduleLoading && age >= 15)
        }
    }
}

@Composable
private fun ScheduleRow(r: BoardRow, zone: ZoneId, colors: ContrailColors) {
    val spacing = ContrailTheme.spacing
    val delayed = r.delayMinutes >= 5
    Row(
        Modifier.fillMaxWidth().padding(vertical = spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Column(Modifier.width(48.dp)) {
            Text(
                HHMM.format(r.scheduled.atZone(zone)),
                style = ContrailTheme.type.numeric,
                color = if (delayed) colors.textTertiary else colors.textPrimary,
                textDecoration = if (delayed) TextDecoration.LineThrough else null,
            )
            if (delayed) Text(HHMM.format(r.revised!!.atZone(zone)), style = ContrailTheme.type.numeric, color = colors.statusDelayed)
        }
        SplitFlapText(r.number.replace(" ", "").take(7).padEnd(7), style = ContrailTheme.type.numericSmall, gap = 2.dp)
        Column(Modifier.weight(1f)) {
            Text(r.otherIata ?: "···", style = ContrailTheme.type.numeric, color = colors.textPrimary)
            Text(r.otherName ?: "", style = ContrailTheme.type.bodySmall, color = colors.textTertiary, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            val label = AeroDataBoxSource.label(if (delayed && r.status == "Expected") "Delayed" else r.status)
            Text(label, style = ContrailTheme.type.label, color = statusColor(label, colors), maxLines = 1)
            r.gate?.let { Text("Gate $it", style = ContrailTheme.type.numericSmall, color = colors.textSecondary) }
        }
    }
}

private fun statusColor(label: String, c: ContrailColors): Color = when (label) {
    "On time", "Landed" -> c.statusOnTime
    "Delayed", "Boarding", "Gate closed", "Check-in" -> c.statusDelayed
    "Cancelled", "Diverted" -> c.statusCancelled
    "In the air", "Departed", "Landing" -> c.statusAirborne
    else -> c.textSecondary
}

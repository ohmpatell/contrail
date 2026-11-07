package com.contrail.contrail.feature.trips.flight

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import com.contrail.contrail.core.data.trip.FlightSegment
import com.contrail.contrail.core.designsystem.component.AccentButton
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.SectionCard
import com.contrail.contrail.core.designsystem.theme.ContrailColors
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.core.network.schedule.AeroDataBoxSource
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

/** Whether the airline card is worth showing: data already in, or the flight is close. */
fun showsAirlineStatus(s: FlightSegment, now: Instant): Boolean =
    s.liveStatus != null || s.revisedDeparture != null || s.actualDeparture != null ||
        (now.isAfter(s.departure - Duration.ofHours(48)) && now.isBefore(s.arrival + Duration.ofHours(24)))

/**
 * What the airline says about your flight (AeroDataBox) and what Contrail saw on ADS-B:
 * status, delay, gate, and real takeoff and landing times.
 */
@Composable
fun AirlineStatusCard(s: FlightSegment, checking: Boolean, unitsLeft: Int?, onCheck: () -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val uri = LocalUriHandler.current
    fun t(i: Instant, z: ZoneId) = HHMM.format(i.atZone(z))
    SectionCard("From the airline", trailing = { s.liveStatus?.let { Eyebrow(it, color = statusTint(it, colors)) } }) {
        val delay = s.delayMinutes
        val revisedDep = s.revisedDeparture
        if (revisedDep != null && s.actualDeparture == null) {
            val note = when {
                delay >= 5 -> "  (+$delay min)"
                delay <= -5 -> "  ($delay min)"
                else -> ""
            }
            Line("Departs", t(revisedDep, s.departureZone) + note, if (delay >= 5) colors.statusDelayed else colors.statusOnTime)
        }
        val revisedArr = s.revisedArrival
        if (revisedArr != null && s.actualArrival == null) Line("Lands", t(revisedArr, s.arrivalZone), colors.textPrimary)
        val place = listOfNotNull(s.departureTerminal?.let { "Terminal $it" }, s.gate?.let { "Gate $it" }).joinToString(" · ")
        if (place.isNotEmpty()) Line("From", place, colors.textPrimary)
        s.actualDeparture?.let { Line("Took off", t(it, s.departureZone), colors.statusAirborne) }
        s.actualArrival?.let { Line("Landed", t(it, s.arrivalZone), colors.statusOnTime) }
        if (s.actualDeparture != null) {
            Text(
                if (s.actualSource == "runway") {
                    "Takeoff and landing from the airport's runway report."
                } else {
                    "Takeoff and landing as Contrail saw them on ADS-B · estimated"
                },
                style = ContrailTheme.type.bodySmall,
                color = colors.textTertiary,
            )
        }
        if (s.liveStatus == null && revisedDep == null && s.actualDeparture == null) {
            Text(
                "Contrail checks with the airline three hours before departure, before boarding, and after landing. You can check now too.",
                style = ContrailTheme.type.bodySmall,
                color = colors.textSecondary,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            Column(Modifier.weight(1f)) {
                Text(
                    AeroDataBoxSource.ATTRIBUTION,
                    style = ContrailTheme.type.label,
                    color = colors.accent,
                    modifier = Modifier.clickable(role = Role.Button) { uri.openUri(AeroDataBoxSource.ATTRIBUTION_URL) },
                )
                unitsLeft?.let { Text("$it units left this month", style = ContrailTheme.type.bodySmall, color = colors.textTertiary) }
            }
            AccentButton(if (checking) "Checking…" else "Check now", onCheck, secondary = true, enabled = !checking && (unitsLeft ?: 2) >= 2)
        }
    }
}

@Composable
private fun Line(label: String, value: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label.uppercase(), style = ContrailTheme.type.eyebrow, color = ContrailTheme.colors.textTertiary, modifier = Modifier.weight(1f))
        Text(value, style = ContrailTheme.type.numeric, color = tint)
    }
}

private fun statusTint(label: String, c: ContrailColors): Color = when (label) {
    "On time", "Landed" -> c.statusOnTime
    "Delayed", "Boarding", "Gate closed", "Check-in" -> c.statusDelayed
    "Cancelled", "Diverted" -> c.statusCancelled
    else -> c.statusAirborne
}

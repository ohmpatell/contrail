package com.contrail.contrail.feature.cabin

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.data.cabin.FlightPack
import com.contrail.contrail.core.data.ticket.AirportInfo
import com.contrail.contrail.core.data.trip.FlightSegment
import com.contrail.contrail.core.designsystem.component.AccentButton
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.GlassSurface
import com.contrail.contrail.core.designsystem.component.LivePulse
import com.contrail.contrail.core.designsystem.component.SectionCard
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Cabin mode is worth showing from two days before until a few hours after landing. */
fun cabinRelevant(s: FlightSegment, now: Instant): Boolean =
    now.isAfter(s.departure.minus(Duration.ofDays(2))) && now.isBefore(s.arrival.plus(Duration.ofHours(6)))

/**
 * The flight page's In the air card: is the flight ready for no internet (route, places, map), a
 * button to get it ready now, and the way into Cabin mode.
 */
@Composable
fun InFlightCard(segment: FlightSegment, pack: FlightPack?, accent: Color, onCabin: () -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val context = LocalContext.current
    val work by remember(segment.id) { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(FlightPackWorker.name(segment.id)) }.collectAsState(emptyList())
    val running = work.firstOrNull { it.state == WorkInfo.State.RUNNING }
    SectionCard("In the air", trailing = { Eyebrow("Cabin mode", color = accent) }) {
        Text(
            "A moving map, what's out your window, sunset from up high, the ride and cabin altitude: all with no internet, from your phone's GPS.",
            style = ContrailTheme.type.bodySmall,
            color = colors.textSecondary,
        )
        when {
            running != null -> {
                val step = running.progress.getString(FlightPackWorker.KEY_STEP) ?: "Getting ready…"
                val pct = running.progress.getInt(FlightPackWorker.KEY_PERCENT, 0)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    LivePulse(color = accent)
                    Text(step + if (pct > 0) " $pct%" else "", style = ContrailTheme.type.label, color = colors.textPrimary)
                }
                Box(Modifier.fillMaxWidth().height(3.dp).clip(ContrailTheme.shapes.chip).background(colors.hairline)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(pct / 100f).background(accent))
                }
            }
            pack != null -> {
                PackLine(ContrailIcons.Check, if (pack.mapReady) "Map along the route saved" else "Map not saved yet (it fills in as you browse)", pack.mapReady, accent)
                PackLine(ContrailIcons.Check, "${pack.places.size} places along the way" + if (pack.places.any { it.blurb != null }) ", with Wikipedia notes" else "", pack.places.isNotEmpty(), accent)
                PackLine(ContrailIcons.Check, if (pack.pathSource == "recent") "Route as this flight really flew it lately" else "Great-circle route", true, accent)
                pack.weather?.let { PackLine(ContrailIcons.Check, "Weather at the other end: $it", true, accent) }
            }
            else -> Text(
                "Contrail gets this ready on flight day while you're online. Or do it now.",
                style = ContrailTheme.type.bodySmall,
                color = colors.textTertiary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            AccentButton("Cabin mode", onCabin, icon = ContrailIcons.Plane, accent = accent, modifier = Modifier.weight(1f))
            AccentButton(if (pack == null) "Get ready" else "Refresh", { FlightPackWorker.runNow(context, segment.id) }, accent = accent, secondary = true, enabled = running == null, modifier = Modifier.weight(0.75f))
        }
    }
}

@Composable
private fun PackLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, done: Boolean, accent: Color) {
    val colors = ContrailTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.sm)) {
        Icon(icon, null, tint = if (done) accent else colors.textDisabled, modifier = Modifier.size(14.dp))
        Text(text, style = ContrailTheme.type.bodySmall, color = if (done) colors.textPrimary else colors.textTertiary)
    }
}

/**
 * Airport mode: on flight day, when you open the flight at the departure airport, a banner with
 * what matters now: the gate, when boarding starts, and a fresh check with the airline.
 */
@Composable
fun AirportModeBanner(segment: FlightSegment, origin: AirportInfo?, now: Instant, accent: Color, onCheck: () -> Unit, checking: Boolean) {
    val context = LocalContext.current
    var here by remember(segment.id) { mutableStateOf(false) }
    LaunchedEffect(segment.id, origin) {
        val o = origin ?: return@LaunchedEffect
        val soon = now.isAfter(segment.departure.minus(Duration.ofHours(8))) && now.isBefore(segment.departure)
        if (!soon) return@LaunchedEffect
        val p = lastKnown(context) ?: return@LaunchedEffect
        here = Geo.distanceM(p.first, p.second, o.lat, o.lon) < 8_000
    }
    if (!here || origin == null) return
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val boarding = segment.expectedDeparture.minus(Duration.ofMinutes(40))
    val time = DateTimeFormatter.ofPattern("HH:mm", Locale.UK).withZone(origin.zone)
    GlassSurface(Modifier.fillMaxWidth(), shape = ContrailTheme.shapes.medium, glow = accent) {
        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                LivePulse(color = accent)
                Eyebrow("You're at ${origin.city}", color = accent)
            }
            Text(
                listOfNotNull(segment.gate?.let { "Gate $it" }, "boarding about ${time.format(boarding)}", segment.departureTerminal?.let { "terminal $it" }).joinToString(" · "),
                style = ContrailTheme.type.headline,
                color = colors.textPrimary,
            )
            Text("Boarding time is estimated (40 minutes before departure). Check with the airline for the latest gate.", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
            AccentButton(if (checking) "Checking…" else "Check the gate now", onCheck, accent = accent, secondary = true, enabled = !checking, modifier = Modifier.fillMaxWidth())
        }
    }
}

@SuppressLint("MissingPermission")
private fun lastKnown(context: Context): Pair<Double, Double>? {
    val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!ok) return null
    val lm = context.getSystemService(LocationManager::class.java) ?: return null
    val best = lm.getProviders(true).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time } ?: return null
    // Only a recent fix says where you are now.
    if (System.currentTimeMillis() - best.time > 30 * 60_000L) return null
    return best.latitude to best.longitude
}

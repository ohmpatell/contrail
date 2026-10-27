package com.contrail.contrail.feature.passport

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.ticket.AirportInfo
import com.contrail.contrail.core.data.trip.FlightSegment
import com.contrail.contrail.core.designsystem.component.DottedGlobe
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.GlassIconButton
import com.contrail.contrail.core.designsystem.component.GlassSurface
import com.contrail.contrail.core.designsystem.component.MapArc
import com.contrail.contrail.core.designsystem.component.SplitFlapText
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.feature.trips.TripFormat
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One flight on the globe, oldest first. */
data class GlobeLeg(val segment: FlightSegment, val points: List<LatLon>, val km: Long)

fun globeLegs(flown: List<FlightSegment>, places: Map<String, AirportInfo>): List<GlobeLeg> =
    flown.sortedBy { it.departure }.mapNotNull { s ->
        val a = places[s.origin] ?: return@mapNotNull null
        val b = places[s.destination] ?: return@mapNotNull null
        GlobeLeg(s, Geo.greatCircle(LatLon(a.lat, a.lon), LatLon(b.lat, b.lon), 48), PassportInsights.distanceKm(s, places))
    }

private val REPLAY_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)

/** Shortest signed turn from [a] to [b] in degrees of longitude. */
private fun wrapDelta(a: Float, b: Float): Float {
    var d = (b - a) % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return d
}

/**
 * Your lifetime globe. At rest every route glows and the globe drifts (drag to spin). Replay
 * flies your history in order: each route draws itself as the camera follows the plane,
 * with the date and running totals ticking along.
 */
@Composable
fun PassportGlobe(legs: List<GlobeLeg>, markers: List<LatLon>, modifier: Modifier = Modifier, autoplay: Boolean = false) {
    val colors = ContrailTheme.colors
    val motion = ContrailTheme.motion
    val scope = rememberCoroutineScope()
    val routes = remember(legs) { legs.distinctBy { listOf(it.segment.origin, it.segment.destination).sorted() } }
    val latest = legs.lastOrNull()
    val home = remember(legs) {
        legs.groupingBy { it.segment.origin }.eachCount().maxByOrNull { it.value }?.key?.let { code -> legs.first { it.segment.origin == code }.points.first() }
    }
    val drift by if (motion.ambientEnabled) {
        rememberInfiniteTransition(label = "globe").animateFloat(0f, 360f, infiniteRepeatable(tween(100_000, easing = LinearEasing), RepeatMode.Restart), label = "drift")
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    var dragged by remember { mutableFloatStateOf(0f) }
    val bloom = remember { Animatable(if (motion.reduced) 1f else 0f) }
    LaunchedEffect(routes.size) { if (!motion.reduced) { bloom.snapTo(0f); bloom.animateTo(1f, tween(2_400, easing = FastOutSlowInEasing)) } }

    var replaying by remember { mutableStateOf(false) }
    val replay = remember { Animatable(0f) }
    var camLon by remember { mutableFloatStateOf(0f) }
    var camLat by remember { mutableFloatStateOf(25f) }
    val restLon = (home?.lon?.toFloat() ?: -40f) + 50f + drift + dragged
    val restLat = (home?.lat?.toFloat() ?: 25f).coerceIn(-20f, 45f)

    fun start() {
        if (legs.isEmpty()) return
        camLon = restLon; camLat = restLat
        replaying = true
        scope.launch {
            replay.snapTo(0f)
            val ms = (legs.size * 650).coerceIn(5_000, 45_000)
            replay.animateTo(1f, tween(if (motion.reduced) 0 else ms, easing = LinearEasing))
            kotlinx.coroutines.delay(1_200)
            replaying = false
            dragged = camLon - ((home?.lon?.toFloat() ?: -40f) + 50f + drift)
        }
    }
    LaunchedEffect(autoplay) { if (autoplay) start() }
    // The camera eases toward the plane, so long routes sweep the globe round smoothly.
    LaunchedEffect(replaying) {
        while (replaying) {
            withFrameNanos { }
            val p = replay.value * legs.size
            val k = p.toInt().coerceIn(0, legs.lastIndex)
            val leg = legs[k]
            val i = ((p - k).coerceIn(0f, 1f) * leg.points.lastIndex).toInt()
            val tip = leg.points[i]
            camLon += wrapDelta(camLon, tip.lon.toFloat()) * 0.06f
            camLat += ((tip.lat.toFloat().coerceIn(-25f, 50f)) - camLat) * 0.06f
        }
    }

    val p = replay.value * legs.size
    val current = p.toInt().coerceIn(0, (legs.size - 1).coerceAtLeast(0))
    val arcs = if (replaying) {
        legs.take(current + 1).mapIndexed { idx, leg ->
            val prog = if (idx < current) 1f else (p - current).coerceIn(0f, 1f)
            MapArc(leg.points, prog, color = if (idx == current) colors.accent else null, emphasis = idx == current)
        }
    } else {
        routes.mapIndexed { idx, leg ->
            val isLatest = latest != null && setOf(leg.segment.origin, leg.segment.destination) == setOf(latest.segment.origin, latest.segment.destination)
            MapArc(leg.points, (bloom.value * (routes.size + 3) - idx * 0.5f).coerceIn(0f, 1f), color = if (isLatest) colors.accent else null, emphasis = isLatest)
        }
    }

    GlassSurface(modifier.fillMaxWidth(), glow = colors.passport.copy(alpha = 0.7f)) {
        Box(Modifier.matchParentSize().background(Brush.radialGradient(listOf(colors.passport.copy(alpha = 0.18f), Color.Transparent))))
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).pointerInput(replaying) {
                if (!replaying) detectHorizontalDragGestures { _, dx -> dragged -= dx * 0.35f }
            },
        ) {
            DottedGlobe(
                arcs = arcs,
                centerLon = if (replaying) camLon else restLon,
                centerLat = if (replaying) camLat else restLat,
                markers = markers,
                modifier = Modifier.fillMaxSize().padding(ContrailTheme.spacing.md),
            )
            AnimatedVisibility(replaying, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopStart)) {
                legs.getOrNull(current)?.let { leg ->
                    val km = legs.take(current + 1).sumOf { it.km }
                    Column(Modifier.padding(ContrailTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.xxs)) {
                        SplitFlapText(leg.segment.departureLocal.year.toString(), style = ContrailTheme.type.flap, color = colors.textPrimary)
                        Text(REPLAY_DATE.format(leg.segment.departureLocal), style = ContrailTheme.type.numericSmall, color = colors.textSecondary)
                        Text("${leg.segment.origin} → ${leg.segment.destination}", style = ContrailTheme.type.numeric, color = colors.accent)
                        Text(
                            String.format(Locale.US, "Flight %,d · %,d km", current + 1, km),
                            style = ContrailTheme.type.numericSmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(
                Modifier.align(Alignment.BottomEnd).padding(ContrailTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.sm),
            ) {
                if (!replaying && legs.isNotEmpty()) {
                    Box(Modifier.clip(ContrailTheme.shapes.chip).background(colors.canvas.copy(alpha = 0.5f)).padding(horizontal = ContrailTheme.spacing.md, vertical = ContrailTheme.spacing.xs)) {
                        Eyebrow("Replay your flying", color = colors.passport)
                    }
                }
                GlassIconButton(
                    if (replaying) ContrailIcons.Pause else ContrailIcons.Play,
                    if (replaying) "Stop replay" else "Replay your flying",
                    onClick = {
                        if (replaying) { scope.launch { replay.stop() }; replaying = false } else start()
                    },
                    active = replaying,
                )
            }
            if (!replaying && latest != null) {
                Column(Modifier.align(Alignment.TopStart).padding(ContrailTheme.spacing.lg)) {
                    Eyebrow("Latest", color = colors.accent)
                    Text("${latest.segment.origin} → ${latest.segment.destination}", style = ContrailTheme.type.numeric, color = colors.textPrimary)
                    Text(TripFormat.day(latest.segment.departureLocal), style = ContrailTheme.type.numericSmall, color = colors.textTertiary)
                }
            }
        }
    }
}

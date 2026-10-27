package com.contrail.contrail.feature.sky

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contrail.contrail.core.data.sky.AlertRules
import com.contrail.contrail.core.data.sky.SkyContact
import com.contrail.contrail.core.designsystem.component.AccentButton
import com.contrail.contrail.core.designsystem.component.ContrailChip
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.GlassIconButton
import com.contrail.contrail.core.designsystem.component.GlassSurface
import com.contrail.contrail.core.designsystem.component.LivePulse
import com.contrail.contrail.core.designsystem.component.RarityBadge
import com.contrail.contrail.core.designsystem.component.RiseIn
import com.contrail.contrail.core.designsystem.component.SectionCard
import com.contrail.contrail.core.designsystem.component.StatTile
import com.contrail.contrail.core.designsystem.component.pressScale
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.AltitudeRamp
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.core.designsystem.theme.RarityPalette
import com.contrail.contrail.core.data.reference.Rarity
import com.contrail.contrail.navigation.LocalNavigator
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

data class SkyActions(
    val onAllowLocation: () -> Unit = {},
    val onRadius: (Int) -> Unit = {},
    val onAircraft: (String) -> Unit = {},
    val onSkyView: () -> Unit = {},
    val onAlerts: (AlertRules) -> Unit = {},
)

@Composable
fun SkyRoute(viewModel: SkyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val orientation = rememberDeviceOrientation()
    DisposableEffect(Unit) {
        viewModel.setActive(true)
        onDispose { viewModel.setActive(false) }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.onPermissionResult() }
    val pendingAlerts = remember { androidx.compose.runtime.mutableStateOf<AlertRules?>(null) }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingAlerts.value?.let(viewModel::setAlerts)
    }
    SkyScreen(
        state = state,
        headingDeg = if (orientation.available) orientation.headingDeg else 0f,
        headingUp = orientation.available,
        actions = SkyActions(
            onAllowLocation = { locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
            onRadius = viewModel::setRadius,
            onAircraft = nav::openAircraft,
            onSkyView = nav::openSkyView,
            onAlerts = { rules ->
                if (rules.enabled && !state.alerts.enabled) {
                    pendingAlerts.value = rules
                    notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.setAlerts(rules)
                }
            },
        ),
    )
}

private val RADII = listOf(5, 10, 25, 50, 100)

@Composable
fun SkyScreen(state: SkyUiState, headingDeg: Float, headingUp: Boolean, actions: SkyActions, modifier: Modifier = Modifier) {
    val spacing = ContrailTheme.spacing
    val colors = ContrailTheme.colors
    val accent = colors.statusAirborne
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = spacing.gutter)
            .padding(top = spacing.xl, bottom = spacing.navBarClearance),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    LivePulse(color = if (state.updatedAtMs != null) accent else colors.textTertiary)
                    Eyebrow(
                        when {
                            state.locating -> "Finding you…"
                            state.near != null -> "Near ${state.near}"
                            else -> "What's overhead"
                        },
                        color = accent,
                    )
                }
                Text("Sky", style = ContrailTheme.type.displayLarge, color = colors.textPrimary)
            }
            GlassIconButton(ContrailIcons.Camera, "Sky View", actions.onSkyView)
        }
        if (!state.permission && state.me == null) {
            RiseIn(0) { PermissionCard(actions.onAllowLocation) }
            return@Column
        }
        RiseIn(0) { ScopeCard(state, headingDeg, headingUp, actions) }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            RADII.forEach { r -> ContrailChip("$r km", state.radiusKm == r, onClick = { actions.onRadius(r) }, accent = accent) }
        }
        state.closest?.let { c -> RiseIn(1) { ClosestCard(c, state, actions) } }
        RiseIn(2) { SkyViewCta(actions.onSkyView) }
        if (state.contacts.size > 1) RiseIn(3) { OverheadList(state.contacts.drop(1).take(25), headingDeg, actions.onAircraft) }
        RiseIn(4) { AlertsCard(state.alerts, actions.onAlerts) }
        state.error?.let { Text(it, style = ContrailTheme.type.bodySmall, color = colors.statusDelayed) }
        if (state.updatedAtMs != null) {
            Text(
                "Live from ${state.source ?: "ADS-B"} · positions glide between updates · every aircraft that shares its position",
                style = ContrailTheme.type.bodySmall,
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
private fun PermissionCard(onAllow: () -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    GlassSurface(Modifier.fillMaxWidth(), glow = colors.statusAirborne.copy(alpha = 0.6f)) {
        Column(Modifier.padding(spacing.xl), verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
            Box(Modifier.fillMaxWidth().aspectRatio(1.5f)) { Scope(emptyList(), 25, 0f, null, Modifier.fillMaxSize()) }
            Text("What's flying over you?", style = ContrailTheme.type.displaySmall, color = colors.textPrimary)
            Text(
                "Contrail needs your location to find the planes above you. It stays on your phone and is only used while Sky is open (and, if you turn on alerts, the last place you opened it).",
                style = ContrailTheme.type.body,
                color = colors.textSecondary,
            )
            AccentButton("Allow location", onAllow, icon = ContrailIcons.Locate, accent = colors.statusAirborne, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** A heading-up scope: your position in the middle, everything in range around you, a sweep going round. */
@Composable
private fun ScopeCard(state: SkyUiState, headingDeg: Float, headingUp: Boolean, actions: SkyActions) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    GlassSurface(Modifier.fillMaxWidth(), glow = colors.statusAirborne.copy(alpha = 0.6f)) {
        Box(Modifier.matchParentSize().background(Brush.radialGradient(listOf(colors.statusAirborne.copy(alpha = 0.12f), Color.Transparent))))
        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow(if (headingUp) "Heading up · ${compass(headingDeg.toDouble())}" else "North up", Modifier.weight(1f))
                Text("${state.contacts.size} in ${state.radiusKm} km", style = ContrailTheme.type.numericSmall, color = colors.statusAirborne)
            }
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                Scope(state.contacts, state.radiusKm, if (headingUp) headingDeg else 0f, state.closest?.aircraft?.hex, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
fun Scope(contacts: List<SkyContact>, radiusKm: Int, headingDeg: Float, highlight: String?, modifier: Modifier) {
    val colors = ContrailTheme.colors
    val motion = ContrailTheme.motion
    val accent = colors.statusAirborne
    val sweep by if (motion.ambientEnabled) {
        rememberInfiniteTransition(label = "scope").animateFloat(0f, 360f, infiniteRepeatable(tween(4_200, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
    } else {
        remember { mutableFloatStateOf(300f) }
    }
    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2 * 0.94f
        for (i in 1..3) drawCircle(colors.hairline.copy(alpha = 0.12f + i * 0.03f), r * i / 3f, c, style = Stroke(1.dp.toPx()))
        drawCircle(accent.copy(alpha = 0.35f), r, c, style = Stroke(1.dp.toPx()))
        // Cardinal ticks turn with you, so north is always where north is.
        rotate(-headingDeg, c) {
            for (deg in 0 until 360 step 10) {
                val a = Math.toRadians(deg.toDouble() - 90)
                val inner = if (deg % 90 == 0) r * 0.9f else r * 0.95f
                drawLine(
                    if (deg == 0) accent else colors.textTertiary.copy(alpha = 0.5f),
                    Offset(c.x + (cos(a) * inner).toFloat(), c.y + (sin(a) * inner).toFloat()),
                    Offset(c.x + (cos(a) * r).toFloat(), c.y + (sin(a) * r).toFloat()),
                    if (deg % 90 == 0) 2.dp.toPx() else 1.dp.toPx(),
                )
            }
            val n = Path().apply {
                moveTo(c.x, c.y - r * 1.0f); lineTo(c.x - 6.dp.toPx(), c.y - r * 0.88f); lineTo(c.x + 6.dp.toPx(), c.y - r * 0.88f); close()
            }
            drawPath(n, accent)
        }
        rotate(sweep, c) {
            drawArc(
                Brush.sweepGradient(0f to Color.Transparent, 0.85f to Color.Transparent, 1f to accent.copy(alpha = 0.3f), center = c),
                0f, 360f, true, topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2),
            )
        }
        contacts.forEach { ct ->
            val rel = Math.toRadians(ct.bearingDeg - headingDeg - 90)
            val d = (ct.distanceKm / radiusKm).coerceIn(0.0, 1.0).toFloat()
            val p = Offset(c.x + (cos(rel) * r * d).toFloat(), c.y + (sin(rel) * r * d).toFloat())
            val col = AltitudeRamp.colorFor(ct.aircraft.altitudeFt)
            val since = ((sweep - (ct.bearingDeg - headingDeg).toFloat() + 720f) % 360f) / 360f
            val glow = (1f - since).coerceIn(0.35f, 1f)
            val track = (ct.aircraft.trackDeg ?: 0f) - headingDeg
            if (ct.aircraft.hex == highlight) {
                drawCircle(accent.copy(alpha = 0.25f), 16.dp.toPx(), p)
                drawCircle(accent, 16.dp.toPx(), p, style = Stroke(1.5.dp.toPx()))
            }
            drawCircle(col.copy(alpha = 0.25f * glow), 10.dp.toPx(), p)
            rotate(track, p) {
                val s = 7.dp.toPx()
                val plane = Path().apply {
                    moveTo(p.x, p.y - s); lineTo(p.x + s * 0.75f, p.y + s * 0.7f); lineTo(p.x, p.y + s * 0.35f); lineTo(p.x - s * 0.75f, p.y + s * 0.7f); close()
                }
                drawPath(plane, col.copy(alpha = glow))
            }
        }
        drawCircle(colors.textPrimary, 4.dp.toPx(), c)
        drawCircle(accent.copy(alpha = 0.35f), 10.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
    }
}

/** The plane nearest you right now, with where to look and when it'll be closest. */
@Composable
private fun ClosestCard(c: SkyContact, state: SkyUiState, actions: SkyActions) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val a = c.aircraft
    val tint = AltitudeRamp.colorFor(a.altitudeFt)
    val interaction = remember { MutableInteractionSource() }
    GlassSurface(
        Modifier.fillMaxWidth().pressScale(interaction).clickable(interaction, indication = null, role = Role.Button) { actions.onAircraft(a.hex) },
        shape = ContrailTheme.shapes.medium,
        glow = tint.copy(alpha = 0.7f),
    ) {
        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("Closest now", color = tint)
                    Text(a.displayCallsign, style = ContrailTheme.type.displaySmall, color = colors.textPrimary)
                    Text(
                        listOfNotNull(a.airline?.name, a.typeName ?: a.typeCode).joinToString(" · ").ifEmpty { "Unknown aircraft" },
                        style = ContrailTheme.type.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(String.format(Locale.US, "%.1f", c.distanceKm), style = ContrailTheme.type.numericLarge, color = tint)
                    Eyebrow("km ${compass(c.bearingDeg)}")
                }
            }
            Row(Modifier.fillMaxWidth()) {
                StatTile("Altitude", a.altitudeFt?.let { String.format(Locale.US, "%,d", it) } ?: "—", Modifier.weight(1f), unit = "ft")
                StatTile("Look up", String.format(Locale.US, "%.0f°", c.elevationDeg.coerceAtLeast(0.0)), Modifier.weight(1f))
                StatTile("Speed", a.groundSpeedKt?.toInt()?.toString() ?: "—", Modifier.weight(1f), unit = "kt")
            }
            c.approach?.let { ap ->
                Row(
                    Modifier.fillMaxWidth().clip(ContrailTheme.shapes.small).background(tint.copy(alpha = 0.1f)).padding(spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    Icon(ContrailIcons.Clock, null, tint = tint, modifier = Modifier.size(16.dp))
                    Text(
                        String.format(Locale.US, "Closest in %d:%02d, %.1f km from you", ap.inSeconds / 60, ap.inSeconds % 60, ap.minDistanceKm),
                        style = ContrailTheme.type.label, color = colors.textPrimary,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                if (a.rarity < Rarity.Common) RarityBadge(a.rarity.label, when (a.rarity) { Rarity.Legendary -> RarityPalette.Legendary; Rarity.Rare -> RarityPalette.Rare; else -> RarityPalette.Uncommon })
                if (a.military) RarityBadge("Military", listOf(colors.statusDelayed, colors.statusDelayed))
                if (a.isEmergency) RarityBadge("Emergency", listOf(colors.emergency, colors.emergency))
                if (state.watchlist.matches(a)) RarityBadge("Watchlist", listOf(colors.passport, colors.passport))
            }
        }
    }
}

@Composable
private fun SkyViewCta(onOpen: () -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val interaction = remember { MutableInteractionSource() }
    GlassSurface(
        Modifier.fillMaxWidth().pressScale(interaction).clickable(interaction, indication = null, role = Role.Button, onClick = onOpen),
        shape = ContrailTheme.shapes.medium,
        glow = colors.accent.copy(alpha = 0.6f),
    ) {
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(colors.accent.copy(alpha = 0.18f), Color.Transparent))))
        Row(Modifier.padding(spacing.lg), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.lg)) {
            Box(Modifier.size(48.dp).clip(ContrailTheme.shapes.small).background(colors.accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(ContrailIcons.Camera, null, tint = colors.accent, modifier = Modifier.size(ContrailTheme.sizes.icon))
            }
            Column(Modifier.weight(1f)) {
                Text("Sky View", style = ContrailTheme.type.headline, color = colors.textPrimary)
                Text("Point your camera at a plane and Contrail labels it.", style = ContrailTheme.type.bodySmall, color = colors.textSecondary)
            }
            Icon(ContrailIcons.ChevronRight, null, tint = colors.accent, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun OverheadList(contacts: List<SkyContact>, headingDeg: Float, onOpen: (String) -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    SectionCard("Also overhead") {
        contacts.forEach { c ->
            val a = c.aircraft
            val tint = AltitudeRamp.colorFor(a.altitudeFt)
            Row(
                Modifier.fillMaxWidth().clip(ContrailTheme.shapes.small).clickable(role = Role.Button) { onOpen(a.hex) }.padding(vertical = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Icon(
                    ContrailIcons.ArrowRight, compass(c.bearingDeg), tint = tint,
                    modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = (c.bearingDeg - headingDeg - 90).toFloat() },
                )
                Column(Modifier.weight(1f)) {
                    Text(a.displayCallsign, style = ContrailTheme.type.title, color = colors.textPrimary, maxLines = 1)
                    Text(listOfNotNull(a.typeName ?: a.typeCode, a.airline?.name).joinToString(" · ").ifEmpty { "—" }, style = ContrailTheme.type.bodySmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(String.format(Locale.US, "%.1f km", c.distanceKm), style = ContrailTheme.type.numeric, color = colors.textPrimary)
                    Text(a.altitudeFt?.let { String.format(Locale.US, "%,d ft", it) } ?: "—", style = ContrailTheme.type.numericSmall, color = tint)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlertsCard(rules: AlertRules, onChange: (AlertRules) -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val gold = colors.statusDelayed
    SectionCard("Proximity alerts", trailing = {
        ContrailChip(if (rules.enabled) "On" else "Off", rules.enabled, onClick = { onChange(rules.copy(enabled = !rules.enabled)) }, accent = gold, leading = {
            Icon(ContrailIcons.Bell, null, tint = if (rules.enabled) gold else colors.textTertiary, modifier = Modifier.size(14.dp))
        })
    }) {
        Text(
            "A notification when something worth looking up passes near the last place you opened Sky. Checked about every 15 minutes.",
            style = ContrailTheme.type.bodySmall, color = colors.textTertiary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            ContrailChip("Rare types", rules.rare, onClick = { onChange(rules.copy(rare = !rules.rare)) }, accent = gold)
            ContrailChip("Military", rules.military, onClick = { onChange(rules.copy(military = !rules.military)) }, accent = gold)
            ContrailChip("Emergencies", rules.emergency, onClick = { onChange(rules.copy(emergency = !rules.emergency)) }, accent = gold)
            ContrailChip("Watchlist", rules.watchlist, onClick = { onChange(rules.copy(watchlist = !rules.watchlist)) }, accent = gold)
            ContrailChip("Below 3,000 ft", rules.lowerThanFt != null, onClick = { onChange(rules.copy(lowerThanFt = if (rules.lowerThanFt == null) 3_000 else null)) }, accent = gold)
        }
    }
}

fun compass(b: Double): String = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[((((b % 360) + 360 + 22.5) % 360) / 45).toInt()]

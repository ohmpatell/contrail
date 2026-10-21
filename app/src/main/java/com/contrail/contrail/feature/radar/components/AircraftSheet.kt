package com.contrail.contrail.feature.radar.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import coil3.compose.AsyncImage
import com.contrail.contrail.core.data.model.AircraftPhoto
import com.contrail.contrail.core.data.model.FlightRoute
import com.contrail.contrail.core.data.model.LiveAircraft
import com.contrail.contrail.core.data.reference.Rarity
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.GlassIconButton
import com.contrail.contrail.core.designsystem.component.GlassLevel
import com.contrail.contrail.core.designsystem.component.GlassSurface
import com.contrail.contrail.core.designsystem.component.RarityBadge
import com.contrail.contrail.core.designsystem.component.Sparkline
import com.contrail.contrail.core.designsystem.component.SplitFlapText
import com.contrail.contrail.core.designsystem.component.StatTile
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.AltitudeRamp
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.core.designsystem.theme.RarityPalette
import com.contrail.contrail.feature.radar.Lookup
import com.contrail.contrail.feature.radar.RadarFormat
import com.contrail.contrail.feature.radar.SelectedAircraft
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class SheetAnchor { Expanded, Peek, Hidden }

/**
 * The radar's aircraft sheet. Rises to a peek when a plane is tapped, drags up to reveal
 * everything, and flicks down to dismiss. The photo grows with the sheet.
 */
@Composable
fun AircraftSheet(
    selected: SelectedAircraft?,
    following: Boolean,
    onFollow: () -> Unit,
    onDismiss: () -> Unit,
    onVisibleHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    startExpanded: Boolean = false,
) {
    // Keep showing the last aircraft while the sheet animates away.
    var shown by remember { mutableStateOf(selected) }
    if (selected != null) shown = selected

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val motion = ContrailTheme.motion
        val sheetHeight = minOf(maxHeight * 0.9f, 780.dp)
        val peekHeight = minOf(PEEK_HEIGHT, sheetHeight)
        val sheetPx = with(density) { sheetHeight.toPx() }
        val peekOffset = with(density) { (sheetHeight - peekHeight).toPx() }
        val hiddenOffset = sheetPx + with(density) { 24.dp.toPx() }
        fun anchorOffset(a: SheetAnchor) = when (a) {
            SheetAnchor.Expanded -> 0f
            SheetAnchor.Peek -> peekOffset
            SheetAnchor.Hidden -> hiddenOffset
        }

        val offset = remember { Animatable(hiddenOffset) }
        val scope = rememberCoroutineScope()
        LaunchedEffect(selected?.aircraft?.hex) {
            val target = if (selected == null) SheetAnchor.Hidden else if (startExpanded) SheetAnchor.Expanded else SheetAnchor.Peek
            if (selected != null && offset.value >= hiddenOffset - 1f) offset.snapTo(hiddenOffset)
            offset.animateTo(anchorOffset(target), motion.gentle())
        }
        LaunchedEffect(sheetPx) {
            snapshotFlow { offset.value }.collect { v -> onVisibleHeightChanged((sheetPx - v).coerceAtLeast(0f).roundToInt()) }
        }

        val dragState = rememberDraggableState { delta ->
            scope.launch { offset.snapTo((offset.value + delta).coerceIn(0f, hiddenOffset)) }
        }
        val progress = (1f - offset.value / peekOffset.coerceAtLeast(1f)).coerceIn(0f, 1f)
        val current = shown ?: return@BoxWithConstraints

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sheetHeight)
                .offset { IntOffset(0, offset.value.roundToInt()) }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity ->
                        val v = offset.value
                        val target = when {
                            velocity > FLING_VELOCITY && v > peekOffset * 0.6f -> SheetAnchor.Hidden
                            velocity > FLING_VELOCITY -> SheetAnchor.Peek
                            velocity < -FLING_VELOCITY -> SheetAnchor.Expanded
                            v > peekOffset + (hiddenOffset - peekOffset) * 0.35f -> SheetAnchor.Hidden
                            v > peekOffset / 2 -> SheetAnchor.Peek
                            else -> SheetAnchor.Expanded
                        }
                        if (target == SheetAnchor.Hidden) onDismiss()
                        offset.animateTo(anchorOffset(target), motion.gentle(), initialVelocity = velocity)
                    },
                ),
        ) {
            SheetContent(current, progress, following, onFollow, onDismiss, expand = {
                scope.launch { offset.animateTo(anchorOffset(if (progress > 0.5f) SheetAnchor.Peek else SheetAnchor.Expanded), motion.gentle()) }
            })
        }
    }
}

@Composable
private fun SheetContent(
    selected: SelectedAircraft,
    progress: Float,
    following: Boolean,
    onFollow: () -> Unit,
    onDismiss: () -> Unit,
    expand: () -> Unit,
) {
    val a = selected.aircraft
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val accent = a.airline?.let { Color(it.accentArgb) } ?: colors.accent
    GlassSurface(
        Modifier.fillMaxSize(),
        shape = ContrailTheme.shapes.sheet,
        level = GlassLevel.Strong,
        glow = accent,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = spacing.lg),
        ) {
            // Drag handle
            Box(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = expand, role = Role.Button)
                    .padding(vertical = spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(width = 40.dp, height = 4.dp).clip(ContrailTheme.shapes.chip).background(colors.textTertiary.copy(alpha = 0.6f)))
            }
            PhotoHero(selected.photo, a, accent, progress)
            Spacer(Modifier.height(spacing.md))
            HeaderRow(a, accent, following, onFollow, onDismiss)
            Spacer(Modifier.height(spacing.md))
            RouteRow(selected.route, accent)
            Spacer(Modifier.height(spacing.lg))
            StatsRow(a)
            // Everything below reveals as the sheet is pulled up.
            Column(
                Modifier
                    .padding(top = spacing.lg)
                    .graphicsLayer {
                        alpha = ((progress - 0.25f) / 0.75f).coerceIn(0f, 1f)
                        translationY = (1f - progress) * 24.dp.toPx()
                    },
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                VerticalSpeedCard(selected.verticalRates, a)
                DetailsGrid(a)
            }
        }
    }
}

@Composable
private fun PhotoHero(photo: Lookup<AircraftPhoto>, a: LiveAircraft, accent: Color, progress: Float) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val height = lerp(PHOTO_PEEK, PHOTO_EXPANDED, progress)
    val uri = LocalUriHandler.current
    val motion = ContrailTheme.motion
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(ContrailTheme.shapes.medium)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.32f), colors.canvasSunken))),
    ) {
        AnimatedContent(
            targetState = photo,
            transitionSpec = { fadeIn(motion.fade(400)) togetherWith fadeOut(motion.fade(200)) },
            contentKey = { it::class },
            label = "photo",
        ) { state ->
            when (state) {
                is Lookup.Found -> Box(Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = state.value.largeUrl,
                        contentDescription = "Photo of ${a.registration ?: a.displayCallsign}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to colors.canvasSunken.copy(alpha = 0.85f))))
                    // Planespotters requires the photographer's credit and a link back.
                    Row(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(spacing.sm)
                            .clip(ContrailTheme.shapes.chip)
                            .background(colors.scrim)
                            .clickable(role = Role.Button) { uri.openUri(state.value.pageUrl) }
                            .padding(horizontal = spacing.sm, vertical = spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        Text("© ${state.value.photographer} · Planespotters.net", style = ContrailTheme.type.bodySmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(ContrailIcons.ExternalLink, "Open on Planespotters", tint = colors.textSecondary, modifier = Modifier.size(12.dp))
                    }
                }
                else -> SilhouettePlaceholder(a, accent, loading = state is Lookup.Loading)
            }
        }
    }
}

/** No photo yet: the aircraft is still the hero, as a glowing silhouette in the airline's colour. */
@Composable
private fun SilhouettePlaceholder(a: LiveAircraft, accent: Color, loading: Boolean) {
    val colors = ContrailTheme.colors
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent))))
        Icon(
            ContrailIcons.Plane,
            contentDescription = null,
            tint = AltitudeRamp.colorFor(if (a.onGround) null else a.altitudeFt).copy(alpha = 0.9f),
            modifier = Modifier.align(Alignment.Center).size(72.dp).graphicsLayer { rotationZ = 90f },
        )
        Text(
            if (loading) "Finding a photo…" else "No photo of this airframe yet",
            style = ContrailTheme.type.bodySmall,
            color = colors.textTertiary,
            modifier = Modifier.align(Alignment.BottomStart).padding(ContrailTheme.spacing.md),
        )
    }
}

@Composable
private fun HeaderRow(a: LiveAircraft, accent: Color, following: Boolean, onFollow: () -> Unit, onDismiss: () -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs + spacing.xxs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                SplitFlapText(a.displayCallsign.take(8), style = ContrailTheme.type.flap, haptics = true)
                when (a.rarity) {
                    Rarity.Legendary -> RarityBadge("Legendary", RarityPalette.Legendary)
                    Rarity.Rare -> RarityBadge("Rare", RarityPalette.Rare)
                    Rarity.Uncommon -> RarityBadge("Uncommon", RarityPalette.Uncommon)
                    Rarity.Common -> Unit
                }
            }
            val subtitle = listOfNotNull(a.airline?.name, a.typeName).joinToString(" · ").ifEmpty { a.registration ?: "Unknown aircraft" }
            Text(subtitle, style = ContrailTheme.type.body, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (a.isEmergency) {
                Text(
                    "${LiveAircraft.emergencyLabel(a.squawk) ?: "Emergency"} · squawk ${RadarFormat.squawk(a)}",
                    style = ContrailTheme.type.label,
                    color = colors.emergency,
                )
            } else if (a.military) {
                Eyebrow("Military", color = accent)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            GlassIconButton(
                ContrailIcons.Follow,
                if (following) "Stop following" else "Follow this aircraft",
                onFollow,
                active = following,
                size = ContrailTheme.sizes.touchTarget - spacing.xs,
            )
            GlassIconButton(ContrailIcons.Close, "Close", onDismiss, size = ContrailTheme.sizes.touchTarget - spacing.xs)
        }
    }
}

@Composable
private fun RouteRow(route: Lookup<FlightRoute>, accent: Color) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    when (route) {
        is Lookup.Found -> Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AirportCode(route.value.origin.code, route.value.origin.city ?: route.value.origin.name, Modifier.weight(1f))
                RouteLine(accent, Modifier.weight(1f))
                AirportCode(route.value.destination.code, route.value.destination.city ?: route.value.destination.name, Modifier.weight(1f), end = true)
            }
            Eyebrow("Route from callsign", color = colors.textTertiary)
        }
        Lookup.Loading -> Text("Looking up route…", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
        Lookup.Missing -> Text("Route unknown for this callsign", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
    }
}

@Composable
private fun AirportCode(code: String, city: String, modifier: Modifier, end: Boolean = false) {
    Column(modifier, horizontalAlignment = if (end) Alignment.End else Alignment.Start) {
        Text(code, style = ContrailTheme.type.displayMedium, color = ContrailTheme.colors.textPrimary)
        Text(city, style = ContrailTheme.type.bodySmall, color = ContrailTheme.colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RouteLine(accent: Color, modifier: Modifier) {
    Box(modifier.height(28.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.1f), accent, accent.copy(alpha = 0.1f)))),
        )
        Icon(ContrailIcons.Plane, null, tint = accent, modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = 90f })
    }
}

@Composable
private fun StatsRow(a: LiveAircraft) {
    val colors = ContrailTheme.colors
    val alt = RadarFormat.altitude(a)
    val gs = RadarFormat.speed(a)
    val vr = RadarFormat.verticalRate(a.verticalRateFpm)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        StatTile("Altitude", alt.value, unit = alt.unit, valueColor = AltitudeRamp.colorFor(if (a.onGround) null else a.altitudeFt))
        StatTile("Speed", gs.value, unit = gs.unit)
        StatTile(
            "V/S", vr.value, unit = vr.unit,
            valueColor = when {
                (a.verticalRateFpm ?: 0) > 64 -> colors.statusOnTime
                (a.verticalRateFpm ?: 0) < -64 -> colors.statusAirborne
                else -> colors.textPrimary
            },
        )
        StatTile("Squawk", RadarFormat.squawk(a), valueColor = if (a.isEmergency) colors.emergency else colors.textPrimary)
    }
}

@Composable
private fun VerticalSpeedCard(rates: List<Float>, a: LiveAircraft) {
    val spacing = ContrailTheme.spacing
    GlassSurface(Modifier.fillMaxWidth(), shape = ContrailTheme.shapes.medium) {
        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row {
                Eyebrow("Vertical speed · last 4 min", Modifier.weight(1f))
                val vr = RadarFormat.verticalRate(a.verticalRateFpm)
                Text("${vr.value} ${vr.unit.orEmpty()}", style = ContrailTheme.type.numericSmall, color = ContrailTheme.colors.textSecondary)
            }
            Sparkline(
                values = rates.takeLast(60),
                color = AltitudeRamp.colorFor(if (a.onGround) null else a.altitudeFt),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            )
        }
    }
}

@Composable
private fun DetailsGrid(a: LiveAircraft) {
    val spacing = ContrailTheme.spacing
    val heading = RadarFormat.heading(a)
    val rows = listOf(
        listOf("Registration" to (a.registration ?: "—"), "Type" to (a.typeCode ?: "—"), "Hex" to a.hex.uppercase()),
        listOf(
            "Track" to (heading.value + heading.unit.orEmpty()),
            "Flight level" to (RadarFormat.flightLevel(a.altitudeFt) ?: "—"),
            "Source" to a.source,
        ),
    )
    GlassSurface(Modifier.fillMaxWidth(), shape = ContrailTheme.shapes.medium) {
        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { (label, value) -> StatTile(label, value, Modifier.weight(1f)) }
                }
            }
        }
    }
}

private val PEEK_HEIGHT = 372.dp
private val PHOTO_PEEK = 112.dp
private val PHOTO_EXPANDED = 184.dp
private const val FLING_VELOCITY = 1_400f

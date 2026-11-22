package com.contrail.contrail.feature.radar.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineStart
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
import androidx.compose.foundation.layout.widthIn
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
import com.contrail.contrail.core.data.model.WatchItem
import com.contrail.contrail.core.data.model.WatchKind
import com.contrail.contrail.core.data.model.Watchlist
import com.contrail.contrail.core.designsystem.component.LiveLogo
import com.contrail.contrail.core.designsystem.livery.Liveries
import com.contrail.contrail.core.designsystem.livery.LiveryRender
import com.contrail.contrail.core.designsystem.livery.Profiles
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
import com.contrail.contrail.core.data.route.RouteVerdict
import com.contrail.contrail.feature.radar.Lookup
import com.contrail.contrail.feature.radar.RadarFormat
import com.contrail.contrail.feature.radar.SelectedAircraft
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class SheetAnchor { Expanded, Peek, Hidden }

/** How much of the sheet shows when a plane is first tapped on a phone. The radar frames the plane above it. */
val AircraftSheetPeek = 372.dp

/**
 * The radar's aircraft sheet. Rises to a peek when a plane is tapped, drags up to reveal
 * everything, and flicks down to dismiss. The photo grows with the sheet. Once fully open its
 * content scrolls, and dragging down from the top lowers the sheet again.
 *
 * As a [panel] (wide screens) it fills its frame, opens fully and doesn't drag.
 */
@Composable
fun AircraftSheet(
    selected: SelectedAircraft?,
    following: Boolean,
    onFollow: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    panel: Boolean = false,
    startExpanded: Boolean = false,
    watchlist: List<WatchItem> = emptyList(),
    onWatch: (WatchItem) -> Unit = {},
    onUnwatch: (WatchKind, String) -> Unit = { _, _ -> },
    onOpenAircraft: (String) -> Unit = {},
    pinned: Boolean = false,
    onTogglePin: () -> Unit = {},
) {
    // Keep showing the last aircraft while the sheet animates away.
    var shown by remember { mutableStateOf(selected) }
    if (selected != null) shown = selected

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val motion = ContrailTheme.motion
        val sheetHeight = if (panel) maxHeight else minOf(maxHeight * 0.9f, 780.dp)
        val peekHeight = minOf(AircraftSheetPeek, sheetHeight)
        val sheetPx = with(density) { sheetHeight.toPx() }
        val peekOffset = with(density) { (sheetHeight - peekHeight).toPx() }
        val hiddenOffset = sheetPx + with(density) { 24.dp.toPx() }
        fun anchorOffset(a: SheetAnchor) = when (a) {
            SheetAnchor.Expanded -> 0f
            SheetAnchor.Peek -> peekOffset
            SheetAnchor.Hidden -> hiddenOffset
        }

        val offset = remember { Animatable(hiddenOffset) }
        val scroll = rememberScrollState()
        val scope = rememberCoroutineScope()
        LaunchedEffect(selected?.aircraft?.hex) {
            val target = if (selected == null) SheetAnchor.Hidden else if (startExpanded || panel) SheetAnchor.Expanded else SheetAnchor.Peek
            if (selected != null && offset.value >= hiddenOffset - 1f) offset.snapTo(hiddenOffset)
            if (selected != null) scroll.scrollTo(0)
            offset.animateTo(anchorOffset(target), motion.gentle())
        }

        // Moves the sheet immediately, so the next scroll delta sees the new position.
        fun moveBy(delta: Float) {
            scope.launch(start = CoroutineStart.UNDISPATCHED) { offset.snapTo((offset.value + delta).coerceIn(0f, hiddenOffset)) }
        }
        fun settle(velocity: Float) {
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
            scope.launch { offset.animateTo(anchorOffset(target), motion.gentle(), initialVelocity = velocity) }
        }
        val connection = remember(panel, peekOffset, hiddenOffset) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    // Finger moving up while the sheet isn't fully open: open the sheet first.
                    if (panel || source != NestedScrollSource.UserInput || available.y >= 0f || offset.value <= 0f) return Offset.Zero
                    val take = maxOf(available.y, -offset.value)
                    moveBy(take)
                    return Offset(0f, take)
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    // Content is at its top and the finger keeps going down: lower the sheet.
                    if (panel || source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                    moveBy(available.y)
                    return Offset(0f, available.y)
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    // Fully open: the content flings. Anywhere else: the sheet settles.
                    if (panel || offset.value <= 0.5f) return Velocity.Zero
                    settle(available.y)
                    return available
                }
            }
        }
        val progress = (1f - offset.value / peekOffset.coerceAtLeast(1f)).coerceIn(0f, 1f)
        val current = shown ?: return@BoxWithConstraints

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sheetHeight)
                .offset { IntOffset(0, offset.value.roundToInt()) }
                .nestedScroll(connection),
        ) {
            SheetContent(current, progress, following, onFollow, onDismiss, Watchlist(watchlist), onWatch, onUnwatch, onOpenAircraft, scroll, pinned, onTogglePin, expand = {
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
    watchlist: Watchlist,
    onWatch: (WatchItem) -> Unit,
    onUnwatch: (WatchKind, String) -> Unit,
    onOpenAircraft: (String) -> Unit,
    scroll: ScrollState,
    pinned: Boolean,
    onTogglePin: () -> Unit,
    expand: () -> Unit,
) {
    val a = selected.aircraft
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val accent = a.airline?.let { Color(it.accentArgb) } ?: colors.accent
    // Everything below the stats reveals as the sheet is pulled up.
    val reveal = ((progress - 0.25f) / 0.75f).coerceIn(0f, 1f)
    GlassSurface(
        Modifier.fillMaxSize(),
        shape = ContrailTheme.shapes.sheet,
        level = GlassLevel.Strong,
        glow = accent,
    ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(horizontal = spacing.lg),
            ) {
                // Drag handle
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = expand, role = Role.Button, onClickLabel = "Expand or collapse")
                        .padding(vertical = spacing.sm),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(width = 40.dp, height = 4.dp).clip(ContrailTheme.shapes.chip).background(colors.textTertiary.copy(alpha = 0.6f)))
                }
                Box(Modifier.clickable(role = Role.Button, onClickLabel = "Open the aircraft page") { onOpenAircraft(a.hex) }) {
                    PhotoHero(selected.photo, a, accent, progress, selected.tailArtUrl, selected.brandColorHex)
                }
                Spacer(Modifier.height(spacing.md))
                HeaderRow(a, accent, following, onFollow, onDismiss, selected.airlineLogoUrl, pinned, onTogglePin)
                Spacer(Modifier.height(spacing.md))
                RouteRow(selected.route, selected.verdict, accent)
                Spacer(Modifier.height(spacing.lg))
                StatsRow(a)
                Column(
                    Modifier
                        .padding(top = spacing.lg, bottom = spacing.lg)
                        .graphicsLayer {
                            alpha = reveal
                            translationY = (1f - progress) * 24.dp.toPx()
                        },
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    VerticalSpeedCard(selected.verticalRates, a)
                    DetailsGrid(a)
                    WatchRow(a, accent, watchlist, onWatch, onUnwatch)
                }
            }
            // Pinned to the bottom, so it's always reachable however tall the content gets.
            OpenAircraftButton(
                accent,
                Modifier
                    .padding(horizontal = spacing.lg, vertical = spacing.md)
                    .graphicsLayer { alpha = reveal },
            ) { onOpenAircraft(a.hex) }
        }
    }
}

@Composable
private fun PhotoHero(photo: Lookup<AircraftPhoto>, a: LiveAircraft, accent: Color, progress: Float, tailArtUrl: String?, brandColorHex: String?) {
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
                else -> SilhouettePlaceholder(a, accent, loading = state is Lookup.Loading, tailArtUrl = tailArtUrl, brandColorHex = brandColorHex)
            }
        }
    }
}

/** No photo yet: the aircraft is still the hero, as a floating livery render of its type. */
@Composable
private fun SilhouettePlaceholder(a: LiveAircraft, accent: Color, loading: Boolean, tailArtUrl: String?, brandColorHex: String?) {
    val colors = ContrailTheme.colors
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent))))
        LiveryRender(
            profile = Profiles.forType(a.typeCode, a.category),
            livery = Liveries.forAirline(a.airline?.icao, brandColorHex, a.airline?.accentArgb),
            tailArtUrl = tailArtUrl,
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.82f),
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
private fun HeaderRow(
    a: LiveAircraft,
    accent: Color,
    following: Boolean,
    onFollow: () -> Unit,
    onDismiss: () -> Unit,
    logoUrl: String?,
    pinned: Boolean,
    onTogglePin: () -> Unit,
) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs + spacing.xxs)) {
            SplitFlapText(a.displayCallsign.take(8), style = ContrailTheme.type.flap, haptics = true)
            // Logo, type and rarity share one level line under the callsign.
            Row(Modifier.height(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                logoUrl?.let { LiveLogo(it, a.airline?.name, Modifier.height(14.dp).widthIn(max = 72.dp), alignment = Alignment.CenterStart) }
                val subtitle = (if (logoUrl != null) a.typeName else listOfNotNull(a.airline?.name, a.typeName).joinToString(" · ").ifEmpty { null })
                    ?: a.registration ?: "Unknown aircraft"
                Text(subtitle, style = ContrailTheme.type.body, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                when (a.rarity) {
                    Rarity.Legendary -> RarityBadge("Legendary", RarityPalette.Legendary)
                    Rarity.Rare -> RarityBadge("Rare", RarityPalette.Rare)
                    Rarity.Uncommon -> RarityBadge("Uncommon", RarityPalette.Uncommon)
                    Rarity.Common -> Unit
                }
            }
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
            // Pinning needs a registration: that's what a plane is known by in your Hangar.
            if (a.registration != null) {
                PinButton(pinned, onTogglePin, size = ContrailTheme.sizes.touchTarget - spacing.xs)
            }
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
private fun RouteRow(route: Lookup<FlightRoute>, verdict: RouteVerdict?, accent: Color) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val small = ContrailTheme.type.bodySmall
    fun claim(r: FlightRoute) = r.airports.joinToString(" → ") { it.code }
    when {
        route is Lookup.Loading || verdict == null -> Text("Looking up route…", style = small, color = colors.textTertiary)
        verdict is RouteVerdict.OnRoute -> Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AirportCode(verdict.from.code, verdict.from.city ?: verdict.from.name, Modifier.weight(1f))
                RouteLine(accent, Modifier.weight(1f))
                AirportCode(verdict.to.code, verdict.to.city ?: verdict.to.name, Modifier.weight(1f), end = true)
            }
            Eyebrow(
                if (verdict.multiStop) "Route from callsign · leg ${verdict.leg + 1} of ${verdict.stops.size - 1} · ${verdict.stops.joinToString(" → ") { it.code }}" else "Route from callsign",
                color = colors.textTertiary,
            )
        }
        verdict is RouteVerdict.Landing -> Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AirportCode("···", "From somewhere", Modifier.weight(1f), dim = true)
                RouteLine(accent, Modifier.weight(1f))
                AirportCode(verdict.at.code, verdict.at.city ?: verdict.at.name, Modifier.weight(1f), end = true)
            }
            Eyebrow("Landing · from its approach · estimated", color = accent)
            verdict.claimed?.let { Text("Its callsign says ${claim(it)}, which doesn't fit how it's flying.", style = small, color = colors.textTertiary) }
        }
        verdict is RouteVerdict.Departing -> Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AirportCode(verdict.from.code, verdict.from.city ?: verdict.from.name, Modifier.weight(1f))
                RouteLine(accent, Modifier.weight(1f))
                AirportCode("···", "Somewhere", Modifier.weight(1f), end = true, dim = true)
            }
            Eyebrow("Departed · from its climb · estimated", color = accent)
            verdict.claimed?.let { Text("Its callsign says ${claim(it)}, which doesn't fit how it's flying.", style = small, color = colors.textTertiary) }
        }
        verdict is RouteVerdict.Mismatch -> Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text("Route unknown", style = ContrailTheme.type.title, color = colors.textPrimary)
            Text("Its callsign says ${claim(verdict.claimed)}, but it isn't flying that way, so that route isn't shown.", style = small, color = colors.textTertiary)
        }
        else -> Text("Route unknown for this callsign", style = small, color = colors.textTertiary)
    }
}

@Composable
private fun AirportCode(code: String, city: String, modifier: Modifier, end: Boolean = false, dim: Boolean = false) {
    Column(modifier, horizontalAlignment = if (end) Alignment.End else Alignment.Start) {
        Text(code, style = ContrailTheme.type.displayMedium, color = if (dim) ContrailTheme.colors.textTertiary else ContrailTheme.colors.textPrimary)
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

/** Pin to My Hangar: a gold star once pinned. */
@Composable
private fun PinButton(pinned: Boolean, onToggle: () -> Unit, size: androidx.compose.ui.unit.Dp) {
    val gold = RarityPalette.Legendary.first()
    Box(contentAlignment = Alignment.Center) {
        GlassIconButton(
            if (pinned) ContrailIcons.StarFilled else ContrailIcons.Star,
            if (pinned) "Remove from My Hangar" else "Pin to My Hangar",
            onToggle,
            size = size,
        )
        if (pinned) {
            Icon(ContrailIcons.StarFilled, null, tint = gold, modifier = Modifier.size(ContrailTheme.sizes.iconSmall))
        }
    }
}

/** Three clear toggles: this tail, this type, this airline. */
@Composable
private fun WatchRow(a: LiveAircraft, accent: Color, watchlist: Watchlist, onWatch: (WatchItem) -> Unit, onUnwatch: (WatchKind, String) -> Unit) {
    val spacing = ContrailTheme.spacing
    val options = buildList {
        a.registration?.let { add("Tail" to WatchItem(WatchKind.Tail, it, it)) }
        a.typeCode?.let { add("Type" to WatchItem(WatchKind.Type, it, a.typeName ?: it)) }
        a.airline?.let { add("Airline" to WatchItem(WatchKind.Airline, it.icao, it.name)) }
    }
    if (options.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Watch for", Modifier.weight(1f))
            Text("A ring on the map, and alerts", style = ContrailTheme.type.bodySmall, color = ContrailTheme.colors.textTertiary)
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            options.forEach { (kind, item) ->
                val on = watchlist.contains(item.kind, item.value)
                WatchTile(kind, item.label, on, accent, Modifier.weight(1f).fillMaxHeight()) {
                    if (on) onUnwatch(item.kind, item.value) else onWatch(item)
                }
            }
        }
    }
}

@Composable
private fun WatchTile(kind: String, value: String, on: Boolean, accent: Color, modifier: Modifier, onToggle: () -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val motion = ContrailTheme.motion
    val fill by animateColorAsState(if (on) accent.copy(alpha = 0.20f) else colors.glassTint.copy(alpha = 0.06f), motion.fade(), label = "fill")
    val edge by animateColorAsState(if (on) accent else colors.hairline, motion.fade(), label = "edge")
    Column(
        modifier
            .clip(ContrailTheme.shapes.medium)
            .background(fill)
            .border(ContrailTheme.sizes.hairline, edge, ContrailTheme.shapes.medium)
            .toggleable(value = on, role = Role.Switch, onValueChange = { onToggle() })
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(kind, Modifier.weight(1f), color = if (on) accent else colors.textTertiary)
            Icon(
                if (on) ContrailIcons.Check else ContrailIcons.Plus,
                null,
                tint = if (on) accent else colors.textTertiary,
                modifier = Modifier.size(ContrailTheme.sizes.iconSmall),
            )
        }
        Text(value, style = ContrailTheme.type.title, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun OpenAircraftButton(accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = ContrailTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(ContrailTheme.sizes.touchTarget)
            .clip(ContrailTheme.shapes.chip)
            .background(accent)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Aircraft page", style = ContrailTheme.type.title, color = colors.onAccent)
        Spacer(Modifier.width(ContrailTheme.spacing.sm))
        Icon(ContrailIcons.ArrowRight, null, tint = colors.onAccent, modifier = Modifier.size(ContrailTheme.sizes.iconSmall))
    }
}

private val PHOTO_PEEK = 112.dp
private val PHOTO_EXPANDED = 184.dp
private const val FLING_VELOCITY = 1_400f

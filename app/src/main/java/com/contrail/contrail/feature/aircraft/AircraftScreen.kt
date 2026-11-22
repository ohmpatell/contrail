package com.contrail.contrail.feature.aircraft

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import com.contrail.contrail.core.data.repository.CutoutStatus
import com.contrail.contrail.core.data.model.AirportRef
import com.contrail.contrail.core.data.route.RouteVerdict
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.contrail.contrail.core.data.model.WatchItem
import com.contrail.contrail.core.data.reference.Rarity
import com.contrail.contrail.core.designsystem.component.ContrailChip
import com.contrail.contrail.core.designsystem.component.DetailScaffold
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.LiveLogo
import com.contrail.contrail.core.designsystem.component.SectionCard
import com.contrail.contrail.core.designsystem.component.StatTile
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.livery.Liveries
import com.contrail.contrail.core.designsystem.livery.LiveryRender
import com.contrail.contrail.core.designsystem.livery.Profiles
import com.contrail.contrail.core.designsystem.theme.AltitudeRamp
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.core.designsystem.theme.RarityPalette
import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.feature.radar.RadarFormat
import com.contrail.contrail.navigation.LocalNavigator

@Composable
fun AircraftRoute(hex: String) {
    val viewModel = hiltViewModel<AircraftViewModel, AircraftViewModel.Factory>(key = "aircraft-$hex") { it.create(hex) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    AircraftScreen(
        state = state,
        onBack = nav::back,
        onHero = viewModel::showHero,
        onToggleWatch = viewModel::toggleWatch,
        onOpenAirport = { iata -> nav.openAirport(iata) },
    )
}

@Composable
fun AircraftScreen(
    state: AircraftUiState,
    onBack: () -> Unit,
    onHero: (HeroMode) -> Unit,
    onToggleWatch: (WatchItem, Boolean) -> Unit,
    onOpenAirport: (String) -> Unit,
) {
    val colors = ContrailTheme.colors
    val accent = Airlines.byIcao(state.airlineIcao)?.let { Color(it.accentArgb) } ?: colors.accent
    DetailScaffold(
        title = state.registration ?: state.callsign ?: state.hex.uppercase(),
        eyebrow = listOfNotNull(state.airlineName, state.typeName).joinToString(" · ").ifBlank { null },
        accent = accent,
        onBack = onBack,
        hero = { Hero(state, accent, onHero) },
    ) {
        IdentityRow(state)
        RouteSection(state, onOpenAirport)
        SectionCard("Airframe") {
            Row(Modifier.fillMaxWidth()) {
                StatTile("Registration", state.registration ?: "—", Modifier.weight(1f))
                StatTile("Type", state.typeCode ?: "—", Modifier.weight(1f))
                StatTile("Hex", state.hex.uppercase(), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                StatTile("Operator", state.airlineName ?: "—", Modifier.weight(2f))
                StatTile("Rarity", state.rarity.label, Modifier.weight(1f), valueColor = rarityColor(state.rarity))
            }
            state.typeName?.let { Text(it, style = ContrailTheme.type.bodySmall, color = colors.textTertiary) }
        }
        SightingsCard(state)
        WatchCard(state, onToggleWatch)
    }
}

@Composable
private fun Hero(state: AircraftUiState, accent: Color, onHero: (HeroMode) -> Unit) {
    val spacing = ContrailTheme.spacing
    val colors = ContrailTheme.colors
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxWidth().padding(horizontal = spacing.gutter), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.55f)
                .clip(ContrailTheme.shapes.large)
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.35f), colors.canvasSunken))),
            contentAlignment = Alignment.Center,
        ) {
            // The real photo, blurred into a backdrop, so the floating plane sits in its own sky.
            state.photo?.let {
                AsyncImage(
                    it.largeUrl, null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().then(if (state.hero == HeroMode.Photo) Modifier else Modifier.blur(28.dp)).graphicsLayer { alpha = if (state.hero == HeroMode.Photo) 1f else 0.55f },
                )
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, colors.canvasSunken.copy(alpha = 0.6f)))))
            AnimatedContent(
                targetState = state.hero,
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.94f)) togetherWith fadeOut() },
                label = "hero",
            ) { mode ->
                when (mode) {
                    HeroMode.Livery -> LiveryRender(
                        profile = Profiles.forType(state.typeCode, state.live?.category),
                        livery = Liveries.forAirline(state.airlineIcao, state.airline?.colors, Airlines.byIcao(state.airlineIcao)?.accentArgb),
                        tailArtUrl = state.airline?.logo?.tail,
                        modifier = Modifier.padding(horizontal = spacing.md),
                    )
                    HeroMode.Cutout -> state.cutout?.let { file ->
                        AsyncImage(file, "Cutout of ${state.registration}", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(spacing.lg))
                    } ?: CutoutProgress(state.cutoutStatus, hasPhoto = state.photo != null, accent = accent)
                    HeroMode.Photo -> Box(Modifier.fillMaxSize())
                }
            }
            state.photo?.let { p ->
                Text(
                    "© ${p.photographer} · Planespotters.net",
                    style = ContrailTheme.type.bodySmall,
                    color = colors.textPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(spacing.sm)
                        .clip(ContrailTheme.shapes.chip)
                        .background(colors.scrim)
                        .clickable(role = Role.Button) { uri.openUri(p.pageUrl) }
                        .padding(horizontal = spacing.sm, vertical = spacing.xs),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            if (state.photo != null) ContrailChip("Photo", state.hero == HeroMode.Photo, onClick = { onHero(HeroMode.Photo) })
            ContrailChip("Livery", state.hero == HeroMode.Livery, onClick = { onHero(HeroMode.Livery) })
            ContrailChip("Cutout", state.hero == HeroMode.Cutout, onClick = { onHero(HeroMode.Cutout) })
        }
    }
}

/** What the cutout is doing, in words, with the model download's progress when there is one. */
@Composable
private fun CutoutProgress(status: CutoutStatus, hasPhoto: Boolean, accent: Color) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val text = when (status) {
        is CutoutStatus.Downloading -> "Downloading the cutout model" + (status.percent?.let { " · $it%" } ?: "…")
        CutoutStatus.Working -> "Lifting the aircraft out of its photo…"
        is CutoutStatus.Failed -> status.reason
        is CutoutStatus.Ready -> ""
        CutoutStatus.Idle -> if (hasPhoto) "Getting the cutout ready…" else "A cutout needs a photo of this aircraft, and there isn't one yet."
    }
    Column(
        Modifier
            .padding(spacing.lg)
            .clip(ContrailTheme.shapes.medium)
            .background(colors.scrim)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(text, style = ContrailTheme.type.bodySmall, color = colors.textPrimary, textAlign = TextAlign.Center)
        if (status is CutoutStatus.Downloading || status == CutoutStatus.Working) {
            val fraction = (status as? CutoutStatus.Downloading)?.percent?.let { it / 100f }
            Box(Modifier.width(160.dp).height(3.dp).clip(ContrailTheme.shapes.chip).background(colors.hairline)) {
                if (fraction != null) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(accent))
                } else {
                    IndeterminateBar(accent)
                }
            }
        }
    }
}

/** A short highlight sliding along its track while the length of the job is unknown. */
@Composable
private fun IndeterminateBar(accent: Color) {
    val motion = ContrailTheme.motion
    val x by if (motion.ambientEnabled) {
        rememberInfiniteTransition(label = "bar").animateFloat(-0.3f, 1f, infiniteRepeatable(tween(1_200, easing = LinearEasing), RepeatMode.Restart), label = "x")
    } else {
        remember { mutableFloatStateOf(0.35f) }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxHeight().width(maxWidth * 0.3f).offset(x = maxWidth * x).background(accent))
    }
}

/** Airline logo and rarity as one level row of equal-height pills, flush with the title above. */
@Composable
private fun IdentityRow(state: AircraftUiState) {
    val logo = state.airline?.logo?.logoWhite
    val rare = state.rarity < Rarity.Common
    if (logo == null && !rare) return
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    Row(Modifier.height(IdentityPillHeight), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
        if (logo != null) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .clip(ContrailTheme.shapes.chip)
                    .background(colors.glassTint.copy(alpha = 0.06f))
                    .border(ContrailTheme.sizes.hairline, colors.hairline, ContrailTheme.shapes.chip)
                    .padding(horizontal = spacing.md),
                contentAlignment = Alignment.CenterStart,
            ) {
                LiveLogo(logo, state.airlineName, Modifier.height(16.dp).widthIn(max = 96.dp), alignment = Alignment.CenterStart)
            }
        }
        if (rare) HoloBadge(state.rarity, Modifier.fillMaxHeight())
    }
}

private val IdentityPillHeight = 30.dp

private fun rarityColor(r: Rarity): Color = when (r) {
    Rarity.Legendary -> RarityPalette.Legendary.first()
    Rarity.Rare -> RarityPalette.Rare.first()
    Rarity.Uncommon -> RarityPalette.Uncommon.first()
    Rarity.Common -> Color.Unspecified
}

/** Gold holographic for Legendary, silver shimmer for Rare, blue for Uncommon. */
@Composable
fun HoloBadge(rarity: Rarity, modifier: Modifier = Modifier) {
    val palette = when (rarity) {
        Rarity.Legendary -> RarityPalette.Legendary
        Rarity.Rare -> RarityPalette.Rare
        else -> RarityPalette.Uncommon
    }
    val motion = ContrailTheme.motion
    val sweep by if (motion.ambientEnabled && rarity != Rarity.Uncommon) {
        rememberInfiniteTransition(label = "holo").animateFloat(-1f, 2f, infiniteRepeatable(tween(2_600, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
    } else {
        remember { mutableFloatStateOf(0.5f) }
    }
    Box(
        modifier
            .clip(ContrailTheme.shapes.chip)
            .background(Brush.linearGradient(palette.map { it.copy(alpha = 0.3f) }))
            .background(
                Brush.linearGradient(
                    0f to Color.Transparent, 0.45f to Color.White.copy(alpha = 0.55f), 0.55f to Color.Transparent,
                    start = Offset(sweep * 300f - 150f, 0f), end = Offset(sweep * 300f, 60f), tileMode = TileMode.Clamp,
                ),
            )
            .border(ContrailTheme.sizes.hairline, Brush.linearGradient(palette), ContrailTheme.shapes.chip)
            .padding(horizontal = ContrailTheme.spacing.md, vertical = ContrailTheme.spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(rarity.label.uppercase(), style = ContrailTheme.type.eyebrow, color = palette.first())
    }
}

@Composable
private fun RouteSection(state: AircraftUiState, onOpenAirport: (String) -> Unit) {
    val claimed = state.route?.airports?.joinToString(" → ") { it.code }
    when (val v = state.verdict) {
        // Not live: the route on file, as it was last seen.
        null -> state.route?.let { RouteCard(state, it.origin, it.destination, "route from callsign", null, onOpenAirport) }
        is RouteVerdict.OnRoute -> RouteCard(
            state, v.from, v.to, "route from callsign",
            if (v.multiStop) "Leg ${v.leg + 1} of ${v.stops.size - 1}: ${v.stops.joinToString(" → ") { it.code }}" else null,
            onOpenAirport,
        )
        is RouteVerdict.Landing -> RouteCard(state, null, v.at, "estimated", "Landing at ${v.at.city ?: v.at.name}, worked out from its approach." + (claimed?.let { " Its callsign says $it, which doesn't fit." } ?: ""), onOpenAirport)
        is RouteVerdict.Departing -> RouteCard(state, v.from, null, "estimated", "Climbing out of ${v.from.city ?: v.from.name}." + (claimed?.let { " Its callsign says $it, which doesn't fit." } ?: ""), onOpenAirport)
        is RouteVerdict.Mismatch -> SectionCard("Today's flight · ${state.callsign}") {
            Text("Route unknown", style = ContrailTheme.type.title, color = ContrailTheme.colors.textPrimary)
            Text("Its callsign says $claimed, but it isn't flying that way, so that route isn't shown.", style = ContrailTheme.type.bodySmall, color = ContrailTheme.colors.textTertiary)
        }
        RouteVerdict.NoRoute -> Unit
    }
}

@Composable
private fun RouteCard(
    state: AircraftUiState,
    from: AirportRef?,
    to: AirportRef?,
    tag: String,
    note: String?,
    onOpenAirport: (String) -> Unit,
) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    SectionCard(if (state.live != null) "Today's flight · ${state.callsign}" else "Last seen route · ${state.callsign}", trailing = { Eyebrow(tag) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(role = Role.Button, enabled = from?.icao != null) { from?.icao?.let(onOpenAirport) }) {
                Text(from?.code ?: "···", style = ContrailTheme.type.displayMedium, color = if (from != null) colors.textPrimary else colors.textTertiary)
                Text(from?.let { it.city ?: it.name } ?: "Unknown", style = ContrailTheme.type.bodySmall, color = colors.textTertiary, maxLines = 1)
            }
            val p = state.progress
            Box(Modifier.weight(1.2f).height(28.dp), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth().height(2.dp).background(colors.hairline))
                Box(Modifier.fillMaxWidth(p ?: 0f).height(2.dp).background(colors.accent))
                Box(Modifier.fillMaxWidth((p ?: 0.5f).coerceIn(0.06f, 0.94f)), contentAlignment = Alignment.CenterEnd) {
                    Icon(ContrailIcons.Plane, null, tint = colors.accent, modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = 90f })
                }
            }
            Column(Modifier.weight(1f).clickable(role = Role.Button, enabled = to?.icao != null) { to?.icao?.let(onOpenAirport) }, horizontalAlignment = Alignment.End) {
                Text(to?.code ?: "···", style = ContrailTheme.type.displayMedium, color = if (to != null) colors.textPrimary else colors.textTertiary)
                Text(to?.let { it.city ?: it.name } ?: "Unknown", style = ContrailTheme.type.bodySmall, color = colors.textTertiary, maxLines = 1)
            }
        }
        note?.let { Text(it, style = ContrailTheme.type.bodySmall, color = colors.textTertiary) }
        state.live?.let { a ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val alt = RadarFormat.altitude(a)
                StatTile("Altitude", alt.value, unit = alt.unit, valueColor = AltitudeRamp.colorFor(if (a.onGround) null else a.altitudeFt))
                val gs = RadarFormat.speed(a)
                StatTile("Speed", gs.value, unit = gs.unit)
                StatTile("Flown", state.progress?.let { "${(it * 100).toInt()}%" } ?: "—")
            }
        }
        Spacer(spacing)
    }
}

@Composable
private fun Spacer(spacing: com.contrail.contrail.core.designsystem.theme.ContrailSpacing) = Box(Modifier.height(spacing.xxs))

@Composable
private fun SightingsCard(state: AircraftUiState) {
    val colors = ContrailTheme.colors
    SectionCard("Flights Contrail has seen") {
        if (state.sightings.isEmpty()) {
            Text("This is the first time Contrail has seen this airframe.", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
        }
        state.sightings.take(8).forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.day, style = ContrailTheme.type.numericSmall, color = colors.textTertiary, modifier = Modifier.width(96.dp))
                Text(s.callsign, style = ContrailTheme.type.numeric, color = colors.textPrimary, modifier = Modifier.width(96.dp))
                Text(listOfNotNull(s.originIata, s.destinationIata).joinToString(" → ").ifBlank { "route unknown" }, style = ContrailTheme.type.bodySmall, color = colors.textSecondary)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WatchCard(state: AircraftUiState, onToggleWatch: (WatchItem, Boolean) -> Unit) {
    val options = AircraftViewModel.watchOptions(state)
    if (options.isEmpty()) return
    SectionCard("Watch for") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.sm)) {
            options.forEach { item ->
                val on = state.watchlist.contains(item.kind, item.value)
                ContrailChip(item.label, on, onClick = { onToggleWatch(item, on) }, leading = {
                    Icon(ContrailIcons.Follow, null, Modifier.size(ContrailTheme.sizes.iconSmall), tint = ContrailTheme.colors.accent)
                })
            }
        }
    }
}

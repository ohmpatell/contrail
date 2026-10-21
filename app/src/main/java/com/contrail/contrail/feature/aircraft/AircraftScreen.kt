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
import androidx.compose.foundation.layout.height
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.md)) {
            state.airline?.logo?.logoWhite?.let { LiveLogo(it, state.airlineName, Modifier.height(26.dp).width(110.dp)) }
            if (state.rarity < Rarity.Common) HoloBadge(state.rarity)
        }
        state.route?.let { RouteCard(state, it, onOpenAirport) }
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
                    } ?: Text(if (state.cutoutWorking) "Lifting the aircraft out of its photo…" else "The cutout model is still downloading. Tap Cutout again in a moment.", style = ContrailTheme.type.bodySmall, color = colors.textSecondary)
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
            ContrailChip("Livery", state.hero == HeroMode.Livery, onClick = { onHero(HeroMode.Livery) })
            ContrailChip("Cutout", state.hero == HeroMode.Cutout, onClick = { onHero(HeroMode.Cutout) })
            if (state.photo != null) ContrailChip("Photo", state.hero == HeroMode.Photo, onClick = { onHero(HeroMode.Photo) })
        }
    }
}

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
    ) {
        Text(rarity.label.uppercase(), style = ContrailTheme.type.eyebrow, color = palette.first())
    }
}

@Composable
private fun RouteCard(state: AircraftUiState, route: com.contrail.contrail.core.data.model.FlightRoute, onOpenAirport: (String) -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    SectionCard(if (state.live != null) "Today's flight · ${state.callsign}" else "Last seen route · ${state.callsign}", trailing = { Eyebrow("route from callsign") }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(role = Role.Button) { route.origin.icao?.let(onOpenAirport) }) {
                Text(route.origin.code, style = ContrailTheme.type.displayMedium, color = colors.textPrimary)
                Text(route.origin.city ?: route.origin.name, style = ContrailTheme.type.bodySmall, color = colors.textTertiary, maxLines = 1)
            }
            val p = state.progress
            Box(Modifier.weight(1.2f).height(28.dp), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth().height(2.dp).background(colors.hairline))
                Box(Modifier.fillMaxWidth(p ?: 0f).height(2.dp).background(colors.accent))
                Box(Modifier.fillMaxWidth((p ?: 0.5f).coerceIn(0.06f, 0.94f)), contentAlignment = Alignment.CenterEnd) {
                    Icon(ContrailIcons.Plane, null, tint = colors.accent, modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = 90f })
                }
            }
            Column(Modifier.weight(1f).clickable(role = Role.Button) { route.destination.icao?.let(onOpenAirport) }, horizontalAlignment = Alignment.End) {
                Text(route.destination.code, style = ContrailTheme.type.displayMedium, color = colors.textPrimary)
                Text(route.destination.city ?: route.destination.name, style = ContrailTheme.type.bodySmall, color = colors.textTertiary, maxLines = 1)
            }
        }
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

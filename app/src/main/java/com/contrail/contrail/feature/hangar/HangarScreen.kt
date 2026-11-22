package com.contrail.contrail.feature.hangar

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.contrail.contrail.core.data.hangar.HangarPlane
import com.contrail.contrail.core.data.reference.AircraftTypes
import com.contrail.contrail.core.data.rotation.EarlierLeg
import com.contrail.contrail.core.designsystem.component.AccentButton
import com.contrail.contrail.core.designsystem.component.DetailScaffold
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.GlassSurface
import com.contrail.contrail.core.designsystem.component.LivePulse
import com.contrail.contrail.core.designsystem.component.RiseIn
import com.contrail.contrail.core.designsystem.component.SectionCard
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.livery.Liveries
import com.contrail.contrail.core.designsystem.livery.LiveryRender
import com.contrail.contrail.core.designsystem.livery.Profiles
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.core.designsystem.theme.RarityPalette
import com.contrail.contrail.feature.radar.RadarFormat
import com.contrail.contrail.feature.radar.components.GlassToggle
import com.contrail.contrail.feature.radar.components.HangarText
import com.contrail.contrail.navigation.LocalNavigator
import kotlinx.coroutines.delay
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Gold get() = RarityPalette.Legendary.first()

@Composable
fun HangarRoute(viewModel: HangarViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    HangarScreen(
        state,
        onBack = nav::back,
        onPlane = nav::openHangarPlane,
        onShowOnRadar = { c -> showOnRadar(c, nav::showOnRadar) },
    )
}

private fun showOnRadar(c: HangarCard, show: (String?, Double, Double) -> Unit) {
    val lat = c.live?.lat ?: c.plane.lastLat ?: return
    val lon = c.live?.lon ?: c.plane.lastLon ?: return
    show(c.live?.hex ?: c.plane.hex, lat, lon)
}

@Composable
fun HangarScreen(state: HangarUiState, onBack: () -> Unit, onPlane: (String) -> Unit, onShowOnRadar: (HangarCard) -> Unit) {
    val colors = ContrailTheme.colors
    DetailScaffold(
        title = "My Hangar",
        eyebrow = if (state.cards.isEmpty()) "Planes that mean something to you" else "${state.cards.size} pinned · found worldwide",
        accent = Gold,
        onBack = onBack,
    ) {
        if (state.cards.isEmpty() && !state.loading) {
            RiseIn(0) { EmptyHangar() }
        }
        state.cards.forEachIndexed { i, c ->
            RiseIn(i) { HangarPlaneCard(c, onClick = { onPlane(c.plane.registration) }, onShowOnRadar = { onShowOnRadar(c) }) }
        }
        if (state.cards.isNotEmpty()) {
            Text(
                "Pinned planes are looked up worldwide every minute while the radar is open, so they're always on it with a gold ring. Off screen, a marker on the edge points the way.",
                style = ContrailTheme.type.bodySmall,
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
private fun EmptyHangar() {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    GlassSurface(Modifier.fillMaxWidth(), glow = Gold.copy(alpha = 0.6f)) {
        Column(Modifier.padding(spacing.xl), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Icon(ContrailIcons.Star, null, tint = Gold, modifier = Modifier.size(32.dp))
            Text("Your hangar is empty", style = ContrailTheme.type.displaySmall, color = colors.textPrimary)
            Text(
                "Pin the planes that mean something: the one you sat in the captain's seat of, your first long-haul, the one you always seem to get. Tap the star on any plane's sheet on the radar, or search its registration and pin it from there.",
                style = ContrailTheme.type.body,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun HangarPlaneCard(c: HangarCard, onClick: () -> Unit, onShowOnRadar: () -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val p = c.plane
    GlassSurface(
        Modifier.fillMaxWidth().clip(ContrailTheme.shapes.large).clickable(role = Role.Button, onClickLabel = "Open ${p.registration}", onClick = onClick),
        shape = ContrailTheme.shapes.large,
        glow = Gold.copy(alpha = 0.5f),
    ) {
        Column {
            PlanePicture(c, Modifier.fillMaxWidth().aspectRatio(1.9f), showCredit = true) {
                Column(Modifier.align(Alignment.BottomStart).padding(spacing.lg)) {
                    Text(p.registration, style = ContrailTheme.type.displayMedium, color = Color.White)
                    Text(
                        listOfNotNull(AircraftTypes.nameFor(c.live?.typeCode ?: p.typeCode), p.airlineName).joinToString(" · "),
                        style = ContrailTheme.type.body,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(ContrailIcons.StarFilled, "In My Hangar", tint = Gold, modifier = Modifier.align(Alignment.TopEnd).padding(spacing.md).size(22.dp))
            }
            Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                StatusLine(c)
                p.story?.let { story ->
                    Text("“$story”", style = ContrailTheme.type.body.copy(fontStyle = FontStyle.Italic), color = colors.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                if (c.live != null || p.lastLat != null) {
                    Text(
                        "Show on radar",
                        style = ContrailTheme.type.label,
                        color = Gold,
                        modifier = Modifier
                            .clip(ContrailTheme.shapes.chip)
                            .border(ContrailTheme.sizes.hairline, Gold.copy(alpha = 0.6f), ContrailTheme.shapes.chip)
                            .clickable(role = Role.Button, onClick = onShowOnRadar)
                            .padding(horizontal = spacing.md, vertical = spacing.sm),
                    )
                }
            }
        }
    }
}

/** "Flying now · CPA251 · HKG → LHR · FL370", or when it was last seen. */
@Composable
private fun StatusLine(c: HangarCard) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val live = c.live
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
        if (live != null) {
            LivePulse(color = if (live.onGround) colors.statusOnTime else Gold)
            val alt = RadarFormat.altitude(live)
            val parts = listOfNotNull(
                if (live.onGround) "On the ground" else "Flying now",
                live.callsign?.trim()?.ifEmpty { null },
                c.route?.let { "${it.origin.code} → ${it.destination.code}" },
                if (live.onGround) null else "${alt.value} ${alt.unit.orEmpty()}".trim(),
            )
            Text(parts.joinToString(" · "), style = ContrailTheme.type.label, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            LivePulse(color = colors.textTertiary)
            Text(HangarText.status(c.plane, System.currentTimeMillis()).replaceFirstChar { it.uppercase() }, style = ContrailTheme.type.label, color = colors.textSecondary)
        }
    }
    if (live != null && c.route != null) Eyebrow("Route from callsign", color = colors.textTertiary)
}

/** Your own photo of it if you added one; otherwise its Planespotters photo (always credited); otherwise its livery. */
@Composable
private fun PlanePicture(c: HangarCard, modifier: Modifier, showCredit: Boolean, overlay: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val uri = LocalUriHandler.current
    Box(modifier.clip(ContrailTheme.shapes.large).background(Brush.linearGradient(listOf(Gold.copy(alpha = 0.25f), colors.canvasSunken)))) {
        val own = c.plane.photoPath?.let { File(it) }?.takeIf { it.exists() }
        when {
            own != null -> AsyncImage(own, "Your photo of ${c.plane.registration}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            c.photo != null -> AsyncImage(c.photo.largeUrl, "${c.plane.registration} on Planespotters", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else -> LiveryRender(
                Profiles.forType(c.plane.typeCode),
                Liveries.forAirline(c.plane.airlineIcao),
                Modifier.align(Alignment.Center).fillMaxWidth(0.8f),
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))))
        overlay()
        if (showCredit && own == null && c.photo != null) {
            Text(
                "© ${c.photo.photographer} · Planespotters.net",
                style = ContrailTheme.type.eyebrow,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(spacing.sm)
                    .clip(ContrailTheme.shapes.chip)
                    .background(colors.scrim)
                    .clickable(role = Role.Button) { uri.openUri(c.photo.pageUrl) }
                    .padding(horizontal = spacing.sm, vertical = spacing.xxs),
            )
        }
    }
}

// region One plane

@Composable
fun HangarPlaneRoute(registration: String) {
    val viewModel = hiltViewModel<HangarPlaneViewModel, HangarPlaneViewModel.Factory>(key = "hangar-$registration") { it.create(registration) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    LaunchedEffect(state.removed) { if (state.removed) nav.back() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(viewModel::setPhoto) }
    HangarPlaneScreen(
        state = state,
        onBack = nav::back,
        onStory = viewModel::setStory,
        onPickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onAlerts = viewModel::setAlerts,
        onRemove = viewModel::remove,
        onShowOnRadar = { c -> showOnRadar(c, nav::showOnRadar) },
        onAircraftPage = { hex -> nav.openAircraft(hex) },
    )
}

@Composable
fun HangarPlaneScreen(
    state: HangarPlaneUiState,
    onBack: () -> Unit,
    onStory: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onAlerts: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onShowOnRadar: (HangarCard) -> Unit,
    onAircraftPage: (String) -> Unit,
) {
    val c = state.card
    val p = c?.plane
    val spacing = ContrailTheme.spacing
    val colors = ContrailTheme.colors
    DetailScaffold(
        title = p?.registration ?: "",
        eyebrow = p?.let { listOfNotNull(p.airlineName, AircraftTypes.nameFor(c.live?.typeCode ?: p.typeCode)).joinToString(" · ").ifBlank { null } },
        accent = Gold,
        onBack = onBack,
        hero = { if (c != null) PlanePicture(c, Modifier.padding(horizontal = spacing.gutter).fillMaxWidth().aspectRatio(1.6f), showCredit = true) {} },
    ) {
        if (c == null || p == null) return@DetailScaffold
        SectionCard("Right now", trailing = { Icon(ContrailIcons.StarFilled, null, tint = Gold, modifier = Modifier.size(16.dp)) }) {
            StatusLine(c)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                if (c.live != null || p.lastLat != null) AccentButton("Show on radar", { onShowOnRadar(c) }, icon = ContrailIcons.Radar, accent = Gold, modifier = Modifier.weight(1f))
                (c.live?.hex ?: p.hex)?.let { hex -> AccentButton("Aircraft page", { onAircraftPage(hex) }, accent = Gold, secondary = true, modifier = Modifier.weight(1f)) }
            }
        }
        StoryCard(p, onStory, onPickPhoto)
        SectionCard("Alerts") {
            Row(Modifier.fillMaxWidth().clickable(role = Role.Switch) { onAlerts(!p.alerts) }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("When it takes off and lands", style = ContrailTheme.type.title, color = colors.textPrimary)
                    Text("Checked about every 15 minutes in the background.", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
                }
                GlassToggle(p.alerts)
            }
        }
        WeekCard(state)
        if (state.flownOnIt > 0) {
            SectionCard("You and ${p.registration}") {
                Text(
                    if (state.flownOnIt == 1) "You've flown on it once." else "You've flown on it ${state.flownOnIt} times.",
                    style = ContrailTheme.type.body,
                    color = colors.textPrimary,
                )
            }
        }
        Text(
            "Remove from My Hangar",
            style = ContrailTheme.type.label,
            color = colors.emergency,
            modifier = Modifier.clip(ContrailTheme.shapes.chip).clickable(role = Role.Button, onClick = onRemove).padding(spacing.sm),
        )
    }
}

@Composable
private fun StoryCard(p: HangarPlane, onStory: (String) -> Unit, onPickPhoto: () -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    var text by rememberSaveable(p.registration) { mutableStateOf(p.story.orEmpty()) }
    // Saved a moment after you stop typing.
    LaunchedEffect(text) {
        delay(800)
        if (text.trim() != p.story.orEmpty()) onStory(text)
    }
    SectionCard("Your story") {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp)
                .clip(ContrailTheme.shapes.small)
                .background(colors.glassTint.copy(alpha = 0.06f))
                .border(ContrailTheme.sizes.hairline, Gold.copy(alpha = 0.4f), ContrailTheme.shapes.small)
                .padding(spacing.md),
        ) {
            if (text.isEmpty()) {
                Text("Why it matters: sat in the captain's seat at HKG, my first long-haul…", style = ContrailTheme.type.body, color = colors.textDisabled)
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(600) },
                textStyle = ContrailTheme.type.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(Gold),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AccentButton(if (p.photoPath != null) "Change your photo" else "Add your own photo", onPickPhoto, icon = ContrailIcons.Image, accent = Gold, secondary = true, modifier = Modifier.fillMaxWidth())
        Text("Your photo stays on this device.", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
    }
}

@Composable
private fun WeekCard(state: HangarPlaneUiState) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    SectionCard("Its last week", trailing = { Eyebrow("OpenSky") }) {
        when {
            state.flightsLoading -> Text("Looking up its flights…", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
            state.flightsError != null -> Text(state.flightsError, style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
            state.flights.isEmpty() -> Text("No flights in OpenSky's history for the last week. It may have been parked, or flying where OpenSky has no receivers.", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
            else -> Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                state.flights.take(14).forEach { FlightRow(it) }
                Text("OpenSky publishes each day's flights the day after.", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
            }
        }
    }
}

private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)
private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)

@Composable
private fun FlightRow(leg: EarlierLeg) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val zone = ZoneId.systemDefault()
    val dep = Instant.ofEpochSecond(leg.departedSec).atZone(zone)
    val mins = ((leg.arrivedSec - leg.departedSec) / 60).toInt()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
        Column(Modifier.weight(0.9f)) {
            Text(DAY.format(dep), style = ContrailTheme.type.label, color = colors.textSecondary)
            Text(TIME.format(dep), style = ContrailTheme.type.numericSmall, color = colors.textTertiary)
        }
        Text("${leg.from ?: "?"} → ${leg.to ?: "?"}", style = ContrailTheme.type.numeric, color = colors.textPrimary, modifier = Modifier.weight(1.2f))
        Column(Modifier.weight(0.9f), horizontalAlignment = Alignment.End) {
            Text(leg.callsign ?: "", style = ContrailTheme.type.numericSmall, color = colors.textSecondary)
            Text("${mins / 60}h ${mins % 60}m", style = ContrailTheme.type.label, color = colors.textTertiary)
        }
    }
}

// endregion

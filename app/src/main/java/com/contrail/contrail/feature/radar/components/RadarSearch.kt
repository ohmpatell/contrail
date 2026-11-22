package com.contrail.contrail.feature.radar.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.hangar.HangarPlane
import com.contrail.contrail.core.data.model.LiveAircraft
import com.contrail.contrail.core.data.search.SearchQuery
import com.contrail.contrail.core.data.search.SearchTarget
import com.contrail.contrail.core.designsystem.component.ContrailChip
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.GlassLevel
import com.contrail.contrail.core.designsystem.component.GlassSurface
import com.contrail.contrail.core.designsystem.component.LivePulse
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.AltitudeRamp
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.core.designsystem.theme.RarityPalette
import com.contrail.contrail.feature.radar.AirportHit
import com.contrail.contrail.feature.radar.RadarFormat
import com.contrail.contrail.feature.radar.SearchUiState
import java.util.Locale

/** Everything the search panel can ask for. */
data class SearchActions(
    val onText: (String) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onAircraft: (LiveAircraft) -> Unit = {},
    val onAirport: (AirportHit) -> Unit = {},
    val onClearRecent: () -> Unit = {},
    val onHangarPlane: (HangarPlane) -> Unit = {},
    val onOpenHangar: () -> Unit = {},
    val onPinRegistration: (String) -> Unit = {},
)

/**
 * Search the whole world's live traffic, like a flight tracker: a flight number (CX 888), a
 * callsign (CPA888), a registration (B-LXP), a transponder code or an airport. Opens with your
 * Hangar and recent searches.
 */
@Composable
fun SearchPanel(
    state: SearchUiState,
    hangar: List<HangarPlane>,
    from: LatLon?,
    actions: SearchActions,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val motion = ContrailTheme.motion
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    GlassSurface(modifier.fillMaxWidth(), level = GlassLevel.Strong) {
        Column(Modifier.heightIn(max = maxHeight).padding(vertical = spacing.md)) {
            // The box
            Row(
                Modifier
                    .padding(horizontal = spacing.lg)
                    .fillMaxWidth()
                    .clip(ContrailTheme.shapes.chip)
                    .background(colors.glassTint.copy(alpha = 0.08f))
                    .border(ContrailTheme.sizes.hairline, colors.hairline, ContrailTheme.shapes.chip)
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Icon(ContrailIcons.Search, null, tint = colors.accent, modifier = Modifier.size(ContrailTheme.sizes.iconSmall))
                Box(Modifier.weight(1f)) {
                    if (state.query.isEmpty()) {
                        Text("Flight, callsign, registration or airport", style = ContrailTheme.type.body, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    BasicTextField(
                        value = state.query,
                        onValueChange = actions.onText,
                        singleLine = true,
                        textStyle = ContrailTheme.type.numeric.copy(color = colors.textPrimary),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Search, autoCorrectEnabled = false),
                        keyboardActions = KeyboardActions(onSearch = { actions.onSubmit() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (state.query.isNotEmpty()) {
                    Icon(
                        ContrailIcons.Close, "Clear", tint = colors.textTertiary,
                        modifier = Modifier.size(ContrailTheme.sizes.iconSmall).clip(CircleShape).clickable(role = Role.Button) { actions.onText("") },
                    )
                }
            }
            state.readings.firstOrNull()?.let { reading ->
                Eyebrow(
                    (listOf(reading) + state.readings.drop(1).take(1)).joinToString("  ·  or  ·  "),
                    color = colors.textTertiary,
                    modifier = Modifier.padding(start = spacing.xl, end = spacing.lg, top = spacing.sm),
                )
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(top = spacing.sm)) {
                AnimatedContent(
                    targetState = when {
                        state.query.isBlank() -> Mode.Start
                        state.results.searching -> Mode.Searching
                        else -> Mode.Results
                    },
                    transitionSpec = { fadeIn(motion.fade()) togetherWith fadeOut(motion.fade(120)) },
                    label = "search",
                ) { mode ->
                    when (mode) {
                        Mode.Start -> StartContent(state, hangar, actions)
                        Mode.Searching -> Row(
                            Modifier.fillMaxWidth().padding(horizontal = spacing.xl, vertical = spacing.lg),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            LivePulse(color = colors.accent)
                            Text("Looking worldwide…", style = ContrailTheme.type.body, color = colors.textSecondary)
                        }
                        Mode.Results -> ResultsContent(state, hangar, from, actions)
                    }
                }
            }
        }
    }
}

private enum class Mode { Start, Searching, Results }

@Composable
private fun StartContent(state: SearchUiState, hangar: List<HangarPlane>, actions: SearchActions) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Row(Modifier.padding(horizontal = spacing.xl).padding(top = spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("My Hangar", Modifier.weight(1f), color = RarityPalette.Legendary.first())
            Text(
                if (hangar.isEmpty()) "Pin a plane from its sheet" else "Open",
                style = ContrailTheme.type.label,
                color = colors.textSecondary,
                modifier = Modifier.clip(ContrailTheme.shapes.chip).clickable(role = Role.Button, onClick = actions.onOpenHangar).padding(horizontal = spacing.sm, vertical = spacing.xxs),
            )
        }
        if (hangar.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = spacing.lg), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                hangar.forEach { p -> HangarChip(p) { actions.onHangarPlane(p) } }
            }
        }
        if (state.recent.isNotEmpty()) {
            Row(Modifier.padding(horizontal = spacing.xl).padding(top = spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("Recent", Modifier.weight(1f))
                Text(
                    "Clear", style = ContrailTheme.type.label, color = colors.textTertiary,
                    modifier = Modifier.clip(ContrailTheme.shapes.chip).clickable(role = Role.Button, onClick = actions.onClearRecent).padding(horizontal = spacing.sm, vertical = spacing.xxs),
                )
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = spacing.lg), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                state.recent.forEach { q -> ContrailChip(q, false, onClick = { actions.onText(q) }) }
            }
        }
        Text(
            "Try CX 888, CPA888, B-LXP or YYZ. Searches every plane sharing its position worldwide, not just this map.",
            style = ContrailTheme.type.bodySmall,
            color = colors.textTertiary,
            modifier = Modifier.padding(horizontal = spacing.xl, vertical = spacing.sm),
        )
    }
}

@Composable
private fun HangarChip(plane: HangarPlane, onClick: () -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val gold = RarityPalette.Legendary.first()
    val canFly = plane.lastLat != null
    Row(
        Modifier
            .clip(ContrailTheme.shapes.chip)
            .background(gold.copy(alpha = 0.12f))
            .border(ContrailTheme.sizes.hairline, gold.copy(alpha = 0.6f), ContrailTheme.shapes.chip)
            .clickable(enabled = canFly, role = Role.Button, onClick = onClick)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Icon(ContrailIcons.StarFilled, null, tint = gold, modifier = Modifier.size(14.dp))
        Text(plane.registration, style = ContrailTheme.type.numeric, color = colors.textPrimary)
        Text(HangarText.status(plane, System.currentTimeMillis()), style = ContrailTheme.type.label, color = colors.textSecondary, maxLines = 1)
    }
}

@Composable
private fun ResultsContent(state: SearchUiState, hangar: List<HangarPlane>, from: LatLon?, actions: SearchActions) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val r = state.results
    Column {
        if (r.aircraft.isNotEmpty()) {
            Eyebrow("Flying now", Modifier.padding(horizontal = spacing.xl, vertical = spacing.xs))
            r.aircraft.forEach { a -> AircraftResult(a, from, pinned = hangar.any { it.hex == a.hex || it.registration == a.registration?.uppercase() }) { actions.onAircraft(a) } }
        }
        if (r.airports.isNotEmpty()) {
            Eyebrow("Airports", Modifier.padding(horizontal = spacing.xl, vertical = spacing.xs))
            r.airports.forEach { ap -> AirportResult(ap) { actions.onAirport(ap) } }
        }
        if (r.aircraft.isEmpty() && r.airports.isEmpty()) {
            val registration = SearchQuery.targets(state.query).filterIsInstance<SearchTarget.Registration>().firstOrNull()?.registration
            Column(Modifier.padding(horizontal = spacing.xl, vertical = spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text(
                    when {
                        r.failed -> "Couldn't reach the live feeds"
                        r.tried -> "Not flying right now"
                        else -> "Nothing matches"
                    },
                    style = ContrailTheme.type.title,
                    color = colors.textPrimary,
                )
                Text(
                    when {
                        r.failed -> "Check you're online, then try again."
                        r.tried -> "${state.query.uppercase(Locale.ROOT)} isn't sharing its position anywhere right now. It may be parked with its transponder off, or out of receiver range (over an ocean, say)."
                        else -> "Try a flight number like CX 888, a callsign like CPA888, a registration like B-LXP, or an airport code."
                    },
                    style = ContrailTheme.type.bodySmall,
                    color = colors.textTertiary,
                )
                if (registration != null && hangar.none { it.registration == registration }) {
                    Text(
                        "Pin $registration to My Hangar",
                        style = ContrailTheme.type.label,
                        color = RarityPalette.Legendary.first(),
                        modifier = Modifier
                            .clip(ContrailTheme.shapes.chip)
                            .border(ContrailTheme.sizes.hairline, RarityPalette.Legendary.first().copy(alpha = 0.6f), ContrailTheme.shapes.chip)
                            .clickable(role = Role.Button) { actions.onPinRegistration(registration) }
                            .padding(horizontal = spacing.md, vertical = spacing.sm),
                    )
                }
            }
        }
    }
}

@Composable
private fun AircraftResult(a: LiveAircraft, from: LatLon?, pinned: Boolean, onClick: () -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val tint = AltitudeRamp.colorFor(if (a.onGround) null else a.altitudeFt)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = spacing.xl, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(ContrailIcons.Plane, null, tint = tint, modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = (a.trackDeg ?: 0f) - 45f })
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(a.displayCallsign, style = ContrailTheme.type.numeric, color = colors.textPrimary)
                if (pinned) Icon(ContrailIcons.StarFilled, "In My Hangar", tint = RarityPalette.Legendary.first(), modifier = Modifier.size(12.dp))
            }
            Text(
                listOfNotNull(a.registration, a.typeName, a.airline?.name).joinToString(" · "),
                style = ContrailTheme.type.bodySmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            val alt = RadarFormat.altitude(a)
            Text(if (a.onGround) "On ground" else "${alt.value} ${alt.unit.orEmpty()}", style = ContrailTheme.type.numericSmall, color = tint)
            from?.let { here ->
                val km = Geo.distanceM(here, LatLon(a.lat, a.lon)) / 1000
                Text(if (km < 1) "here" else String.format(Locale.US, "%,.0f km away", km), style = ContrailTheme.type.label, color = colors.textTertiary)
            }
        }
    }
}

@Composable
private fun AirportResult(ap: AirportHit, onClick: () -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = spacing.xl, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(ap.code, style = ContrailTheme.type.numeric, color = colors.accent, modifier = Modifier.padding(end = spacing.xs))
        Column(Modifier.weight(1f)) {
            Text(ap.city.substringBefore(" (").ifBlank { ap.name }, style = ContrailTheme.type.title, color = colors.textPrimary, maxLines = 1)
            Text(ap.name, style = ContrailTheme.type.bodySmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(ContrailIcons.ChevronRight, null, tint = colors.textTertiary, modifier = Modifier.size(ContrailTheme.sizes.iconSmall))
    }
}

/** Short status lines for a pinned plane. */
object HangarText {
    fun status(p: HangarPlane, now: Long): String {
        val seen = p.lastSeenMs ?: return "not seen yet"
        val mins = (now - seen) / 60_000
        return when {
            mins < 3 && p.lastAirborne == true -> "flying now"
            mins < 3 -> "on the ground"
            mins < 60 -> "seen ${mins} min ago"
            mins < 48 * 60 -> "seen ${mins / 60} h ago"
            else -> "seen ${mins / (24 * 60)} days ago"
        }
    }
}

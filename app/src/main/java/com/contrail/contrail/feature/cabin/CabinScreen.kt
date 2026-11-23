package com.contrail.contrail.feature.cabin

import android.Manifest
import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contrail.contrail.core.data.cabin.Bumpiness
import com.contrail.contrail.core.data.cabin.Side
import com.contrail.contrail.core.data.cabin.Upcoming
import com.contrail.contrail.core.data.reference.AircraftTypes
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.GlassIconButton
import com.contrail.contrail.core.designsystem.component.GlassLevel
import com.contrail.contrail.core.designsystem.component.GlassSurface
import com.contrail.contrail.core.designsystem.component.LivePulse
import com.contrail.contrail.core.designsystem.component.SplitFlapText
import com.contrail.contrail.core.designsystem.component.StatTile
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.AltitudeRamp
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.core.designsystem.theme.RarityPalette
import com.contrail.contrail.feature.radar.components.GlassToggle
import com.contrail.contrail.feature.trips.airlineAccent
import com.contrail.contrail.navigation.LocalNavigator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CabinRoute(segmentId: Long) {
    val viewModel = hiltViewModel<CabinViewModel, CabinViewModel.Factory>(key = "cabin-$segmentId") { it.create(segmentId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.setActive(false); viewModel.setActive(true) }
    LaunchedEffect(Unit) { permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
    DisposableEffect(Unit) {
        viewModel.setActive(true)
        onDispose { viewModel.setActive(false) }
    }
    CabinScreen(state, onBack = nav::back)
}

/**
 * Cabin mode: a moving map and everything about the flight you're on, working with no internet.
 * On a phone the map sits on top and the details scroll below; on a tablet they sit side by side.
 */
@Composable
fun CabinScreen(state: CabinState, onBack: () -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val seg = state.segment
    val accent = airlineAccent(seg?.airlineIata)
    var follow by rememberSaveable { mutableStateOf(true) }
    var keepOn by rememberSaveable { mutableStateOf(false) }
    val activity = LocalContext.current as? Activity
    DisposableEffect(keepOn) {
        if (keepOn) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.canvas)) {
        val wide = maxWidth > maxHeight && maxWidth >= 600.dp
        val map: @Composable (Modifier) -> Unit = { m ->
            Box(m) {
                if (state.route.isNotEmpty()) {
                    CabinMap(
                        route = state.route,
                        flown = state.flown,
                        position = state.position,
                        headingDeg = state.headingDeg,
                        accent = accent,
                        silhouette = AircraftTypes.silhouetteFor(seg?.aircraftType, null),
                        follow = follow,
                        onUserPan = { follow = false },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(Modifier.fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(colors.canvas.copy(alpha = 0.85f), Color.Transparent))))
                Row(
                    Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(spacing.lg).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    GlassIconButton(ContrailIcons.Back, "Back", onBack)
                    Column(Modifier.weight(1f)) {
                        Eyebrow("Cabin mode", color = accent)
                        Text(
                            listOfNotNull(seg?.designator, seg?.let { "${it.origin} → ${it.destination}" }).joinToString(" · "),
                            style = ContrailTheme.type.title, color = colors.textPrimary, maxLines = 1,
                        )
                    }
                    if (!follow) GlassIconButton(ContrailIcons.Locate, "Follow the plane", { follow = true }, active = true)
                }
                SourcePill(state, Modifier.align(Alignment.BottomStart).padding(spacing.lg))
            }
        }
        val panel: @Composable (Modifier) -> Unit = { m -> Panel(state, accent, keepOn, { keepOn = it }, m) }
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                map(Modifier.weight(1.2f).fillMaxHeight())
                panel(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                map(Modifier.fillMaxWidth().weight(0.9f))
                panel(Modifier.fillMaxWidth().weight(1.1f))
            }
        }
    }
}

/** Where the plane's position comes from: GPS, ADS-B over Wi-Fi, or the clock. Always said. */
@Composable
private fun SourcePill(state: CabinState, modifier: Modifier) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val (dot, text) = when (state.source) {
        PositionSource.Gps -> colors.statusOnTime to "GPS" + (state.satellites?.let { " · $it satellites" } ?: "")
        PositionSource.Adsb -> colors.statusAirborne to "ADS-B via Wi-Fi"
        PositionSource.Estimated -> colors.statusDelayed to "Estimated from the clock"
    }
    GlassSurface(modifier, shape = ContrailTheme.shapes.chip) {
        Row(Modifier.padding(horizontal = spacing.md, vertical = spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            LivePulse(color = dot)
            Text(text, style = ContrailTheme.type.label, color = colors.textPrimary)
        }
    }
}

@Composable
private fun Panel(state: CabinState, accent: Color, keepOn: Boolean, onKeepOn: (Boolean) -> Unit, modifier: Modifier) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        when (state.phase) {
            CabinPhase.Landed -> Welcome(state, accent)
            CabinPhase.Boarding -> Boarding(state, accent)
            CabinPhase.Airborne -> Progress(state, accent)
        }
        if (state.phase == CabinPhase.Airborne) Readouts(state)
        SunCard(state)
        if (state.upcoming.isNotEmpty()) ComingUp(state)
        state.nextBorder?.let { (country, minutes) ->
            GlassSurface(Modifier.fillMaxWidth(), shape = ContrailTheme.shapes.medium) {
                Row(Modifier.padding(spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    Icon(ContrailIcons.Globe, null, tint = colors.passport, modifier = Modifier.size(20.dp))
                    Text("Into $country in about ${duration(minutes)}", style = ContrailTheme.type.body, color = colors.textPrimary)
                }
            }
        }
        if (state.captured.isNotEmpty()) {
            Eyebrow("Moments captured · ${state.captured.size}", color = RarityPalette.Legendary.first())
        }
        Row(Modifier.fillMaxWidth().clip(ContrailTheme.shapes.small).clickable(role = Role.Switch) { onKeepOn(!keepOn) }.padding(vertical = spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Keep the screen on", style = ContrailTheme.type.title, color = colors.textPrimary)
                Text("Uses more battery. GPS works in airplane mode; a window seat helps.", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
            }
            GlassToggle(keepOn)
        }
        if (state.pack == null) {
            Text("No flight pack: places along the way and the offline map weren't downloaded. The route and the sun still work.", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
        } else if (state.pack.pathSource == "recent") {
            Text("The route is the path this flight flew recently (OpenSky).", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
        }
    }
}

@Composable
private fun Progress(state: CabinState, accent: Color) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val to = state.destination
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Eyebrow("To ${to?.city ?: state.segment?.destination}", color = accent)
        SplitFlapText(state.minutesLeft?.let { duration(it) } ?: "--", style = ContrailTheme.type.flap)
        ProgressLine(state.fraction.toFloat(), accent)
        val land = state.landingMs?.let { ms -> to?.zone?.let { z -> TIME.withZone(z).format(Instant.ofEpochMilli(ms)) } }
        Text(
            listOfNotNull(land?.let { "Lands about $it local" }, if (state.source == PositionSource.Estimated) "estimated" else null).joinToString(" · "),
            style = ContrailTheme.type.bodySmall,
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun ProgressLine(fraction: Float, accent: Color) {
    val colors = ContrailTheme.colors
    BoxWithConstraints(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(3.dp).clip(ContrailTheme.shapes.chip).background(colors.hairline))
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(3.dp).clip(ContrailTheme.shapes.chip).background(accent))
        Icon(
            ContrailIcons.Plane, null, tint = accent,
            modifier = Modifier.padding(start = (maxWidth - 20.dp) * fraction.coerceIn(0f, 1f)).size(20.dp).graphicsLayer { rotationZ = 90f },
        )
    }
}

@Composable
private fun Readouts(state: CabinState) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    GlassSurface(Modifier.fillMaxWidth(), shape = ContrailTheme.shapes.medium) {
        Column(Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatTile("Ground speed", state.groundSpeedKt?.let { "%,.0f".format(Locale.US, it) } ?: "—", unit = "kt")
                StatTile("Altitude", state.altitudeFt?.let { "%,d".format(Locale.US, it) } ?: "—", unit = "ft", valueColor = AltitudeRamp.colorFor(state.altitudeFt))
                StatTile("Heading", "%03.0f°".format(Locale.US, state.headingDeg))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatTile("Cabin altitude", state.cabinAltitudeFt?.let { "%,d".format(Locale.US, it) } ?: "—", unit = "ft")
                StatTile(
                    "Ride", state.bumpiness.label,
                    valueColor = when (state.bumpiness) {
                        Bumpiness.Smooth -> colors.statusOnTime
                        Bumpiness.Light -> colors.textPrimary
                        Bumpiness.Moderate -> colors.statusDelayed
                        Bumpiness.Strong -> colors.emergency
                    },
                )
            }
            if (state.cabinAltitudeFt != null) {
                Text("Cabin altitude is the pressure you're breathing, from the phone's barometer: planes keep it around 6,000 to 8,000 ft.", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
            }
        }
    }
}

@Composable
private fun SunCard(state: CabinState) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val gold = RarityPalette.Legendary.first()
    val e = state.sunEvent
    val zone = ZoneId.systemDefault()
    val lines = buildList {
        state.sunSide?.let { side ->
            val yours = state.window != null && state.window == side
            add("The sun is ${sideWords(side)}" + if (yours) ", on your window's side" else "")
        }
        e?.let {
            val mins = ((it.atMs - state.nowMs) / 60_000).toInt()
            add((if (it.sunset) "Sunset" else "Sunrise") + " in ${duration(mins)}, ${sideWords(it.side)}" + if (state.window != null && state.window == it.side) ": your window" else "")
        }
        state.window?.let { add("You're by the ${if (it == Side.Left) "left" else "right"} window (seat ${state.segment?.seat})") }
    }
    if (lines.isEmpty()) return
    GlassSurface(Modifier.fillMaxWidth(), shape = ContrailTheme.shapes.medium, glow = gold.copy(alpha = 0.3f)) {
        Row(Modifier.padding(spacing.md), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            Icon(ContrailIcons.Moon, null, tint = gold, modifier = Modifier.size(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                lines.forEach { Text(it, style = ContrailTheme.type.body, color = colors.textPrimary) }
                e?.let { Text("Up here the horizon sits lower, so the sun sets later than on the ground.", style = ContrailTheme.type.bodySmall, color = colors.textTertiary) }
            }
        }
    }
}

@Composable
private fun ComingUp(state: CabinState) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Eyebrow("Coming up")
        state.upcoming.forEach { u -> UpcomingRow(u, state.window) }
    }
}

@Composable
private fun UpcomingRow(u: Upcoming, window: Side?) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val yours = window != null && u.side == window
    GlassSurface(Modifier.fillMaxWidth(), shape = ContrailTheme.shapes.medium, glow = if (yours) colors.accent.copy(alpha = 0.4f) else null) {
        Row(Modifier.padding(spacing.md), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(
                    ContrailIcons.ArrowRight, null, tint = colors.accent,
                    modifier = Modifier.size(18.dp).graphicsLayer {
                        rotationZ = when (u.side) {
                            Side.Left -> 180f
                            Side.Right -> 0f
                            Side.Ahead -> -90f
                            Side.Behind -> 90f
                        }
                    },
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(u.place.name, style = ContrailTheme.type.title, color = colors.textPrimary)
                Eyebrow(
                    "in ${duration(u.minutes)} · ${sideWords(u.side)}" + (if (yours) " · your window" else "") + if (u.distanceKm > 15) " · ${u.distanceKm.toInt()} km off" else "",
                    color = if (yours) colors.accent else colors.textTertiary,
                )
                u.place.blurb?.let { Text(it, style = ContrailTheme.type.bodySmall, color = colors.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
private fun Boarding(state: CabinState, accent: Color) {
    val colors = ContrailTheme.colors
    val seg = state.segment ?: return
    val dep = state.origin?.zone?.let { TIME.withZone(it).format(seg.expectedDeparture) }
    Column(verticalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.sm)) {
        Eyebrow("Before take-off", color = accent)
        Text(listOfNotNull(dep?.let { "Departs $it" }, seg.gate?.let { "gate $it" }, seg.seat?.let { "seat $it" }).joinToString(" · "), style = ContrailTheme.type.headline, color = colors.textPrimary)
        Text("Cabin mode notices take-off on its own and switches to the flight. GPS works in airplane mode, so it keeps going with no Wi-Fi.", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
    }
}

/** On the ground at the other end: the welcome, local time, weather and what's next. */
@Composable
private fun Welcome(state: CabinState, accent: Color) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val to = state.destination
    GlassSurface(Modifier.fillMaxWidth(), shape = ContrailTheme.shapes.large, level = GlassLevel.Strong, glow = accent) {
        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Eyebrow("You've landed", color = accent)
            Text("Welcome to ${to?.city ?: state.segment?.destination}", style = ContrailTheme.type.displaySmall, color = colors.textPrimary)
            to?.zone?.let { z ->
                Text("It's ${TIME.withZone(z).format(Instant.ofEpochMilli(state.nowMs))} local time", style = ContrailTheme.type.title, color = colors.textSecondary)
            }
            state.pack?.weather?.let { Text("Weather on arrival: $it", style = ContrailTheme.type.body, color = colors.textSecondary) }
            state.segment?.arrivalTerminal?.let { Text("Terminal $it", style = ContrailTheme.type.body, color = colors.textSecondary) }
            if (state.captured.isNotEmpty()) Text("${state.captured.size} moments from this flight are in your Passport.", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
        }
    }
}

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)

private fun duration(minutes: Int): String = when {
    minutes < 1 -> "a minute"
    minutes < 60 -> "$minutes min"
    else -> "${minutes / 60}h ${(minutes % 60).toString().padStart(2, '0')}m"
}

private fun sideWords(s: Side): String = when (s) {
    Side.Left -> "on the left"
    Side.Right -> "on the right"
    Side.Ahead -> "ahead"
    Side.Behind -> "behind"
}

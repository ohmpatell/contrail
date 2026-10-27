package com.contrail.contrail.feature.trips.importing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.logbook.LogPreview
import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.core.designsystem.component.AccentButton
import com.contrail.contrail.core.designsystem.component.DetailScaffold
import com.contrail.contrail.core.designsystem.component.DottedGlobe
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.GlassSurface
import com.contrail.contrail.core.designsystem.component.MapArc
import com.contrail.contrail.core.designsystem.component.RiseIn
import com.contrail.contrail.core.designsystem.component.SplitFlapText
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.feature.passport.Odometer
import com.contrail.contrail.feature.trips.Tag
import com.contrail.contrail.feature.trips.TripFormat
import com.contrail.contrail.navigation.LocalNavigator
import java.util.Locale

@Composable
fun LogImportRoute(viewModel: LogImportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::read) }
    LogImportScreen(
        state = state,
        onBack = nav::back,
        onPick = { pick.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "text/plain", "application/vnd.ms-excel", "application/octet-stream")) },
        onSave = viewModel::save,
        onAgain = viewModel::reset,
        onDone = { nav.back(); nav.openLogbook() },
    )
}

@Composable
fun LogImportScreen(
    state: LogImportState,
    onBack: () -> Unit,
    onPick: () -> Unit = {},
    onSave: () -> Unit = {},
    onAgain: () -> Unit = {},
    onDone: () -> Unit = {},
) {
    val colors = ContrailTheme.colors
    val motion = ContrailTheme.motion
    DetailScaffold(
        title = when (state.stage) {
            LogStage.Intro -> "Import your flight log"
            LogStage.Reading -> "Reading your log"
            LogStage.Preview -> "Your flying, found"
            LogStage.Saving -> "Adding your flights"
            LogStage.Done -> "Welcome aboard"
        },
        eyebrow = "myFlightradar24 · Flighty · OpenFlights",
        accent = colors.passport,
        onBack = onBack,
    ) {
        state.message?.let { msg ->
            Row(
                Modifier.fillMaxWidth().clip(ContrailTheme.shapes.small).background(colors.statusDelayed.copy(alpha = 0.12f)).padding(ContrailTheme.spacing.md),
                horizontalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(ContrailIcons.Alert, null, tint = colors.statusDelayed, modifier = Modifier.size(18.dp))
                Text(msg, style = ContrailTheme.type.bodySmall, color = colors.textPrimary)
            }
        }
        AnimatedContent(state.stage, transitionSpec = { fadeIn(motion.fade()) togetherWith fadeOut(motion.fade(120)) }, label = "logStage") { stage ->
            Column(verticalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.lg)) {
                when (stage) {
                    LogStage.Intro -> Intro(onPick)
                    LogStage.Reading, LogStage.Saving -> Busy(if (stage == LogStage.Reading) "READING" else "BOARDING")
                    LogStage.Preview -> state.preview?.let { Preview(it, state, onSave, onAgain) }
                    LogStage.Done -> Done(state, onDone)
                }
            }
        }
    }
}

@Composable
private fun Intro(onPick: () -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    RiseIn(0) {
        GlassSurface(Modifier.fillMaxWidth(), glow = colors.passport.copy(alpha = 0.7f)) {
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(colors.passport.copy(alpha = 0.2f), Color.Transparent))))
            Column(Modifier.padding(spacing.xl), verticalArrangement = Arrangement.spacedBy(spacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
                StackedPasses()
                Text("Every flight you've logged, in one tap", style = ContrailTheme.type.displaySmall, color = colors.textPrimary, textAlign = TextAlign.Center)
                Text(
                    "Bring your history from myFlightradar24, Flighty or OpenFlights. Contrail reads the CSV on your phone, skips flights you already have, and builds your Passport from it.",
                    style = ContrailTheme.type.body,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                AccentButton("Choose the CSV file", onPick, icon = ContrailIcons.Import, accent = colors.passport, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    RiseIn(1) { HowTo("myFlightradar24", listOf("On a computer, sign in at my.flightradar24.com", "Click Settings (top right), then the Export tab", "Click Download CSV", "Email or Drive the file to your phone, then choose it here")) }
    RiseIn(2) { HowTo("Flighty", listOf("Profile tab → Settings (gear)", "Manage → Account Data", "Export Your Flights", "Save to Files, then choose it here, or share it straight to Contrail")) }
    RiseIn(3) {
        Text(
            "Times missing from a log are estimated from the distance and labelled “estimated”. Nothing leaves your phone.",
            style = ContrailTheme.type.bodySmall,
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun HowTo(app: String, steps: List<String>) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    GlassSurface(Modifier.fillMaxWidth(), shape = ContrailTheme.shapes.medium) {
        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Eyebrow("From $app", color = colors.passport)
            steps.forEachIndexed { i, s ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(22.dp).clip(ContrailTheme.shapes.chip).background(colors.passport.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = ContrailTheme.type.numericSmall, color = colors.passport)
                    }
                    Text(s, style = ContrailTheme.type.bodySmall, color = colors.textSecondary, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Three boarding passes fanned out, gently swaying. */
@Composable
private fun StackedPasses() {
    val colors = ContrailTheme.colors
    val motion = ContrailTheme.motion
    val sway by if (motion.ambientEnabled) {
        rememberInfiniteTransition(label = "passes").animateFloat(-1f, 1f, infiniteRepeatable(tween(3_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sway")
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    val tints = listOf(colors.accent, colors.statusOnTime, colors.passport)
    Canvas(Modifier.fillMaxWidth().height(130.dp)) {
        val w = size.width * 0.62f
        val h = size.height * 0.62f
        tints.forEachIndexed { i, tint ->
            val angle = (i - 1) * 9f + sway * 2.5f * (i - 1)
            val c = Offset(size.width / 2 + (i - 1) * 26.dp.toPx(), size.height / 2 + (1 - kotlin.math.abs(i - 1)) * -6.dp.toPx())
            rotate(angle, c) {
                val tl = Offset(c.x - w / 2, c.y - h / 2)
                drawRoundRect(colors.canvasRaised, tl, Size(w, h), CornerRadius(14.dp.toPx()))
                drawRoundRect(Brush.linearGradient(listOf(tint.copy(alpha = 0.55f), tint.copy(alpha = 0.12f)), tl, Offset(tl.x + w, tl.y + h)), tl, Size(w, h), CornerRadius(14.dp.toPx()))
                drawRoundRect(tint.copy(alpha = 0.8f), tl, Size(w, h), CornerRadius(14.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                for (k in 0 until 3) {
                    val y = tl.y + h * (0.3f + k * 0.2f)
                    drawLine(Color.White.copy(alpha = 0.5f - k * 0.12f), Offset(tl.x + w * 0.1f, y), Offset(tl.x + w * (0.6f - k * 0.12f), y), 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                }
                drawCircle(colors.canvas, 7.dp.toPx(), Offset(tl.x + w * 0.74f, tl.y))
                drawCircle(colors.canvas, 7.dp.toPx(), Offset(tl.x + w * 0.74f, tl.y + h))
            }
        }
    }
}

@Composable
private fun Busy(word: String) {
    val colors = ContrailTheme.colors
    GlassSurface(Modifier.fillMaxWidth(), glow = colors.passport.copy(alpha = 0.6f)) {
        Column(Modifier.padding(ContrailTheme.spacing.xxl).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.lg)) {
            StackedPasses()
            SplitFlapText(word, style = ContrailTheme.type.flap, color = colors.passport)
        }
    }
}

@Composable
private fun Preview(p: LogPreview, state: LogImportState, onSave: () -> Unit, onAgain: () -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val motion = ContrailTheme.motion
    val places = state.places
    val arcs = remember(p, places) {
        p.flights.map { it.origin to it.destination }.distinct().mapNotNull { (a, b) ->
            val x = places[a] ?: return@mapNotNull null
            val y = places[b] ?: return@mapNotNull null
            Geo.greatCircle(LatLon(x.lat, x.lon), LatLon(y.lat, y.lon), 40)
        }
    }
    val bloom = remember { Animatable(if (motion.reduced) 1f else 0f) }
    LaunchedEffect(arcs.size) { if (!motion.reduced) bloom.animateTo(1f, tween(2_400, easing = FastOutSlowInEasing)) }
    val drift by if (motion.ambientEnabled) {
        rememberInfiniteTransition(label = "globe").animateFloat(0f, 360f, infiniteRepeatable(tween(70_000, easing = LinearEasing)), label = "drift")
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    val home = p.flights.groupingBy { it.origin }.eachCount().maxByOrNull { it.value }?.key?.let { places[it] }
    RiseIn(0) {
        GlassSurface(Modifier.fillMaxWidth(), glow = colors.passport.copy(alpha = 0.7f)) {
            Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Eyebrow("From ${p.format.label}", color = colors.passport)
                        Odometer(if (p.flights.size == 1) "New flight" else "New flights", p.flights.size.toLong(), big = true)
                    }
                    p.years?.let { y ->
                        Text(if (y.first == y.last) "${y.first}" else "${y.first} – ${y.last}", style = ContrailTheme.type.numeric, color = colors.textSecondary)
                    }
                }
                Box(Modifier.fillMaxWidth().aspectRatio(1.15f)) {
                    DottedGlobe(
                        arcs = arcs.mapIndexed { i, a -> MapArc(a, progress = (bloom.value * (arcs.size + 3) - i * 0.5f).coerceIn(0f, 1f)) },
                        centerLon = (home?.lon?.toFloat() ?: -40f) + 40f + drift,
                        centerLat = (home?.lat?.toFloat() ?: 25f).coerceIn(-15f, 45f),
                        markers = places.values.map { LatLon(it.lat, it.lon) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Row {
                    Odometer("Kilometres", state.distanceKm, Modifier.weight(1.4f))
                    Odometer("Airports", places.size.toLong(), Modifier.weight(1f))
                    Odometer("Trips", p.trips.size.toLong(), Modifier.weight(1f))
                }
            }
        }
    }
    RiseIn(1) { YearBars(p) }
    if (p.duplicates > 0 || p.skipped > 0 || p.estimated > 0) {
        RiseIn(2) {
            GlassSurface(Modifier.fillMaxWidth(), shape = ContrailTheme.shapes.medium) {
                Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Eyebrow("Good to know")
                    if (p.duplicates > 0) Note(ContrailIcons.Check, colors.statusOnTime, "${p.duplicates} already in Contrail, skipped")
                    if (p.estimated > 0) Note(ContrailIcons.Clock, colors.statusDelayed, "${p.estimated} without times: estimated from distance")
                    if (p.skipped > 0) {
                        Note(
                            ContrailIcons.Alert, colors.statusCancelled,
                            "${p.skipped} row${if (p.skipped == 1) "" else "s"} left out" +
                                (if (p.unknownAirports.isNotEmpty()) " (airports not in the list: ${p.unknownAirports.take(6).joinToString(", ")})" else ""),
                        )
                    }
                }
            }
        }
    }
    RiseIn(3) {
        GlassSurface(Modifier.fillMaxWidth(), shape = ContrailTheme.shapes.medium) {
            Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Eyebrow("Most recent")
                p.flights.takeLast(6).asReversed().forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                        Text(s.origin, style = ContrailTheme.type.numeric, color = colors.textPrimary)
                        Icon(ContrailIcons.ArrowRight, null, tint = colors.textTertiary, modifier = Modifier.size(14.dp))
                        Text(s.destination, style = ContrailTheme.type.numeric, color = colors.textPrimary)
                        Text(
                            listOfNotNull(s.designator.ifBlank { null }, Airlines.byIata(s.airlineIata)?.name ?: s.airlineName).joinToString(" · "),
                            style = ContrailTheme.type.bodySmall, color = colors.textTertiary, maxLines = 1, modifier = Modifier.weight(1f),
                        )
                        Text(TripFormat.day(s.departureLocal), style = ContrailTheme.type.numericSmall, color = colors.textTertiary)
                    }
                }
                if (p.flights.size > 6) Text("…and ${p.flights.size - 6} more", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
            }
        }
    }
    AccentButton(
        if (p.flights.isEmpty()) "Nothing new to add" else "Add ${p.flights.size} flights to my Passport",
        onSave, icon = ContrailIcons.Check, accent = colors.passport, enabled = p.flights.isNotEmpty(), modifier = Modifier.fillMaxWidth(),
    )
    AccentButton("Choose a different file", onAgain, secondary = true, accent = colors.passport, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Note(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Text(text, style = ContrailTheme.type.bodySmall, color = ContrailTheme.colors.textSecondary)
    }
}

/** Flights per year as rising bars. */
@Composable
private fun YearBars(p: LogPreview) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val motion = ContrailTheme.motion
    val byYear = remember(p) { p.flights.groupingBy { it.departureLocal.year }.eachCount().toSortedMap() }
    if (byYear.size < 2) return
    val years = (byYear.firstKey()..byYear.lastKey()).toList().takeLast(16)
    val max = byYear.values.max()
    val grow = remember { Animatable(if (motion.reduced) 1f else 0f) }
    LaunchedEffect(Unit) { if (!motion.reduced) grow.animateTo(1f, tween(1_200, delayMillis = 300, easing = FastOutSlowInEasing)) }
    GlassSurface(Modifier.fillMaxWidth(), shape = ContrailTheme.shapes.medium) {
        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Eyebrow("Flights per year")
            Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalAlignment = Alignment.Bottom) {
                years.forEach { y ->
                    val n = byYear[y] ?: 0
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        if (n > 0) Text("$n", style = ContrailTheme.type.numericSmall, color = colors.textSecondary, maxLines = 1)
                        Box(
                            Modifier
                                .width(14.dp)
                                .height((80.dp * (n.toFloat() / max) * grow.value).coerceAtLeast(2.dp))
                                .clip(ContrailTheme.shapes.chip)
                                .background(Brush.verticalGradient(listOf(colors.passport, colors.accent.copy(alpha = 0.5f)))),
                        )
                    }
                }
            }
            Row {
                Text("${years.first()}", style = ContrailTheme.type.numericSmall, color = colors.textTertiary, modifier = Modifier.weight(1f))
                Text("${years.last()}", style = ContrailTheme.type.numericSmall, color = colors.textTertiary)
            }
        }
    }
}

@Composable
private fun Done(state: LogImportState, onDone: () -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    GlassSurface(Modifier.fillMaxWidth(), glow = colors.passport) {
        Box(Modifier.matchParentSize().background(Brush.radialGradient(listOf(colors.passport.copy(alpha = 0.3f), Color.Transparent))))
        Column(Modifier.padding(spacing.xl).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
            Icon(ContrailIcons.Stamp, null, tint = colors.passport, modifier = Modifier.size(56.dp))
            Odometer("Flights added", state.imported.toLong(), big = true)
            Text(
                String.format(Locale.US, "%,d km of your flying are now in your Passport.", state.distanceKm),
                style = ContrailTheme.type.body, color = colors.textSecondary, textAlign = TextAlign.Center,
            )
            Tag("Stamped", colors.passport, filled = true)
            AccentButton("Open my logbook", onDone, icon = ContrailIcons.Passport, accent = colors.passport, modifier = Modifier.fillMaxWidth())
        }
    }
}

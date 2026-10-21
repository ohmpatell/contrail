package com.contrail.contrail.feature.trips

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.dp
import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.SplitFlapText
import com.contrail.contrail.core.designsystem.component.TabScaffold
import com.contrail.contrail.core.designsystem.component.UpcomingFeature
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import kotlinx.coroutines.delay
import kotlin.math.atan2

@Composable
fun TripsScreen(modifier: Modifier = Modifier) {
    val etihad = Color(Airlines.byIcao("ETD")!!.accentArgb)
    TabScaffold(
        modifier = modifier,
        title = "Trips",
        subtitle = "Your next flights, with the aircraft itself as the hero.",
        eyebrow = "Coming next",
        accent = etihad,
        features = listOf(
            UpcomingFeature(ContrailIcons.Trips, "Glass flight cards", "Floating aircraft, a self-drawing route arc and a split-flap countdown to departure."),
            UpcomingFeature(ContrailIcons.Plane, "Cockpit-visit planner", "Your aircraft's whole day: the inbound leg, the next leg, and the turnaround at your arrival."),
            UpcomingFeature(ContrailIcons.Sparkle, "Boarding pass scan", "Add a flight from its barcode in seconds, even in airplane mode."),
        ),
    ) { BoardingPassPreview(etihad) }
}

@Composable
private fun BoardingPassPreview(accent: Color) {
    val spacing = ContrailTheme.spacing
    val colors = ContrailTheme.colors
    Column(Modifier.padding(spacing.xl), verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Sample · Etihad", color = accent)
                Text("EY 140", style = ContrailTheme.type.displaySmall, color = colors.textPrimary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Eyebrow("Boeing 787-9")
                Text("A6-BLT", style = ContrailTheme.type.numeric, color = colors.textSecondary)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            AirportFlap("YYZ", "Toronto")
            RouteArc(accent, Modifier.weight(1f).height(56.dp))
            AirportFlap("AUH", "Abu Dhabi", alignEnd = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xl)) {
            CountdownBlock(accent)
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Eyebrow("Gate")
                SplitFlapText("E73", style = ContrailTheme.type.flap)
            }
        }
    }
}

@Composable
private fun AirportFlap(code: String, city: String, alignEnd: Boolean = false) {
    Column(
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.xs),
    ) {
        SplitFlapText(code, style = ContrailTheme.type.flap, haptics = false)
        Text(city, style = ContrailTheme.type.bodySmall, color = ContrailTheme.colors.textTertiary)
    }
}

@Composable
private fun CountdownBlock(accent: Color) {
    // A gently ticking countdown so the preview feels alive.
    var seconds by remember { mutableLongStateOf(3L * 86_400 + 14 * 3_600 + 22 * 60 + 5) }
    val motion = ContrailTheme.motion
    LaunchedEffect(motion.ambientEnabled) {
        while (motion.ambientEnabled) {
            delay(1_000)
            seconds -= 1
        }
    }
    val d = seconds / 86_400
    val h = (seconds % 86_400) / 3_600
    val m = (seconds % 3_600) / 60
    val s = seconds % 60
    Column(verticalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.xs)) {
        Eyebrow("Departs in")
        Text(
            "%dd %02d:%02d:%02d".format(d, h, m, s),
            style = ContrailTheme.type.numericLarge,
            color = accent,
        )
    }
}

/** A great-circle style arc that draws itself, with a plane riding the tip. */
@Composable
private fun RouteArc(accent: Color, modifier: Modifier) {
    val motion = ContrailTheme.motion
    val progress = remember { Animatable(if (motion.reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!motion.reduced) {
            delay(350)
            progress.animateTo(1f, tween(1400))
        }
    }
    val hairline = ContrailTheme.colors.hairline
    val plane = rememberVectorPainter(ContrailIcons.Plane)
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val path = Path().apply {
                moveTo(0f, size.height * 0.8f)
                quadraticTo(size.width / 2f, -size.height * 0.45f, size.width, size.height * 0.8f)
            }
            drawPath(path, hairline, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))))
            val measure = PathMeasure().apply { setPath(path, false) }
            val drawn = Path()
            val length = measure.length * progress.value
            measure.getSegment(0f, length, drawn, true)
            drawPath(
                drawn,
                Brush.horizontalGradient(listOf(accent.copy(alpha = 0.2f), accent)),
                style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round),
            )
            val tip = measure.getPosition(length.coerceAtLeast(0.01f))
            val tangent = measure.getTangent(length.coerceAtLeast(0.01f))
            val angle = Math.toDegrees(atan2(tangent.y, tangent.x).toDouble()).toFloat() + 90f
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.5f), Color.Transparent), center = tip, radius = 16.dp.toPx()), radius = 16.dp.toPx(), center = tip)
            val s = 18.dp.toPx()
            rotate(angle, pivot = tip) {
                translate(tip.x - s / 2, tip.y - s / 2) {
                    with(plane) { draw(Size(s, s), colorFilter = ColorFilter.tint(Color.White)) }
                }
            }
            drawCircle(accent, radius = 3.dp.toPx(), center = Offset(0f, size.height * 0.8f))
            drawCircle(accent.copy(alpha = 0.5f), radius = 3.dp.toPx(), center = Offset(size.width, size.height * 0.8f))
        }
    }
}

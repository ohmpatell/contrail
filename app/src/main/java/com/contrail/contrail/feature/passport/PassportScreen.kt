package com.contrail.contrail.feature.passport

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.TabScaffold
import com.contrail.contrail.core.designsystem.component.UpcomingFeature
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PassportScreen(modifier: Modifier = Modifier) {
    val violet = ContrailTheme.colors.passport
    TabScaffold(
        modifier = modifier,
        title = "Passport",
        subtitle = "Every route you've flown, every type you've collected.",
        eyebrow = "On the roadmap",
        accent = violet,
        features = listOf(
            UpcomingFeature(ContrailIcons.Passport, "Lifetime globe", "Your routes bloom across the globe as glowing arcs."),
            UpcomingFeature(ContrailIcons.Sparkle, "The collection", "Every aircraft type as a livery render. Rare ones unlock with a holographic card."),
            UpcomingFeature(ContrailIcons.Trips, "Year in review", "A swipeable story of the year's flying."),
        ),
    ) { GlobeHero(violet) }
}

@Composable
private fun GlobeHero(accent: Color) {
    val colors = ContrailTheme.colors
    val motion = ContrailTheme.motion
    val transition = rememberInfiniteTransition(label = "globe")
    val spin by if (motion.ambientEnabled) {
        transition.animateFloat(0f, 360f, infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Restart), label = "spin")
    } else {
        remember { mutableFloatStateOf(20f) }
    }
    val bloom = remember { Animatable(if (motion.reduced) 1f else 0f) }
    LaunchedEffect(Unit) { if (!motion.reduced) bloom.animateTo(1f, tween(1800)) }

    Column(Modifier.padding(ContrailTheme.spacing.xl), verticalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.lg)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.7f)) {
            Canvas(Modifier.matchParentSize()) {
                val r = size.height / 2 * 0.96f
                val c = Offset(size.width / 2, size.height / 2)
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.18f), Color.Transparent), c, r * 1.3f), r * 1.3f, c)
                drawCircle(colors.canvasSunken.copy(alpha = 0.7f), r, c)
                drawCircle(colors.glassBorderTop, r, c, style = Stroke(1.dp.toPx()))
                // Meridians and parallels of an orthographic globe.
                for (lon in 0 until 180 step 30) {
                    val a = Math.toRadians((lon + spin).toDouble())
                    val w = (r * cos(a)).toFloat()
                    drawOval(
                        colors.hairline,
                        topLeft = Offset(c.x - kotlin.math.abs(w), c.y - r),
                        size = androidx.compose.ui.geometry.Size(kotlin.math.abs(w) * 2, r * 2),
                        style = Stroke(1.dp.toPx()),
                    )
                }
                for (lat in listOf(-60, -30, 0, 30, 60)) {
                    val y = c.y - r * sin(Math.toRadians(lat.toDouble())).toFloat()
                    val half = r * cos(Math.toRadians(lat.toDouble())).toFloat()
                    drawLine(colors.hairline, Offset(c.x - half, y), Offset(c.x + half, y), 1.dp.toPx())
                }
                // Route arcs bloom out from a home airport.
                val home = Offset(c.x - r * 0.35f, c.y - r * 0.25f)
                val targets = listOf(Offset(c.x + r * 0.55f, c.y + r * 0.05f), Offset(c.x + r * 0.2f, c.y + r * 0.55f), Offset(c.x - r * 0.65f, c.y + r * 0.35f))
                targets.forEachIndexed { i, t ->
                    val p = ((bloom.value * 1.4f) - i * 0.2f).coerceIn(0f, 1f)
                    val mid = Offset((home.x + t.x) / 2, (home.y + t.y) / 2 - r * 0.35f)
                    val path = Path().apply {
                        moveTo(home.x, home.y)
                        val steps = 40
                        for (s in 1..(steps * p).toInt()) {
                            val u = s / steps.toFloat()
                            val x = (1 - u) * (1 - u) * home.x + 2 * (1 - u) * u * mid.x + u * u * t.x
                            val y = (1 - u) * (1 - u) * home.y + 2 * (1 - u) * u * mid.y + u * u * t.y
                            lineTo(x, y)
                        }
                    }
                    drawPath(path, accent.copy(alpha = 0.35f), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
                    drawPath(path, accent, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
                    if (p >= 1f) drawCircle(accent, 3.dp.toPx(), t)
                }
                drawCircle(colors.textPrimary, 4.dp.toPx(), home)
                drawCircle(accent.copy(alpha = 0.4f), 9.dp.toPx() * (0.6f + 0.4f * sin(spin / 360f * 2 * PI.toFloat() * 6).let { it * it }), home, style = Stroke(1.5.dp.toPx()))
            }
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Odometer("Flights", "0")
            Odometer("Distance", "0 km")
            Odometer("Types", "0")
            Odometer("Tails", "0")
        }
    }
}

@Composable
private fun Odometer(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.xxs)) {
        Text(value, style = ContrailTheme.type.numeric, color = ContrailTheme.colors.textPrimary)
        Eyebrow(label)
    }
}

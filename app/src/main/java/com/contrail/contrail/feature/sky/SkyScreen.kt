package com.contrail.contrail.feature.sky

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.TabScaffold
import com.contrail.contrail.core.designsystem.component.UpcomingFeature
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.AltitudeRamp
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SkyScreen(modifier: Modifier = Modifier) {
    TabScaffold(
        modifier = modifier,
        title = "Sky",
        subtitle = "What's overhead right now, and what's worth looking up for.",
        eyebrow = "On the roadmap",
        accent = ContrailTheme.colors.statusAirborne,
        features = listOf(
            UpcomingFeature(ContrailIcons.Locate, "Closest plane now", "Distance, bearing and a countdown to its closest approach."),
            UpcomingFeature(ContrailIcons.Sky, "Sky View", "Point your camera at the sky and labels pin to each aircraft."),
            UpcomingFeature(ContrailIcons.Signal, "Proximity alerts", "Rare types, military, emergencies or a watched tail passing within your radius."),
        ),
    ) { ScopeHero() }
}

/** A sweeping proximity scope with a few contacts in the altitude ramp. */
@Composable
private fun ScopeHero() {
    val colors = ContrailTheme.colors
    val motion = ContrailTheme.motion
    val transition = rememberInfiniteTransition(label = "scope")
    val sweep by if (motion.ambientEnabled) {
        transition.animateFloat(0f, 360f, infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
    } else {
        remember { mutableFloatStateOf(300f) }
    }
    val contacts = remember {
        listOf(
            Triple(40f, 0.35f, 4_000),
            Triple(130f, 0.7f, 36_000),
            Triple(215f, 0.55f, 21_000),
            Triple(300f, 0.82f, 11_000),
        )
    }
    val accent = colors.statusAirborne
    Column(Modifier.padding(ContrailTheme.spacing.xl), verticalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Closest now · preview", color = accent)
                Text("ACA112", style = ContrailTheme.type.displaySmall, color = colors.textPrimary)
                Text("Boeing 787-9 · 26,000 ft", style = ContrailTheme.type.bodySmall, color = colors.textSecondary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("4.2", style = ContrailTheme.type.numericLarge, color = accent)
                Eyebrow("km NE")
            }
        }
        Box(Modifier.fillMaxWidth().aspectRatio(1.6f)) {
            Canvas(Modifier.matchParentSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.height / 2 * 0.95f
                for (i in 1..3) {
                    drawCircle(colors.hairline, radius * i / 3f, center, style = Stroke(1.dp.toPx()))
                }
                drawLine(colors.hairline, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y))
                drawLine(colors.hairline, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius))
                rotate(sweep, center) {
                    drawArc(
                        Brush.sweepGradient(
                            0f to Color.Transparent, 0.82f to Color.Transparent, 1f to accent.copy(alpha = 0.35f),
                            center = center,
                        ),
                        startAngle = 0f, sweepAngle = 360f, useCenter = true,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    )
                }
                contacts.forEach { (bearing, dist, alt) ->
                    val rad = Math.toRadians(bearing.toDouble() - 90)
                    val p = Offset(center.x + (cos(rad) * radius * dist).toFloat(), center.y + (sin(rad) * radius * dist).toFloat())
                    // Contacts glow brightest just after the sweep passes them.
                    val since = ((sweep - bearing + 360f) % 360f) / 360f
                    val glow = (1f - since).coerceIn(0.25f, 1f)
                    val c = AltitudeRamp.colorFor(alt)
                    drawCircle(c.copy(alpha = 0.25f * glow), 10.dp.toPx(), p)
                    drawCircle(c.copy(alpha = glow), 3.5.dp.toPx(), p)
                }
                drawCircle(colors.textPrimary, 4.dp.toPx(), center)
                drawCircle(accent.copy(alpha = 0.3f), 9.dp.toPx(), center, style = Stroke(1.5.dp.toPx()))
            }
        }
    }
}

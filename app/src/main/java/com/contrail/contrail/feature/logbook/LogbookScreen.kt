package com.contrail.contrail.feature.logbook

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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.TabScaffold
import com.contrail.contrail.core.designsystem.component.UpcomingFeature
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.ContrailTheme

@Composable
fun LogbookScreen(modifier: Modifier = Modifier) {
    val amber = ContrailTheme.colors.statusDelayed
    TabScaffold(
        modifier = modifier,
        title = "Logbook",
        subtitle = "Your spotter tools: what you saw, and what you're waiting for.",
        eyebrow = "On the roadmap",
        accent = amber,
        features = listOf(
            UpcomingFeature(ContrailIcons.Logbook, "Spotting log", "Snap a photo and Contrail suggests which aircraft it was from its time and place."),
            UpcomingFeature(ContrailIcons.Follow, "Watchlist", "Tails, types or airlines that ping you when they go airborne or come near."),
            UpcomingFeature(ContrailIcons.Sparkle, "Spotter stats", "Types and airlines you've spotted, separate from the ones you've flown."),
        ),
    ) { ViewfinderHero(amber) }
}

@Composable
private fun ViewfinderHero(accent: Color) {
    val colors = ContrailTheme.colors
    val motion = ContrailTheme.motion
    val transition = rememberInfiniteTransition(label = "viewfinder")
    val drift by if (motion.ambientEnabled) {
        transition.animateFloat(-1f, 1f, infiniteRepeatable(tween(3800), RepeatMode.Reverse), label = "drift")
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    Column(Modifier.padding(ContrailTheme.spacing.xl), verticalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.md)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.9f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val len = 22.dp.toPx()
                val w = 2.dp.toPx()
                val inset = 4.dp.toPx()
                val corners = listOf(
                    Offset(inset, inset) to Pair(1, 1),
                    Offset(size.width - inset, inset) to Pair(-1, 1),
                    Offset(inset, size.height - inset) to Pair(1, -1),
                    Offset(size.width - inset, size.height - inset) to Pair(-1, -1),
                )
                corners.forEach { (p, d) ->
                    drawLine(accent, p, Offset(p.x + len * d.first, p.y), w, StrokeCap.Round)
                    drawLine(accent, p, Offset(p.x, p.y + len * d.second), w, StrokeCap.Round)
                }
                val c = Offset(size.width / 2, size.height / 2)
                drawLine(colors.hairline, Offset(c.x - 14.dp.toPx(), c.y), Offset(c.x + 14.dp.toPx(), c.y), 1.dp.toPx())
                drawLine(colors.hairline, Offset(c.x, c.y - 14.dp.toPx()), Offset(c.x, c.y + 14.dp.toPx()), 1.dp.toPx())
            }
            Icon(
                ContrailIcons.Plane,
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier
                    .size(64.dp)
                    .graphicsLayer {
                        rotationZ = 62f + drift * 2f
                        translationX = drift * 10.dp.toPx()
                        translationY = -drift * 4.dp.toPx()
                    },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Suggested match · preview", color = accent)
                Text("C-FFNW", style = ContrailTheme.type.displaySmall, color = colors.textPrimary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("98%", style = ContrailTheme.type.numericLarge, color = accent)
                Eyebrow("Confidence")
            }
        }
    }
}

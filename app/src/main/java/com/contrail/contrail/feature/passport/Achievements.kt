package com.contrail.contrail.feature.passport

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.SectionCard
import com.contrail.contrail.core.designsystem.theme.ContrailTheme

/** Metal gradients for the medal tiers: dark edge, face, highlight. */
object TierPalette {
    fun colors(t: Tier): List<Color> = when (t) {
        Tier.Bronze -> listOf(Color(0xFF7A4A2A), Color(0xFFD08C5A), Color(0xFFFFC9A0))
        Tier.Silver -> listOf(Color(0xFF6C7891), Color(0xFFC9D2E4), Color(0xFFFFFFFF))
        Tier.Gold -> listOf(Color(0xFF8A6418), Color(0xFFF2C14E), Color(0xFFFFF0B8))
        Tier.Platinum -> listOf(Color(0xFF5B4FA8), Color(0xFF9EE6FF), Color(0xFFFFD6FA))
    }
}

/** A minted medal: metal face and rim when earned, a dark disc with a progress ring when not. */
@Composable
fun Medal(a: Achievement, modifier: Modifier = Modifier) {
    val colors = ContrailTheme.colors
    val motion = ContrailTheme.motion
    val tier = a.tier
    val shine by if (tier != null && tier >= Tier.Gold && motion.ambientEnabled) {
        rememberInfiniteTransition(label = "medal").animateFloat(-0.6f, 1.6f, infiniteRepeatable(tween(3_600, easing = LinearEasing), RepeatMode.Restart), label = "shine")
    } else {
        remember { mutableFloatStateOf(-1f) }
    }
    val ring = remember { Animatable(if (motion.reduced) a.progress else 0f) }
    LaunchedEffect(a.progress) { if (!motion.reduced) ring.animateTo(a.progress, tween(1_200, easing = FastOutSlowInEasing)) }
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val r = size.minDimension / 2f * 0.92f
            val c = center
            if (tier != null) {
                val (dark, face, light) = TierPalette.colors(tier)
                drawCircle(Brush.radialGradient(listOf(face.copy(alpha = 0.35f), Color.Transparent), c, r * 1.5f), r * 1.5f, c)
                drawCircle(Brush.linearGradient(listOf(light, face, dark), Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r)), r, c)
                drawCircle(Brush.linearGradient(listOf(dark, face, light), Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r)), r * 0.8f, c)
                drawCircle(light.copy(alpha = 0.6f), r * 0.8f, c, style = Stroke(1.dp.toPx()))
                // Reeded edge.
                for (i in 0 until 48) {
                    val ang = Math.toRadians(i * 7.5)
                    val p1 = Offset(c.x + (kotlin.math.cos(ang) * r * 0.9f).toFloat(), c.y + (kotlin.math.sin(ang) * r * 0.9f).toFloat())
                    val p2 = Offset(c.x + (kotlin.math.cos(ang) * r * 0.98f).toFloat(), c.y + (kotlin.math.sin(ang) * r * 0.98f).toFloat())
                    drawLine(dark.copy(alpha = 0.35f), p1, p2, 1.dp.toPx())
                }
                if (shine > -0.5f) {
                    drawCircle(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                            start = Offset(size.width * (shine - 0.3f), 0f), end = Offset(size.width * (shine + 0.1f), size.height),
                        ),
                        r, c, blendMode = BlendMode.Plus,
                    )
                }
            } else {
                drawCircle(colors.canvasSunken.copy(alpha = 0.9f), r, c)
                drawCircle(colors.hairline, r, c, style = Stroke(1.dp.toPx()))
                drawCircle(colors.textDisabled.copy(alpha = 0.25f), r * 0.86f, c, style = Stroke(3.dp.toPx()))
                if (ring.value > 0f) {
                    drawArc(
                        colors.passport, -90f, 360f * ring.value, useCenter = false,
                        topLeft = Offset(c.x - r * 0.86f, c.y - r * 0.86f), size = Size(r * 1.72f, r * 1.72f),
                        style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
        }
        val glyphColor = if (tier != null) TierPalette.colors(tier).first() else colors.textDisabled
        Text(
            a.glyph,
            style = ContrailTheme.type.headline.copy(fontSize = if (a.glyph.length > 3) 13.sp else 18.sp),
            color = glyphColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** The badge cabinet: earned medals first, then the ones you're working toward. Tap one for details. */
@Composable
fun AchievementsCard(list: List<Achievement>) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var all by rememberSaveable { mutableStateOf(false) }
    val sorted = remember(list) { list.sortedWith(compareByDescending<Achievement> { it.tier?.ordinal ?: -1 }.thenByDescending { it.progress }) }
    val shown = if (all) sorted else sorted.take(8)
    SectionCard("Achievements", trailing = {
        Text("${list.count { it.unlocked }} of ${list.size}", style = ContrailTheme.type.numeric, color = colors.passport)
    }) {
        shown.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                row.forEach { a ->
                    Column(
                        Modifier.weight(1f).clip(ContrailTheme.shapes.small).clickable(role = Role.Button) { open = if (open == a.id) null else a.id }.padding(vertical = spacing.xs),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        Medal(a, Modifier.fillMaxWidth().padding(horizontal = spacing.xs))
                        Text(
                            a.title, style = ContrailTheme.type.label.copy(fontSize = 11.sp, lineHeight = 13.sp),
                            color = if (a.unlocked) colors.textPrimary else colors.textTertiary,
                            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
            val detail = row.firstOrNull { it.id == open }
            AnimatedVisibility(detail != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                detail?.let { a ->
                    val tint = a.tier?.let { TierPalette.colors(it)[1] } ?: colors.passport
                    Column(
                        Modifier.fillMaxWidth().clip(ContrailTheme.shapes.small).padding(spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(a.title, style = ContrailTheme.type.title, color = colors.textPrimary, modifier = Modifier.weight(1f))
                            Eyebrow(a.tier?.label ?: "Locked", color = tint)
                        }
                        Text(a.description, style = ContrailTheme.type.bodySmall, color = colors.textSecondary)
                        Text(a.earnedOn ?: a.progressLabel, style = ContrailTheme.type.numericSmall, color = tint)
                    }
                }
            }
        }
        if (list.size > 8) {
            Text(
                if (all) "Show fewer" else "Show all ${list.size}",
                style = ContrailTheme.type.label,
                color = colors.passport,
                modifier = Modifier.clickable(role = Role.Button) { all = !all },
            )
        }
    }
}

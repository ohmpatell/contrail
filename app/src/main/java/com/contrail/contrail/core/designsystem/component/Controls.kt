package com.contrail.contrail.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.contrail.contrail.core.designsystem.theme.ContrailTheme

/** Small uppercase label above a value or section. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = ContrailTheme.colors.textTertiary) {
    Text(text.uppercase(), modifier = modifier, style = ContrailTheme.type.eyebrow, color = color, maxLines = 1)
}

/** Scale-down-on-press feedback shared by every tappable glass control. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, ContrailTheme.motion.snappy(), label = "press")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Round frosted icon button used over the map and on headers. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    size: Dp = ContrailTheme.sizes.touchTarget,
) {
    val colors = ContrailTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val tint by animateColorAsState(if (active) colors.onAccent else colors.textPrimary, ContrailTheme.motion.fade(), label = "tint")
    val fill by animateColorAsState(if (active) colors.accent else Color.Transparent, ContrailTheme.motion.fade(), label = "fill")
    GlassSurface(
        modifier = modifier
            .size(size)
            .pressScale(interaction)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
        shape = ContrailTheme.shapes.chip,
    ) {
        Box(Modifier.matchParentSize().background(fill), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(ContrailTheme.sizes.icon))
        }
    }
}

/** Pill chip for filters. Selected chips fill with the accent. */
@Composable
fun ContrailChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    accent: Color = ContrailTheme.colors.accent,
) {
    val colors = ContrailTheme.colors
    val motion = ContrailTheme.motion
    val interaction = remember { MutableInteractionSource() }
    val fill by animateColorAsState(if (selected) accent.copy(alpha = 0.22f) else colors.glassTint, motion.fade(), label = "chipFill")
    val border by animateColorAsState(if (selected) accent.copy(alpha = 0.9f) else colors.hairline, motion.fade(), label = "chipBorder")
    val text by animateColorAsState(if (selected) colors.textPrimary else colors.textSecondary, motion.fade(), label = "chipText")
    Row(
        modifier = modifier
            .pressScale(interaction)
            .clip(ContrailTheme.shapes.chip)
            .background(fill)
            .border(ContrailTheme.sizes.hairline, border, ContrailTheme.shapes.chip)
            .clickable(interaction, indication = null, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = ContrailTheme.spacing.md, vertical = ContrailTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.xs + ContrailTheme.spacing.xxs),
    ) {
        leading?.invoke()
        Text(label, style = ContrailTheme.type.label, color = text, maxLines = 1)
    }
}

/** Label + monospaced value, used for altitude, speed, squawk and friends. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    valueColor: Color = ContrailTheme.colors.textPrimary,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.xs)) {
        Eyebrow(label)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = ContrailTheme.type.numeric, color = valueColor, maxLines = 1, overflow = TextOverflow.Clip)
            if (unit != null) {
                Spacer(Modifier.width(ContrailTheme.spacing.xxs + ContrailTheme.spacing.xxs))
                Text(unit, style = ContrailTheme.type.numericSmall, color = ContrailTheme.colors.textTertiary, maxLines = 1)
            }
        }
    }
}

/** A breathing dot that says "this is live". */
@Composable
fun LivePulse(modifier: Modifier = Modifier, color: Color = ContrailTheme.colors.statusOnTime, size: Dp = 8.dp) {
    val motion = ContrailTheme.motion
    val transition = rememberInfiniteTransition(label = "live")
    val pulse by if (motion.ambientEnabled) {
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Restart), label = "pulse")
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }
    Canvas(modifier.size(size * 2.4f)) {
        val r = size.toPx() / 2f
        drawCircle(color.copy(alpha = (1f - pulse) * 0.45f), radius = r + r * 1.4f * pulse)
        drawCircle(color, radius = r)
    }
}

/** Compact rarity badge: holographic gold, silver shimmer, or blue. */
@Composable
fun RarityBadge(label: String, colors: List<Color>, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(ContrailTheme.shapes.chip)
            .background(Brush.linearGradient(colors.map { it.copy(alpha = 0.28f) }))
            .border(ContrailTheme.sizes.hairline, Brush.linearGradient(colors), ContrailTheme.shapes.chip)
            .padding(horizontal = ContrailTheme.spacing.sm, vertical = ContrailTheme.spacing.xxs + 1.dp),
    ) {
        Text(label.uppercase(), style = ContrailTheme.type.eyebrow, color = colors.first(), maxLines = 1)
    }
}

/**
 * Minimal line chart with a soft gradient fill. [values] are plotted left to right; the
 * zero line is drawn when the range spans it (vertical speed climbs above, descents below).
 */
@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = ContrailTheme.colors.accent,
    showZeroLine: Boolean = true,
) {
    val hairline = ContrailTheme.colors.hairline
    Canvas(modifier) {
        if (values.size < 2) {
            drawLine(hairline, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1.dp.toPx())
            return@Canvas
        }
        val max = maxOf(values.max(), 0f)
        val min = minOf(values.min(), 0f)
        val range = (max - min).takeIf { it > 1e-3f } ?: 1f
        val pad = 3.dp.toPx()
        val h = size.height - pad * 2
        fun y(v: Float) = pad + h - (v - min) / range * h
        val stepX = size.width / (values.size - 1)
        val line = Path().apply {
            values.forEachIndexed { i, v ->
                val x = i * stepX
                if (i == 0) moveTo(x, y(v)) else {
                    val px = (i - 1) * stepX
                    val cx = (px + x) / 2f
                    cubicTo(cx, y(values[i - 1]), cx, y(v), x, y(v))
                }
            }
        }
        if (showZeroLine && min < 0f && max > 0f) {
            drawLine(hairline, Offset(0f, y(0f)), Offset(size.width, y(0f)), strokeWidth = 1.dp.toPx())
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, y(0f))
            lineTo(0f, y(0f))
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), Color.Transparent)))
        drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, radius = 3.dp.toPx(), center = Offset(size.width, y(values.last())))
    }
}

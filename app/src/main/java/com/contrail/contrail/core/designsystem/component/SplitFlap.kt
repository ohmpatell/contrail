package com.contrail.contrail.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import kotlinx.coroutines.delay

/** The characters a flap cycles through, in board order. */
const val FlapAlphabet = " ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789:.-/→+"

/** Longest flip sequence per character, so long jumps still settle quickly. */
private const val MAX_FLIPS = 9

/** Characters a flap passes through to get from [from] to [to], ending with [to]. */
internal fun flapSequence(from: Char, to: Char, alphabet: String = FlapAlphabet): List<Char> {
    if (from == to) return emptyList()
    val start = alphabet.indexOf(from)
    val end = alphabet.indexOf(to)
    if (start < 0 || end < 0) return listOf(to)
    val distance = (end - start + alphabet.length) % alphabet.length
    val steps = minOf(distance, MAX_FLIPS)
    return (steps - 1 downTo 0).map { back ->
        alphabet[(end - back + alphabet.length) % alphabet.length]
    }
}

/**
 * Departure-board text. Each character flips through the alphabet into place, staggered
 * left to right, with an optional soft haptic tick as each one lands.
 */
@Composable
fun SplitFlapText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = ContrailTheme.type.flap,
    color: Color = ContrailTheme.colors.textPrimary,
    tiles: Boolean = true,
    tileColor: Color = ContrailTheme.colors.flapTile,
    haptics: Boolean = false,
    gap: Dp = 3.dp,
) {
    val density = LocalDensity.current
    val fontDp = with(density) { style.fontSize.toDp() }
    val cellWidth = if (tiles) fontDp * 0.98f else fontDp * 0.66f
    val cellHeight = if (tiles) fontDp * 1.48f else fontDp * 1.25f
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = text },
        horizontalArrangement = Arrangement.spacedBy(if (tiles) gap else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        text.uppercase().forEachIndexed { index, char ->
            FlapCell(
                target = char,
                index = index,
                width = cellWidth,
                height = cellHeight,
                style = style,
                color = color,
                tileColor = if (tiles) tileColor else Color.Transparent,
                haptics = haptics,
            )
        }
    }
}

@Composable
private fun FlapCell(
    target: Char,
    index: Int,
    width: Dp,
    height: Dp,
    style: TextStyle,
    color: Color,
    tileColor: Color,
    haptics: Boolean,
) {
    val motion = ContrailTheme.motion
    val haptic = LocalHapticFeedback.current
    var current by remember { mutableStateOf(if (motion.reduced) target else ' ') }
    var next by remember { mutableStateOf(current) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(target) {
        if (motion.reduced) {
            current = target
            next = target
            return@LaunchedEffect
        }
        delay(index.toLong() * motion.flapStaggerMillis)
        val sequence = flapSequence(current, target)
        sequence.forEachIndexed { step, char ->
            next = char
            progress.snapTo(0f)
            // Each flip is a little quicker than the last, like a board spinning down.
            val duration = (motion.flapStepMillis * (1f - step * 0.04f)).toInt().coerceAtLeast(40)
            progress.animateTo(1f, tween(duration, easing = FastOutLinearInEasing))
            current = char
        }
        progress.snapTo(0f)
        if (haptics && sequence.isNotEmpty()) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }

    val p = progress.value
    val tileShape = ContrailTheme.shapes.flapTile
    val edge = ContrailTheme.colors.flapTileEdge
    Box(Modifier.size(width, height)) {
        // Static faces: the top half already shows the next character, the bottom still the current one.
        FlapFace(next, Half.Top, width, height, style, color, tileColor, tileShape)
        FlapFace(current, Half.Bottom, width, height, style, color, tileColor, tileShape)
        // The moving flap.
        if (p > 0f && p < 1f) {
            if (p < 0.5f) {
                FlapFace(current, Half.Top, width, height, style, color, tileColor, tileShape, rotation = -180f * p, shade = p * 1.2f)
            } else {
                FlapFace(next, Half.Bottom, width, height, style, color, tileColor, tileShape, rotation = 180f * (1f - p), shade = (1f - p) * 0.9f)
            }
        }
        if (tileColor != Color.Transparent) {
            // The hinge between the two halves.
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(width, 1.dp)
                    .background(edge.copy(alpha = 0.85f)),
            )
        }
    }
}

private enum class Half { Top, Bottom }

@Composable
private fun FlapFace(
    char: Char,
    half: Half,
    width: Dp,
    height: Dp,
    style: TextStyle,
    color: Color,
    tileColor: Color,
    tileShape: androidx.compose.ui.graphics.Shape,
    rotation: Float = 0f,
    shade: Float = 0f,
) {
    Box(
        modifier = Modifier
            .size(width, height)
            .graphicsLayer {
                rotationX = rotation
                transformOrigin = TransformOrigin(0.5f, 0.5f)
                cameraDistance = 14f * density
            }
            .drawWithContent {
                val mid = size.height / 2f
                if (half == Half.Top) {
                    clipRect(bottom = mid) { this@drawWithContent.drawContent() }
                } else {
                    clipRect(top = mid) { this@drawWithContent.drawContent() }
                }
                if (shade > 0f) {
                    val top = if (half == Half.Top) 0f else mid
                    drawRect(
                        Color.Black.copy(alpha = shade.coerceIn(0f, 0.6f)),
                        topLeft = androidx.compose.ui.geometry.Offset(0f, top),
                        size = androidx.compose.ui.geometry.Size(size.width, mid),
                    )
                }
            }
            .background(tileColor, tileShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (char == ' ') "" else char.toString(),
            style = style,
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
    }
}

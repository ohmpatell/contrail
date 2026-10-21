package com.contrail.contrail.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * Haze state for whatever sits behind glass on the current screen (the aurora canvas,
 * or the radar map). Glass surfaces blur it when present.
 */
val LocalBackdropHaze = compositionLocalOf<HazeState?> { null }

/** Haze state for the whole tab content, blurred by app chrome such as the nav bar. */
val LocalChromeHaze = compositionLocalOf<HazeState?> { null }

enum class GlassLevel { Regular, Strong }

/**
 * Frosted glass: 12% white tint over a background blur, with a light-catching top edge.
 * Falls back to a deeper translucent tint where blur is unavailable.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = ContrailTheme.shapes.large,
    level: GlassLevel = GlassLevel.Regular,
    hazeState: HazeState? = LocalBackdropHaze.current,
    tint: Color = if (level == GlassLevel.Strong) ContrailTheme.colors.glassTintStrong else ContrailTheme.colors.glassTint,
    glow: Color? = null,
    blurRadius: Dp = ContrailTheme.sizes.glassBlur,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = ContrailTheme.colors
    val baseUnderTint = colors.canvasRaised.copy(alpha = if (hazeState != null) 0.38f else 0.82f)
    val style = remember(blurRadius, baseUnderTint) {
        HazeBlurStyle {
            blurRadius(blurRadius)
            backgroundColor(baseUnderTint)
            colorEffects(listOf(HazeColorEffect.tint(baseUnderTint)))
            noiseFactor(0.12f)
        }
    }
    val borderBrush = remember(colors, glow) {
        Brush.verticalGradient(
            listOf(glow?.copy(alpha = 0.7f) ?: colors.glassBorderTop, colors.glassBorderBottom),
        )
    }
    val sheen = remember(tint, glow) {
        Brush.verticalGradient(
            listOf(
                (glow?.copy(alpha = 0.16f) ?: tint.copy(alpha = tint.alpha * 1.25f)),
                tint.copy(alpha = tint.alpha * 0.55f),
            ),
        )
    }
    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (hazeState != null) {
                    Modifier.hazeBlur(input = HazeInput.Sources(hazeState), style = style)
                } else {
                    Modifier.background(baseUnderTint)
                },
            )
            .background(sheen)
            .border(ContrailTheme.sizes.hairline, borderBrush, shape),
        content = content,
    )
}

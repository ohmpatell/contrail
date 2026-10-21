package com.contrail.contrail.core.designsystem.component

import android.graphics.RuntimeShader
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import com.contrail.contrail.core.designsystem.theme.ContrailColors
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import androidx.compose.runtime.withFrameMillis
import kotlin.math.max

private const val AURORA_SHADER = """
uniform float2 resolution;
uniform float time;
layout(color) uniform half4 base;
layout(color) uniform half4 colorA;
layout(color) uniform half4 colorB;
layout(color) uniform half4 colorC;

float blob(float2 uv, float2 center, float radius) {
    float d = length(uv - center) / radius;
    return exp(-d * d * 2.2);
}

half4 main(float2 fragCoord) {
    float aspect = resolution.x / resolution.y;
    float2 uv = fragCoord / resolution;
    uv.x *= aspect;
    float t = time * 0.045;

    float2 p1 = float2((0.18 + 0.10 * sin(t * 1.3)) * aspect, 0.10 + 0.07 * cos(t * 1.1));
    float2 p2 = float2((0.92 + 0.08 * cos(t * 0.9)) * aspect, 0.30 + 0.09 * sin(t * 1.6));
    float2 p3 = float2((0.40 + 0.18 * sin(t * 0.6)) * aspect, 0.62 + 0.06 * cos(t * 0.8));

    half3 c = base.rgb;
    c += colorA.rgb * half(blob(uv, p1, 0.55 * aspect) * 0.70);
    c += colorB.rgb * half(blob(uv, p2, 0.60 * aspect) * 0.55);
    c += colorC.rgb * half(blob(uv, p3, 0.50 * aspect) * 0.32);

    // Fade the aurora out towards the bottom so content stays legible.
    float vignette = smoothstep(1.25, 0.15, uv.y);
    c = mix(base.rgb, c, half(vignette));

    // Blue-noise-ish dither to stop gradient banding on OLED.
    float n = fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453);
    c += half((n - 0.5) / 255.0 * 2.0);
    return half4(c, 1.0);
}
"""

/**
 * The app canvas: near-black navy with a slowly drifting aurora. Uses an AGSL shader on
 * device and a static layered gradient in previews/tests or when motion is reduced.
 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    val colors = ContrailTheme.colors
    val animate = ContrailTheme.motion.ambientEnabled && !LocalInspectionMode.current
    val shader = remember { runCatching { RuntimeShader(AURORA_SHADER) }.getOrNull() }
    val time = remember { mutableFloatStateOf(37f) }

    if (animate && shader != null) {
        LaunchedEffect(Unit) {
            val start = withFrameMillis { it }
            while (true) {
                withFrameMillis { now -> time.floatValue = 37f + (now - start) / 1000f }
            }
        }
    }

    Box(
        modifier = modifier.drawBehind {
            if (shader != null && animate) {
                drawAuroraShader(shader, time.floatValue, colors)
            } else {
                drawAuroraFallback(colors)
            }
        },
    ) { content() }
}

private fun DrawScope.drawAuroraShader(shader: RuntimeShader, time: Float, colors: ContrailColors) {
    shader.setFloatUniform("resolution", size.width, size.height)
    shader.setFloatUniform("time", time)
    shader.setColorUniform("base", colors.canvas.toArgb())
    shader.setColorUniform("colorA", colors.auroraA.toArgb())
    shader.setColorUniform("colorB", colors.auroraB.toArgb())
    shader.setColorUniform("colorC", colors.auroraC.toArgb())
    drawRect(ShaderBrush(shader))
}

private fun DrawScope.drawAuroraFallback(colors: ContrailColors) {
    drawRect(colors.canvas)
    val r = max(size.width, size.height)
    drawRect(
        Brush.radialGradient(
            listOf(colors.auroraA.copy(alpha = 0.75f), Color.Transparent),
            center = Offset(size.width * 0.15f, size.height * 0.06f),
            radius = r * 0.55f,
        ),
    )
    drawRect(
        Brush.radialGradient(
            listOf(colors.auroraB.copy(alpha = 0.65f), Color.Transparent),
            center = Offset(size.width * 0.95f, size.height * 0.26f),
            radius = r * 0.5f,
        ),
    )
    drawRect(
        Brush.radialGradient(
            listOf(colors.auroraC.copy(alpha = 0.35f), Color.Transparent),
            center = Offset(size.width * 0.4f, size.height * 0.6f),
            radius = r * 0.45f,
        ),
    )
}

package com.contrail.contrail.core.designsystem.livery

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import coil3.compose.rememberAsyncImagePainter
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import kotlin.math.max
import kotlin.math.sin

/**
 * A floating side-profile render of an aircraft type in an airline's livery, nose to the
 * right, with the airline's tail art (if any) painted on the fin.
 */
@Composable
fun LiveryRender(
    profile: Profile,
    livery: Livery,
    modifier: Modifier = Modifier,
    tailArtUrl: String? = null,
    floating: Boolean = true,
) {
    val motion = ContrailTheme.motion
    val context = LocalContext.current
    val tailPainter: Painter? = if (tailArtUrl != null && livery.useTailArt) {
        rememberAsyncImagePainter(
            remember(tailArtUrl) {
                // logostream free plan: no disk caching of logo assets.
                ImageRequest.Builder(context).data(tailArtUrl)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .httpHeaders(NetworkHeaders.Builder().set("Cache-Control", "no-store").build())
                    .build()
            },
        )
    } else {
        null
    }
    val bob by if (floating && motion.ambientEnabled) {
        rememberInfiniteTransition(label = "bob").animateFloat(
            0f, 1f, infiniteRepeatable(tween(5_200, easing = LinearEasing), RepeatMode.Restart), label = "bob",
        )
    } else {
        remember { mutableFloatStateOf(0.25f) }
    }
    val wave = sin(bob * 2 * Math.PI).toFloat()
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(2.4f)
            .graphicsLayer {
                translationY = wave * size.height * 0.025f
                rotationZ = wave * 0.6f
            },
    ) {
        drawShadow(profile, wave)
        drawAircraft(profile, livery, tailPainter)
    }
}

private class Geometry(val w: Float, val h: Float, p: Profile) {
    val cy = h * 0.5f
    val fh = p.height * w * 0.62f * (if (p.doubleDeck) 1.32f else 1f)
    val x0 = w * (1f - p.length) / 2f
    val x1 = x0 + p.length * w
    val nose = p.noseLength * w
    val tailcone = p.tailcone * w
    val top = cy - fh / 2f
    val bottom = cy + fh / 2f
}

private fun DrawScope.drawShadow(p: Profile, wave: Float) {
    val g = Geometry(size.width, size.height, p)
    val center = Offset(size.width / 2f, g.bottom + g.fh * 1.2f)
    val r = size.width * p.length * 0.42f * (1f - wave * 0.03f)
    drawOval(
        Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), center, r),
        topLeft = Offset(center.x - r, center.y - g.fh * 0.35f),
        size = Size(r * 2, g.fh * 0.7f),
    )
}

private fun fuselagePath(g: Geometry, p: Profile): Path = Path().apply {
    val tailTipTop = g.cy - g.fh * 0.42f
    val tailTipBottom = g.cy - g.fh * 0.18f
    moveTo(g.x0, tailTipTop)
    // Top: tailcone, cabin (with a 747 hump), nose.
    cubicTo(g.x0 + g.tailcone * 0.4f, g.top, g.x0 + g.tailcone * 0.7f, g.top, g.x0 + g.tailcone, g.top)
    if (p.hump > 0f) {
        val humpEnd = g.x1 - g.nose * 0.6f
        val humpStart = humpEnd - p.hump * g.w
        lineTo(humpStart, g.top)
        cubicTo(humpStart + g.w * 0.05f, g.top - g.fh * 0.32f, humpEnd - g.w * 0.04f, g.top - g.fh * 0.32f, humpEnd, g.top - g.fh * 0.12f)
    } else {
        lineTo(g.x1 - g.nose, g.top)
    }
    val noseTipY = g.cy + g.fh * if (p.smoothNose) 0.02f else 0.1f
    val k = if (p.smoothNose) 0.75f else 0.55f
    cubicTo(g.x1 - g.nose * (1f - k), g.top, g.x1, noseTipY - g.fh * 0.3f, g.x1, noseTipY)
    cubicTo(g.x1, g.bottom - g.fh * 0.05f, g.x1 - g.nose * 0.4f, g.bottom, g.x1 - g.nose, g.bottom)
    // Bottom back to the tail, sweeping up under the tailcone.
    lineTo(g.x0 + g.tailcone * 1.25f, g.bottom)
    cubicTo(g.x0 + g.tailcone * 0.6f, g.bottom, g.x0 + g.tailcone * 0.2f, tailTipBottom + g.fh * 0.1f, g.x0, tailTipBottom)
    close()
}

private fun finPath(g: Geometry, p: Profile): Path = Path().apply {
    val baseY = g.top + g.fh * 0.08f
    val chord = p.finChord * g.w
    val height = p.finHeight * g.w * 0.55f
    val rootFront = g.x0 + chord * 1.05f
    moveTo(rootFront, baseY)
    lineTo(g.x0 + chord * 0.42f, baseY - height)
    lineTo(g.x0 + chord * 0.08f, baseY - height)
    lineTo(g.x0 + g.w * 0.004f, g.cy - g.fh * 0.4f)
    close()
}

private fun DrawScope.drawEngine(center: Offset, length: Float, height: Float, color: Color, far: Boolean) {
    val shade = if (far) 0.55f else 1f
    val body = lerp(Color.Black, color, shade)
    val rect = Rect(center.x - length / 2, center.y - height / 2, center.x + length / 2, center.y + height / 2)
    val path = Path().apply {
        moveTo(rect.left + length * 0.08f, rect.top + height * 0.12f)
        lineTo(rect.right - length * 0.04f, rect.top)
        quadraticTo(rect.right + length * 0.03f, center.y, rect.right - length * 0.04f, rect.bottom)
        lineTo(rect.left + length * 0.08f, rect.bottom - height * 0.12f)
        quadraticTo(rect.left - length * 0.02f, center.y, rect.left + length * 0.08f, rect.top + height * 0.12f)
        close()
    }
    drawPath(path, Brush.verticalGradient(listOf(lerp(body, Color.White, 0.25f), body, lerp(body, Color.Black, 0.35f)), rect.top, rect.bottom))
    // Intake lip and dark fan face.
    drawOval(Color(0xFF0B0F18), topLeft = Offset(rect.right - length * 0.1f, rect.top + height * 0.08f), size = Size(length * 0.12f, height * 0.84f))
    drawOval(lerp(body, Color.White, 0.4f), topLeft = Offset(rect.right - length * 0.07f, rect.top + height * 0.02f), size = Size(length * 0.06f, height * 0.96f), alpha = 0.5f)
}

private fun DrawScope.drawAircraft(p: Profile, l: Livery, tail: Painter?) {
    val g = Geometry(size.width, size.height, p)
    val fuselage = fuselagePath(g, p)
    val wingRoot = g.x0 + p.length * g.w * if (p.engines == EngineLayout.Rear) 0.52f else 0.47f
    val engL = p.engineSize * g.w * 1.7f
    val engH = p.engineSize * g.w * 0.72f
    val wingColor = lerp(l.belly, Color.Black, 0.18f)

    // Far-side engines and wing, partly hidden behind the fuselage.
    if (p.engines == EngineLayout.UnderWing || p.engines == EngineLayout.UnderWingQuad) {
        drawEngine(Offset(wingRoot - g.w * 0.035f, g.bottom + engH * 0.1f), engL * 0.92f, engH * 0.92f, l.engines, far = true)
    }
    if (p.engines == EngineLayout.UnderWingQuad) {
        drawEngine(Offset(wingRoot - g.w * 0.14f, g.bottom + engH * 0.25f), engL * 0.85f, engH * 0.85f, l.engines, far = true)
    }

    // Fin first so the fuselage overlaps its root.
    val fin = finPath(g, p)
    drawPath(fin, l.tail)
    if (tail != null) {
        val b = fin.getBounds()
        clipPath(fin) {
            val intrinsic = tail.intrinsicSize
            val scale = if (intrinsic.isSpecified()) max(b.width / intrinsic.width, b.height / intrinsic.height) else 1f
            val drawSize = if (intrinsic.isSpecified()) Size(intrinsic.width * scale, intrinsic.height * scale) else b.size
            translate(b.left + (b.width - drawSize.width) / 2f, b.top + (b.height - drawSize.height) / 2f) {
                with(tail) { draw(drawSize) }
            }
        }
    }
    drawPath(fin, Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent), fin.getBounds().left, fin.getBounds().right))

    // Fuselage, belly, cheatline.
    drawPath(fuselage, l.fuselage)
    clipPath(fuselage) {
        drawRect(l.belly, topLeft = Offset(0f, g.cy + g.fh * 0.14f), size = Size(size.width, g.fh))
        l.cheatline?.let { c ->
            drawRect(c, topLeft = Offset(g.x0, g.cy + g.fh * 0.02f), size = Size(g.x1 - g.x0, g.fh * 0.09f))
        }
        // Windows: one deck, or two on an A380.
        if (p.windows) {
            val rows = if (p.doubleDeck) listOf(g.cy - g.fh * 0.24f, g.cy + g.fh * 0.02f) else listOf(g.cy - g.fh * 0.12f)
            val pitch = g.w * 0.0125f
            val ww = pitch * 0.45f
            val wh = g.fh * 0.085f
            rows.forEach { y ->
                var x = g.x0 + g.tailcone * 1.05f
                while (x < g.x1 - g.nose * 1.15f) {
                    drawRoundRect(l.windows, topLeft = Offset(x, y), size = Size(ww, wh), cornerRadius = androidx.compose.ui.geometry.CornerRadius(ww / 2))
                    x += pitch
                }
            }
            if (p.hump > 0f) {
                val humpY = g.top - g.fh * 0.12f
                var x = g.x1 - g.nose * 0.6f - p.hump * g.w * 0.85f
                while (x < g.x1 - g.nose * 0.8f) {
                    drawRoundRect(l.windows, topLeft = Offset(x, humpY), size = Size(ww, wh), cornerRadius = androidx.compose.ui.geometry.CornerRadius(ww / 2))
                    x += pitch
                }
            }
        }
        // Gloss along the crown.
        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), g.top - g.fh * 0.3f, g.cy), topLeft = Offset(0f, g.top - g.fh * 0.4f), size = Size(size.width, g.fh))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f)), g.cy, g.bottom), topLeft = Offset(0f, g.cy), size = Size(size.width, g.fh))
    }
    // Cockpit windows.
    val cockpit = Path().apply {
        val cx = g.x1 - g.nose * 0.55f
        val y = g.cy - g.fh * (if (p.smoothNose) 0.18f else 0.22f)
        moveTo(cx, y)
        lineTo(cx + g.nose * 0.28f, y + g.fh * 0.04f)
        lineTo(cx + g.nose * 0.22f, y + g.fh * 0.13f)
        lineTo(cx - g.nose * 0.05f, y + g.fh * 0.11f)
        close()
    }
    if (p.windows || p.engines == EngineLayout.None) drawPath(cockpit, l.windows)

    // Horizontal stabiliser: at the tailcone, or on top of the fin for a T-tail.
    val stabY = if (p.tTail) g.top + g.fh * 0.08f - p.finHeight * g.w * 0.53f else g.cy - g.fh * 0.12f
    val stab = Path().apply {
        moveTo(g.x0 + g.w * 0.02f, stabY)
        lineTo(g.x0 + p.finChord * g.w * 0.85f, stabY - g.fh * 0.05f)
        lineTo(g.x0 + p.finChord * g.w * 0.95f, stabY + g.fh * 0.04f)
        lineTo(g.x0 + g.w * 0.01f, stabY + g.fh * 0.08f)
        close()
    }
    drawPath(stab, lerp(l.tail, Color.Black, 0.15f))

    // Near wing, then engines.
    val wing = Path().apply {
        if (p.highWing) {
            moveTo(wingRoot + g.w * 0.05f, g.top + g.fh * 0.05f)
            lineTo(wingRoot - g.w * 0.07f, g.top + g.fh * 0.05f)
            lineTo(wingRoot - g.w * 0.08f, g.top + g.fh * 0.2f)
            lineTo(wingRoot + g.w * 0.05f, g.top + g.fh * 0.2f)
        } else {
            moveTo(wingRoot + g.w * 0.03f, g.cy + g.fh * 0.3f)
            lineTo(wingRoot - g.w * 0.15f, g.bottom + g.fh * 0.22f)
            lineTo(wingRoot - g.w * 0.21f, g.bottom + g.fh * 0.22f)
            lineTo(wingRoot - g.w * 0.07f, g.cy + g.fh * 0.36f)
        }
        close()
    }
    drawPath(wing, wingColor)
    when (p.engines) {
        EngineLayout.UnderWing -> drawEngine(Offset(wingRoot - g.w * 0.05f, g.bottom + engH * 0.42f), engL, engH, l.engines, far = false)
        EngineLayout.UnderWingQuad -> {
            drawEngine(Offset(wingRoot - g.w * 0.05f, g.bottom + engH * 0.42f), engL, engH, l.engines, far = false)
            drawEngine(Offset(wingRoot - g.w * 0.165f, g.bottom + engH * 0.6f), engL * 0.95f, engH * 0.95f, l.engines, far = false)
        }
        EngineLayout.Rear -> drawEngine(Offset(g.x0 + g.tailcone * 1.25f, g.cy - g.fh * 0.12f), engL, engH, l.engines, far = false)
        EngineLayout.Tail -> {
            drawEngine(Offset(wingRoot - g.w * 0.05f, g.bottom + engH * 0.42f), engL, engH, l.engines, far = false)
            drawEngine(Offset(g.x0 + p.finChord * g.w * 0.75f, g.top - engH * 0.15f), engL * 0.95f, engH * 0.9f, l.engines, far = false)
        }
        EngineLayout.Props -> {
            val y = if (p.highWing) g.top + g.fh * 0.3f else g.bottom
            val c = Offset(wingRoot + g.w * 0.0f, y)
            drawEngine(c, engL, engH, l.engines, far = false)
            // Propeller disc, blurred by its own speed.
            drawOval(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.Transparent), c.x + engL * 0.45f, c.x + engL * 0.6f),
                topLeft = Offset(c.x + engL * 0.46f, c.y - engH * 1.6f),
                size = Size(engL * 0.12f, engH * 3.2f),
            )
        }
        EngineLayout.None -> Unit
    }
}

private fun Size.isSpecified() = width.isFinite() && height.isFinite() && width > 0 && height > 0

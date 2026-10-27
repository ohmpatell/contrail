package com.contrail.contrail.feature.passport

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import com.contrail.contrail.R
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Rubber-stamp inks: bright enough to read on the night canvas, like a fresh stamp under UV. */
object StampInk {
    val inks = listOf(
        Color(0xFFFF5A6E), Color(0xFF6F96FF), Color(0xFF3FD69E), Color(0xFFB98CFF),
        Color(0xFFFFB84D), Color(0xFF45CBE0), Color(0xFFFF7FCB),
    )

    /** Each country keeps one ink, so a page of Japan stamps all match. */
    fun forCountry(iso: String): Color = inks[(iso.hashCode() and 0x7FFFFFFF) % inks.size]
}

private enum class StampShape { Circle, Rect, Oval, Octagon }

private val STAMP_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.UK)

/**
 * One inked passport stamp for [stamp]: the shape, tilt and ink come from the airport code,
 * so a stamp always looks the same. City text runs around circular stamps; a grain pass
 * knocks specks out of the ink like a real rubber stamp. [delayMs] staggers the "thump" in,
 * which waits until [play] (a stamp-book page lands only when you turn to it).
 */
@Composable
fun PassportStamp(stamp: Stamp, modifier: Modifier = Modifier, delayMs: Long = 0, animate: Boolean = true, play: Boolean = true) {
    val context = LocalContext.current
    val motion = ContrailTheme.motion
    val typeface = remember {
        val base = ResourcesCompat.getFont(context, R.font.space_grotesk) ?: Typeface.DEFAULT
        Typeface.create(base, 700, false)
    }
    val mono = remember {
        val base = ResourcesCompat.getFont(context, R.font.jetbrains_mono) ?: Typeface.MONOSPACE
        Typeface.create(base, 600, false)
    }
    val seed = stamp.airport.hashCode()
    val shape = StampShape.entries[(seed and 0x7FFFFFFF) % StampShape.entries.size]
    val ink = if (stamp.transit) ContrailTheme.colors.textSecondary else StampInk.forCountry(stamp.countryIso.ifEmpty { stamp.airport })
    val tilt = ((seed shr 3) % 15).toFloat()
    val press = remember { Animatable(if (!animate || motion.reduced) 1f else 0f) }
    LaunchedEffect(stamp.airport, play) {
        if (animate && play && !motion.reduced && press.value < 1f) {
            delay(delayMs)
            press.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    Box(
        modifier.graphicsLayer {
            val p = press.value
            // Lands from above, slightly too big, then settles: the thump of a stamp.
            val s = 1f + (1f - p) * 0.5f
            scaleX = s; scaleY = s
            alpha = p.coerceIn(0f, 1f)
            rotationZ = tilt
            compositingStrategy = CompositingStrategy.Offscreen
        },
    ) {
        Canvas(Modifier.matchParentSize()) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink.toArgb(); this.typeface = typeface; textAlign = Paint.Align.CENTER }
            val monoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink.toArgb(); this.typeface = mono; textAlign = Paint.Align.CENTER }
            val label = if (stamp.transit) "TRANSIT" else "ARRIVED"
            when (shape) {
                StampShape.Circle -> circleStamp(stamp, ink, paint, monoPaint, label)
                StampShape.Rect -> rectStamp(stamp, ink, paint, monoPaint, label, rounded = true)
                StampShape.Oval -> ovalStamp(stamp, ink, paint, monoPaint, label)
                StampShape.Octagon -> octagonStamp(stamp, ink, paint, monoPaint, label)
            }
            grain(seed)
        }
    }
}

private fun DrawScope.circleStamp(s: Stamp, ink: Color, paint: Paint, mono: Paint, label: String) {
    val r = min(size.width, size.height) / 2f * 0.94f
    val c = center
    drawCircle(ink, r, c, style = Stroke(r * 0.06f))
    drawCircle(ink, r * 0.72f, c, style = Stroke(r * 0.025f))
    val canvas = drawContext.canvas.nativeCanvas
    // City around the top, the label around the bottom.
    paint.textSize = r * 0.17f
    paint.letterSpacing = 0.12f
    val top = android.graphics.Path().apply { addArc(c.x - r * 0.82f, c.y - r * 0.82f, c.x + r * 0.82f, c.y + r * 0.82f, 180f, 180f) }
    canvas.drawTextOnPath(s.city.uppercase().take(18), top, 0f, paint.textSize * 0.35f, paint)
    val bottom = android.graphics.Path().apply { addArc(c.x - r * 0.86f, c.y - r * 0.86f, c.x + r * 0.86f, c.y + r * 0.86f, 160f, -140f) }
    canvas.drawTextOnPath(label, bottom, 0f, paint.textSize * 0.35f, paint)
    paint.letterSpacing = 0f
    paint.textSize = r * 0.42f
    canvas.drawText(s.airport, c.x, c.y + r * 0.08f, paint)
    mono.textSize = r * 0.13f
    canvas.drawText(STAMP_DATE.format(s.date).uppercase(), c.x, c.y + r * 0.36f, mono)
    star(Offset(c.x - r * 0.84f, c.y), r * 0.06f, ink)
    star(Offset(c.x + r * 0.84f, c.y), r * 0.06f, ink)
}

private fun DrawScope.rectStamp(s: Stamp, ink: Color, paint: Paint, mono: Paint, label: String, rounded: Boolean) {
    val w = size.width * 0.94f
    val h = min(size.height * 0.8f, w * 0.68f)
    val tl = Offset((size.width - w) / 2, (size.height - h) / 2)
    val corner = if (rounded) CornerRadius(h * 0.12f) else CornerRadius.Zero
    drawRoundRect(ink, tl, Size(w, h), corner, style = Stroke(h * 0.05f))
    val inset = h * 0.09f
    drawRoundRect(ink, Offset(tl.x + inset, tl.y + inset), Size(w - inset * 2, h - inset * 2), corner, style = Stroke(h * 0.018f))
    val canvas = drawContext.canvas.nativeCanvas
    val cx = size.width / 2
    paint.textSize = h * 0.14f
    paint.letterSpacing = 0.14f
    canvas.drawText(s.city.uppercase().take(16), cx, tl.y + h * 0.3f, paint)
    paint.letterSpacing = 0.02f
    paint.textSize = h * 0.32f
    canvas.drawText(s.airport, cx, tl.y + h * 0.63f, paint)
    val line = "$label · ${STAMP_DATE.format(s.date).uppercase()}"
    mono.textSize = h * 0.11f
    // Shrink the bottom line until it sits inside the inner border.
    val room = w - inset * 4
    while (mono.measureText(line) > room && mono.textSize > h * 0.06f) mono.textSize *= 0.92f
    canvas.drawText(line, cx, tl.y + h * 0.82f, mono)
    paint.letterSpacing = 0f
}

private fun DrawScope.ovalStamp(s: Stamp, ink: Color, paint: Paint, mono: Paint, label: String) {
    val w = size.width * 0.95f
    val h = min(size.height * 0.82f, w * 0.7f)
    val tl = Offset((size.width - w) / 2, (size.height - h) / 2)
    drawOval(ink, tl, Size(w, h), style = Stroke(h * 0.05f))
    drawOval(ink, Offset(tl.x + h * 0.08f, tl.y + h * 0.08f), Size(w - h * 0.16f, h - h * 0.16f), style = Stroke(h * 0.016f))
    val canvas = drawContext.canvas.nativeCanvas
    val cx = size.width / 2
    // A plane silhouette across the top.
    plane(Offset(cx, tl.y + h * 0.2f), h * 0.075f, ink)
    paint.textSize = h * 0.3f
    canvas.drawText(s.airport, cx, tl.y + h * 0.6f, paint)
    paint.textSize = h * 0.11f
    paint.letterSpacing = 0.12f
    canvas.drawText(s.city.uppercase().take(16), cx, tl.y + h * 0.74f, paint)
    paint.letterSpacing = 0f
    mono.textSize = h * 0.095f
    canvas.drawText(STAMP_DATE.format(s.date).uppercase(), cx, tl.y + h * 0.86f, mono)
    if (label == "TRANSIT") {
        mono.textSize = h * 0.08f
        canvas.drawText(label, cx, tl.y + h * 0.4f, mono)
    }
}

private fun DrawScope.octagonStamp(s: Stamp, ink: Color, paint: Paint, mono: Paint, label: String) {
    val r = min(size.width, size.height) / 2f * 0.95f
    val c = center
    fun poly(radius: Float): Path = Path().apply {
        for (i in 0 until 8) {
            val a = PI / 8 + i * PI / 4
            val p = Offset(c.x + (cos(a) * radius).toFloat(), c.y + (sin(a) * radius).toFloat())
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
    drawPath(poly(r), ink, style = Stroke(r * 0.06f))
    drawPath(poly(r * 0.8f), ink, style = Stroke(r * 0.022f))
    val canvas = drawContext.canvas.nativeCanvas
    mono.textSize = r * 0.13f
    canvas.drawText(label, c.x, c.y - r * 0.38f, mono)
    paint.textSize = r * 0.4f
    canvas.drawText(s.airport, c.x, c.y + r * 0.12f, paint)
    paint.textSize = r * 0.14f
    paint.letterSpacing = 0.1f
    canvas.drawText(s.city.uppercase().take(13), c.x, c.y + r * 0.36f, paint)
    paint.letterSpacing = 0f
    mono.textSize = r * 0.115f
    canvas.drawText(STAMP_DATE.format(s.date).uppercase(), c.x, c.y + r * 0.54f, mono)
}

private fun DrawScope.star(c: Offset, r: Float, ink: Color) {
    val p = Path()
    for (i in 0 until 10) {
        val rr = if (i % 2 == 0) r else r * 0.45f
        val a = -PI / 2 + i * PI / 5
        val o = Offset(c.x + (cos(a) * rr).toFloat(), c.y + (sin(a) * rr).toFloat())
        if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y)
    }
    p.close()
    drawPath(p, ink)
}

/** A tiny side-on airliner for stamp faces. */
private fun DrawScope.plane(c: Offset, s: Float, ink: Color) {
    val body = Path().apply {
        moveTo(c.x - s * 2.4f, c.y)
        lineTo(c.x + s * 2.2f, c.y - s * 0.25f)
        quadraticTo(c.x + s * 2.8f, c.y, c.x + s * 2.2f, c.y + s * 0.25f)
        lineTo(c.x - s * 2.4f, c.y + s * 0.15f)
        close()
        // Wing.
        moveTo(c.x - s * 0.2f, c.y)
        lineTo(c.x - s * 1.1f, c.y + s * 1.2f)
        lineTo(c.x - s * 0.5f, c.y + s * 1.2f)
        lineTo(c.x + s * 0.7f, c.y)
        close()
        // Tail.
        moveTo(c.x - s * 2.4f, c.y)
        lineTo(c.x - s * 2.8f, c.y - s * 1.1f)
        lineTo(c.x - s * 2.2f, c.y - s * 1.1f)
        lineTo(c.x - s * 1.5f, c.y)
        close()
    }
    drawPath(body, ink)
}

/** Knocks specks and streaks out of the ink so it reads as stamped, not printed. */
private fun DrawScope.grain(seed: Int) {
    val r = Random(seed)
    val area = size.width * size.height
    val specks = (area / 90f).toInt().coerceIn(120, 900)
    repeat(specks) {
        val o = Offset(r.nextFloat() * size.width, r.nextFloat() * size.height)
        drawCircle(Color.Black.copy(alpha = 0.35f + r.nextFloat() * 0.65f), (0.4f + r.nextFloat() * 1.3f) * density, o, blendMode = BlendMode.DstOut)
    }
    // Uneven pressure: one side a little lighter.
    val angle = r.nextFloat() * 360f
    rotate(angle) {
        drawRect(
            androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent), startX = 0f, endX = size.width * 0.6f),
            topLeft = Offset(-size.width, -size.height), size = Size(size.width * 3, size.height * 3),
            blendMode = BlendMode.DstOut,
        )
    }
}

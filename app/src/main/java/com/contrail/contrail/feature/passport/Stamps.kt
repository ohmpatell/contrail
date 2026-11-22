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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
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
        Color(0xFFFFB84D), Color(0xFF45CBE0), Color(0xFFFF7FCB), Color(0xFF9EE05A),
    )

    /** Each country keeps one ink, so a page of Japan stamps all match. */
    fun forCountry(iso: String): Color = inks[(iso.hashCode() and 0x7FFFFFFF) % inks.size]

    /**
     * Many border agencies change ink colour every year, so the same country's stamps differ by
     * year but match within one.
     */
    fun forCountryYear(iso: String, year: Int): Color = inks[((iso.hashCode() * 31 + year) and 0x7FFFFFFF) % inks.size]
}

private val DATE_BAND: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.UK)
private val DATE_COMPACT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yy", Locale.UK)

/** The face's paints and the knock-out each style can use. */
private class Inks(val ink: Color, val bold: Paint, val mono: Paint)

/**
 * One inked passport stamp. Its look follows the country (see [StampStyle]) and what it records
 * (entry, exit, a domestic cachet or a transit mark); tilt, ink pressure, a missing edge and the
 * odd double strike come from the stamp itself, so it always looks the same. [delayMs] staggers
 * the "thump" in, which waits until [play] (a stamp-book page lands only when you turn to it).
 */
@Composable
fun PassportStamp(stamp: Stamp, modifier: Modifier = Modifier, delayMs: Long = 0, animate: Boolean = true, play: Boolean = true) {
    val context = LocalContext.current
    val motion = ContrailTheme.motion
    val secondary = ContrailTheme.colors.textSecondary
    val bold = remember {
        val base = ResourcesCompat.getFont(context, R.font.space_grotesk) ?: Typeface.DEFAULT
        Typeface.create(base, 700, false)
    }
    val mono = remember {
        val base = ResourcesCompat.getFont(context, R.font.jetbrains_mono) ?: Typeface.MONOSPACE
        Typeface.create(base, 600, false)
    }
    val seed = (stamp.airport + stamp.date + stamp.kind).hashCode()
    val rnd = Random(seed)
    val ink = when (stamp.kind) {
        StampKind.Transit -> secondary
        else -> StampInk.forCountryYear(stamp.countryIso.ifEmpty { stamp.airport }, stamp.date.year)
    }
    val tilt = (rnd.nextFloat() - 0.5f) * if (stamp.kind == StampKind.Visit) 28f else 18f
    val press = remember { Animatable(if (!animate || motion.reduced) 1f else 0f) }
    LaunchedEffect(seed, play) {
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
            fun paints(alpha: Float) = Inks(
                ink.copy(alpha = alpha),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink.copy(alpha = alpha).toArgb(); typeface = bold; textAlign = Paint.Align.CENTER },
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink.copy(alpha = alpha).toArgb(); typeface = mono; textAlign = Paint.Align.CENTER },
            )
            fun face(p: Inks) = when (stamp.kind) {
                StampKind.Visit -> cachet(stamp, p)
                StampKind.Transit -> transitMark(stamp, p)
                else -> when (StampRules.styleFor(stamp.countryIso)) {
                    StampStyle.Schengen -> schengen(stamp, p)
                    StampStyle.Admitted -> admitted(stamp, p)
                    StampStyle.Band -> band(stamp, p)
                    StampStyle.Hexagon -> hexagon(stamp, p)
                    StampStyle.Round -> round(stamp, p)
                }
            }
            // Now and then the officer's hand bounced: a faint second strike, slightly off.
            if (rnd.nextFloat() < 0.12f) {
                translate(left = (rnd.nextFloat() - 0.5f) * 8f * density, top = (rnd.nextFloat() - 0.5f) * 8f * density) { face(paints(0.22f)) }
            }
            face(paints(1f))
            grain(seed)
            partialPrint(seed)
        }
    }
}

// region Faces

/** Schengen: square corners on the way in, rounded on the way out; a plane, the country code and an arrow. */
private fun DrawScope.schengen(s: Stamp, p: Inks) {
    val entry = s.kind == StampKind.Entry
    val w = size.width * 0.9f
    val h = min(size.height * 0.78f, w * 0.78f)
    val tl = Offset((size.width - w) / 2, (size.height - h) / 2)
    val corner = if (entry) CornerRadius(h * 0.02f) else CornerRadius(h * 0.2f)
    drawRoundRect(p.ink, tl, Size(w, h), corner, style = Stroke(h * 0.045f))
    val canvas = drawContext.canvas.nativeCanvas
    // Header: little plane at left, country code boxed at right.
    plane(Offset(tl.x + w * 0.22f, tl.y + h * 0.2f), h * 0.055f, p.ink)
    val box = Size(w * 0.24f, h * 0.2f)
    val boxTl = Offset(tl.x + w * 0.68f, tl.y + h * 0.09f)
    drawRect(p.ink, boxTl, box, style = Stroke(h * 0.02f))
    p.bold.textSize = h * 0.15f
    canvas.drawText(s.countryIso.uppercase().ifEmpty { "··" }, boxTl.x + box.width / 2, boxTl.y + box.height * 0.76f, p.bold)
    // Port.
    p.bold.textSize = h * 0.13f
    p.bold.letterSpacing = 0.06f
    canvas.drawText(s.city.uppercase().take(14), tl.x + w / 2, tl.y + h * 0.48f, p.bold)
    p.bold.letterSpacing = 0f
    dateBand(s, p, tl.x + w * 0.08f, tl.x + w * 0.92f, tl.y + h * 0.56f, h * 0.17f)
    // Arrow in or out, and the officer's number.
    arrow(Offset(tl.x + w * 0.25f, tl.y + h * 0.88f), h * 0.07f, inward = entry, ink = p.ink)
    p.mono.textSize = h * 0.11f
    canvas.drawText(String.format(Locale.US, "%03d", s.seq), tl.x + w * 0.72f, tl.y + h * 0.92f, p.mono)
}

/** A double rectangle: ADMITTED or DEPARTED, the port and a date band. */
private fun DrawScope.admitted(s: Stamp, p: Inks) {
    val w = size.width * 0.94f
    val h = min(size.height * 0.74f, w * 0.66f)
    val tl = Offset((size.width - w) / 2, (size.height - h) / 2)
    drawRect(p.ink, tl, Size(w, h), style = Stroke(h * 0.05f))
    val inset = h * 0.08f
    drawRect(p.ink, Offset(tl.x + inset, tl.y + inset), Size(w - inset * 2, h - inset * 2), style = Stroke(h * 0.016f))
    val canvas = drawContext.canvas.nativeCanvas
    p.bold.textSize = h * 0.19f
    p.bold.letterSpacing = 0.12f
    canvas.drawText(if (s.kind == StampKind.Entry) "ADMITTED" else "DEPARTED", tl.x + w / 2, tl.y + h * 0.33f, p.bold)
    p.bold.letterSpacing = 0.04f
    p.bold.textSize = h * 0.115f
    canvas.drawText("${s.city.uppercase().take(14)} · ${s.airport}", tl.x + w / 2, tl.y + h * 0.5f, p.bold)
    p.bold.letterSpacing = 0f
    dateBand(s, p, tl.x + inset * 1.6f, tl.x + w - inset * 1.6f, tl.y + h * 0.58f, h * 0.17f)
    p.mono.textSize = h * 0.09f
    canvas.drawText("REF ${s.seq}", tl.x + w / 2, tl.y + h * 0.88f, p.mono)
}

/** A rectangle with a solid ink band and the word knocked out of it, the way many Asian stamps read. */
private fun DrawScope.band(s: Stamp, p: Inks) {
    val w = size.width * 0.86f
    val h = min(size.height * 0.84f, w * 0.88f)
    val tl = Offset((size.width - w) / 2, (size.height - h) / 2)
    drawRoundRect(p.ink, tl, Size(w, h), CornerRadius(h * 0.05f), style = Stroke(h * 0.04f))
    // The band, with its label knocked out.
    val bandTop = tl.y + h * 0.1f
    val bandH = h * 0.2f
    drawRect(p.ink, Offset(tl.x + w * 0.08f, bandTop), Size(w * 0.84f, bandH))
    val canvas = drawContext.canvas.nativeCanvas
    val knock = Paint(p.bold).apply { textSize = bandH * 0.62f; letterSpacing = 0.16f; xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT) }
    canvas.drawText(if (s.kind == StampKind.Entry) "ARRIVAL" else "DEPARTURE", tl.x + w / 2, bandTop + bandH * 0.72f, knock)
    p.bold.textSize = h * 0.2f
    canvas.drawText(s.airport, tl.x + w / 2, tl.y + h * 0.56f, p.bold)
    p.bold.textSize = h * 0.095f
    p.bold.letterSpacing = 0.1f
    canvas.drawText(s.city.uppercase().take(14), tl.x + w / 2, tl.y + h * 0.69f, p.bold)
    p.bold.letterSpacing = 0f
    p.mono.textSize = h * 0.105f
    canvas.drawText(DATE_COMPACT.format(s.date), tl.x + w / 2, tl.y + h * 0.83f, p.mono)
    p.mono.textSize = h * 0.07f
    canvas.drawText("No. ${s.seq}", tl.x + w / 2, tl.y + h * 0.93f, p.mono)
}

/** A hexagon with ENTRY or EXIT, the port and the date. */
private fun DrawScope.hexagon(s: Stamp, p: Inks) {
    val r = min(size.width, size.height) / 2f * 0.95f
    val c = center
    fun poly(radius: Float): Path = Path().apply {
        for (i in 0 until 6) {
            val a = i * PI / 3
            val o = Offset(c.x + (cos(a) * radius).toFloat(), c.y + (sin(a) * radius * 0.86f).toFloat())
            if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
        }
        close()
    }
    drawPath(poly(r), p.ink, style = Stroke(r * 0.06f))
    drawPath(poly(r * 0.82f), p.ink, style = Stroke(r * 0.02f))
    val canvas = drawContext.canvas.nativeCanvas
    p.mono.textSize = r * 0.14f
    canvas.drawText(if (s.kind == StampKind.Entry) "ENTRY" else "EXIT", c.x, c.y - r * 0.36f, p.mono)
    p.bold.textSize = r * 0.36f
    canvas.drawText(s.airport, c.x, c.y + r * 0.1f, p.bold)
    p.bold.textSize = r * 0.12f
    p.bold.letterSpacing = 0.1f
    canvas.drawText(s.city.uppercase().take(13), c.x, c.y + r * 0.32f, p.bold)
    p.bold.letterSpacing = 0f
    p.mono.textSize = r * 0.115f
    canvas.drawText(DATE_BAND.format(s.date).uppercase(), c.x, c.y + r * 0.52f, p.mono)
    star(Offset(c.x - r * 0.62f, c.y), r * 0.05f, p.ink)
    star(Offset(c.x + r * 0.62f, c.y), r * 0.05f, p.ink)
}

/** A round port stamp: the city around the top, ENTRY or EXIT around the bottom. */
private fun DrawScope.round(s: Stamp, p: Inks) {
    val r = min(size.width, size.height) / 2f * 0.94f
    val c = center
    drawCircle(p.ink, r, c, style = Stroke(r * 0.06f))
    drawCircle(p.ink, r * 0.72f, c, style = Stroke(r * 0.025f))
    val canvas = drawContext.canvas.nativeCanvas
    p.bold.textSize = r * 0.16f
    p.bold.letterSpacing = 0.12f
    val top = android.graphics.Path().apply { addArc(c.x - r * 0.82f, c.y - r * 0.82f, c.x + r * 0.82f, c.y + r * 0.82f, 180f, 180f) }
    canvas.drawTextOnPath(s.city.uppercase().take(18), top, 0f, p.bold.textSize * 0.35f, p.bold)
    val bottom = android.graphics.Path().apply { addArc(c.x - r * 0.86f, c.y - r * 0.86f, c.x + r * 0.86f, c.y + r * 0.86f, 160f, -140f) }
    canvas.drawTextOnPath(if (s.kind == StampKind.Entry) "ENTRY" else "EXIT", bottom, 0f, p.bold.textSize * 0.35f, p.bold)
    p.bold.letterSpacing = 0f
    p.bold.textSize = r * 0.4f
    canvas.drawText(s.airport, c.x, c.y + r * 0.06f, p.bold)
    p.mono.textSize = r * 0.12f
    canvas.drawText(DATE_BAND.format(s.date).uppercase(), c.x, c.y + r * 0.32f, p.mono)
    arrow(Offset(c.x, c.y - r * 0.4f), r * 0.07f, inward = s.kind == StampKind.Entry, ink = p.ink)
    star(Offset(c.x - r * 0.84f, c.y), r * 0.06f, p.ink)
    star(Offset(c.x + r * 0.84f, c.y), r * 0.06f, p.ink)
}

/** A small souvenir cachet for a domestic airport: no border, just "you were here". */
private fun DrawScope.cachet(s: Stamp, p: Inks) {
    val r = min(size.width, size.height) / 2f * 0.9f
    val c = center
    // A scalloped rim, like a souvenir stamp.
    val rim = Path()
    val teeth = 28
    for (i in 0..teeth * 2) {
        val a = i * PI / teeth
        val rr = if (i % 2 == 0) r else r * 0.93f
        val o = Offset(c.x + (cos(a) * rr).toFloat(), c.y + (sin(a) * rr).toFloat())
        if (i == 0) rim.moveTo(o.x, o.y) else rim.lineTo(o.x, o.y)
    }
    rim.close()
    drawPath(rim, p.ink, style = Stroke(r * 0.04f))
    drawCircle(p.ink, r * 0.78f, c, style = Stroke(r * 0.02f))
    plane(Offset(c.x, c.y - r * 0.42f), r * 0.07f, p.ink)
    val canvas = drawContext.canvas.nativeCanvas
    p.bold.textSize = r * 0.42f
    canvas.drawText(s.airport, c.x, c.y + r * 0.14f, p.bold)
    p.bold.textSize = r * 0.13f
    p.bold.letterSpacing = 0.1f
    canvas.drawText(s.city.uppercase().take(12), c.x, c.y + r * 0.36f, p.bold)
    p.bold.letterSpacing = 0f
    p.mono.textSize = r * 0.11f
    canvas.drawText(s.date.year.toString(), c.x, c.y + r * 0.55f, p.mono)
}

/** A triangle for changing planes airside. */
private fun DrawScope.transitMark(s: Stamp, p: Inks) {
    val w = size.width * 0.86f
    val h = w * 0.8f
    val top = Offset(size.width / 2, (size.height - h) / 2)
    val tri = Path().apply {
        moveTo(top.x, top.y)
        lineTo(top.x + w / 2, top.y + h)
        lineTo(top.x - w / 2, top.y + h)
        close()
    }
    drawPath(tri, p.ink, style = Stroke(h * 0.045f))
    val canvas = drawContext.canvas.nativeCanvas
    p.mono.textSize = h * 0.1f
    canvas.drawText("TRANSIT", top.x, top.y + h * 0.6f, p.mono)
    p.bold.textSize = h * 0.18f
    canvas.drawText(s.airport, top.x, top.y + h * 0.82f, p.bold)
    p.mono.textSize = h * 0.075f
    canvas.drawText(DATE_COMPACT.format(s.date), top.x, top.y + h * 0.94f, p.mono)
}

// endregion

// region Details and ink

/** The date between two rules, the way most stamps print it. */
private fun DrawScope.dateBand(s: Stamp, p: Inks, left: Float, right: Float, top: Float, height: Float) {
    drawLine(p.ink, Offset(left, top), Offset(right, top), strokeWidth = height * 0.08f)
    drawLine(p.ink, Offset(left, top + height), Offset(right, top + height), strokeWidth = height * 0.08f)
    val canvas = drawContext.canvas.nativeCanvas
    p.mono.textSize = height * 0.6f
    val text = DATE_BAND.format(s.date).uppercase()
    while (p.mono.measureText(text) > (right - left) * 0.94f && p.mono.textSize > height * 0.3f) p.mono.textSize *= 0.92f
    canvas.drawText(text, (left + right) / 2, top + height * 0.72f, p.mono)
}

private fun DrawScope.arrow(c: Offset, s: Float, inward: Boolean, ink: Color) {
    val dir = if (inward) 1f else -1f
    val body = Path().apply {
        moveTo(c.x - s * 1.6f * dir, c.y - s * 0.35f)
        lineTo(c.x + s * 0.4f * dir, c.y - s * 0.35f)
        lineTo(c.x + s * 0.4f * dir, c.y - s)
        lineTo(c.x + s * 1.6f * dir, c.y)
        lineTo(c.x + s * 0.4f * dir, c.y + s)
        lineTo(c.x + s * 0.4f * dir, c.y + s * 0.35f)
        lineTo(c.x - s * 1.6f * dir, c.y + s * 0.35f)
        close()
    }
    drawPath(body, ink)
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
        moveTo(c.x - s * 0.2f, c.y)
        lineTo(c.x - s * 1.1f, c.y + s * 1.2f)
        lineTo(c.x - s * 0.5f, c.y + s * 1.2f)
        lineTo(c.x + s * 0.7f, c.y)
        close()
        moveTo(c.x - s * 2.4f, c.y)
        lineTo(c.x - s * 2.8f, c.y - s * 1.1f)
        lineTo(c.x - s * 2.2f, c.y - s * 1.1f)
        lineTo(c.x - s * 1.5f, c.y)
        close()
    }
    drawPath(body, ink)
}

/** Knocks specks and streaks out of the ink so it reads as stamped, not printed; one side presses lighter. */
private fun DrawScope.grain(seed: Int) {
    val r = Random(seed)
    val area = size.width * size.height
    val specks = (area / 90f).toInt().coerceIn(120, 900)
    repeat(specks) {
        val o = Offset(r.nextFloat() * size.width, r.nextFloat() * size.height)
        drawCircle(Color.Black.copy(alpha = 0.35f + r.nextFloat() * 0.65f), (0.4f + r.nextFloat() * 1.3f) * density, o, blendMode = BlendMode.DstOut)
    }
    val angle = r.nextFloat() * 360f
    rotate(angle) {
        drawRect(
            Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent), startX = 0f, endX = size.width * 0.6f),
            topLeft = Offset(-size.width, -size.height), size = Size(size.width * 3, size.height * 3),
            blendMode = BlendMode.DstOut,
        )
    }
}

/** About a third of stamps missed a little of the paper: an edge fades out where the stamp didn't land flat. */
private fun DrawScope.partialPrint(seed: Int) {
    val r = Random(seed * 7 + 3)
    if (r.nextFloat() > 0.34f) return
    val angle = r.nextFloat() * 360f
    val reach = 0.18f + r.nextFloat() * 0.16f
    rotate(angle) {
        drawRect(
            Brush.horizontalGradient(listOf(Color.Black, Color.Black.copy(alpha = 0.6f), Color.Transparent), startX = 0f, endX = size.width * reach),
            topLeft = Offset(0f, -size.height), size = Size(size.width, size.height * 3),
            blendMode = BlendMode.DstOut,
        )
    }
}

// endregion

package com.contrail.contrail.feature.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import androidx.annotation.FontRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withRotation
import androidx.core.graphics.withTranslation
import com.contrail.contrail.R
import com.contrail.contrail.core.designsystem.theme.AltitudeRamp
import com.contrail.contrail.core.designsystem.theme.ContrailColors
import com.contrail.contrail.core.designsystem.theme.NightCockpitColors
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Home-screen widget art, drawn as bitmaps at the widget's exact size.
 *
 * Glance can't use bundled fonts or draw shapes, so the departure board's flaps, the radar
 * scope and the Passport ring are painted here with the theme's colours and fonts.
 */
class WidgetArt(context: Context, private val colors: ContrailColors = NightCockpitColors) {
    private val density = context.resources.displayMetrics.density
    private val scaled = context.resources.displayMetrics.scaledDensity.takeIf { it > 0 } ?: density
    private val mono = font(context, R.font.jetbrains_mono, 700)
    private val display = font(context, R.font.space_grotesk, 700)
    private val ui = font(context, R.font.inter, 600)
    private val uiRegular = font(context, R.font.inter, 400)
    private val planeIcon = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_widget_plane)

    private fun dp(v: Float) = v * density
    private fun sp(v: Float) = v * scaled
    private fun Color.argb() = toArgb()

    private fun font(context: Context, @FontRes id: Int, weight: Int): Typeface {
        val base = runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() ?: Typeface.DEFAULT
        return Typeface.create(base, weight, false)
    }

    private fun paint(tf: Typeface = ui, size: Float = 12f, color: Color = colors.textPrimary, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf
            textSize = sp(size)
            this.color = color.argb()
            textAlign = align
        }

    /** The glass card every widget sits on: deep navy, a faint aurora in one corner, a hairline edge. */
    private fun card(w: Int, h: Int, glow: Color = colors.accent): Pair<Bitmap, Canvas> {
        val bmp = createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1))
        val c = Canvas(bmp)
        val r = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val corner = dp(24f)
        c.drawRoundRect(r, corner, corner, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), intArrayOf(colors.canvasRaised.argb(), colors.canvas.argb(), colors.auroraC.copy(alpha = 0.55f).compositeOver(colors.canvas).argb()), null, Shader.TileMode.CLAMP)
        })
        c.drawRoundRect(r, corner, corner, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(0f, 0f, maxOf(w, h) * 0.9f, glow.copy(alpha = 0.16f).argb(), 0, Shader.TileMode.CLAMP)
        })
        c.drawRoundRect(RectF(0.5f, 0.5f, w - 0.5f, h - 0.5f), corner, corner, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1f)
            color = Color.White.copy(alpha = 0.14f).argb()
        })
        return bmp to c
    }

    private fun Color.compositeOver(bg: Color): Color {
        val a = alpha
        return Color(red * a + bg.red * (1 - a), green * a + bg.green * (1 - a), blue * a + bg.blue * (1 - a), 1f)
    }

    // ---------------------------------------------------------------- departure board

    /** One split-flap tile with its character, hinge line and side notches. */
    private fun flap(c: Canvas, x: Float, y: Float, w: Float, h: Float, ch: Char, ink: Color = colors.textPrimary) {
        val r = RectF(x, y, x + w, y + h)
        val corner = w * 0.14f
        c.drawRoundRect(r, corner, corner, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, y, 0f, y + h, intArrayOf(colors.flapTile.copy(alpha = 1f).argb(), colors.canvasSunken.argb()), floatArrayOf(0.5f, 1f), Shader.TileMode.CLAMP)
        })
        // Top half catches a little light.
        c.drawRoundRect(RectF(x, y, x + w, y + h / 2), corner, corner, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.White.copy(alpha = 0.05f).argb() })
        val p = paint(mono, 1f, ink, Paint.Align.CENTER).apply { textSize = h * 0.68f }
        val fm = p.fontMetrics
        c.drawText(ch.toString(), x + w / 2, y + h / 2 - (fm.ascent + fm.descent) / 2, p)
        val hinge = Paint().apply { color = colors.flapTileEdge.argb(); strokeWidth = maxOf(1f, h * 0.035f) }
        c.drawLine(x, y + h / 2, x + w, y + h / 2, hinge)
        val notch = Paint().apply { color = colors.flapTileEdge.argb() }
        c.drawRect(x - 0.5f, y + h * 0.42f, x + w * 0.06f, y + h * 0.58f, notch)
        c.drawRect(x + w * 0.94f, y + h * 0.42f, x + w + 0.5f, y + h * 0.58f, notch)
    }

    private fun flaps(c: Canvas, x: Float, y: Float, tileW: Float, tileH: Float, text: String, ink: Color = colors.textPrimary): Float {
        val gap = tileW * 0.1f
        text.forEachIndexed { i, ch -> flap(c, x + i * (tileW + gap), y, tileW, tileH, ch, ink) }
        return text.length * (tileW + gap) - gap
    }

    private fun plane(c: Canvas, cx: Float, cy: Float, size: Float, tint: Color, rotation: Float = 90f) {
        val d = planeIcon?.mutate() ?: return
        d.setTint(tint.argb())
        d.setBounds((-size / 2).toInt(), (-size / 2).toInt(), (size / 2).toInt(), (size / 2).toInt())
        c.withTranslation(cx, cy) { withRotation(rotation - 90f) { d.draw(this) } }
    }

    private fun pill(c: Canvas, right: Float, top: Float, text: String, tint: Color): Float {
        val p = paint(ui, 9.5f, tint).apply { letterSpacing = 0.08f }
        val tw = p.measureText(text)
        val padH = dp(7f)
        val h = dp(18f)
        val r = RectF(right - tw - padH * 2, top, right, top + h)
        c.drawRoundRect(r, h / 2, h / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint.copy(alpha = 0.16f).argb() })
        c.drawRoundRect(r, h / 2, h / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1f); color = tint.copy(alpha = 0.45f).argb() })
        val fm = p.fontMetrics
        c.drawText(text, r.left + padH, r.centerY() - (fm.ascent + fm.descent) / 2, p)
        return r.left
    }

    private fun ellipsize(text: String, p: Paint, max: Float): String {
        if (p.measureText(text) <= max) return text
        var t = text
        while (t.isNotEmpty() && p.measureText("$t…") > max) t = t.dropLast(1)
        return "$t…"
    }

    fun board(widthDp: Float, heightDp: Float, m: BoardModel): Bitmap {
        val w = dp(widthDp).toInt()
        val h = dp(heightDp).toInt()
        val (bmp, c) = card(w, h, m.accent)
        val pad = dp(14f)
        val inner = w - pad * 2
        val compact = widthDp < 200f

        // Header: airline line and status pill.
        val statusLeft = m.status?.let { pill(c, w - pad, pad - dp(1f), it.uppercase(Locale.UK), m.statusColor) } ?: (w - pad)
        val head = paint(ui, 10f, m.accent).apply { letterSpacing = 0.1f }
        val header = if (compact) m.header.substringAfterLast(" · ") else m.header
        c.drawText(ellipsize(header.uppercase(Locale.UK), head, statusLeft - pad - dp(8f)), pad, pad + sp(10f), head)

        if (m.from == null || m.to == null) {
            val tileH = min(dp(44f), (h - pad * 2) * 0.4f)
            val words = if (compact) "NO\nFLIGHTS" else "NO FLIGHTS"
            var y = pad + dp(26f)
            words.split("\n").forEach { line ->
                val tileW = min(tileH * 0.72f, inner / (line.length * 1.1f))
                flaps(c, pad, y, tileW, tileW / 0.72f, line, colors.textSecondary)
                y += tileW / 0.72f + dp(6f)
            }
            c.drawText("Add one in Trips", pad, (y + sp(12f)).coerceAtMost(h - pad), paint(uiRegular, 12f, colors.textTertiary))
            return bmp
        }

        // Route: big flaps, the plane between them, city names underneath.
        val routeTop = pad + dp(24f)
        val gapForPlane = 1.4f
        val maxTileH = if (compact) (h - pad * 2) * 0.32f else (h - pad * 2) * 0.36f
        val tileW = min(maxTileH * 0.72f, inner / ((m.from.length + m.to.length) * 1.1f + gapForPlane))
        val tileH = tileW / 0.72f
        val fromW = flaps(c, pad, routeTop, tileW, tileH, m.from)
        val planeX = pad + fromW + tileW * gapForPlane / 2 + tileW * 0.05f
        plane(c, planeX, routeTop + tileH / 2, tileW * 0.8f, m.accent)
        val toX = pad + fromW + tileW * gapForPlane
        flaps(c, toX, routeTop, tileW, tileH, m.to)
        val city = paint(uiRegular, 11f, colors.textSecondary)
        val cityY = routeTop + tileH + sp(14f)
        c.drawText(ellipsize(m.fromCity, city, toX - pad - dp(8f)), pad, cityY, city)
        c.drawText(ellipsize(m.toCity, city, w - pad - toX), toX, cityY, city)

        var y = cityY + dp(10f)
        val bottom = h - pad

        // Details as small flap cells, as many as fit.
        if (!compact && m.cells.isNotEmpty() && bottom - y > dp(64f)) {
            val cellTileH = dp(19f)
            val cellTileW = cellTileH * 0.72f
            var x = pad
            val label = paint(ui, 8.5f, colors.textTertiary).apply { letterSpacing = 0.12f }
            for ((k, v) in m.cells) {
                val vw = v.length * cellTileW * 1.1f
                val cw = maxOf(vw, label.measureText(k.uppercase(Locale.UK)))
                if (x + cw > w - pad + 1) break
                c.drawText(k.uppercase(Locale.UK), x, y + sp(8.5f), label)
                flaps(c, x, y + sp(12f), cellTileW, cellTileH, v)
                x += cw + dp(12f)
            }
            y += sp(12f) + cellTileH + dp(8f)
        }

        // Progress once the flight is under way, otherwise the countdown.
        if (m.progress != null && bottom - y > dp(22f)) {
            val barY = bottom - dp(20f)
            val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = dp(3f); strokeCap = Paint.Cap.ROUND; color = Color.White.copy(alpha = 0.12f).argb() }
            val endX = w - pad - dp(4f)
            c.drawLine(pad + dp(4f), barY, endX, barY, track)
            val px = pad + dp(4f) + (endX - pad - dp(4f)) * m.progress.coerceIn(0f, 1f)
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeWidth = dp(3f)
                strokeCap = Paint.Cap.ROUND
                shader = LinearGradient(pad, 0f, px, 0f, m.accent.copy(alpha = 0.35f).argb(), m.accent.argb(), Shader.TileMode.CLAMP)
            }
            c.drawLine(pad + dp(4f), barY, px, barY, fill)
            plane(c, px, barY, dp(16f), colors.textPrimary)
            m.footer?.let { c.drawText(it, w - pad, bottom, paint(ui, 11f, colors.textSecondary, Paint.Align.RIGHT)) }
        } else if (m.footer != null) {
            val big = paint(display, 20f, m.accent)
            if (compact && m.footer.startsWith("Departs in ")) {
                c.drawText("DEPARTS IN", pad, bottom - sp(26f), paint(ui, 9f, colors.textTertiary).apply { letterSpacing = 0.12f })
                c.drawText(ellipsize(m.footer.removePrefix("Departs in "), big, inner), pad, bottom - dp(2f), big)
            } else {
                c.drawText(ellipsize(m.footer, big, inner), pad, bottom - dp(2f), big)
                val note = paint(uiRegular, 11f, colors.textTertiary, Paint.Align.RIGHT)
                m.footnote?.let { if (!compact && big.measureText(m.footer) + note.measureText(it) + dp(16f) < inner) c.drawText(it, w - pad, bottom - dp(2f), note) }
            }
        }
        return bmp
    }

    // ---------------------------------------------------------------- radar scope

    fun scope(widthDp: Float, heightDp: Float, m: ScopeModel): Bitmap {
        val w = dp(widthDp).toInt()
        val h = dp(heightDp).toInt()
        val (bmp, c) = card(w, h, colors.statusAirborne)
        val pad = dp(12f)
        val wide = widthDp > heightDp * 1.45f
        val size = if (wide) h - pad * 2 else min(w.toFloat(), h - dp(40f)) - pad * 2
        val cx = if (wide) pad + size / 2 else w / 2f
        val cy = pad + size / 2
        val r = size / 2

        c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cy, r, intArrayOf(colors.canvasRaised.argb(), colors.canvasSunken.argb()), null, Shader.TileMode.CLAMP)
        })
        // A static sweep for the radar look, trailing clockwise from north-east.
        c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = SweepGradient(cx, cy, intArrayOf(0, 0, colors.accent.copy(alpha = 0.22f).argb()), floatArrayOf(0f, 0.75f, 1f)).also {
                it.setLocalMatrix(android.graphics.Matrix().apply { setRotate(-45f, cx, cy) })
            }
        })
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1f); color = colors.accent.copy(alpha = 0.18f).argb() }
        listOf(1f, 2f / 3f, 1f / 3f).forEach { c.drawCircle(cx, cy, r * it, ring) }
        val cross = Paint().apply { strokeWidth = dp(0.8f); color = colors.accent.copy(alpha = 0.08f).argb() }
        c.drawLine(cx - r, cy, cx + r, cy, cross)
        c.drawLine(cx, cy - r, cx, cy + r, cross)
        c.drawText("N", cx, cy - r + sp(11f), paint(ui, 9f, colors.textTertiary, Paint.Align.CENTER))
        m.radiusKm?.let { c.drawText("${it} km", cx + r * 0.72f, cy + r * 0.95f, paint(uiRegular, 8f, colors.textTertiary, Paint.Align.CENTER)) }

        // Aircraft: north-up, each pointing along its track, coloured by altitude.
        val scale = r / (m.radiusKm ?: 25).toFloat()
        m.blips.sortedByDescending { it.distanceKm }.forEach { b ->
            val d = (b.distanceKm * scale).toFloat().coerceAtMost(r * 0.97f)
            val a = Math.toRadians(b.bearingDeg)
            val x = cx + d * sin(a).toFloat()
            val y = cy - d * cos(a).toFloat()
            val tint = AltitudeRamp.colorFor(b.altitudeFt)
            if (b.closest) {
                c.drawCircle(x, y, dp(9f), Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1.2f); color = colors.textPrimary.copy(alpha = 0.8f).argb() })
            }
            plane(c, x, y, if (b.closest) dp(13f) else dp(10f), tint, b.trackDeg ?: 0f)
        }
        c.drawCircle(cx, cy, dp(3f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.accent.argb() })
        c.drawCircle(cx, cy, dp(6f), Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1f); color = colors.accent.copy(alpha = 0.5f).argb() })

        // Words: beside the scope when wide, under it when square.
        val left = if (wide) cx + r + dp(14f) else pad + dp(2f)
        val maxText = w - pad - left
        var y = if (wide) pad + sp(10f) else cy + r + sp(14f)
        val label = paint(ui, 9.5f, colors.statusAirborne).apply { letterSpacing = 0.1f }
        if (wide) {
            c.drawText(ellipsize(("Overhead" + (m.spotName?.let { " · $it" } ?: "")).uppercase(Locale.UK), label, maxText), left, y, label)
            y += sp(26f)
        }
        val head = paint(display, if (wide) 22f else 15f, colors.textPrimary)
        val line = paint(uiRegular, if (wide) 12f else 10.5f, colors.textSecondary)
        when {
            m.closestCallsign == null -> {
                c.drawText(m.emptyTitle, left, y, head.apply { textSize = sp(if (wide) 18f else 14f) })
                if (wide) c.drawText(ellipsize(m.emptySubtitle, line, maxText), left, y + sp(18f), line)
            }
            wide -> {
                c.drawText(ellipsize(m.closestCallsign, head, maxText), left, y, head)
                m.closestType?.let { c.drawText(ellipsize(it, line, maxText), left, y + sp(18f), line) }
                m.closestDetail?.let { c.drawText(ellipsize(it, line, maxText), left, y + sp(36f), line.apply { color = colors.textPrimary.argb() }) }
            }
            else -> {
                val text = m.closestCallsign + (m.closestDetail?.substringBefore(" · ")?.let { "  ·  $it" } ?: "")
                c.drawText(ellipsize(text, head, w - pad * 2), w / 2f, y, head.apply { textAlign = Paint.Align.CENTER; textSize = sp(11f) })
            }
        }
        m.footer?.let {
            if (wide) c.drawText(ellipsize(it, line, maxText), left, h - pad, paint(uiRegular, 10.5f, colors.textTertiary))
        }
        return bmp
    }

    // ---------------------------------------------------------------- Passport ring

    fun yearRing(widthDp: Float, heightDp: Float, m: RingModel): Bitmap {
        val w = dp(widthDp).toInt()
        val h = dp(heightDp).toInt()
        val (bmp, c) = card(w, h, colors.passport)
        val pad = dp(14f)
        val wide = widthDp > heightDp * 1.35f
        val size = if (wide) h - pad * 2 else min(w.toFloat(), h.toFloat()) - pad * 2 - dp(18f)
        val cx = if (wide) pad + size / 2 else w / 2f
        val cy = pad + size / 2 + if (wide) 0f else dp(4f)
        val stroke = size * 0.09f
        val r = size / 2 - stroke / 2
        val oval = RectF(cx - r, cy - r, cx + r, cy + r)
        c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; color = Color.White.copy(alpha = 0.08f).argb() })
        val laps = m.yearKm / EARTH_KM
        val sweep = ((laps % 1.0) * 360).toFloat().let { if (laps >= 1 && it < 2f) 360f else it }
        if (sweep > 0f) {
            val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
                shader = SweepGradient(cx, cy, intArrayOf(colors.passport.argb(), colors.accent.argb(), colors.passport.argb()), null).also {
                    it.setLocalMatrix(android.graphics.Matrix().apply { setRotate(-90f, cx, cy) })
                }
            }
            c.drawArc(oval, -90f, sweep, false, arc)
            // A small plane rides the end of the arc.
            val end = Math.toRadians((sweep - 90f).toDouble())
            plane(c, cx + r * cos(end).toFloat(), cy + r * sin(end).toFloat(), stroke * 1.5f, colors.textPrimary, sweep + 90f)
        }
        val n = paint(display, 1f, colors.textPrimary, Paint.Align.CENTER).apply { textSize = size * 0.26f }
        c.drawText(String.format(Locale.US, "%,d", m.yearFlights), cx, cy + size * 0.06f, n)
        c.drawText(if (m.yearFlights == 1) "flight in ${m.year}" else "flights in ${m.year}", cx, cy + size * 0.06f + sp(13f), paint(uiRegular, 10f, colors.textTertiary, Paint.Align.CENTER))

        val earth = when {
            m.yearKm <= 0 -> "Your year starts with one flight"
            laps < 0.1 -> String.format(Locale.US, "%,d km this year", m.yearKm.toLong())
            else -> String.format(Locale.US, "%.2f× around Earth", laps)
        }
        if (wide) {
            val left = cx + size / 2 + dp(16f)
            val maxText = w - pad - left
            var y = pad + sp(10f)
            c.drawText("PASSPORT", left, y, paint(ui, 9.5f, colors.passport).apply { letterSpacing = 0.12f })
            y += sp(24f)
            val big = paint(display, 16f, colors.textPrimary)
            c.drawText(ellipsize(earth, big, maxText), left, y, big)
            y += sp(18f)
            val line = paint(uiRegular, 11.5f, colors.textSecondary)
            c.drawText(ellipsize(String.format(Locale.US, "%,d km in %d", m.yearKm.toLong(), m.year), line, maxText), left, y, line)
            y += sp(24f)
            c.drawText("ALL TIME", left, y, paint(ui, 8.5f, colors.textTertiary).apply { letterSpacing = 0.12f })
            y += sp(15f)
            c.drawText(ellipsize(String.format(Locale.US, "%,d flights · %d countries", m.lifetimeFlights, m.countries), line, maxText), left, y, line.apply { color = colors.textPrimary.argb() })
            m.favourite?.let { c.drawText(ellipsize("Favourite: $it", line, maxText), left, y + sp(16f), paint(uiRegular, 11f, colors.textTertiary)) }
        } else {
            c.drawText(ellipsize(earth, paint(ui, 10.5f), w - pad * 2), w / 2f, h - pad + dp(2f), paint(ui, 10.5f, colors.textSecondary, Paint.Align.CENTER))
        }
        return bmp
    }

    companion object {
        const val EARTH_KM = 40_075.0
    }
}

data class BoardModel(
    val header: String,
    val accent: Color,
    val from: String?,
    val to: String?,
    val fromCity: String = "",
    val toCity: String = "",
    val status: String? = null,
    val statusColor: Color = NightCockpitColors.statusOnTime,
    /** Label → value, e.g. "Gate" → "B12". Shown as small flaps when there's room. */
    val cells: List<Pair<String, String>> = emptyList(),
    /** 0..1 once the flight is under way. */
    val progress: Float? = null,
    val footer: String? = null,
    val footnote: String? = null,
)

data class Blip(val bearingDeg: Double, val distanceKm: Double, val altitudeFt: Int?, val trackDeg: Float?, val closest: Boolean = false)

data class ScopeModel(
    val blips: List<Blip>,
    val radiusKm: Int?,
    val spotName: String?,
    val closestCallsign: String?,
    val closestType: String?,
    val closestDetail: String?,
    val footer: String?,
    val emptyTitle: String = "Clear skies",
    val emptySubtitle: String = "Nothing nearby right now",
)

data class RingModel(
    val year: Int,
    val yearFlights: Int,
    val yearKm: Double,
    val lifetimeFlights: Int,
    val lifetimeKm: Long,
    val countries: Int,
    val favourite: String?,
)

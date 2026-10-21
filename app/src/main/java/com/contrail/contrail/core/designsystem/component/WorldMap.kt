package com.contrail.contrail.core.designsystem.component

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import java.io.DataInputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/** The bundled land stipple (Natural Earth, public domain): lat/lon pairs, about 3,400 dots. */
object WorldDots {
    @Volatile private var cached: FloatArray? = null

    fun load(context: Context): FloatArray = cached ?: synchronized(this) {
        cached ?: runCatching {
            context.assets.open("land_dots.bin").use { raw ->
                val bytes = raw.readBytes()
                val input = DataInputStream(bytes.inputStream())
                FloatArray(bytes.size / 2) { input.readShort() / 100f }
            }
        }.getOrDefault(FloatArray(0)).also { cached = it }
    }
}

@Composable
fun rememberWorldDots(): FloatArray {
    val context = LocalContext.current
    return remember { WorldDots.load(context) }
}

/** An arc to draw: great-circle points, and how much of it has drawn itself (0..1). */
data class MapArc(val points: List<LatLon>, val progress: Float = 1f, val color: Color? = null, val emphasis: Boolean = false)

/**
 * A slowly spinning dotted globe (orthographic projection) with glowing route arcs lifted
 * off the surface. [centerLon]/[centerLat] say which side faces you.
 */
@Composable
fun DottedGlobe(
    arcs: List<MapArc>,
    centerLon: Float,
    modifier: Modifier = Modifier,
    centerLat: Float = 22f,
    accent: Color = ContrailTheme.colors.passport,
    markers: List<LatLon> = emptyList(),
) {
    val dots = rememberWorldDots()
    val colors = ContrailTheme.colors
    Canvas(modifier) {
        val r = min(size.width, size.height) / 2f * 0.92f
        val c = Offset(size.width / 2f, size.height / 2f)
        val lat0 = Geo.toRad(centerLat.toDouble())
        val lon0 = Geo.toRad(centerLon.toDouble())
        val sinLat0 = sin(lat0)
        val cosLat0 = cos(lat0)

        // Halo and ocean.
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.22f), Color.Transparent), c, r * 1.35f), r * 1.35f, c)
        drawCircle(Brush.radialGradient(listOf(colors.canvasRaised, colors.canvasSunken), Offset(c.x - r * 0.3f, c.y - r * 0.35f), r * 1.4f), r, c)
        drawCircle(accent.copy(alpha = 0.35f), r, c, style = Stroke(1.dp.toPx()))

        // Land dots in three brightness bands, brighter towards the centre of the disc.
        val bands = Array(3) { ArrayList<Offset>(dots.size / 3) }
        var i = 0
        while (i < dots.size - 1) {
            val lat = Geo.toRad(dots[i].toDouble())
            val dl = Geo.toRad(dots[i + 1].toDouble()) - lon0
            val cosLat = cos(lat)
            val cosc = sinLat0 * sin(lat) + cosLat0 * cosLat * cos(dl)
            if (cosc > 0) {
                val x = (r * cosLat * sin(dl)).toFloat()
                val y = (r * (cosLat0 * sin(lat) - sinLat0 * cosLat * cos(dl))).toFloat()
                bands[if (cosc > 0.66) 2 else if (cosc > 0.33) 1 else 0] += Offset(c.x + x, c.y - y)
            }
            i += 2
        }
        val dotSize = max(1.6f, r / 95f)
        bands.forEachIndexed { b, pts ->
            drawPoints(pts, PointMode.Points, colors.textSecondary.copy(alpha = 0.18f + b * 0.17f), strokeWidth = dotSize, cap = StrokeCap.Round)
        }

        // Routes, lifted off the surface by up to 12% of the radius at mid-arc.
        fun project(p: LatLon, lift: Double): Offset? {
            val lat = Geo.toRad(p.lat)
            val dl = Geo.toRad(p.lon) - lon0
            val cosLat = cos(lat)
            val cosc = sinLat0 * sin(lat) + cosLat0 * cosLat * cos(dl)
            val rr = r * (1 + lift)
            val x = rr * cosLat * sin(dl)
            val y = rr * (cosLat0 * sin(lat) - sinLat0 * cosLat * cos(dl))
            // Behind the globe and inside its outline: hidden. A lifted arc peeking over the limb stays.
            if (cosc < 0 && kotlin.math.hypot(x, y) < r) return null
            return Offset(c.x + x.toFloat(), c.y - y.toFloat())
        }
        arcs.forEach { arc ->
            if (arc.points.size < 2 || arc.progress <= 0f) return@forEach
            val n = arc.points.size
            val last = ((n - 1) * arc.progress.coerceIn(0f, 1f)).toInt()
            val distance = Geo.distanceM(arc.points.first(), arc.points.last()) / Geo.EARTH_RADIUS_M
            val height = (distance / PI * 0.25).coerceIn(0.02, 0.12)
            val path = Path()
            var pen = false
            for (k in 0..last) {
                val t = k.toDouble() / (n - 1)
                val o = project(arc.points[k], height * sin(PI * t))
                if (o == null) { pen = false; continue }
                if (!pen) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                pen = true
            }
            val col = arc.color ?: accent
            drawPath(path, col.copy(alpha = if (arc.emphasis) 0.32f else 0.2f), style = Stroke(if (arc.emphasis) 7.dp.toPx() else 5.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, col, style = Stroke(if (arc.emphasis) 2.dp.toPx() else 1.4.dp.toPx(), cap = StrokeCap.Round))
        }
        markers.forEach { m ->
            project(m, 0.0)?.let {
                drawCircle(accent.copy(alpha = 0.35f), 5.dp.toPx(), it)
                drawCircle(colors.textPrimary, 2.2.dp.toPx(), it)
            }
        }
        // Light from the top-left.
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent), Offset(c.x - r * 0.45f, c.y - r * 0.5f), r * 0.9f),
            r, c,
        )
    }
}

/**
 * A Web Mercator view fitted around some points: the same maths as a MapLibre camera, so a
 * map snapshot and anything drawn over it line up exactly.
 */
class MercatorFit(points: List<LatLon>, val widthPx: Float, val heightPx: Float, density: Float, padding: Float = 0.82f, maxZoom: Double = 9.0) {
    val centerLon: Double
    private val cx: Double
    private val cy: Double
    val zoom: Double
    private val scalePx: Double

    init {
        val lons = points.map { it.lon }
        centerLon = if (lons.isEmpty()) 0.0 else (lons.max() + lons.min()) / 2
        val xs = points.map { x(unwrap(it.lon)) }
        val ys = points.map { y(it.lat) }
        cx = if (xs.isEmpty()) 0.5 else (xs.max() + xs.min()) / 2
        cy = if (ys.isEmpty()) 0.4 else (ys.max() + ys.min()) / 2
        val spanX = if (xs.isEmpty()) 1.0 else (xs.max() - xs.min()).coerceAtLeast(1e-4)
        val spanY = if (ys.isEmpty()) 1.0 else (ys.max() - ys.min()).coerceAtLeast(1e-4)
        val wDp = widthPx / density
        val hDp = heightPx / density
        zoom = kotlin.math.log2(min(wDp * padding / (spanX * 512), hDp * padding / (spanY * 512))).coerceIn(0.0, maxZoom)
        scalePx = 512 * Math.pow(2.0, zoom) * density
    }

    val centerLat: Double get() = Geo.toDeg(kotlin.math.atan(kotlin.math.sinh(PI * (1 - 2 * cy))))
    val centerLonNormalized: Double get() = Geo.normalizeLon(cx * 360.0 - 180.0)

    fun unwrap(lon: Double): Double {
        var l = lon
        while (l - centerLon > 180) l -= 360
        while (l - centerLon < -180) l += 360
        return l
    }

    fun project(p: LatLon): Offset = project(p.lat, p.lon)

    fun project(lat: Double, lon: Double): Offset = Offset(
        ((x(unwrap(lon)) - cx) * scalePx + widthPx / 2).toFloat(),
        ((y(lat) - cy) * scalePx + heightPx / 2).toFloat(),
    )

    companion object {
        fun x(lon: Double) = (lon + 180.0) / 360.0
        fun y(lat: Double): Double {
            val phi = Geo.toRad(lat.coerceIn(-85.0, 85.0))
            return (1 - ln(tan(PI / 4 + phi / 2)) / PI) / 2
        }
    }
}

/** Land dots inside a fitted Mercator view: the stylised base map when no map snapshot is drawn. */
fun DrawScope.drawLandDots(fit: MercatorFit, dots: FloatArray, color: Color) {
    val pts = ArrayList<Offset>(512)
    var i = 0
    val margin = 8f
    while (i < dots.size - 1) {
        val o = fit.project(dots[i].toDouble(), dots[i + 1].toDouble())
        if (o.x > -margin && o.x < size.width + margin && o.y > -margin && o.y < size.height + margin) pts += o
        i += 2
    }
    // Dots get bigger as the view zooms in, so close-ups don't look sparse.
    val s = (2.2f * Math.pow(1.35, fit.zoom).toFloat()).coerceIn(2.2f, 9f).dp.toPx() / 2
    drawPoints(pts, PointMode.Points, color, strokeWidth = s, cap = StrokeCap.Round)
}

/** A route as a glowing line, trimmed to [progress], breaking where it wraps the antimeridian. */
fun DrawScope.drawRouteArc(points: List<Offset>, color: Color, progress: Float = 1f, dashedRest: Color? = null, width: Float = 2.2f) {
    if (points.size < 2) return
    val last = ((points.size - 1) * progress.coerceIn(0f, 1f))
    val full = last.toInt()
    fun build(from: Int, to: Int, tail: Offset?): Path = Path().apply {
        for (k in from..to) {
            val o = points[k]
            if (k == from || abs(o.x - points[k - 1].x) > size.width / 2) moveTo(o.x, o.y) else lineTo(o.x, o.y)
        }
        tail?.let { lineTo(it.x, it.y) }
    }
    val frac = last - full
    val tip = if (full < points.size - 1 && frac > 0f) {
        val a = points[full]; val b = points[full + 1]
        Offset(a.x + (b.x - a.x) * frac, a.y + (b.y - a.y) * frac)
    } else null
    if (dashedRest != null) {
        drawPath(
            build(0, points.size - 1, null), dashedRest,
            style = Stroke(1.2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))),
        )
    }
    val drawn = build(0, full, tip)
    drawPath(drawn, color.copy(alpha = 0.22f), style = Stroke((width + 4.5f).dp.toPx(), cap = StrokeCap.Round))
    drawPath(drawn, color, style = Stroke(width.dp.toPx(), cap = StrokeCap.Round))
}

/** Where a drawn arc's tip is, and which way it points (degrees, 0 = up), for the riding plane. */
fun arcTip(points: List<Offset>, progress: Float): Pair<Offset, Float>? {
    if (points.size < 2) return null
    val last = ((points.size - 1) * progress.coerceIn(0f, 1f))
    val i = last.toInt().coerceAtMost(points.size - 2)
    val frac = (last - i).coerceIn(0f, 1f)
    val a = points[i]; val b = points[i + 1]
    val tip = Offset(a.x + (b.x - a.x) * frac, a.y + (b.y - a.y) * frac)
    val angle = Math.toDegrees(kotlin.math.atan2((b.x - a.x).toDouble(), -(b.y - a.y).toDouble())).toFloat()
    return tip to angle
}

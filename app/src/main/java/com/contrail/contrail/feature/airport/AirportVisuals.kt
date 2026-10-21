package com.contrail.contrail.feature.airport

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.model.LiveAircraft
import com.contrail.contrail.core.database.RunwayEntity
import com.contrail.contrail.core.designsystem.theme.AltitudeRamp
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.core.network.airport.OsmResult
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** Local flat projection around an airport: metres east/north scaled into the canvas. */
private class LocalProjection(center: LatLon, private val metresPerPx: Double, private val cx: Float, private val cy: Float) {
    private val lat0 = center.lat
    private val lon0 = center.lon
    private val kx = Geo.METERS_PER_DEG_LAT * cos(Geo.toRad(lat0))
    fun x(lon: Double) = cx + ((lon - lon0) * kx / metresPerPx).toFloat()
    fun y(lat: Double) = cy - ((lat - lat0) * Geo.METERS_PER_DEG_LAT / metresPerPx).toFloat()
}

/**
 * Airport diagram: OpenStreetMap runways, taxiways and terminals when available, else the
 * OurAirports runway endpoints. The likely active runway end, from the wind, glows.
 */
@Composable
fun RunwayDiagram(
    center: LatLon,
    runways: List<RunwayEntity>,
    geometry: OsmResult?,
    activeEnd: String?,
    modifier: Modifier = Modifier,
) {
    val colors = ContrailTheme.colors
    val type = ContrailTheme.type
    val measurer = rememberTextMeasurer()
    Canvas(modifier.fillMaxWidth().aspectRatio(1.25f)) {
        val pts = buildList {
            geometry?.elements?.forEach { e -> e.geometry.forEach { add(LatLon(it.lat, it.lon)) } }
            runways.forEach { r ->
                if (r.leLat != null && r.leLon != null) add(LatLon(r.leLat, r.leLon))
                if (r.heLat != null && r.heLon != null) add(LatLon(r.heLat, r.heLon))
            }
        }
        if (pts.isEmpty()) return@Canvas
        val kx = Geo.METERS_PER_DEG_LAT * cos(Geo.toRad(center.lat))
        val spanX = (pts.maxOf { it.lon } - pts.minOf { it.lon }) * kx
        val spanY = (pts.maxOf { it.lat } - pts.minOf { it.lat }) * Geo.METERS_PER_DEG_LAT
        val mid = LatLon((pts.maxOf { it.lat } + pts.minOf { it.lat }) / 2, (pts.maxOf { it.lon } + pts.minOf { it.lon }) / 2)
        val mpp = max(spanX / (size.width * 0.86), spanY / (size.height * 0.86)).coerceAtLeast(1.0)
        val p = LocalProjection(mid, mpp, size.width / 2, size.height / 2)
        fun path(points: List<LatLon>, close: Boolean = false) = Path().apply {
            points.forEachIndexed { i, pt -> if (i == 0) moveTo(p.x(pt.lon), p.y(pt.lat)) else lineTo(p.x(pt.lon), p.y(pt.lat)) }
            if (close) close()
        }
        val widthPx = { m: Double -> (m / mpp).toFloat().coerceIn(2f, 26f) }
        val osm = geometry?.elements.orEmpty()
        osm.filter { it.tags["aeroway"] == "apron" }.forEach { drawPath(path(it.geometry.map { g -> LatLon(g.lat, g.lon) }, close = true), colors.glassTint) }
        osm.filter { it.tags["aeroway"] == "taxiway" }.forEach {
            drawPath(path(it.geometry.map { g -> LatLon(g.lat, g.lon) }), colors.textTertiary.copy(alpha = 0.35f), style = Stroke(widthPx(20.0), cap = StrokeCap.Round))
        }
        val osmRunways = osm.filter { it.tags["aeroway"] == "runway" && it.geometry.size >= 2 }
        if (osmRunways.isNotEmpty()) {
            osmRunways.forEach {
                drawPath(path(it.geometry.map { g -> LatLon(g.lat, g.lon) }), colors.textSecondary.copy(alpha = 0.9f), style = Stroke(widthPx(it.tags["width"]?.toDoubleOrNull() ?: 45.0), cap = StrokeCap.Butt))
            }
        } else {
            runways.forEach { r ->
                if (r.leLat == null || r.leLon == null || r.heLat == null || r.heLon == null) return@forEach
                drawLine(colors.textSecondary.copy(alpha = 0.9f), Offset(p.x(r.leLon), p.y(r.leLat)), Offset(p.x(r.heLon), p.y(r.heLat)), widthPx((r.widthFt ?: 150) * 0.3048))
            }
        }
        // Terminals: polygons plus their names.
        osm.filter { it.tags["aeroway"] == "terminal" }.forEach { t ->
            val ring = t.geometry.ifEmpty { t.members.firstOrNull { m -> m.role == "outer" }?.geometry.orEmpty() }.map { g -> LatLon(g.lat, g.lon) }
            if (ring.size < 3) return@forEach
            drawPath(path(ring, close = true), colors.accent.copy(alpha = 0.28f))
            drawPath(path(ring, close = true), colors.accent.copy(alpha = 0.8f), style = Stroke(1.2.dp.toPx()))
            t.tags["name"]?.let { name ->
                val c = LatLon(ring.sumOf { it.lat } / ring.size, ring.sumOf { it.lon } / ring.size)
                val layout = measurer.measure(name, type.eyebrow.copy(color = colors.textPrimary))
                drawText(layout, topLeft = Offset(p.x(c.lon) - layout.size.width / 2f, p.y(c.lat) - layout.size.height / 2f))
            }
        }
        // Runway end labels; the likely active end is highlighted.
        runways.forEach { r ->
            listOf(Triple(r.leIdent, r.leLat, r.leLon), Triple(r.heIdent, r.heLat, r.heLon)).forEach { (ident, la, lo) ->
                if (la == null || lo == null || ident.isBlank()) return@forEach
                val active = ident == activeEnd
                val o = Offset(p.x(lo), p.y(la))
                if (active) {
                    drawCircle(Brush.radialGradient(listOf(colors.statusOnTime.copy(alpha = 0.55f), Color.Transparent), o, 26.dp.toPx()), 26.dp.toPx(), o)
                }
                val layout = measurer.measure(ident, type.numericSmall.copy(color = if (active) colors.statusOnTime else colors.textPrimary))
                drawText(layout, topLeft = Offset(o.x - layout.size.width / 2f, o.y - layout.size.height / 2f))
            }
        }
    }
}

/** A small radar scope of traffic around the airport, coloured by altitude. */
@Composable
fun NearbyScope(center: LatLon, traffic: List<LiveAircraft>, radiusM: Double, modifier: Modifier = Modifier) {
    val colors = ContrailTheme.colors
    Canvas(modifier.fillMaxWidth().aspectRatio(1.4f)) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = min(size.width, size.height) / 2 * 0.95f
        for (i in 1..3) drawCircle(colors.hairline, r * i / 3f, c, style = Stroke(1.dp.toPx()))
        drawLine(colors.hairline, Offset(c.x - r, c.y), Offset(c.x + r, c.y))
        drawLine(colors.hairline, Offset(c.x, c.y - r), Offset(c.x, c.y + r))
        val p = LocalProjection(center, radiusM / r, c.x, c.y)
        traffic.forEach { a ->
            val o = Offset(p.x(a.lon), p.y(a.lat))
            if ((o - c).getDistance() > r) return@forEach
            val col = AltitudeRamp.colorFor(if (a.onGround) null else a.altitudeFt)
            drawCircle(col.copy(alpha = 0.25f), 8.dp.toPx(), o)
            drawCircle(col, 3.dp.toPx(), o)
            a.trackDeg?.let { hdg ->
                rotate(hdg, o) { drawLine(col, o, Offset(o.x, o.y - 9.dp.toPx()), 1.6.dp.toPx(), StrokeCap.Round) }
            }
        }
        drawCircle(colors.textPrimary, 4.dp.toPx(), c)
    }
}

/**
 * Every nonstop destination as a glowing great-circle arc over a snapshot of the night map.
 * Falls back to arcs on a plain canvas where the map renderer isn't available (tests).
 */
@Composable
fun DestinationsMap(home: LatLon, destinations: List<Destination>, modifier: Modifier = Modifier) {
    val colors = ContrailTheme.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val inPreview = LocalInspectionMode.current
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(1.6f).clip(ContrailTheme.shapes.small)) {
        val wPx = with(density) { maxWidth.roundToPx() }
        val hPx = with(density) { maxHeight.roundToPx() }
        var snapshot by remember { mutableStateOf<Pair<Bitmap, List<List<PointF>>>?>(null) }
        if (!inPreview && destinations.isNotEmpty() && wPx > 0) {
            DisposableEffect(destinations, wPx, hPx) {
                val all = destinations.map { LatLon(it.lat, it.lon) } + home
                val bounds = LatLngBounds.Builder().apply { all.forEach { include(LatLng(it.lat, it.lon)) } }.build()
                val json = context.assets.open("map/contrail_dark.json").bufferedReader().use { it.readText() }
                val snapper = runCatching {
                    MapSnapshotter(
                        context,
                        MapSnapshotter.Options(wPx, hPx)
                            .withStyleBuilder(Style.Builder().fromJson(json))
                            .withRegion(bounds)
                            .withLogo(false)
                            .withPixelRatio(density.density),
                    )
                }.getOrNull()
                snapper?.start({ snap ->
                    val arcs = destinations.map { d ->
                        Geo.greatCircle(home, LatLon(d.lat, d.lon), 40).map { snap.pixelForLatLng(LatLng(it.lat, it.lon)) }
                    }
                    snapshot = snap.bitmap to arcs
                }, { })
                onDispose { snapper?.cancel() }
            }
        }
        val snap = snapshot
        if (snap != null) {
            Image(snap.first.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        }
        Canvas(Modifier.fillMaxSize()) {
            if (snap == null) drawRect(colors.canvasSunken)
            val arcs: List<List<Offset>> = snap?.second?.map { a -> a.map { Offset(it.x, it.y) } }
                ?: fallbackArcs(home, destinations)
            arcs.forEach { arc ->
                val path = Path().apply { arc.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
                drawPath(path, colors.accent.copy(alpha = 0.18f), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                drawPath(path, colors.accent.copy(alpha = 0.85f), style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round))
                arc.lastOrNull()?.let { drawCircle(colors.accent, 2.2.dp.toPx(), it) }
            }
            (arcs.firstOrNull()?.firstOrNull())?.let {
                drawCircle(colors.textPrimary, 4.5.dp.toPx(), it)
                drawCircle(colors.accent.copy(alpha = 0.4f), 10.dp.toPx(), it, style = Stroke(1.5.dp.toPx()))
            }
        }
    }
}

/** Equirectangular arcs fitted to the canvas, for when no map snapshot is available. */
private fun DrawScope.fallbackArcs(home: LatLon, destinations: List<Destination>): List<List<Offset>> {
    val all = destinations.map { LatLon(it.lat, it.lon) } + home
    if (all.size < 2) return emptyList()
    val minLat = all.minOf { it.lat }; val maxLat = all.maxOf { it.lat }
    val minLon = all.minOf { it.lon }; val maxLon = all.maxOf { it.lon }
    val sx = size.width * 0.9f / (maxLon - minLon).coerceAtLeast(1.0).toFloat()
    val sy = size.height * 0.85f / (maxLat - minLat).coerceAtLeast(1.0).toFloat()
    val s = min(sx, sy)
    val ox = (size.width - (maxLon - minLon).toFloat() * s) / 2
    val oy = (size.height - (maxLat - minLat).toFloat() * s) / 2
    fun o(p: LatLon) = Offset(ox + ((p.lon - minLon) * s).toFloat(), oy + ((maxLat - p.lat) * s).toFloat())
    return destinations.map { d -> Geo.greatCircle(home, LatLon(d.lat, d.lon), 24).map(::o) }
}

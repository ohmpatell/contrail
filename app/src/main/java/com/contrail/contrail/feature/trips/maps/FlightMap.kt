package com.contrail.contrail.feature.trips.maps

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.designsystem.component.MercatorFit
import com.contrail.contrail.core.designsystem.component.drawLandDots
import com.contrail.contrail.core.designsystem.component.rememberWorldDots
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter

/**
 * A map fitted around [focus]. With [realMap] it's a MapLibre snapshot of the night style
 * (one static render, no live GL view to keep alive); otherwise, and until the snapshot is
 * ready, it's the dotted land stipple. [overlay] draws on top using the same projection.
 */
@Composable
fun FlightMap(
    focus: List<LatLon>,
    modifier: Modifier = Modifier,
    realMap: Boolean = true,
    overlay: DrawScope.(MercatorFit) -> Unit,
) {
    val colors = ContrailTheme.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val inPreview = LocalInspectionMode.current
    val dots = rememberWorldDots()
    BoxWithConstraints(modifier) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val fit = remember(focus, wPx, hPx) { MercatorFit(focus, wPx, hPx, density.density) }
        var snapshot by remember { mutableStateOf<Bitmap?>(null) }
        if (realMap && !inPreview && focus.isNotEmpty() && wPx > 0 && hPx > 0) {
            DisposableEffect(fit) {
                val json = runCatching { context.assets.open("map/contrail_dark.json").bufferedReader().use { it.readText() } }.getOrNull()
                val snapper = json?.let {
                    runCatching {
                        MapSnapshotter(
                            context,
                            MapSnapshotter.Options((wPx / density.density).toInt(), (hPx / density.density).toInt())
                                .withStyleBuilder(Style.Builder().fromJson(it))
                                .withCameraPosition(
                                    CameraPosition.Builder().target(LatLng(fit.centerLat, fit.centerLonNormalized)).zoom(fit.zoom).build(),
                                )
                                .withLogo(false)
                                .withPixelRatio(density.density),
                        )
                    }.getOrNull()
                }
                snapper?.start({ snap -> snapshot = snap.bitmap }, { })
                onDispose { snapper?.cancel() }
            }
        }
        snapshot?.let {
            Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        }
        val land = colors.textSecondary.copy(alpha = 0.22f)
        Canvas(Modifier.fillMaxSize()) {
            if (snapshot == null) {
                drawRect(Brush.verticalGradient(listOf(colors.canvasRaised, colors.canvasSunken)))
                drawLandDots(fit, dots, land)
            }
            overlay(fit)
        }
    }
}

/** Draws airport dots with their codes and a plane icon; shared by the trip and flight maps. */
class MapMarkers(val drawPlane: DrawScope.(Offset, Float, Color, Float) -> Unit, val drawCode: DrawScope.(String, Offset, Color, Boolean) -> Unit)

@Composable
fun rememberMapMarkers(): MapMarkers {
    val plane = rememberVectorPainter(ContrailIcons.Plane)
    val measurer = rememberTextMeasurer()
    val style = ContrailTheme.type.numericSmall
    val textColor = ContrailTheme.colors.textPrimary
    val halo = ContrailTheme.colors.canvasSunken
    return remember(plane, measurer, style) {
        MapMarkers(
            drawPlane = { at, heading, tint, sizeDp ->
                val s = sizeDp.dp.toPx()
                drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.55f), Color.Transparent), at, s * 1.2f), s * 1.2f, at)
                rotate(heading, pivot = at) {
                    translate(at.x - s / 2, at.y - s / 2) {
                        with(plane) { draw(Size(s, s), colorFilter = ColorFilter.tint(Color.White)) }
                    }
                }
            },
            drawCode = { code, at, tint, below ->
                drawCircle(halo, 5.dp.toPx(), at)
                drawCircle(tint, 3.2.dp.toPx(), at)
                val layout = measurer.measure(code, TextStyle.Default.merge(style).copy(color = textColor))
                val tl = Offset(at.x - layout.size.width / 2f, if (below) at.y + 7.dp.toPx() else at.y - 7.dp.toPx() - layout.size.height)
                drawText(layout, topLeft = tl)
            },
        )
    }
}

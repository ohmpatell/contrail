package com.contrail.contrail.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.asImage
import coil3.test.FakeImageLoaderEngine
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.AltitudeRamp
import com.contrail.contrail.core.designsystem.theme.NightCockpitColors

/**
 * A static stand-in for the MapLibre radar (which needs a GPU), drawn to look like the
 * real night-cockpit map: land, a lake, a few roads, trails and aircraft in the altitude ramp.
 */
@Composable
fun FakeRadarBackdrop(modifier: Modifier = Modifier) {
    val plane = rememberVectorPainter(ContrailIcons.Plane)
    Canvas(modifier.fillMaxSize()) {
        drawRect(NightCockpitColors.canvas)
        val lake = ComposePath().apply {
            moveTo(size.width * 0.35f, size.height * 0.55f)
            cubicTo(size.width * 0.6f, size.height * 0.45f, size.width * 1.1f, size.height * 0.48f, size.width * 1.1f, size.height * 0.62f)
            lineTo(size.width * 1.1f, size.height * 0.75f)
            cubicTo(size.width * 0.8f, size.height * 0.72f, size.width * 0.5f, size.height * 0.7f, size.width * 0.35f, size.height * 0.55f)
            close()
        }
        drawPath(lake, Color(0xFF0A1F3A))
        drawPath(lake, Color(0xFF173A63), style = Stroke(1.5.dp.toPx()))
        listOf(0.2f to 0.1f, 0.7f to 0.25f, 0.1f to 0.8f).forEach { (x, y) ->
            drawLine(Color(0xFF18284A), Offset(size.width * x, 0f), Offset(size.width * y, size.height), 2.dp.toPx())
        }
        data class Blip(val x: Float, val y: Float, val heading: Float, val alt: Int?)
        val blips = listOf(
            Blip(0.42f, 0.40f, 45f, 3_000), Blip(0.55f, 0.36f, 120f, 9_000), Blip(0.30f, 0.30f, 300f, 21_000),
            Blip(0.70f, 0.22f, 80f, 36_000), Blip(0.18f, 0.55f, 260f, 41_000), Blip(0.82f, 0.44f, 200f, 28_000),
            Blip(0.48f, 0.47f, 10f, null), Blip(0.62f, 0.68f, 95f, 34_000), Blip(0.25f, 0.70f, 140f, 15_000),
        )
        blips.forEach { b ->
            val c = AltitudeRamp.colorFor(b.alt)
            val p = Offset(size.width * b.x, size.height * b.y)
            val rad = Math.toRadians(b.heading.toDouble())
            val tail = Offset(p.x - (kotlin.math.sin(rad) * 160).toFloat(), p.y + (kotlin.math.cos(rad) * 160).toFloat())
            drawLine(c.copy(alpha = 0.45f), tail, p, 2.dp.toPx())
            val s = 26.dp.toPx()
            rotate(b.heading, p) {
                translate(p.x - s / 2, p.y - s / 2) {
                    with(plane) { draw(Size(s, s), colorFilter = ColorFilter.tint(c)) }
                }
            }
        }
    }
}

/** Coil loader that serves a painted "photo" (sky, aircraft, runway haze) for every request. */
fun fakePhotoLoader(context: android.content.Context): ImageLoader {
    val w = 840
    val h = 560
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF5D8FD0.toInt(), 0xFFC9DDF2.toInt(), Shader.TileMode.CLAMP)
    })
    val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF4F6FA.toInt() }
    val red = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD62030.toInt() }
    // Fuselage, wing, tail: a side-on airliner.
    canvas.drawRoundRect(140f, 250f, 720f, 320f, 35f, 35f, white)
    canvas.drawPath(Path().apply { moveTo(360f, 300f); lineTo(520f, 300f); lineTo(330f, 410f); lineTo(280f, 410f); close() }, white)
    canvas.drawPath(Path().apply { moveTo(150f, 260f); lineTo(190f, 140f); lineTo(240f, 140f); lineTo(250f, 260f); close() }, red)
    canvas.drawRoundRect(420f, 320f, 500f, 360f, 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB8C1CF.toInt() })
    val engine = FakeImageLoaderEngine.Builder().default(bitmap.asImage()).build()
    return ImageLoader.Builder(context).components { add(engine) }.build()
}

package com.contrail.contrail.feature.radar.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.contrail.contrail.core.data.reference.Silhouette

/**
 * Top-down aircraft silhouettes for the radar, nose up, drawn on a 100×100 grid and
 * rendered to SDF bitmaps so MapLibre can tint them by altitude.
 */
object AircraftIcons {

    fun imageName(silhouette: Silhouette) = "ac-" + silhouette.name.lowercase()

    /** Logical icon size in dp at icon-size 1, per silhouette, so a 747 reads bigger than a Cessna. */
    private fun logicalSize(s: Silhouette): Float = when (s) {
        Silhouette.Quad -> 38f
        Silhouette.Widebody -> 35f
        Silhouette.Narrowbody, Silhouette.Generic -> 30f
        Silhouette.Regional -> 27f
        Silhouette.Turboprop -> 26f
        Silhouette.Bizjet -> 24f
        Silhouette.Light -> 21f
        Silhouette.Helicopter -> 24f
    }

    /** Builds one SDF bitmap per silhouette. Bitmap density carries the pixel ratio to MapLibre. */
    fun render(density: Float): Map<String, Bitmap> = Silhouette.entries.associate { s ->
        imageName(s) to renderSdf(path(s), logicalSize(s), density)
    }

    private fun renderSdf(path: Path, logicalDp: Float, density: Float): Bitmap {
        val pad = (7f * density).toInt() + 2
        val inner = (logicalDp * density).toInt()
        val size = inner + pad * 2
        val mask = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(mask)
        val matrix = Matrix().apply {
            setRectToRect(RectF(0f, 0f, 100f, 100f), RectF(pad.toFloat(), pad.toFloat(), (pad + inner).toFloat(), (pad + inner).toFloat()), Matrix.ScaleToFit.CENTER)
        }
        val scaled = Path(path).apply { transform(matrix) }
        canvas.drawPath(scaled, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL })

        val pixels = ByteArray(size * size)
        mask.copyPixelsToBuffer(java.nio.ByteBuffer.wrap(pixels))
        mask.recycle()
        val coverage = FloatArray(size * size) { (pixels[it].toInt() and 0xFF) / 255f }
        val sdf = SignedDistanceField.encode(coverage, size, size, density)

        val argb = IntArray(size * size) { i -> (sdf[i].toInt() and 0xFF) shl 24 or 0x00FFFFFF }
        return Bitmap.createBitmap(argb, size, size, Bitmap.Config.ARGB_8888).apply {
            this.density = (density * 160f).toInt()
        }
    }

    /** Right half outline (x offset from the centreline, y down), mirrored to a full shape. */
    private fun mirrored(vararg pts: Float): Path {
        val path = Path()
        val n = pts.size / 2
        path.moveTo(50f + pts[0], pts[1])
        for (i in 1 until n) path.lineTo(50f + pts[i * 2], pts[i * 2 + 1])
        for (i in n - 1 downTo 0) path.lineTo(50f - pts[i * 2], pts[i * 2 + 1])
        path.close()
        return path
    }

    private fun Path.withEngines(vararg centres: Float, top: Float, bottom: Float, halfWidth: Float): Path {
        for (cx in centres) {
            addRoundRect(RectF(50f + cx - halfWidth, top, 50f + cx + halfWidth, bottom), halfWidth, halfWidth, Path.Direction.CW)
            addRoundRect(RectF(50f - cx - halfWidth, top, 50f - cx + halfWidth, bottom), halfWidth, halfWidth, Path.Direction.CW)
        }
        return this
    }

    private fun path(s: Silhouette): Path = when (s) {
        Silhouette.Narrowbody, Silhouette.Generic -> mirrored(
            0f, 3f, 2.6f, 6f, 3.8f, 12f, 4f, 38f, 44f, 58f, 45f, 62.5f, 4f, 54f,
            4f, 78f, 2.8f, 84f, 15f, 92f, 15f, 95.5f, 1.6f, 93.5f, 0f, 98f,
        ).withEngines(14f, top = 43f, bottom = 53f, halfWidth = 2.6f)

        Silhouette.Widebody -> mirrored(
            0f, 2f, 3.4f, 5f, 5f, 11f, 5.2f, 36f, 47f, 59f, 48f, 63f, 5.2f, 53f,
            5f, 78f, 3.5f, 84f, 17f, 92f, 17f, 95.5f, 2f, 93.5f, 0f, 98.5f,
        ).withEngines(16f, top = 40f, bottom = 52f, halfWidth = 3.4f)

        Silhouette.Quad -> mirrored(
            0f, 1.5f, 3.6f, 4.5f, 5.6f, 10f, 5.8f, 34f, 48f, 60f, 49f, 64f, 5.8f, 52f,
            5.4f, 78f, 3.6f, 84f, 18f, 92f, 18f, 96f, 2f, 93.5f, 0f, 99f,
        ).withEngines(14f, 28f, top = 40f, bottom = 51f, halfWidth = 3f)

        Silhouette.Regional -> mirrored(
            0f, 8f, 2.4f, 11f, 3.4f, 16f, 3.6f, 42f, 36f, 56f, 37f, 60f, 3.6f, 54f,
            3.4f, 82f, 2f, 86f, 13f, 89f, 13f, 92.5f, 1.4f, 92f, 0f, 95f,
        ).withEngines(6.5f, top = 64f, bottom = 75f, halfWidth = 2.6f)

        Silhouette.Turboprop -> mirrored(
            0f, 9f, 2.6f, 12f, 3.6f, 18f, 3.8f, 38f, 46f, 39.5f, 46f, 45f, 3.8f, 47f,
            3.4f, 80f, 2f, 85f, 15f, 87f, 15f, 91f, 1.4f, 90.5f, 0f, 94f,
        ).withEngines(13f, top = 31f, bottom = 47f, halfWidth = 2.6f)

        Silhouette.Bizjet -> mirrored(
            0f, 6f, 2f, 9f, 3f, 15f, 3.2f, 44f, 33f, 60f, 34f, 63.5f, 3.2f, 57f,
            3.2f, 80f, 2f, 84f, 13f, 90f, 13f, 93f, 1.2f, 92f, 0f, 96f,
        ).withEngines(6.4f, top = 63f, bottom = 76f, halfWidth = 2.4f)

        Silhouette.Light -> mirrored(
            0f, 12f, 2.8f, 15f, 3.6f, 22f, 3.6f, 31f, 47f, 31f, 47f, 40f, 3.6f, 40f,
            2.6f, 72f, 1.6f, 82f, 15f, 84f, 15f, 90f, 1.2f, 90f, 0f, 92f,
        )

        Silhouette.Helicopter -> Path().apply {
            // Fuselage, tail boom, tail rotor and two crossed rotor blades.
            addOval(RectF(41f, 26f, 59f, 60f), Path.Direction.CW)
            addRect(RectF(48.4f, 56f, 51.6f, 90f), Path.Direction.CW)
            addRoundRect(RectF(42f, 86f, 58f, 91f), 2f, 2f, Path.Direction.CW)
            val blade = Path().apply { addRoundRect(RectF(47.8f, 2f, 52.2f, 84f), 2.2f, 2.2f, Path.Direction.CW) }
            val m = Matrix()
            m.setRotate(45f, 50f, 43f)
            addPath(Path(blade).apply { transform(m) })
            m.setRotate(-45f, 50f, 43f)
            addPath(Path(blade).apply { transform(m) })
        }
    }
}

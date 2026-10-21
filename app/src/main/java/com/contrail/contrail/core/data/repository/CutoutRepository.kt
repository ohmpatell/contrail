package com.contrail.contrail.core.data.repository

import android.content.Context
import android.graphics.Bitmap
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * "Floating plane" cutouts: ML Kit Subject Segmentation lifts the aircraft out of its
 * Planespotters photo, on the phone. The result changes the photographer's image, so it is
 * stored privately in app files, never shared, and always shown with the credit.
 */
@Singleton
class CutoutRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val dir = File(context.filesDir, "cutouts").apply { mkdirs() }
    private val mutex = Mutex()

    fun cached(hex: String): File? = File(dir, "${hex.lowercase()}.png").takeIf { it.exists() && it.length() > 0 }

    /** Returns the cutout file, computing it once and keeping it forever. Null if segmentation fails. */
    suspend fun cutout(hex: String, photoUrl: String): File? = mutex.withLock {
        cached(hex)?.let { return it }
        val photo = load(photoUrl) ?: return null
        val subject = segment(photo) ?: return null
        val trimmed = trim(subject) ?: return null
        withContext(Dispatchers.IO) {
            val out = File(dir, "${hex.lowercase()}.png")
            out.outputStream().use { trimmed.compress(Bitmap.CompressFormat.PNG, 100, it) }
            out
        }
    }

    private suspend fun load(url: String): Bitmap? {
        val loader = SingletonImageLoader.get(context)
        val result = loader.execute(ImageRequest.Builder(context).data(url).allowHardware(false).build())
        return (result as? SuccessResult)?.image?.toBitmap()
    }

    private suspend fun segment(bitmap: Bitmap): Bitmap? = suspendCancellableCoroutine { cont ->
        val segmenter = SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder().enableForegroundBitmap().build(),
        )
        segmenter.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { cont.resume(it.foregroundBitmap) }
            .addOnFailureListener { cont.resume(null) }
            .addOnCompleteListener { segmenter.close() }
    }

    /** Crops to the opaque pixels so the plane fills its frame. */
    private fun trim(b: Bitmap): Bitmap? {
        val w = b.width
        val h = b.height
        val px = IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h) }
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (y in 0 until h) for (x in 0 until w) {
            if ((px[y * w + x] ushr 24) > 24) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        if (maxX < minX || maxY < minY) return null
        // Reject tiny or near-full-frame results: those are failed segmentations, not aircraft.
        val area = (maxX - minX + 1).toFloat() * (maxY - minY + 1) / (w * h)
        if (area < 0.04f) return null
        return Bitmap.createBitmap(b, minX, minY, maxX - minX + 1, maxY - minY + 1)
    }
}

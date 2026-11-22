package com.contrail.contrail.core.data.repository

import android.content.Context
import android.graphics.Bitmap
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.OptionalModuleApi
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

    /**
     * Returns the cutout, computing it once and keeping it forever. Reports each step through
     * [progress]: the model download (with a percentage when Play services gives one), then the
     * segmentation. A failure says what actually went wrong.
     */
    suspend fun cutout(hex: String, photoUrl: String, progress: (CutoutStatus) -> Unit = {}): CutoutStatus = mutex.withLock {
        cached(hex)?.let { return CutoutStatus.Ready(it) }
        val segmenter = SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundBitmap().build())
        try {
            when (withTimeoutOrNull(MODEL_TIMEOUT_MS) { ensureModel(segmenter, progress) }) {
                ModelState.Ready -> Unit
                ModelState.NoPlayServices -> return CutoutStatus.Failed("Cutouts need Google Play services, which this device doesn't have.")
                ModelState.Failed -> return CutoutStatus.Failed("Google Play services couldn't download the cutout model. Check you're online, then tap Cutout again.")
                null -> return CutoutStatus.Failed("The cutout model is taking a while to download. Tap Cutout again in a minute.")
            }
            progress(CutoutStatus.Working)
            val photo = load(photoUrl) ?: return CutoutStatus.Failed("The photo didn't load. Tap Cutout to try again.")
            val subject = segment(segmenter, photo).getOrElse { e ->
                return CutoutStatus.Failed("The cutout model couldn't read this photo (${e.message ?: e.javaClass.simpleName}).")
            } ?: return CutoutStatus.Failed("The cutout model returned nothing for this photo.")
            val trimmed = trim(subject) ?: return CutoutStatus.Failed("No aircraft stands out clearly enough in this photo to lift it out.")
            val file = withContext(Dispatchers.IO) {
                val out = File(dir, "${hex.lowercase()}.png")
                out.outputStream().use { trimmed.compress(Bitmap.CompressFormat.PNG, 100, it) }
                out
            }
            CutoutStatus.Ready(file)
        } finally {
            segmenter.close()
        }
    }

    private enum class ModelState { Ready, Failed, NoPlayServices }

    /**
     * The segmentation model is a Play services module. An APK from outside the Play Store only
     * gets it on request, so ask for it explicitly and follow the download.
     */
    private suspend fun ensureModel(api: OptionalModuleApi, progress: (CutoutStatus) -> Unit): ModelState {
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) return ModelState.NoPlayServices
        val client = ModuleInstall.getClient(context)
        val available = suspendCancellableCoroutine { cont ->
            client.areModulesAvailable(api)
                .addOnSuccessListener { cont.resume(it.areModulesAvailable()) }
                .addOnFailureListener { cont.resume(false) }
        }
        if (available) return ModelState.Ready
        progress(CutoutStatus.Downloading(null))
        return suspendCancellableCoroutine { cont ->
            val listener = object : InstallStatusListener {
                override fun onInstallStatusUpdated(update: ModuleInstallStatusUpdate) {
                    update.progressInfo?.let { p ->
                        if (p.totalBytesToDownload > 0) progress(CutoutStatus.Downloading((p.bytesDownloaded * 100 / p.totalBytesToDownload).toInt()))
                    }
                    val done = when (update.installState) {
                        ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED -> ModelState.Ready
                        ModuleInstallStatusUpdate.InstallState.STATE_FAILED,
                        ModuleInstallStatusUpdate.InstallState.STATE_CANCELED -> ModelState.Failed
                        else -> null
                    }
                    if (done != null) {
                        client.unregisterListener(this)
                        if (cont.isActive) cont.resume(done)
                    }
                }
            }
            cont.invokeOnCancellation { client.unregisterListener(listener) }
            client.installModules(ModuleInstallRequest.newBuilder().addApi(api).setListener(listener).build())
                .addOnSuccessListener { response ->
                    if (response.areModulesAlreadyInstalled()) {
                        client.unregisterListener(listener)
                        if (cont.isActive) cont.resume(ModelState.Ready)
                    }
                }
                .addOnFailureListener {
                    client.unregisterListener(listener)
                    if (cont.isActive) cont.resume(ModelState.Failed)
                }
        }
    }

    private suspend fun load(url: String): Bitmap? {
        val loader = SingletonImageLoader.get(context)
        val result = loader.execute(ImageRequest.Builder(context).data(url).allowHardware(false).build())
        return (result as? SuccessResult)?.image?.toBitmap()
    }

    private suspend fun segment(segmenter: SubjectSegmenter, bitmap: Bitmap): Result<Bitmap?> = suspendCancellableCoroutine { cont ->
        segmenter.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { cont.resume(Result.success(it.foregroundBitmap)) }
            .addOnFailureListener { cont.resume(Result.failure(it)) }
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

    private companion object {
        const val MODEL_TIMEOUT_MS = 120_000L
    }
}

/** Where a photo cutout is up to. */
sealed interface CutoutStatus {
    /** Nothing asked for yet (or no photo to cut out). */
    data object Idle : CutoutStatus
    /** Play services is fetching the model; [percent] when it says how far along it is. */
    data class Downloading(val percent: Int?) : CutoutStatus
    data object Working : CutoutStatus
    data class Ready(val file: File) : CutoutStatus
    /** [reason] is written for the person reading it. */
    data class Failed(val reason: String) : CutoutStatus
}

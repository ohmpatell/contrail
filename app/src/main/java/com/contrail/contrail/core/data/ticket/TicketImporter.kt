package com.contrail.contrail.core.data.ticket

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.data.repository.AirportRepository
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min

/**
 * Reads tickets entirely on the phone: BCBP barcodes with ML Kit Barcode Scanning, printed
 * text with ML Kit Text Recognition, and PDFs rendered page by page with [PdfRenderer] (plus
 * the PDF's own text layer on Android 15+). Nothing is uploaded.
 */
@Singleton
class TicketImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val airports: AirportRepository,
    private val clock: Clock,
) {
    private val barcodes by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_PDF417, Barcode.FORMAT_AZTEC, Barcode.FORMAT_QR_CODE, Barcode.FORMAT_DATA_MATRIX)
                .build(),
        )
    }
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private val today: LocalDate get() = Instant.ofEpochMilli(clock.nowMs()).atZone(ZoneId.systemDefault()).toLocalDate()

    suspend fun fromText(text: String, source: ImportSource = ImportSource.Text): TicketDraft {
        BcbpParser.parse(text.trim(), today)?.let { return it }
        return TicketTextParser(airports.codes()).parse(text, today, source)
    }

    /** A barcode value from the live scanner. */
    fun fromBarcode(raw: String): TicketDraft? = BcbpParser.parse(raw, today)

    suspend fun fromUri(uri: Uri, mime: String?): TicketDraft {
        val type = mime ?: context.contentResolver.getType(uri)
        return if (type == "application/pdf" || uri.toString().endsWith(".pdf", ignoreCase = true)) fromPdf(uri) else fromImage(uri)
    }

    private suspend fun fromImage(uri: Uri): TicketDraft {
        val bitmap = withContext(Dispatchers.IO) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = max(info.size.width, info.size.height)
                if (longest > MAX_IMAGE_PX) {
                    val s = MAX_IMAGE_PX.toFloat() / longest
                    decoder.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
                }
            }
        }
        return fromBitmaps(listOf(bitmap), ImportSource.Image, extraText = null)
    }

    private suspend fun fromPdf(uri: Uri): TicketDraft {
        val (pages, text) = withContext(Dispatchers.IO) {
            val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: error("Can't open the PDF")
            fd.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val bitmaps = ArrayList<Bitmap>()
                    val texts = StringBuilder()
                    for (i in 0 until min(renderer.pageCount, MAX_PDF_PAGES)) {
                        renderer.openPage(i).use { page ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                                runCatching { page.textContents.forEach { texts.append(it.text).append('\n') } }
                            }
                            // Render about 2,000 px wide: sharp enough for small PDF417 codes and fine print.
                            val scale = PDF_RENDER_WIDTH.toFloat() / page.width
                            val bmp = Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, android.graphics.Matrix().apply { setScale(scale, scale) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmaps += bmp
                        }
                    }
                    bitmaps to texts.toString()
                }
            }
        }
        return fromBitmaps(pages, ImportSource.Pdf, extraText = text.takeIf { it.isNotBlank() })
    }

    /** Barcodes first (exact), then text (times, terminals, cabin); the two are merged. */
    private suspend fun fromBitmaps(bitmaps: List<Bitmap>, source: ImportSource, extraText: String?): TicketDraft {
        var draft = TicketDraft(sources = setOf(source))
        val ocr = StringBuilder()
        for (b in bitmaps) {
            val image = InputImage.fromBitmap(b, 0)
            runCatching { barcodes.process(image).await() }.getOrNull().orEmpty().forEach { code ->
                code.rawValue?.let { BcbpParser.parse(it, today) }?.let { draft = draft.merge(it) }
            }
            runCatching { recognizer.process(image).await() }.getOrNull()?.let { ocr.append(layoutText(it)).append('\n') }
        }
        val parser = TicketTextParser(airports.codes())
        // A PDF's own text layer keeps the layout better than OCR, so it's preferred when present.
        val text = extraText ?: ocr.toString()
        if (text.isNotBlank()) draft = draft.merge(parser.parse(text, today, source))
        if (extraText != null && ocr.isNotBlank()) draft = draft.merge(parser.parse(ocr.toString(), today, source))
        return draft
    }

    companion object {
        private const val MAX_IMAGE_PX = 2_400
        private const val MAX_PDF_PAGES = 6
        private const val PDF_RENDER_WIDTH = 2_000

        /**
         * Rebuilds reading order from OCR line boxes: lines whose vertical centres line up form
         * one row, left to right, separated by wide gaps, so table rows (Etihad's e-ticket)
         * survive instead of being read column by column.
         */
        fun layoutText(text: Text): String {
            data class L(val s: String, val top: Int, val bottom: Int, val left: Int)
            val lines = text.textBlocks.flatMap { b -> b.lines.mapNotNull { l -> l.boundingBox?.let { r -> L(l.text, r.top, r.bottom, r.left) } } }
                .sortedBy { (it.top + it.bottom) / 2 }
            val rows = ArrayList<MutableList<L>>()
            for (l in lines) {
                val row = rows.lastOrNull()
                val centre = (l.top + l.bottom) / 2
                val h = (l.bottom - l.top).coerceAtLeast(1)
                if (row != null && kotlin.math.abs(centre - row.map { (it.top + it.bottom) / 2 }.average()) < h * 0.6) row += l else rows += mutableListOf(l)
            }
            return rows.joinToString("\n") { r -> r.sortedBy { it.left }.joinToString("   ") { it.s } }
        }
    }
}

suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

/** Something shared to Contrail from another app, waiting for the import screen to pick it up. */
data class SharedTicket(val uris: List<Uri>, val mime: String?, val text: String?)

@Singleton
class SharedTicketInbox @Inject constructor() {
    private val pending = MutableStateFlow<SharedTicket?>(null)
    val shared: StateFlow<SharedTicket?> = pending.asStateFlow()

    fun deliver(ticket: SharedTicket) { pending.value = ticket }

    fun take(): SharedTicket? = pending.value.also { pending.value = null }
}

package com.contrail.contrail.feature.trips.importing

import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

/**
 * Live boarding-pass scanner: CameraX preview analysed by ML Kit Barcode Scanning, entirely
 * offline. [onCode] returns true when the value was a boarding pass, which stops scanning.
 */
@Composable
fun BarcodeScanner(onCode: (String) -> Boolean, modifier: Modifier = Modifier) {
    val accent = ContrailTheme.colors.accent
    val haptics = LocalHapticFeedback.current
    val handler by rememberUpdatedState(onCode)
    Box(modifier.clip(ContrailTheme.shapes.large)) {
        if (!LocalInspectionMode.current) {
            val context = LocalContext.current
            val owner = LocalLifecycleOwner.current
            val controller = remember { LifecycleCameraController(context) }
            DisposableEffect(owner) {
                val executor = ContextCompat.getMainExecutor(context)
                val scanner = BarcodeScanning.getClient(
                    BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_PDF417, Barcode.FORMAT_AZTEC, Barcode.FORMAT_QR_CODE, Barcode.FORMAT_DATA_MATRIX)
                        .build(),
                )
                var done = false
                controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
                controller.setImageAnalysisAnalyzer(
                    executor,
                    MlKitAnalyzer(listOf(scanner), CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED, executor) { result ->
                        if (done) return@MlKitAnalyzer
                        val raw = result.getValue(scanner)?.firstNotNullOfOrNull { it.rawValue } ?: return@MlKitAnalyzer
                        if (handler(raw)) {
                            done = true
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        }
                    },
                )
                controller.bindToLifecycle(owner)
                onDispose {
                    controller.unbind()
                    scanner.close()
                }
            }
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        // TextureView, so the preview clips to the rounded frame.
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        this.controller = controller
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        ScanFrame(accent, Modifier.fillMaxSize())
    }
}

/** Corner brackets and a sweeping scan line over the camera. */
@Composable
private fun ScanFrame(accent: Color, modifier: Modifier) {
    val motion = ContrailTheme.motion
    val sweep by if (motion.ambientEnabled) {
        rememberInfiniteTransition(label = "scan").animateFloat(0f, 1f, infiniteRepeatable(tween(1_800, easing = LinearEasing), RepeatMode.Reverse), label = "sweep")
    } else {
        remember { mutableFloatStateOf(0.5f) }
    }
    val scrim = ContrailTheme.colors.scrim
    Canvas(modifier) {
        val w = size.width * 0.84f
        val h = size.height * 0.46f
        val left = (size.width - w) / 2
        val top = (size.height - h) / 2
        // Dim everything outside the window.
        drawRect(scrim, Offset.Zero, androidx.compose.ui.geometry.Size(size.width, top))
        drawRect(scrim, Offset(0f, top + h), androidx.compose.ui.geometry.Size(size.width, size.height - top - h))
        val arm = 26.dp.toPx()
        val stroke = 3.dp.toPx()
        listOf(Offset(left, top) to (1 to 1), Offset(left + w, top) to (-1 to 1), Offset(left, top + h) to (1 to -1), Offset(left + w, top + h) to (-1 to -1))
            .forEach { (p, d) ->
                drawLine(accent, p, Offset(p.x + arm * d.first, p.y), stroke, StrokeCap.Round)
                drawLine(accent, p, Offset(p.x, p.y + arm * d.second), stroke, StrokeCap.Round)
            }
        val y = top + h * sweep
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.28f), Color.Transparent), startY = y - 30.dp.toPx(), endY = y + 30.dp.toPx()),
            Offset(left, y - 30.dp.toPx()), androidx.compose.ui.geometry.Size(w, 60.dp.toPx()),
        )
        drawLine(accent, Offset(left + 8.dp.toPx(), y), Offset(left + w - 8.dp.toPx(), y), 1.5.dp.toPx(), StrokeCap.Round)
    }
}

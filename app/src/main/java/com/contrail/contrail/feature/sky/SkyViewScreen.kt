package com.contrail.contrail.feature.sky

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contrail.contrail.core.data.sky.SkyContact
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.GlassIconButton
import com.contrail.contrail.core.designsystem.component.GlassSurface
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.AltitudeRamp
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.navigation.LocalNavigator
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/** Horizontal field of view of the preview in portrait, after centre-crop. */
private const val FOV_DEG = 52.0

/** Where a contact lands on screen, or null when it's behind you. */
private fun project(c: SkyContact, o: DeviceOrientation, w: Float, h: Float): Offset? {
    val v = Vec3(c.east, c.north, c.up)
    val z = v dot o.forward
    if (z < 0.08) return null
    val f = (w / 2) / tan(Math.toRadians(FOV_DEG / 2)).toFloat()
    val x = (v dot o.right) / z
    val y = (v dot o.up) / z
    return Offset(w / 2 + (x * f).toFloat(), h / 2 - (y * f).toFloat())
}

@Composable
fun SkyViewRoute(viewModel: SkyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val context = LocalContext.current
    DisposableEffect(Unit) {
        viewModel.setActive(true)
        onDispose { viewModel.setActive(false) }
    }
    var camera by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { camera = it }
    LaunchedEffect(Unit) { if (!camera) ask.launch(Manifest.permission.CAMERA) }
    val orientation = rememberDeviceOrientation()
    SkyViewScreen(state, orientation, camera, onBack = nav::back, onAircraft = nav::openAircraft)
}

/**
 * Point the phone at the sky: every plane in range gets a label pinned where it really is,
 * worked out from GPS, the compass and its live position. Planes out of frame get an arrow
 * at the edge of the screen showing which way to turn.
 */
@Composable
fun SkyViewScreen(state: SkyUiState, orientation: DeviceOrientation, camera: Boolean, onBack: () -> Unit, onAircraft: (String) -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    var screen by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize().background(colors.canvasSunken).onSizeChanged { screen = it }) {
        if (camera && !LocalInspectionMode.current) CameraPreview(Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1E44), Color(0xFF1B3B6E), Color(0xFF39557F)))))
        // A soft vignette keeps labels readable over bright sky.
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)), radius = 1_400f)))

        val w = screen.width.toFloat()
        val h = screen.height.toFloat()
        val contacts = state.contacts.take(30)
        // Reticles, leader lines and edge arrows are drawn in the draw phase: the sensor moves them without recomposing.
        Canvas(Modifier.fillMaxSize()) {
            // Horizon line from the camera's pitch.
            val horizon = Vec3(orientation.forward.e, orientation.forward.n, 0.0).normalized()
            val hz = horizon dot orientation.forward
            if (hz > 0.05) {
                val f = (size.width / 2) / tan(Math.toRadians(FOV_DEG / 2)).toFloat()
                val y = size.height / 2 - ((horizon dot orientation.up) / hz * f).toFloat()
                val roll = Math.toDegrees(atan2(orientation.right.u, Vec3(orientation.right.e, orientation.right.n, 0.0).let { kotlin.math.sqrt(it dot it) })).toFloat()
                rotate(roll, Offset(size.width / 2, y)) {
                    drawLine(
                        colors.textPrimary.copy(alpha = 0.35f), Offset(-size.width, y), Offset(size.width * 2, y), 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx())),
                    )
                }
            }
            contacts.forEachIndexed { i, c ->
                val tint = AltitudeRamp.colorFor(c.aircraft.altitudeFt)
                val p = project(c, orientation, size.width, size.height)
                if (p != null && p.x in -40f..size.width + 40f && p.y in -40f..size.height + 40f) {
                    drawCircle(tint.copy(alpha = 0.25f), 22.dp.toPx(), p)
                    drawCircle(tint, 22.dp.toPx(), p, style = Stroke(2.dp.toPx()))
                    drawCircle(tint, 3.dp.toPx(), p)
                    drawLine(tint.copy(alpha = 0.7f), Offset(p.x, p.y - 22.dp.toPx()), Offset(p.x, p.y - 46.dp.toPx()), 1.5.dp.toPx())
                } else if (i < 4) {
                    // Off screen: an arrow on the edge toward it.
                    val v = Vec3(c.east, c.north, c.up)
                    val ang = atan2(-(v dot orientation.up), v dot orientation.right)
                    // Out along that direction to the edge of the screen, inside a margin.
                    val m = 40.dp.toPx()
                    val dx = cos(ang).toFloat()
                    val dy = sin(ang).toFloat()
                    val t = minOf(
                        if (abs(dx) > 1e-3f) (size.width / 2 - m) / abs(dx) else Float.MAX_VALUE,
                        if (abs(dy) > 1e-3f) (size.height / 2 - m * 3) / abs(dy) else Float.MAX_VALUE,
                    )
                    val e = Offset(size.width / 2 + dx * t, size.height / 2 + dy * t)
                    drawCircle(tint.copy(alpha = 0.2f), 18.dp.toPx(), e)
                    rotate(Math.toDegrees(ang).toFloat(), e) {
                        val s = 12.dp.toPx()
                        val arrow = Path().apply { moveTo(e.x + s, e.y); lineTo(e.x - s * 0.6f, e.y - s * 0.7f); lineTo(e.x - s * 0.3f, e.y); lineTo(e.x - s * 0.6f, e.y + s * 0.7f); close() }
                        drawPath(arrow, tint)
                    }
                }
            }
            // Centre crosshair.
            val cx = size.width / 2
            val cy = size.height / 2
            drawCircle(colors.textPrimary.copy(alpha = 0.5f), 28.dp.toPx(), Offset(cx, cy), style = Stroke(1.dp.toPx()))
            drawLine(colors.textPrimary.copy(alpha = 0.5f), Offset(cx - 40.dp.toPx(), cy), Offset(cx - 30.dp.toPx(), cy), 1.dp.toPx())
            drawLine(colors.textPrimary.copy(alpha = 0.5f), Offset(cx + 30.dp.toPx(), cy), Offset(cx + 40.dp.toPx(), cy), 1.dp.toPx())
        }
        // Labels, placed in the layout phase so they follow the sensor too.
        contacts.forEach { c ->
            val tint = AltitudeRamp.colorFor(c.aircraft.altitudeFt)
            Box(
                Modifier
                    .offset {
                        val p = project(c, orientation, w, h) ?: return@offset IntOffset(-10_000, -10_000)
                        IntOffset((p.x - with(density) { 70.dp.toPx() }).roundToInt(), (p.y - with(density) { 104.dp.toPx() }).roundToInt())
                    }
                    .clip(ContrailTheme.shapes.small)
                    .background(colors.canvas.copy(alpha = 0.72f))
                    .border(ContrailTheme.sizes.hairline, tint.copy(alpha = 0.8f), ContrailTheme.shapes.small)
                    .clickable(role = Role.Button) { onAircraft(c.aircraft.hex) }
                    .padding(horizontal = spacing.sm, vertical = spacing.xs),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(c.aircraft.displayCallsign, style = ContrailTheme.type.numeric, color = colors.textPrimary, maxLines = 1)
                    Text(
                        listOfNotNull(c.aircraft.typeCode, c.aircraft.altitudeFt?.let { String.format(Locale.US, "%,d ft", it) }, String.format(Locale.US, "%.1f km", c.distanceKm)).joinToString(" · "),
                        style = ContrailTheme.type.numericSmall, color = tint, maxLines = 1,
                    )
                }
            }
        }

        // Top bar with a compass tape.
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                GlassIconButton(ContrailIcons.Back, "Back", onBack)
                Column(Modifier.weight(1f)) {
                    Eyebrow("Sky View", color = colors.accent)
                    Text("${state.contacts.size} planes within ${state.radiusKm} km", style = ContrailTheme.type.title, color = colors.textPrimary)
                }
            }
            CompassTape(orientation)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            when {
                !orientation.available -> Hint("This phone has no compass sensor, so labels can't be pinned. The Sky tab's scope still works.")
                state.me == null -> Hint("Finding your location…")
                state.contacts.isEmpty() -> Hint("Nothing within ${state.radiusKm} km right now. Try a larger radius on the Sky tab.")
                else -> Hint("Hold the phone up and turn toward the arrows. Tap a label for the aircraft.")
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    GlassSurface(shape = ContrailTheme.shapes.chip) {
        Text(
            text, style = ContrailTheme.type.bodySmall, color = ContrailTheme.colors.textPrimary, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = ContrailTheme.spacing.lg, vertical = ContrailTheme.spacing.sm),
        )
    }
}

/** A sliding compass ruler: where you're facing, to the degree. */
@Composable
private fun CompassTape(o: DeviceOrientation) {
    val colors = ContrailTheme.colors
    val type = ContrailTheme.type.eyebrow
    GlassSurface(Modifier.fillMaxWidth().height(40.dp), shape = ContrailTheme.shapes.chip) {
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        Canvas(Modifier.fillMaxSize()) {
            val heading = o.headingDeg
            val pxPerDeg = size.width / 90f
            val mid = size.width / 2
            for (d in -50..50) {
                val deg = ((heading.roundToInt() + d) % 360 + 360) % 360
                val x = mid + (deg - heading + if (abs(deg - heading) > 180) (if (deg < heading) 360 else -360) else 0) * pxPerDeg
                if (deg % 5 != 0) continue
                val major = deg % 45 == 0
                drawLine(colors.textPrimary.copy(alpha = if (major) 0.8f else 0.3f), Offset(x, size.height * (if (major) 0.15f else 0.3f)), Offset(x, size.height * 0.45f), 1.dp.toPx())
                if (major) {
                    val label = mapOf(0 to "N", 45 to "NE", 90 to "E", 135 to "SE", 180 to "S", 225 to "SW", 270 to "W", 315 to "NW")[deg]!!
                    val t = measurer.measure(label, type.copy(color = if (deg == 0) colors.accent else colors.textSecondary))
                    drawText(t, topLeft = Offset(x - t.size.width / 2, size.height * 0.5f))
                }
            }
            val tri = Path().apply { moveTo(mid, size.height * 0.5f); lineTo(mid - 6.dp.toPx(), size.height); lineTo(mid + 6.dp.toPx(), size.height); close() }
            drawPath(tri, colors.accent)
        }
    }
}

@Composable
private fun CameraPreview(modifier: Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val controller = remember { LifecycleCameraController(context) }
    DisposableEffect(owner) {
        controller.setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        controller.bindToLifecycle(owner)
        onDispose { controller.unbind() }
    }
    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                this.controller = controller
            }
        },
        modifier = modifier,
    )
}

package com.contrail.contrail.feature.trips.importing

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contrail.contrail.core.data.ticket.ImportSource
import com.contrail.contrail.core.data.trip.Cabin
import com.contrail.contrail.core.data.trip.ConnectionKind
import com.contrail.contrail.core.designsystem.component.AccentButton
import com.contrail.contrail.core.designsystem.component.ContrailChip
import com.contrail.contrail.core.designsystem.component.DetailScaffold
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.GlassSurface
import com.contrail.contrail.core.designsystem.component.RiseIn
import com.contrail.contrail.core.designsystem.component.SplitFlapText
import com.contrail.contrail.core.designsystem.component.pressScale
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.feature.trips.Tag
import com.contrail.contrail.feature.trips.TripFormat
import com.contrail.contrail.feature.trips.airlineAccent
import com.contrail.contrail.feature.trips.kind
import com.contrail.contrail.feature.trips.length
import com.contrail.contrail.navigation.LocalNavigator

@Composable
fun ImportRoute(viewModel: ImportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.readUri(it, "image/*") }
    }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.readUri(it, "application/pdf") }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.choose(ImportStage.Scan)
    }
    LaunchedEffect(state.savedTripId) {
        state.savedTripId?.let { id ->
            nav.back()
            nav.openTrip(id)
        }
    }
    BackHandler(enabled = state.stage != ImportStage.Choose && state.stage != ImportStage.Working) { viewModel.back() }
    ImportScreen(
        state = state,
        onBack = { if (!viewModel.back()) nav.back() },
        onScan = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                viewModel.choose(ImportStage.Scan)
            } else {
                camera.launch(Manifest.permission.CAMERA)
            }
        },
        onPhoto = { photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onPdf = { pdf.launch(arrayOf("application/pdf")) },
        onPaste = { viewModel.choose(ImportStage.Paste) },
        onManual = { viewModel.choose(ImportStage.Review) },
        onPastedChange = viewModel::setPasted,
        onReadPasted = viewModel::readPasted,
        onClipboard = {
            val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
            clip?.getItemAt(0)?.coerceToText(context)?.toString()?.let(viewModel::setPasted)
        },
        onBarcode = viewModel::onBarcode,
        onEdit = viewModel::edit,
        onPnr = viewModel::setPnr,
        onPassenger = viewModel::setPassenger,
        onAdd = viewModel::addSegment,
        onRemove = viewModel::removeSegment,
        onSplit = viewModel::toggleSplit,
        onSave = viewModel::save,
    )
}

@Composable
fun ImportScreen(
    state: ImportUiState,
    onBack: () -> Unit,
    onScan: () -> Unit = {},
    onPhoto: () -> Unit = {},
    onPdf: () -> Unit = {},
    onPaste: () -> Unit = {},
    onManual: () -> Unit = {},
    onPastedChange: (String) -> Unit = {},
    onReadPasted: () -> Unit = {},
    onClipboard: () -> Unit = {},
    onBarcode: (String) -> Boolean = { false },
    onEdit: (Int, (SegmentForm) -> SegmentForm) -> Unit = { _, _ -> },
    onPnr: (String) -> Unit = {},
    onPassenger: (String) -> Unit = {},
    onAdd: () -> Unit = {},
    onRemove: (Int) -> Unit = {},
    onSplit: (Int) -> Unit = {},
    onSave: () -> Unit = {},
) {
    val title = when (state.stage) {
        ImportStage.Choose -> "Add a trip"
        ImportStage.Paste -> "Paste your booking"
        ImportStage.Scan -> "Scan a boarding pass"
        ImportStage.Working -> "Reading your ticket"
        ImportStage.Review -> if (state.sources == setOf(ImportSource.Manual)) "Enter your flights" else "Check your trip"
    }
    DetailScaffold(
        title = title,
        eyebrow = if (state.stage == ImportStage.Review) "Nothing is saved until you tap Save" else "Read on your phone, never uploaded",
        onBack = onBack,
    ) {
        state.message?.let { Notice(it) }
        val motion = ContrailTheme.motion
        AnimatedContent(
            targetState = state.stage,
            transitionSpec = { fadeIn(motion.fade()) togetherWith fadeOut(motion.fade(120)) },
            label = "importStage",
        ) { stage ->
            Column(verticalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.lg)) {
                when (stage) {
                    ImportStage.Choose -> ChooseSource(onScan, onPhoto, onPdf, onPaste, onManual)
                    ImportStage.Paste -> PasteBox(state.pasted, onPastedChange, onReadPasted, onClipboard)
                    ImportStage.Scan -> ScanStage(onBarcode)
                    ImportStage.Working -> Working(state.working ?: "Reading")
                    ImportStage.Review -> Review(state, onEdit, onPnr, onPassenger, onAdd, onRemove, onSplit, onSave)
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String) {
    val colors = ContrailTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ContrailTheme.shapes.small)
            .background(colors.statusDelayed.copy(alpha = 0.12f))
            .border(ContrailTheme.sizes.hairline, colors.statusDelayed.copy(alpha = 0.5f), ContrailTheme.shapes.small)
            .padding(ContrailTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ContrailIcons.Alert, null, tint = colors.statusDelayed, modifier = Modifier.size(18.dp))
        Text(text, style = ContrailTheme.type.bodySmall, color = colors.textPrimary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChooseSource(onScan: () -> Unit, onPhoto: () -> Unit, onPdf: () -> Unit, onPaste: () -> Unit, onManual: () -> Unit) {
    val spacing = ContrailTheme.spacing
    val colors = ContrailTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            RiseIn(0, Modifier.weight(1f)) { SourceTile(ContrailIcons.Scan, "Scan", "Boarding pass barcode, live", colors.accent, onScan) }
            RiseIn(1, Modifier.weight(1f)) { SourceTile(ContrailIcons.Document, "PDF", "E-ticket or itinerary", colors.passport, onPdf) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            RiseIn(2, Modifier.weight(1f)) { SourceTile(ContrailIcons.Image, "Screenshot", "Boarding pass or photo", colors.statusOnTime, onPhoto) }
            RiseIn(3, Modifier.weight(1f)) { SourceTile(ContrailIcons.Paste, "Paste", "Confirmation email text", colors.statusDelayed, onPaste) }
        }
        RiseIn(4) {
            GlassSurface(
                Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onManual),
                shape = ContrailTheme.shapes.medium,
            ) {
                Row(Modifier.padding(spacing.lg), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    Icon(ContrailIcons.Edit, null, tint = colors.textSecondary, modifier = Modifier.size(ContrailTheme.sizes.icon))
                    Column(Modifier.weight(1f)) {
                        Text("Enter a flight by hand", style = ContrailTheme.type.title, color = colors.textPrimary)
                        Text("Type the flight number and Contrail fills in the route.", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
                    }
                    Icon(ContrailIcons.ChevronRight, null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
                }
            }
        }
        RiseIn(5) {
            GlassSurface(Modifier.fillMaxWidth(), shape = ContrailTheme.shapes.medium, tint = colors.accent.copy(alpha = 0.06f)) {
                Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Eyebrow("Share to Contrail", color = colors.accent)
                    Text(
                        "In Gmail, the Etihad app or the Cathay Pacific app, open your e-ticket or boarding pass, tap Share, then Contrail.",
                        style = ContrailTheme.type.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceTile(icon: ImageVector, title: String, body: String, tint: Color, onClick: () -> Unit) {
    val spacing = ContrailTheme.spacing
    val interaction = remember { MutableInteractionSource() }
    GlassSurface(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.05f)
            .pressScale(interaction)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
        shape = ContrailTheme.shapes.medium,
        glow = tint.copy(alpha = 0.5f),
    ) {
        Box(Modifier.matchParentSize().background(Brush.radialGradient(listOf(tint.copy(alpha = 0.16f), Color.Transparent), radius = 420f)))
        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Box(
                Modifier.size(44.dp).clip(ContrailTheme.shapes.small).background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = tint, modifier = Modifier.size(ContrailTheme.sizes.icon)) }
            Box(Modifier.weight(1f))
            Text(title, style = ContrailTheme.type.headline, color = ContrailTheme.colors.textPrimary)
            Text(body, style = ContrailTheme.type.bodySmall, color = ContrailTheme.colors.textTertiary, maxLines = 2)
        }
    }
}

@Composable
private fun PasteBox(text: String, onChange: (String) -> Unit, onRead: () -> Unit, onClipboard: () -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    GlassSurface(Modifier.fillMaxWidth(), shape = ContrailTheme.shapes.medium) {
        Box(Modifier.padding(spacing.lg)) {
            if (text.isEmpty()) {
                Text("Paste your confirmation email or itinerary here…", style = ContrailTheme.type.body, color = colors.textDisabled)
            }
            BasicTextField(
                value = text,
                onValueChange = onChange,
                textStyle = ContrailTheme.type.bodySmall.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
        AccentButton("From clipboard", onClipboard, icon = ContrailIcons.Paste, secondary = true, modifier = Modifier.weight(1f))
        AccentButton("Read", onRead, icon = ContrailIcons.Sparkle, enabled = text.isNotBlank(), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ScanStage(onBarcode: (String) -> Boolean) {
    BarcodeScanner(onBarcode, Modifier.fillMaxWidth().aspectRatio(0.8f))
    Text(
        "Hold the boarding pass barcode inside the frame. It works offline, on paper or on another screen.",
        style = ContrailTheme.type.bodySmall,
        color = ContrailTheme.colors.textTertiary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A ticket outline with a scan beam sweeping over it while the parsers run. */
@Composable
private fun Working(label: String) {
    val colors = ContrailTheme.colors
    val motion = ContrailTheme.motion
    val sweep by if (motion.ambientEnabled) {
        rememberInfiniteTransition(label = "reading").animateFloat(0f, 1f, infiniteRepeatable(tween(1_400, easing = LinearEasing), RepeatMode.Reverse), label = "sweep")
    } else {
        remember { mutableFloatStateOf(0.5f) }
    }
    GlassSurface(Modifier.fillMaxWidth(), glow = colors.accent.copy(alpha = 0.6f)) {
        Column(Modifier.padding(ContrailTheme.spacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.lg)) {
            Canvas(Modifier.fillMaxWidth().height(170.dp)) {
                val w = size.width * 0.8f
                val h = size.height * 0.9f
                val tl = Offset((size.width - w) / 2, (size.height - h) / 2)
                drawRoundRect(colors.accent.copy(alpha = 0.06f), tl, Size(w, h), CornerRadius(16.dp.toPx()))
                drawRoundRect(colors.accent.copy(alpha = 0.45f), tl, Size(w, h), CornerRadius(16.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                // Text lines being read.
                for (i in 0 until 6) {
                    val y = tl.y + h * (0.18f + i * 0.13f)
                    val lw = w * (if (i % 2 == 0) 0.62f else 0.44f)
                    drawLine(colors.textTertiary.copy(alpha = 0.35f), Offset(tl.x + w * 0.1f, y), Offset(tl.x + w * 0.1f + lw, y), 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                }
                val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))
                drawLine(colors.accent.copy(alpha = 0.4f), Offset(tl.x + w * 0.78f, tl.y + 8.dp.toPx()), Offset(tl.x + w * 0.78f, tl.y + h - 8.dp.toPx()), 1.5.dp.toPx(), pathEffect = dash)
                val y = tl.y + h * sweep
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, colors.accent.copy(alpha = 0.3f), Color.Transparent), startY = y - 26.dp.toPx(), endY = y + 26.dp.toPx()), Offset(tl.x, y - 26.dp.toPx()), Size(w, 52.dp.toPx()))
                drawLine(colors.accent, Offset(tl.x, y), Offset(tl.x + w, y), 2.dp.toPx())
            }
            SplitFlapText("READING", style = ContrailTheme.type.flap, color = colors.accent)
            Text("$label…", style = ContrailTheme.type.body, color = colors.textSecondary)
        }
    }
}

@Composable
private fun Review(
    state: ImportUiState,
    onEdit: (Int, (SegmentForm) -> SegmentForm) -> Unit,
    onPnr: (String) -> Unit,
    onPassenger: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onSplit: (Int) -> Unit,
    onSave: () -> Unit,
) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val preview = state.preview
    val n = state.segments.size
    GlassSurface(Modifier.fillMaxWidth(), shape = ContrailTheme.shapes.medium, glow = colors.accent.copy(alpha = 0.5f)) {
        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.sources == setOf(ImportSource.Manual)) "New trip" else "Found $n flight${if (n == 1) "" else "s"}",
                        style = ContrailTheme.type.displaySmall,
                        color = colors.textPrimary,
                    )
                    Text(
                        state.sources.joinToString(" + ") { it.label },
                        style = ContrailTheme.type.bodySmall,
                        color = colors.textTertiary,
                    )
                }
                val missing = preview?.resolved?.count { it == null } ?: n
                if (missing > 0) Tag("$missing to fix", colors.statusDelayed) else Tag("Ready", colors.statusOnTime, filled = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                FormField("Booking ref", state.pnr, onPnr, Modifier.weight(1f), placeholder = "ABC123", caps = true)
                FormField("Passenger", state.passenger, onPassenger, Modifier.weight(1.6f), placeholder = "Your name", mono = false)
            }
            if (!preview?.titles.isNullOrEmpty()) {
                Text(
                    "Saves as " + preview!!.titles.joinToString(" and ") { "“$it”" },
                    style = ContrailTheme.type.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
    state.segments.forEachIndexed { i, form ->
        if (i > 0) BetweenSegments(i, state, onEdit, onSplit)
        SegmentEditor(i, form, state, onEdit, onRemove)
    }
    AccentButton("Add another flight", onAdd, icon = ContrailIcons.Plus, secondary = true, modifier = Modifier.fillMaxWidth())
    val trips = preview?.titles?.size ?: 1
    AccentButton(
        if (trips > 1) "Save $trips trips" else "Save trip",
        onSave,
        icon = ContrailIcons.Check,
        enabled = preview?.ready == true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SegmentEditor(i: Int, form: SegmentForm, state: ImportUiState, onEdit: (Int, (SegmentForm) -> SegmentForm) -> Unit, onRemove: (Int) -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val draft = form.toDraft()
    val resolved = state.preview?.resolved?.getOrNull(i)
    val accent = airlineAccent(draft.airlineIata)
    val missing = draft.missing.toSet()
    val unknown = state.preview?.unknownAirports.orEmpty()
    GlassSurface(Modifier.fillMaxWidth(), shape = ContrailTheme.shapes.medium, glow = if (resolved != null) accent.copy(alpha = 0.45f) else colors.statusDelayed.copy(alpha = 0.45f)) {
        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("Flight ${i + 1}", Modifier.weight(1f), color = accent)
                if (state.routeHint == i) Tag("Route from callsign", colors.textTertiary)
                if (state.segments.size > 1) {
                    Icon(
                        ContrailIcons.Trash, "Remove flight", tint = colors.textTertiary,
                        modifier = Modifier.padding(start = spacing.sm).size(20.dp).clickable(role = Role.Button) { onRemove(i) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.Bottom) {
                FormField("Flight", form.flight, { v -> onEdit(i) { it.copy(flight = v) } }, Modifier.weight(1.3f), "EY 140", missing = "flight number" in missing, caps = true, big = true)
                FormField("From", form.from, { v -> onEdit(i) { it.copy(from = v.take(3)) } }, Modifier.weight(1f), "YYZ", missing = "from" in missing || form.from.uppercase() in unknown, caps = true, big = true)
                Icon(ContrailIcons.ArrowRight, null, tint = colors.textTertiary, modifier = Modifier.padding(bottom = spacing.md).size(18.dp))
                FormField("To", form.to, { v -> onEdit(i) { it.copy(to = v.take(3)) } }, Modifier.weight(1f), "AUH", missing = "to" in missing || form.to.uppercase() in unknown, caps = true, big = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                FormField("Date", form.date, { v -> onEdit(i) { it.copy(date = v) } }, Modifier.weight(1.4f), "12 Dec 2026", missing = "date" in missing)
                FormField("Departs", form.departure, { v -> onEdit(i) { it.copy(departure = v) } }, Modifier.weight(1f), "22:10", missing = "departure time" in missing, number = true)
                FormField("Arrives", form.arrival, { v -> onEdit(i) { it.copy(arrival = v) } }, Modifier.weight(1f), "19:55", missing = "arrival time" in missing, number = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                FormField("Terminal", form.departureTerminal, { v -> onEdit(i) { it.copy(departureTerminal = v.take(3)) } }, Modifier.weight(1f), "3", caps = true)
                FormField("Arr. terminal", form.arrivalTerminal, { v -> onEdit(i) { it.copy(arrivalTerminal = v.take(3)) } }, Modifier.weight(1f), "A", caps = true)
                FormField("Seat", form.seat, { v -> onEdit(i) { it.copy(seat = v.take(4)) } }, Modifier.weight(1f), "7K", caps = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                FormField("Class", form.fareClass, { v -> onEdit(i) { it.copy(fareClass = v.take(1)) } }, Modifier.weight(1f), "Z", caps = true)
                FormField("Aircraft", form.aircraft, { v -> onEdit(i) { it.copy(aircraft = v.take(4)) } }, Modifier.weight(1f), "B789", caps = true)
                FormField("Gate", form.gate, { v -> onEdit(i) { it.copy(gate = v.take(4)) } }, Modifier.weight(1f), "—", caps = true)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Cabin.entries.forEach { c ->
                    ContrailChip(c.label, form.cabin == c, onClick = { onEdit(i) { it.copy(cabin = if (it.cabin == c) null else c) } }, accent = accent)
                }
            }
            if (resolved != null) {
                Text(
                    "${resolved.origin} ${TripFormat.time(resolved.departureLocal)} → ${resolved.destination} ${TripFormat.time(resolved.arrivalLocal)}${TripFormat.dayShift(resolved)?.let { " $it" }.orEmpty()} · ${TripFormat.duration(resolved.duration)} · ${TripFormat.day(resolved.departureLocal)}",
                    style = ContrailTheme.type.numericSmall,
                    color = accent,
                )
            } else {
                val why = buildList {
                    addAll(draft.missing)
                    unknown.filter { it == draft.origin || it == draft.destination }.forEach { add("unknown airport $it") }
                }
                Text("Needs: ${why.joinToString(", ")}", style = ContrailTheme.type.bodySmall, color = colors.statusDelayed)
            }
        }
    }
}

/** Between two flights: the connection it will make, or where a new trip starts. */
@Composable
private fun BetweenSegments(i: Int, state: ImportUiState, onEdit: (Int, (SegmentForm) -> SegmentForm) -> Unit, onSplit: (Int) -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val split = i in state.preview?.splits.orEmpty()
    val c = state.preview?.connections?.get(i - 1)
    val inbound = state.segments[i - 1]
    Column(Modifier.padding(horizontal = spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        if (split) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Icon(ContrailIcons.Swap, null, tint = colors.passport, modifier = Modifier.size(16.dp))
                Text("A new trip starts here", style = ContrailTheme.type.label, color = colors.passport, modifier = Modifier.weight(1f))
                ContrailChip("Join", false, onClick = { onSplit(i) })
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                if (c != null) {
                    Text(TripFormat.duration(c.duration), style = ContrailTheme.type.numeric, color = colors.textPrimary)
                    Tag(c.kind.name, colors.kind(c.kind), filled = true)
                    if (c.kind == ConnectionKind.Layover) Tag(c.length.label, colors.length(c.length))
                    c.terminalChange?.let { (a, b) -> Tag("T$a → T$b", colors.statusDelayed) }
                } else {
                    Text("Connection", style = ContrailTheme.type.label, color = colors.textTertiary)
                }
                Box(Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                ContrailChip("Hotel", inbound.hotelAfter, onClick = { onEdit(i - 1) { it.copy(hotelAfter = !it.hotelAfter) } }, accent = colors.passport, leading = {
                    Icon(ContrailIcons.Bed, null, tint = colors.passport, modifier = Modifier.size(14.dp))
                })
                ContrailChip("New trip here", false, onClick = { onSplit(i) })
            }
        }
    }
}

/** A labelled glass text field; amber when the import couldn't fill it. */
@Composable
fun FormField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    missing: Boolean = false,
    caps: Boolean = false,
    number: Boolean = false,
    mono: Boolean = true,
    big: Boolean = false,
) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val edge = if (missing) colors.statusDelayed.copy(alpha = 0.8f) else colors.hairline
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Eyebrow(label, color = if (missing) colors.statusDelayed else colors.textTertiary)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(ContrailTheme.shapes.small)
                .background(if (missing) colors.statusDelayed.copy(alpha = 0.08f) else colors.glassTint.copy(alpha = 0.06f))
                .border(ContrailTheme.sizes.hairline, edge, ContrailTheme.shapes.small)
                .padding(horizontal = spacing.md, vertical = if (big) spacing.md else spacing.sm + spacing.xxs),
        ) {
            val style = (if (big) ContrailTheme.type.headline else if (mono) ContrailTheme.type.numeric else ContrailTheme.type.body)
            if (value.isEmpty()) Text(placeholder, style = style, color = colors.textDisabled, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = { onChange(if (caps) it.uppercase() else it) },
                singleLine = true,
                textStyle = style.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(
                    capitalization = if (caps) KeyboardCapitalization.Characters else KeyboardCapitalization.Words,
                    keyboardType = if (number) KeyboardType.Number else KeyboardType.Text,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

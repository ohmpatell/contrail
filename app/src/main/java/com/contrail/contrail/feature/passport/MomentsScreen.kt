package com.contrail.contrail.feature.passport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.contrail.contrail.core.data.moments.Moment
import com.contrail.contrail.core.data.moments.MomentKind
import com.contrail.contrail.core.data.ticket.AirportInfo
import com.contrail.contrail.core.data.trip.FlightSegment
import com.contrail.contrail.core.designsystem.component.AccentButton
import com.contrail.contrail.core.designsystem.component.ContrailChip
import com.contrail.contrail.core.designsystem.component.DateQuickPicks
import com.contrail.contrail.core.designsystem.component.DetailScaffold
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.GlassCalendar
import com.contrail.contrail.core.designsystem.component.GlassIconButton
import com.contrail.contrail.core.designsystem.component.GlassSurface
import com.contrail.contrail.core.designsystem.component.RiseIn
import com.contrail.contrail.core.designsystem.component.SectionCard
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.core.designsystem.theme.RarityPalette
import com.contrail.contrail.navigation.LocalNavigator
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Gold get() = RarityPalette.Legendary.first()
private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)

/** The icon for a kind of moment. */
fun momentIcon(kind: MomentKind): ImageVector = when (kind) {
    MomentKind.Cockpit -> ContrailIcons.Headset
    MomentKind.Upgrade -> ContrailIcons.Seat
    MomentKind.Livery -> ContrailIcons.Sparkle
    MomentKind.Crew -> ContrailIcons.Passport
    MomentKind.View -> ContrailIcons.Camera
    MomentKind.Other -> ContrailIcons.Edit
    MomentKind.FirstFlight, MomentKind.FirstType, MomentKind.Takeoff, MomentKind.Landing -> ContrailIcons.Plane
    MomentKind.FirstAirline -> ContrailIcons.Trips
    MomentKind.Milestone -> ContrailIcons.Medal
    MomentKind.Crossing, MomentKind.Border -> ContrailIcons.Globe
    MomentKind.HangarFlight -> ContrailIcons.StarFilled
    MomentKind.Sunset, MomentKind.Sunrise -> ContrailIcons.Moon
}

@Composable
private fun tintFor(m: Moment): Color = when {
    m.mine -> Gold
    m.captured -> ContrailTheme.colors.statusAirborne
    else -> ContrailTheme.colors.passport
}

/** The Passport's row of moments: the newest few and a way to add one. */
@Composable
fun MomentsStrip(moments: List<Moment>, onAll: () -> Unit, onAdd: () -> Unit, onMoment: (Moment) -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Moments", Modifier.weight(1f), color = Gold)
            if (moments.isNotEmpty()) {
                Text(
                    "All ${moments.size}", style = ContrailTheme.type.label, color = colors.textSecondary,
                    modifier = Modifier.clip(ContrailTheme.shapes.chip).clickable(role = Role.Button, onClick = onAll).padding(horizontal = spacing.sm, vertical = spacing.xxs),
                )
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            GlassSurface(
                Modifier.width(150.dp).heightIn(min = 128.dp).clip(ContrailTheme.shapes.medium).clickable(role = Role.Button, onClickLabel = "Add a moment", onClick = onAdd),
                shape = ContrailTheme.shapes.medium,
                glow = Gold.copy(alpha = 0.5f),
            ) {
                Column(Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Gold.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        Icon(ContrailIcons.Plus, null, tint = Gold, modifier = Modifier.size(18.dp))
                    }
                    Text("Add a moment", style = ContrailTheme.type.title, color = colors.textPrimary)
                    Text("A cockpit visit, an upgrade, a view", style = ContrailTheme.type.bodySmall, color = colors.textTertiary, maxLines = 2)
                }
            }
            moments.take(10).forEach { m -> MomentTile(m) { onMoment(m) } }
        }
    }
}

@Composable
private fun MomentTile(m: Moment, onClick: () -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val tint = tintFor(m)
    GlassSurface(
        Modifier.width(210.dp).heightIn(min = 128.dp).clip(ContrailTheme.shapes.medium).clickable(role = Role.Button, onClick = onClick),
        shape = ContrailTheme.shapes.medium,
        glow = if (m.mine) Gold.copy(alpha = 0.35f) else null,
    ) {
        Column(Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(momentIcon(m.kind), null, tint = tint, modifier = Modifier.size(16.dp))
                }
                Eyebrow(DAY.format(m.date).uppercase(Locale.UK), color = colors.textTertiary)
            }
            Text(m.title, style = ContrailTheme.type.title, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            m.note?.let { Text(it, style = ContrailTheme.type.bodySmall, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** One moment in the timeline: bigger, with its photo. */
@Composable
fun MomentCard(m: Moment, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val tint = tintFor(m)
    GlassSurface(
        modifier.fillMaxWidth().clip(ContrailTheme.shapes.medium).clickable(role = Role.Button, onClick = onClick),
        shape = ContrailTheme.shapes.medium,
        glow = if (m.mine) Gold.copy(alpha = 0.35f) else null,
    ) {
        Column {
            m.photoPath?.let { File(it) }?.takeIf { it.exists() }?.let { file ->
                AsyncImage(file, m.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(2f).clip(ContrailTheme.shapes.medium))
            }
            Row(Modifier.padding(spacing.md), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(momentIcon(m.kind), null, tint = tint, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    Eyebrow(
                        listOfNotNull(DAY.format(m.date), m.kind.label, m.registration, if (m.captured) "captured in flight" else null).joinToString(" · ").uppercase(Locale.UK),
                        color = tint,
                    )
                    Text(m.title, style = ContrailTheme.type.title, color = colors.textPrimary)
                    m.note?.let { Text(it, style = ContrailTheme.type.body, color = colors.textSecondary) }
                }
            }
        }
    }
}

@Composable
fun MomentsRoute(viewModel: MomentsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    MomentsScreen(
        state,
        onBack = nav::back,
        onAdd = { nav.openAddMoment() },
        onMoment = { m -> if (m.mine) nav.openAddMoment(momentId = m.id) else m.segmentId?.let(nav::openFlight) },
    )
}

@Composable
fun MomentsScreen(state: MomentsData, onBack: () -> Unit, onAdd: () -> Unit, onMoment: (Moment) -> Unit) {
    val colors = ContrailTheme.colors
    DetailScaffold(
        title = "Moments",
        eyebrow = "${state.moments.count { it.mine }} yours · ${state.moments.count { !it.mine }} from your flying",
        accent = Gold,
        onBack = onBack,
        actions = { GlassIconButton(ContrailIcons.Plus, "Add a moment", onAdd) },
    ) {
        if (state.moments.isEmpty()) {
            Text("Your moments gather here: the ones you write, and firsts and milestones from your logbook.", style = ContrailTheme.type.body, color = colors.textTertiary)
        }
        state.moments.groupBy { it.date.year }.forEach { (year, list) ->
            Eyebrow("$year", color = colors.passport)
            list.forEachIndexed { i, m -> RiseIn(i.coerceAtMost(6)) { MomentCard(m, onClick = { onMoment(m) }) } }
        }
    }
}

@Composable
fun AddMomentRoute(momentId: String, segmentId: Long, registration: String) {
    val viewModel = hiltViewModel<AddMomentViewModel, AddMomentViewModel.Factory>(key = "moment-$momentId-$segmentId-$registration") {
        it.create(momentId, segmentId, registration)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    LaunchedEffect(state.saved, state.deleted) { if (state.saved || state.deleted) nav.back() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) viewModel.edit { it.copy(photo = uri) } }
    AddMomentScreen(
        state,
        onBack = nav::back,
        onEdit = viewModel::edit,
        onFlight = viewModel::pickFlight,
        onPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onSave = viewModel::save,
        onDelete = viewModel::delete,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddMomentScreen(
    d: MomentDraft,
    onBack: () -> Unit,
    onEdit: ((MomentDraft) -> MomentDraft) -> Unit,
    onFlight: (FlightSegment?) -> Unit,
    onPhoto: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    var calendar by rememberSaveable { mutableStateOf(false) }
    DetailScaffold(
        title = if (d.id == null) "New moment" else "Edit moment",
        eyebrow = "Something to remember",
        accent = Gold,
        onBack = onBack,
        actions = { if (d.id != null) GlassIconButton(ContrailIcons.Trash, "Delete moment", onDelete) },
    ) {
        SectionCard("What happened") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                MomentKind.writable.forEach { k ->
                    ContrailChip(k.label, d.kind == k, onClick = { onEdit { it.copy(kind = k) } }, accent = Gold, leading = {
                        Icon(momentIcon(k), null, Modifier.size(ContrailTheme.sizes.iconSmall), tint = if (d.kind == k) colors.onAccent else Gold)
                    })
                }
            }
            Field(d.title, d.kind.label, singleLine = true) { v -> onEdit { it.copy(title = v.take(80)) } }
            Field(d.note, "The story: who, what, how it felt…", singleLine = false) { v -> onEdit { it.copy(note = v.take(800)) } }
        }
        SectionCard("When") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(d.date?.let { DAY.format(it) } ?: "Pick a day", style = ContrailTheme.type.headline, color = colors.textPrimary, modifier = Modifier.weight(1f))
                Text(
                    if (calendar) "Done" else "Change", style = ContrailTheme.type.label, color = Gold,
                    modifier = Modifier.clip(ContrailTheme.shapes.chip).clickable(role = Role.Button) { calendar = !calendar }.padding(spacing.sm),
                )
            }
            DateQuickPicks(d.today, d.date, onSelect = { day -> onEdit { it.copy(date = day) } }, accent = Gold)
            if (calendar) GlassCalendar(d.date, onSelect = { day -> onEdit { it.copy(date = day) }; calendar = false }, today = d.today, accent = Gold)
        }
        if (d.recentFlights.isNotEmpty()) {
            SectionCard("On a flight") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    d.recentFlights.forEach { s -> FlightChip(s, d.places, d.segmentId == s.id) { onFlight(s) } }
                }
            }
        }
        SectionCard("On a plane") {
            Field(d.registration, "Registration, e.g. B-LXP", singleLine = true, caps = true) { v -> onEdit { it.copy(registration = v.uppercase().take(10)) } }
            if (d.hangar.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    d.hangar.forEach { reg ->
                        ContrailChip(reg, d.registration == reg, onClick = { onEdit { it.copy(registration = if (it.registration == reg) "" else reg) } }, accent = Gold, leading = {
                            Icon(ContrailIcons.StarFilled, null, Modifier.size(ContrailTheme.sizes.iconSmall), tint = Gold)
                        })
                    }
                }
            }
        }
        SectionCard("A photo") {
            val preview: Any? = d.photo ?: d.existingPhoto?.let { File(it) }
            if (preview != null) {
                AsyncImage(preview, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(1.8f).clip(ContrailTheme.shapes.medium))
            }
            AccentButton(if (preview == null) "Add a photo" else "Change the photo", onPhoto, icon = ContrailIcons.Image, accent = Gold, secondary = true, modifier = Modifier.fillMaxWidth())
            Text("Photos stay on this device.", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
        }
        AccentButton(if (d.id == null) "Keep this moment" else "Save changes", onSave, icon = ContrailIcons.Check, accent = Gold, enabled = d.canSave, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun FlightChip(s: FlightSegment, places: Map<String, AirportInfo>, selected: Boolean, onClick: () -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    Column(
        Modifier
            .clip(ContrailTheme.shapes.medium)
            .background(if (selected) Gold.copy(alpha = 0.18f) else colors.glassTint.copy(alpha = 0.06f))
            .border(ContrailTheme.sizes.hairline, if (selected) Gold else colors.hairline, ContrailTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
    ) {
        Text("${s.origin} → ${s.destination}", style = ContrailTheme.type.numeric, color = colors.textPrimary)
        Text(
            "${s.designator} · ${DateTimeFormatter.ofPattern("d MMM yy", Locale.UK).format(s.departureLocal)}",
            style = ContrailTheme.type.label, color = colors.textTertiary,
        )
        places[s.destination]?.city?.let { Text(it, style = ContrailTheme.type.bodySmall, color = colors.textSecondary, maxLines = 1) }
    }
}

@Composable
private fun Field(value: String, placeholder: String, singleLine: Boolean, caps: Boolean = false, onChange: (String) -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (singleLine) 0.dp else 96.dp)
            .clip(ContrailTheme.shapes.small)
            .background(colors.glassTint.copy(alpha = 0.06f))
            .border(ContrailTheme.sizes.hairline, colors.hairline, ContrailTheme.shapes.small)
            .padding(spacing.md),
    ) {
        if (value.isEmpty()) Text(placeholder, style = ContrailTheme.type.body, color = colors.textDisabled)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            textStyle = (if (caps) ContrailTheme.type.numeric else ContrailTheme.type.body).copy(color = colors.textPrimary),
            cursorBrush = SolidColor(Gold),
            keyboardOptions = KeyboardOptions(capitalization = if (caps) KeyboardCapitalization.Characters else KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

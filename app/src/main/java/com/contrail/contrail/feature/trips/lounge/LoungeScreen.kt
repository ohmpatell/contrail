package com.contrail.contrail.feature.trips.lounge

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contrail.contrail.core.data.repository.Lounge
import com.contrail.contrail.core.data.repository.LoungeAccess
import com.contrail.contrail.core.data.repository.LoungePhase
import com.contrail.contrail.core.data.repository.LoungeSource
import com.contrail.contrail.core.designsystem.component.AccentButton
import com.contrail.contrail.core.designsystem.component.ContrailChip
import com.contrail.contrail.core.designsystem.component.DetailScaffold
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.SectionCard
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.feature.trips.Stars
import com.contrail.contrail.feature.trips.Tag
import com.contrail.contrail.feature.trips.TripFormat
import com.contrail.contrail.feature.trips.importing.FormField
import com.contrail.contrail.feature.trips.importing.SegmentForm
import com.contrail.contrail.navigation.LocalNavigator
import java.time.Duration

@Composable
fun LoungeRoute(segmentId: Long, phase: String, visitId: Long) {
    val viewModel = hiltViewModel<LoungeViewModel, LoungeViewModel.Factory>(key = "lounge-$segmentId-$phase-$visitId") { it.create(segmentId, phase, visitId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    LaunchedEffect(state.done) { if (state.done) nav.back() }
    LoungeScreen(state, nav::back, viewModel::pick, viewModel::edit, viewModel::addNew, viewModel::toggleFavourite, viewModel::save, viewModel::delete)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoungeScreen(
    state: LoungeUiState,
    onBack: () -> Unit,
    onPick: (Lounge) -> Unit,
    onEdit: ((LoungeForm) -> LoungeForm) -> Unit,
    onAddNew: () -> Unit,
    onFavourite: (Lounge) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val gold = colors.statusDelayed
    val f = state.form
    val seg = state.segment
    DetailScaffold(
        title = if (state.editing) "Lounge visit" else "Log a lounge",
        eyebrow = listOfNotNull(
            state.city ?: state.airport,
            f.terminal.takeIf { it.isNotBlank() }?.let { "Terminal $it" },
            seg?.let { if (state.phase == LoungePhase.Departure) "before ${it.designator}" else "after ${it.designator}" },
        ).joinToString(" · "),
        accent = gold,
        onBack = onBack,
    ) {
        SectionCard("Which lounge") {
            state.suggestions.forEach { l -> LoungeOption(l, l.key == f.loungeKey, onPick, onFavourite) }
            if (state.suggestions.isEmpty()) {
                Text("No lounges listed here yet. Add the one you're in below and Contrail will remember it.", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                FormField("Not listed? Add it", f.newName, { v -> onEdit { it.copy(newName = v) } }, Modifier.weight(1f), placeholder = "Lounge name", mono = false)
                AccentButton("Add", onAddNew, accent = gold, secondary = true, enabled = f.newName.isNotBlank())
            }
            if (state.suggestions.any { it.source == LoungeSource.OpenStreetMap }) {
                val uri = LocalUriHandler.current
                Text(
                    "Some lounges © OpenStreetMap contributors",
                    style = ContrailTheme.type.bodySmall,
                    color = colors.textTertiary,
                    modifier = Modifier.clickable { uri.openUri("https://www.openstreetmap.org/copyright") },
                )
            }
        }
        SectionCard("When") {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                FormField("Date", f.date, { v -> onEdit { it.copy(date = v) } }, Modifier.weight(1.4f), "12 Dec 2026", missing = SegmentForm.parseDate(f.date) == null)
                FormField("In", f.timeIn, { v -> onEdit { it.copy(timeIn = v) } }, Modifier.weight(1f), "20:10", number = true)
                FormField("Out", f.timeOut, { v -> onEdit { it.copy(timeOut = v) } }, Modifier.weight(1f), "21:30", number = true)
                FormField("Term.", f.terminal, { v -> onEdit { it.copy(terminal = v.uppercase().take(3)) } }, Modifier.weight(0.8f), "3", caps = true)
            }
            val a = SegmentForm.parseTime(f.timeIn)
            val b = SegmentForm.parseTime(f.timeOut)
            if (a != null && b != null) {
                val d = Duration.between(a, b).let { if (it.isNegative) it.plusHours(24) else it }
                Text("${TripFormat.duration(d)} in the lounge", style = ContrailTheme.type.numeric, color = gold)
            }
        }
        SectionCard("How you got in") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                LoungeAccess.entries.forEach { a ->
                    ContrailChip(
                        when (a) { LoungeAccess.Cabin -> "Cabin class"; LoungeAccess.Status -> "Airline status"; LoungeAccess.Card -> "Card or membership"; LoungeAccess.Paid -> "Paid" },
                        f.access == a,
                        onClick = { onEdit { it.copy(access = a) } },
                        accent = gold,
                    )
                }
            }
        }
        SectionCard("Your rating") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                Stars(f.rating, size = 34.dp, onRate = { r -> onEdit { it.copy(rating = r) } })
                Text(
                    when (f.rating) { 1 -> "Skip it"; 2 -> "Meh"; 3 -> "Fine"; 4 -> "Great"; else -> "Unmissable" },
                    style = ContrailTheme.type.headline,
                    color = gold,
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(ContrailTheme.shapes.small)
                    .background(colors.glassTint.copy(alpha = 0.06f))
                    .border(ContrailTheme.sizes.hairline, colors.hairline, ContrailTheme.shapes.small)
                    .padding(spacing.md),
            ) {
                if (f.note.isEmpty()) Text("A short note: the showers, the noodle bar, the view…", style = ContrailTheme.type.body, color = colors.textDisabled)
                BasicTextField(
                    value = f.note,
                    onValueChange = { v -> onEdit { it.copy(note = v.take(280)) } },
                    textStyle = ContrailTheme.type.body.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(gold),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                )
            }
        }
        AccentButton(if (state.editing) "Save changes" else "Save visit", onSave, icon = ContrailIcons.Check, accent = gold, enabled = state.canSave, modifier = Modifier.fillMaxWidth())
        if (state.editing) {
            AccentButton("Delete visit", onDelete, icon = ContrailIcons.Trash, accent = colors.statusCancelled, secondary = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun LoungeOption(l: Lounge, selected: Boolean, onPick: (Lounge) -> Unit, onFavourite: (Lounge) -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val gold = colors.statusDelayed
    val fill by animateColorAsState(if (selected) gold.copy(alpha = 0.16f) else colors.glassTint.copy(alpha = 0.05f), ContrailTheme.motion.fade(), label = "fill")
    val edge by animateColorAsState(if (selected) gold.copy(alpha = 0.85f) else colors.hairline, ContrailTheme.motion.fade(), label = "edge")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ContrailTheme.shapes.small)
            .background(fill)
            .border(ContrailTheme.sizes.hairline, edge, ContrailTheme.shapes.small)
            .clickable(role = Role.RadioButton) { onPick(l) }
            .padding(horizontal = spacing.md, vertical = spacing.sm + spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Icon(ContrailIcons.Lounge, null, tint = if (selected) gold else colors.textTertiary, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(l.name, style = ContrailTheme.type.title, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                l.terminal?.let { Eyebrow("Terminal $it") }
                Eyebrow(l.source.label, color = if (l.source == LoungeSource.Yours) gold else colors.textTertiary)
            }
        }
        if (selected) Tag("Here", gold, filled = true)
        Icon(
            if (l.favourite) ContrailIcons.StarFilled else ContrailIcons.Star,
            if (l.favourite) "Remove favourite" else "Favourite",
            tint = if (l.favourite) gold else colors.textDisabled,
            modifier = Modifier.size(22.dp).clickable(role = Role.Button) { onFavourite(l) },
        )
    }
}

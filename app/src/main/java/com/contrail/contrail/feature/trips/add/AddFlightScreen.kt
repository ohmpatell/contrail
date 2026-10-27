package com.contrail.contrail.feature.trips.add

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contrail.contrail.core.data.trip.Cabin
import com.contrail.contrail.core.data.trip.FlightReason
import com.contrail.contrail.core.data.trip.SeatType
import com.contrail.contrail.core.designsystem.component.AccentButton
import com.contrail.contrail.core.designsystem.component.ContrailChip
import com.contrail.contrail.core.designsystem.component.DateQuickPicks
import com.contrail.contrail.core.designsystem.component.DetailScaffold
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.GlassCalendar
import com.contrail.contrail.core.designsystem.component.GlassIconButton
import com.contrail.contrail.core.designsystem.component.GlassSurface
import com.contrail.contrail.core.designsystem.component.RiseIn
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.livery.Liveries
import com.contrail.contrail.core.designsystem.livery.LiveryRender
import com.contrail.contrail.core.designsystem.livery.Profiles
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.feature.trips.BoardingPass
import com.contrail.contrail.feature.trips.PassData
import com.contrail.contrail.feature.trips.Tag
import com.contrail.contrail.feature.trips.airlineAccent
import com.contrail.contrail.feature.trips.emptyPass
import com.contrail.contrail.feature.trips.importing.FormField
import com.contrail.contrail.navigation.LocalNavigator
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AddFlightRoute(segmentId: Long) {
    val viewModel = hiltViewModel<AddFlightViewModel, AddFlightViewModel.Factory>(key = "add-$segmentId") { it.create(segmentId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    LaunchedEffect(state.savedSegmentId, state.deleted) {
        state.savedSegmentId?.let { id ->
            nav.back()
            if (segmentId == 0L) nav.openFlight(id)
        }
        if (state.deleted) {
            nav.back()
            nav.back()
        }
    }
    AddFlightScreen(
        state = state,
        onBack = nav::back,
        onEdit = viewModel::edit,
        onAirport = viewModel::setAirport,
        onPickAirport = viewModel::pickAirport,
        onSwap = viewModel::swap,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
    )
}

/** Popular airliners for one-tap picking, most-flown long-haul first. */
private val QuickTypes = listOf(
    "A388" to "A380", "B748" to "747-8", "B744" to "747-400", "B77W" to "777-300ER", "B772" to "777-200",
    "B789" to "787-9", "B788" to "787-8", "B78X" to "787-10", "A359" to "A350-900", "A35K" to "A350-1000",
    "A333" to "A330-300", "A339" to "A330neo", "A332" to "A330-200", "A21N" to "A321neo", "A321" to "A321",
    "A20N" to "A320neo", "A320" to "A320", "A319" to "A319", "BCS3" to "A220-300", "B38M" to "737 MAX 8",
    "B738" to "737-800", "E75L" to "E175", "E190" to "E190", "CRJ9" to "CRJ-900", "DH8D" to "Dash 8", "AT76" to "ATR 72",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddFlightScreen(
    state: AddFlightState,
    onBack: () -> Unit,
    onEdit: ((AddFlightState) -> AddFlightState) -> Unit,
    onAirport: (AirportField, String) -> Unit,
    onPickAirport: (AirportField, String) -> Unit,
    onSwap: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val accent = airlineAccent(state.airlineIata)
    val editing = state.editingId != 0L
    var calendar by rememberSaveable { mutableStateOf(false) }
    DetailScaffold(
        title = if (editing) "Edit flight" else "Add a flight",
        eyebrow = if (editing) "Changes save to your logbook" else "Past or upcoming · all on your phone",
        accent = accent,
        onBack = onBack,
        actions = { if (editing) GlassIconButton(ContrailIcons.Trash, "Delete flight", onDelete) },
    ) {
        RiseIn(0) {
            val pass = state.preview?.let { PassData.of(it, state.places) } ?: emptyPass(state.flight.uppercase(), state.from, state.to, state.airlineIata)
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                val airline = Airlines.byIata(state.airlineIata)
                LiveryRender(
                    Profiles.forType(state.aircraft.uppercase().ifBlank { "B789" }),
                    Liveries.forAirline(airline?.icao, accentArgb = airline?.accentArgb),
                    Modifier.fillMaxWidth().padding(horizontal = spacing.xxl),
                )
                BoardingPass(pass)
            }
        }

        Section("Flight", accent) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.Bottom) {
                FormField("Flight number", state.flight, { v -> onEdit { it.copy(flight = v.take(8)) } }, Modifier.weight(1f), "EY 140", caps = true, big = true)
                Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Eyebrow("Date", color = if (state.date == null) colors.textTertiary else colors.textTertiary)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(ContrailTheme.shapes.small)
                            .background(colors.glassTint.copy(alpha = 0.06f))
                            .border(ContrailTheme.sizes.hairline, if (calendar) accent else colors.hairline, ContrailTheme.shapes.small)
                            .clickable(role = Role.Button) { calendar = !calendar }
                            .padding(horizontal = spacing.md, vertical = spacing.md),
                    ) {
                        Text(
                            state.date?.let { DATE.format(it) } ?: "Pick a day",
                            style = ContrailTheme.type.headline,
                            color = if (state.date != null) colors.textPrimary else colors.textDisabled,
                            maxLines = 1,
                        )
                    }
                }
            }
            when {
                state.lookingUp -> Text("Looking up the route…", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
                state.routeFromCallsign -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Icon(ContrailIcons.Sparkle, null, tint = accent, modifier = Modifier.size(14.dp))
                    Text("Route filled in from the flight number", style = ContrailTheme.type.bodySmall, color = colors.textSecondary, modifier = Modifier.weight(1f))
                    Tag("Route from callsign", colors.textTertiary)
                }
            }
            DateQuickPicks(state.today, state.date, onSelect = { d -> onEdit { it.copy(date = d) }; calendar = false }, accent = accent)
            AnimatedVisibility(calendar, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                GlassCalendar(state.date, onSelect = { d -> onEdit { it.copy(date = d) }; calendar = false }, today = state.today, accent = accent)
            }
        }

        Section("Route", accent) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.Bottom) {
                FormField("From", state.from, { onAirport(AirportField.From, it) }, Modifier.weight(1f), "YYZ", caps = false, big = true, mono = false)
                GlassIconButton(ContrailIcons.Swap, "Swap", onSwap, size = 40.dp, modifier = Modifier.padding(bottom = spacing.sm))
                FormField("To", state.to, { onAirport(AirportField.To, it) }, Modifier.weight(1f), "HKG", caps = false, big = true, mono = false)
            }
            AnimatedVisibility(state.suggestions.isNotEmpty() && state.activeAirport != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    state.suggestions.forEach { a ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(ContrailTheme.shapes.small)
                                .clickable(role = Role.Button) { state.activeAirport?.let { onPickAirport(it, a.iata) } }
                                .padding(horizontal = spacing.sm, vertical = spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.md),
                        ) {
                            Text(a.iata, style = ContrailTheme.type.numeric, color = accent)
                            Column(Modifier.weight(1f)) {
                                Text(a.city.ifBlank { a.name }, style = ContrailTheme.type.title, color = colors.textPrimary, maxLines = 1)
                                Text(a.name, style = ContrailTheme.type.bodySmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(flag(a.countryIso), style = ContrailTheme.type.headline)
                        }
                    }
                }
            }
            state.distanceKm?.let { km ->
                Text(
                    String.format(Locale.US, "%,d km great-circle", km),
                    style = ContrailTheme.type.numericSmall,
                    color = colors.textTertiary,
                )
            }
        }

        Section("Times", accent, hint = "Local times. Leave blank and Contrail estimates them from the distance.") {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                FormField("Departs", state.departure, { v -> onEdit { it.copy(departure = clockText(v)) } }, Modifier.weight(1f), "22:10", number = true)
                FormField("Arrives", state.arrival, { v -> onEdit { it.copy(arrival = clockText(v)) } }, Modifier.weight(1f), "19:55", number = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                FormField("Terminal", state.departureTerminal, { v -> onEdit { it.copy(departureTerminal = v.take(3)) } }, Modifier.weight(1f), "3", caps = true)
                FormField("Arr. terminal", state.arrivalTerminal, { v -> onEdit { it.copy(arrivalTerminal = v.take(3)) } }, Modifier.weight(1f), "A", caps = true)
            }
        }

        Section("Aircraft", accent) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                QuickTypes.forEach { (code, label) ->
                    ContrailChip(label, state.aircraft.equals(code, ignoreCase = true), onClick = { onEdit { it.copy(aircraft = if (it.aircraft == code) "" else code) } }, accent = accent)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                FormField("Type code", state.aircraft, { v -> onEdit { it.copy(aircraft = v.take(4)) } }, Modifier.weight(1f), "B789", caps = true)
                FormField("Registration", state.tail, { v -> onEdit { it.copy(tail = v.take(10)) } }, Modifier.weight(1.3f), "A6-BLT", caps = true)
            }
        }

        Section("Seat", accent) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Cabin.entries.forEach { c ->
                    ContrailChip(c.label, state.cabin == c, onClick = { onEdit { it.copy(cabin = if (it.cabin == c) null else c) } }, accent = accent)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.Bottom) {
                FormField("Seat", state.seat, { v -> onEdit { it.copy(seat = v.take(4)) } }, Modifier.weight(0.7f), "7K", caps = true)
                Row(Modifier.weight(1.6f), horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    SeatType.entries.forEach { t ->
                        ContrailChip(t.label, state.seatType == t, onClick = { onEdit { it.copy(seatType = if (it.seatType == t) null else t) } }, accent = accent)
                    }
                }
            }
        }

        Section("Notes", accent) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                FlightReason.entries.forEach { r ->
                    ContrailChip(r.label, state.reason == r, onClick = { onEdit { it.copy(reason = if (it.reason == r) null else r) } }, accent = accent)
                }
            }
            NoteField(state.note) { v -> onEdit { it.copy(note = v.take(500)) } }
        }

        AccentButton(
            if (editing) "Save changes" else "Add to my flights",
            onSave,
            icon = ContrailIcons.Check,
            accent = accent,
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!state.canSave && !state.loading) {
            Text(
                "Add the route and the day to save.",
                style = ContrailTheme.type.bodySmall,
                color = colors.textTertiary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Section(title: String, accent: Color, hint: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val spacing = ContrailTheme.spacing
    GlassSurface(Modifier.fillMaxWidth(), shape = ContrailTheme.shapes.medium) {
        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Eyebrow(title, color = accent)
            content()
            hint?.let { Text(it, style = ContrailTheme.type.bodySmall, color = ContrailTheme.colors.textTertiary) }
        }
    }
}

@Composable
private fun NoteField(value: String, onChange: (String) -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    Box(
        Modifier
            .fillMaxWidth()
            .clip(ContrailTheme.shapes.small)
            .background(colors.glassTint.copy(alpha = 0.06f))
            .border(ContrailTheme.sizes.hairline, colors.hairline, ContrailTheme.shapes.small)
            .padding(spacing.md),
    ) {
        if (value.isEmpty()) Text("Upgrade, cockpit visit, great sunrise…", style = ContrailTheme.type.body, color = colors.textDisabled)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = ContrailTheme.type.body.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Digits only, at most four, shown as "21:40" once all four are in. */
internal fun clockText(raw: String): String {
    val d = raw.filter { it.isDigit() }.take(4)
    return if (d.length == 4) d.take(2) + ":" + d.takeLast(2) else d
}

private val DATE = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.UK)

/** "CA" → 🇨🇦, from the two regional-indicator letters. */
fun flag(iso: String): String {
    if (iso.length != 2 || !iso.all { it.isLetter() }) return ""
    return iso.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}

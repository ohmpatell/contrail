package com.contrail.contrail.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.contrail.contrail.BuildConfig
import com.contrail.contrail.core.data.settings.ApiKeys
import com.contrail.contrail.core.data.settings.ApiService
import com.contrail.contrail.core.data.settings.KeySource
import com.contrail.contrail.core.data.settings.KeyStatus
import com.contrail.contrail.core.data.settings.LogostreamUsage
import com.contrail.contrail.core.data.settings.OpenSkyUsage
import com.contrail.contrail.core.data.repository.TripSettings
import com.contrail.contrail.core.data.rotation.TurnThresholds
import com.contrail.contrail.core.data.settings.UsageTracker
import com.contrail.contrail.core.designsystem.component.DetailScaffold
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.SectionCard
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.navigation.LocalNavigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class SettingsUiState(
    val keys: List<KeyStatus> = emptyList(),
    val usage: LogostreamUsage? = null,
    val openSky: OpenSkyUsage? = null,
    val thresholds: TurnThresholds = TurnThresholds(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val apiKeys: ApiKeys,
    usage: UsageTracker,
    private val tripSettings: TripSettings,
) : ViewModel() {
    val state: StateFlow<SettingsUiState> = combine(
        combine(ApiService.entries.map { apiKeys.status(it) }) { it.toList() },
        usage.logostream,
        usage.openSky,
        tripSettings.thresholds,
    ) { keys, u, o, t -> SettingsUiState(keys, u, o, t) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setThresholds(t: TurnThresholds) {
        viewModelScope.launch { tripSettings.setThresholds(t) }
    }

    fun save(service: ApiService, key: String?) {
        viewModelScope.launch { apiKeys.save(service, key) }
    }
}

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(state, onSave = viewModel::save, onBack = LocalNavigator.current::back, onThresholds = viewModel::setThresholds)
}

@Composable
fun SettingsScreen(state: SettingsUiState, onSave: (ApiService, String?) -> Unit, onBack: () -> Unit, onThresholds: (TurnThresholds) -> Unit = {}) {
    DetailScaffold(title = "Settings", eyebrow = "Contrail ${BuildConfig.VERSION_NAME}", onBack = onBack) {
        SectionCard("API keys") {
            Text(
                "Keys are stored encrypted on this phone and never leave it except to call their service.",
                style = ContrailTheme.type.bodySmall,
                color = ContrailTheme.colors.textTertiary,
            )
            state.keys.forEach { KeyRow(it, onSave) }
            OpenSkyHelp(state)
        }
        SectionCard("logostream usage this month") { UsageMeter(state.usage) }
        SectionCard("Cockpit visit planner") { Thresholds(state.thresholds, onThresholds) }
        Credits()
    }
}

@Composable
private fun KeyRow(status: KeyStatus, onSave: (ApiService, String?) -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val uri = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(status.service.title, style = ContrailTheme.type.title, color = colors.textPrimary)
                val (label, tint) = when (status.source) {
                    KeySource.Settings -> "Saved on this phone · ${status.masked}" to colors.statusOnTime
                    KeySource.LocalProperties -> "From local.properties (debug) · ${status.masked}" to colors.statusAirborne
                    KeySource.Missing -> "Not set" to colors.statusDelayed
                }
                Text(label, style = ContrailTheme.type.bodySmall, color = tint)
            }
            Text(
                if (editing) "Cancel" else if (status.source == KeySource.Settings) "Change" else "Add key",
                style = ContrailTheme.type.label,
                color = colors.accent,
                modifier = Modifier
                    .clip(ContrailTheme.shapes.chip)
                    .clickable(role = Role.Button) { editing = !editing; draft = "" }
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
            )
        }
        if (editing) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(ContrailTheme.shapes.chip)
                    .background(colors.glassTint)
                    .border(ContrailTheme.sizes.hairline, colors.hairline, ContrailTheme.shapes.chip)
                    .padding(start = spacing.lg, end = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it.trim() },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    textStyle = ContrailTheme.type.numeric.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier.weight(1f).padding(vertical = spacing.md),
                )
                Text(
                    "Save",
                    style = ContrailTheme.type.label,
                    color = colors.accent,
                    modifier = Modifier
                        .clip(ContrailTheme.shapes.chip)
                        .clickable(enabled = draft.isNotBlank(), role = Role.Button) {
                            onSave(status.service, draft)
                            editing = false
                        }
                        .padding(horizontal = spacing.md, vertical = spacing.sm),
                )
            }
            Text(
                "Get a free key at ${status.service.signUpUrl}",
                style = ContrailTheme.type.bodySmall,
                color = colors.textTertiary,
                modifier = Modifier.clickable { uri.openUri(status.service.signUpUrl) },
            )
        }
    }
}

@Composable
private fun UsageMeter(usage: LogostreamUsage?) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val fraction = usage?.usedFraction
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                usage?.remaining?.let { String.format(Locale.US, "%,d", it) } ?: "—",
                style = ContrailTheme.type.numericLarge,
                color = if (usage?.exhausted == true) colors.statusDelayed else colors.textPrimary,
            )
            Text(
                usage?.limit?.let { " of ${String.format(Locale.US, "%,d", it)} left" } ?: " requests left",
                style = ContrailTheme.type.body,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = spacing.xxs),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(spacing.sm)
                .clip(ContrailTheme.shapes.chip)
                .background(colors.glassTint),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction ?: 0f)
                    .height(spacing.sm)
                    .clip(ContrailTheme.shapes.chip)
                    .background(if (usage?.exhausted == true) colors.statusDelayed else colors.accent),
            )
        }
        Text(
            buildString {
                append("${usage?.made ?: 0} requests from Contrail this month")
                usage?.resetsOn?.let { append(" · resets $it") }
                if (usage?.exhausted == true) append(" · paused to keep a reserve")
            },
            style = ContrailTheme.type.bodySmall,
            color = colors.textTertiary,
        )
    }
}

/** Where the OpenSky client ID and secret come from, and today's credit budget. */
@Composable
private fun OpenSkyHelp(state: SettingsUiState) {
    val colors = ContrailTheme.colors
    val o = state.openSky
    val ready = state.keys.filter { it.service == ApiService.OpenSkyClientId || it.service == ApiService.OpenSkyClientSecret }.all { it.source != KeySource.Missing }
    Text(
        if (ready) "OpenSky is set up: the cockpit-visit planner uses real turnaround history and earlier legs."
        else "OpenSky (free account): My OpenSky → Account → API Client → Create, then paste the client ID and secret above. Until then the planner shows sample history.",
        style = ContrailTheme.type.bodySmall,
        color = if (ready) colors.statusOnTime else colors.textTertiary,
    )
    if (o != null && o.madeToday > 0) {
        Text(
            buildString {
                append("OpenSky today: ${o.madeToday} requests")
                o.remaining?.let { append(" · ${String.format(Locale.US, "%,d", it)} credits left") }
                if (o.exhausted) append(" · paused to keep a reserve")
            },
            style = ContrailTheme.type.bodySmall,
            color = if (o.exhausted) colors.statusDelayed else colors.textTertiary,
        )
    }
}

/** Relaxed / maybe thresholds for the turnaround badge, in 15-minute steps. */
@Composable
private fun Thresholds(t: TurnThresholds, onChange: (TurnThresholds) -> Unit) {
    val colors = ContrailTheme.colors
    Text("How long the aircraft sits at your destination decides the badge.", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
    Stepper("Relaxed from", t.relaxedMinutes, colors.statusOnTime, { onChange(t.copy(relaxedMinutes = (t.relaxedMinutes + it).coerceIn(t.maybeMinutes + 15, 480))) })
    Stepper("Maybe from", t.maybeMinutes, colors.statusDelayed, { onChange(t.copy(maybeMinutes = (t.maybeMinutes + it).coerceIn(15, t.relaxedMinutes - 15))) })
    Text("Under ${t.maybeMinutes} minutes is tight.", style = ContrailTheme.type.bodySmall, color = colors.statusCancelled)
}

@Composable
private fun Stepper(label: String, minutes: Int, tint: androidx.compose.ui.graphics.Color, onStep: (Int) -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
        Text(label, style = ContrailTheme.type.title, color = colors.textPrimary, modifier = Modifier.weight(1f))
        Text("−", style = ContrailTheme.type.headline, color = colors.textSecondary, modifier = Modifier.clip(ContrailTheme.shapes.chip).clickable(role = Role.Button) { onStep(-15) }.padding(horizontal = spacing.md))
        Text("${minutes / 60}h ${"%02d".format(minutes % 60)}m", style = ContrailTheme.type.numeric, color = tint)
        Text("+", style = ContrailTheme.type.headline, color = colors.textSecondary, modifier = Modifier.clip(ContrailTheme.shapes.chip).clickable(role = Role.Button) { onStep(15) }.padding(horizontal = spacing.md))
    }
}

/** Every source's credit, as their terms ask. logostream's free plan requires this exact line. */
@Composable
private fun Credits() {
    val uri = LocalUriHandler.current
    val credits = listOf(
        "Logos by logostream" to "https://logostream.dev",
        "Aircraft photos: Planespotters.net photographers" to "https://www.planespotters.net",
        "Map: OpenFreeMap · © OpenMapTiles · © OpenStreetMap contributors" to "https://openfreemap.org",
        "Live positions: adsb.fi open data" to "https://adsb.fi",
        "Live positions: adsb.lol (ODbL)" to "https://adsb.lol",
        "Flight tracks and history: The OpenSky Network" to "https://opensky-network.org",
        "Lounges and airport diagrams: © OpenStreetMap contributors (ODbL)" to "https://www.openstreetmap.org/copyright",
        "Ticket and boarding-pass reading: Google ML Kit, on device" to "https://developers.google.com/ml-kit",
        "Globe land outline: Natural Earth (public domain)" to "https://www.naturalearthdata.com",
        "Routes and callsigns: adsbdb" to "https://www.adsbdb.com",
        "Weather: NOAA aviationweather.gov · RainViewer" to "https://aviationweather.gov",
        "Airports: OurAirports (public domain)" to "https://ourairports.com",
        "News: Simple Flying, AeroTime, FlightGlobal, AirlineGeeks, Aviation Week, Google News" to "https://news.google.com",
        "Aircraft types: ICAO Doc 8643 via Mictronics" to "https://www.mictronics.de/aircraft-database/",
    )
    SectionCard("Data and credits") {
        credits.forEach { (label, url) ->
            Text(
                label,
                style = ContrailTheme.type.bodySmall,
                color = if (label.startsWith("Logos by logostream")) ContrailTheme.colors.accent else ContrailTheme.colors.textSecondary,
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { uri.openUri(url) },
            )
        }
        Eyebrow("No accounts, no ads, no tracking")
    }
}

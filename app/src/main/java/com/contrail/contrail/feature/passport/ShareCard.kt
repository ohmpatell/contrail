package com.contrail.contrail.feature.passport

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.designsystem.component.AccentButton
import com.contrail.contrail.core.designsystem.component.AuroraBackground
import com.contrail.contrail.core.designsystem.component.DottedGlobe
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.GlassIconButton
import com.contrail.contrail.core.designsystem.component.MapArc
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.navigation.LocalNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun ShareCardRoute(viewModel: PassportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ShareCardScreen(state, onBack = LocalNavigator.current::back)
}

/** Your Passport as a story-sized card, ready to send anywhere. */
@Composable
fun ShareCardScreen(state: PassportUiState, onBack: () -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var busy by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        AuroraBackground(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars).padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(ContrailIcons.Back, "Back", onBack)
                Text("Share your Passport", style = ContrailTheme.type.headline, color = colors.textPrimary, modifier = Modifier.padding(start = spacing.md))
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                PassportCard(
                    state,
                    Modifier
                        .aspectRatio(9f / 16f)
                        .drawWithContent {
                            layer.record { this@drawWithContent.drawContent() }
                            drawLayer(layer)
                        },
                )
            }
            AccentButton(
                if (busy) "Preparing…" else "Share",
                onClick = {
                    if (busy) return@AccentButton
                    busy = true
                    scope.launch {
                        runCatching { share(context, layer.toImageBitmap().asAndroidBitmap()) }
                        busy = false
                    }
                },
                icon = ContrailIcons.Share,
                accent = colors.passport,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private suspend fun share(context: Context, bitmap: Bitmap) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        File(dir, "contrail-passport.png").also { f -> f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Share your Passport").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** The card itself: globe, the big numbers, flags and a row of stamps. */
@Composable
fun PassportCard(state: PassportUiState, modifier: Modifier = Modifier) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val s = state.stats
    val legs = remember(s.flown, state.places) { globeLegs(s.flown, state.places) }
    val routes = remember(legs) { legs.distinctBy { listOf(it.segment.origin, it.segment.destination).sorted() } }
    val home = legs.groupingBy { it.segment.origin }.eachCount().maxByOrNull { it.value }?.key?.let { state.places[it] }
    Box(
        modifier
            .clip(ContrailTheme.shapes.large)
            .background(Brush.verticalGradient(listOf(Color(0xFF14204A), Color(0xFF0A1022), Color(0xFF1B1240))))
            .border(1.dp, colors.passport.copy(alpha = 0.5f), ContrailTheme.shapes.large),
    ) {
        Column(Modifier.fillMaxSize().padding(spacing.xl), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Eyebrow(state.selectedYear?.let { "My $it in the air" } ?: (s.years.minOrNull()?.let { "Flying since $it" } ?: "My flying"), color = colors.passport)
                    Text("Passport", style = ContrailTheme.type.displayMedium, color = colors.textPrimary)
                }
                Text("CONTRAIL", style = ContrailTheme.type.eyebrow, color = Color(0xFFE9C46A))
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                DottedGlobe(
                    arcs = routes.map { MapArc(it.points) },
                    centerLon = (home?.lon?.toFloat() ?: -40f) + 45f,
                    centerLat = (home?.lat?.toFloat() ?: 25f).coerceIn(-15f, 45f),
                    markers = (s.visited + s.transited).mapNotNull { state.places[it]?.let { p -> LatLon(p.lat, p.lon) } },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Row {
                Big("${s.flights}", "flights", Modifier.weight(1f))
                Big(String.format(Locale.US, "%,d", s.distanceKm), "km", Modifier.weight(1.4f))
            }
            Row {
                Big("${s.countries.size}", "countries", Modifier.weight(1f))
                Big("${s.visited.size + s.transited.size}", "airports", Modifier.weight(1f))
                Big("${s.minutesAloft / 60}", "hours", Modifier.weight(1f))
            }
            Text(
                String.format(Locale.US, "%.1f× around the Earth · %d medals", s.laps, s.achievements.count { it.unlocked }),
                style = ContrailTheme.type.label, color = colors.passport,
            )
            Text(s.countries.sorted().joinToString(" ") { com.contrail.contrail.feature.trips.add.flag(it) }, style = ContrailTheme.type.title, maxLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                s.stamps.asReversed().take(4).forEach { st -> PassportStamp(st, Modifier.size(64.dp), animate = false) }
            }
        }
    }
}

@Composable
private fun Big(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = ContrailTheme.type.numericLarge, color = ContrailTheme.colors.textPrimary, maxLines = 1)
        Eyebrow(label)
    }
}

package com.contrail.contrail.feature.radar.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.core.data.repository.RadarLayers
import com.contrail.contrail.core.designsystem.component.ContrailChip
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.GlassLevel
import com.contrail.contrail.core.designsystem.component.GlassSurface
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.AltitudeRamp
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.feature.radar.AltitudeBand
import com.contrail.contrail.feature.radar.FilterFacets
import com.contrail.contrail.feature.radar.RadarFilters

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterPanel(
    filters: RadarFilters,
    facets: FilterFacets,
    shownCount: Int,
    totalCount: Int,
    onChange: ((RadarFilters) -> RadarFilters) -> Unit,
    onClear: () -> Unit,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val spacing = ContrailTheme.spacing
    val colors = ContrailTheme.colors
    GlassSurface(modifier.fillMaxWidth(), level = GlassLevel.Strong) {
        Column(
            Modifier
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Filters", style = ContrailTheme.type.headline, color = colors.textPrimary)
                    Text("$shownCount of $totalCount aircraft", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
                }
                if (!filters.isEmpty) {
                    Text(
                        "Clear all",
                        style = ContrailTheme.type.label,
                        color = colors.accent,
                        modifier = Modifier
                            .clip(ContrailTheme.shapes.chip)
                            .clickable(role = Role.Button, onClick = onClear)
                            .padding(horizontal = spacing.md, vertical = spacing.sm),
                    )
                }
            }

            Section("Highlights") {
                ContrailChip(
                    label = "Rare types · ${facets.rareCount}",
                    selected = filters.rareOnly,
                    onClick = { onChange { it.copy(rareOnly = !it.rareOnly) } },
                    leading = { Icon(ContrailIcons.Sparkle, null, Modifier.size(ContrailTheme.sizes.iconSmall), tint = colors.accent) },
                )
                ContrailChip(
                    label = "Military only · ${facets.militaryCount}",
                    selected = filters.militaryOnly,
                    onClick = { onChange { it.copy(militaryOnly = !it.militaryOnly) } },
                    leading = { Icon(ContrailIcons.Shield, null, Modifier.size(ContrailTheme.sizes.iconSmall), tint = colors.accent) },
                )
            }

            Section("Altitude") {
                AltitudeBand.entries.forEach { band ->
                    ContrailChip(
                        label = band.label,
                        selected = band in filters.bands,
                        onClick = { onChange { it.toggleBand(band) } },
                        accent = AltitudeRamp.colorFor(band.sampleAltitudeFt),
                        leading = { Dot(AltitudeRamp.colorFor(band.sampleAltitudeFt)) },
                    )
                }
            }

            if (facets.airlines.isNotEmpty()) {
                Section("Airline") {
                    facets.airlines.forEach { facet ->
                        ContrailChip(
                            label = "${facet.label} · ${facet.count}",
                            selected = facet.key in filters.airlines,
                            onClick = { onChange { it.toggleAirline(facet.key) } },
                            accent = Airlines.byIcao(facet.key)?.let { Color(it.accentArgb) } ?: colors.accent,
                        )
                    }
                }
            }
            if (facets.types.isNotEmpty()) {
                Section("Aircraft type") {
                    facets.types.forEach { facet ->
                        ContrailChip(
                            label = "${facet.label} · ${facet.count}",
                            selected = facet.key in filters.types,
                            onClick = { onChange { it.toggleType(facet.key) } },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.sm)) {
        Eyebrow(title)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.sm),
        ) { content() }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(ContrailTheme.spacing.sm).clip(CircleShape).background(color))
}

@Composable
fun LayersPanel(
    layers: RadarLayers,
    onChange: ((RadarLayers) -> RadarLayers) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = ContrailTheme.spacing
    GlassSurface(modifier.fillMaxWidth(), level = GlassLevel.Strong) {
        Column(Modifier.padding(vertical = spacing.lg)) {
            Text(
                "Map layers",
                style = ContrailTheme.type.headline,
                color = ContrailTheme.colors.textPrimary,
                modifier = Modifier.padding(horizontal = spacing.xl, vertical = spacing.xs),
            )
            ToggleRow(ContrailIcons.Locate, "Airport labels", "IATA codes and names from the map", layers.airportLabels) { onChange { it.copy(airportLabels = !it.airportLabels) } }
            ToggleRow(ContrailIcons.Signal, "Weather radar", "Live precipitation from RainViewer", layers.weather) { onChange { it.copy(weather = !it.weather) } }
            ToggleRow(ContrailIcons.Passport, "Day / night", "Shades where the sun has set", layers.dayNight) { onChange { it.copy(dayNight = !it.dayNight) } }
            ToggleRow(ContrailIcons.Radar, "Trails", "Last four minutes, fading with age", layers.trails) { onChange { it.copy(trails = !it.trails) } }
            ToggleRow(ContrailIcons.Trips, "Callsigns", "Labels when zoomed in", layers.callsigns) { onChange { it.copy(callsigns = !it.callsigns) } }
        }
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit) {
    val spacing = ContrailTheme.spacing
    val colors = ContrailTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interaction, indication = null, role = Role.Switch, onClick = onToggle)
            .padding(horizontal = spacing.xl, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Icon(icon, null, tint = if (checked) colors.accent else colors.textTertiary, modifier = Modifier.size(ContrailTheme.sizes.icon))
        Column(Modifier.weight(1f)) {
            Text(title, style = ContrailTheme.type.title, color = colors.textPrimary)
            Text(subtitle, style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
        }
        GlassToggle(checked)
    }
}

/** A pill switch whose knob springs across. */
@Composable
fun GlassToggle(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = ContrailTheme.colors
    val motion = ContrailTheme.motion
    val knob by animateDpAsState(if (checked) 21.dp else 3.dp, motion.bouncy(), label = "knob")
    val track by animateColorAsState(if (checked) colors.accent.copy(alpha = 0.85f) else colors.glassTint, motion.fade(), label = "track")
    val knobColor by animateColorAsState(if (checked) colors.onAccent else colors.textSecondary, motion.fade(), label = "knobColor")
    Box(
        modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(ContrailTheme.shapes.chip)
            .background(track)
            .border(ContrailTheme.sizes.hairline, colors.hairline, ContrailTheme.shapes.chip),
    ) {
        Box(
            Modifier
                .offset { IntOffset(knob.roundToPx(), 3.dp.roundToPx()) }
                .size(20.dp)
                .clip(CircleShape)
                .background(knobColor),
        )
    }
}

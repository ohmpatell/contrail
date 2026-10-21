package com.contrail.contrail.feature.radar.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.contrail.contrail.core.data.model.LiveAircraft
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.GlassIconButton
import com.contrail.contrail.core.designsystem.component.GlassLevel
import com.contrail.contrail.core.designsystem.component.GlassSurface
import com.contrail.contrail.core.designsystem.component.LivePulse
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.feature.radar.FeedStatus
import com.contrail.contrail.feature.radar.RadarPanel
import com.contrail.contrail.feature.radar.RadarUiState

/** Top-of-map chrome: feed status and counts on the left, filter and layer buttons on the right. */
@Composable
fun RadarHud(
    state: RadarUiState,
    onTogglePanel: (RadarPanel) -> Unit,
    onSelectAircraft: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = ContrailTheme.spacing
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FeedPill(state)
            Spacer(Modifier.weight(1f))
            Box {
                GlassIconButton(
                    ContrailIcons.Filter,
                    contentDescription = "Filters",
                    onClick = { onTogglePanel(RadarPanel.Filters) },
                    active = state.panel == RadarPanel.Filters,
                )
                CountBadge(state.filters.activeCount, Modifier.align(Alignment.TopEnd))
            }
            Spacer(Modifier.width(spacing.sm))
            GlassIconButton(
                ContrailIcons.Layers,
                contentDescription = "Map layers",
                onClick = { onTogglePanel(RadarPanel.Layers) },
                active = state.panel == RadarPanel.Layers,
            )
        }
        val emergency = state.emergencies.firstOrNull()
        AnimatedVisibility(
            visible = emergency != null && state.selected?.aircraft?.hex != emergency.hex,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
        ) {
            if (emergency != null) EmergencyBanner(emergency, onClick = { onSelectAircraft(emergency.hex) })
        }
    }
}

@Composable
private fun FeedPill(state: RadarUiState, modifier: Modifier = Modifier) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    GlassSurface(modifier.height(ContrailTheme.sizes.touchTarget), shape = ContrailTheme.shapes.chip) {
        Row(
            Modifier.align(Alignment.CenterStart).padding(start = spacing.sm, end = spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val (dot, source) = when (val feed = state.feed) {
                is FeedStatus.Live -> colors.statusOnTime to feed.source
                is FeedStatus.Offline -> colors.statusDelayed to "Reconnecting"
                FeedStatus.Connecting -> colors.statusAirborne to "Connecting"
            }
            LivePulse(color = dot)
            Spacer(Modifier.width(spacing.xs))
            AnimatedContent(
                targetState = state.shownCount,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith (slideOutVertically { if (up) -it else it } + fadeOut())
                },
                label = "count",
            ) { count ->
                Text(count.toString(), style = ContrailTheme.type.numeric, color = colors.textPrimary)
            }
            Spacer(Modifier.width(spacing.xs + spacing.xxs))
            Column {
                val label = if (state.filters.isEmpty) "aircraft" else "of ${state.totalCount} shown"
                Text(label, style = ContrailTheme.type.label, color = colors.textSecondary, maxLines = 1)
                Eyebrow(if (state.radiusCapped) "$source · zoom in for more" else source, color = colors.textTertiary)
            }
        }
    }
}

@Composable
private fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    AnimatedVisibility(count > 0, modifier, enter = scaleIn(ContrailTheme.motion.bouncy()) + fadeIn(), exit = scaleOut() + fadeOut()) {
        Box(
            Modifier
                .offset(x = 2.dp, y = (-2).dp)
                .sizeIn(minWidth = 18.dp, minHeight = 18.dp)
                .clip(CircleShape)
                .background(ContrailTheme.colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(count.toString(), style = ContrailTheme.type.eyebrow, color = ContrailTheme.colors.onAccent)
        }
    }
}

@Composable
private fun EmergencyBanner(aircraft: LiveAircraft, onClick: () -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    GlassSurface(
        Modifier.clickable(onClick = onClick),
        shape = ContrailTheme.shapes.chip,
        level = GlassLevel.Strong,
        tint = colors.emergency.copy(alpha = 0.22f),
        glow = colors.emergency,
    ) {
        Row(
            Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            LivePulse(color = colors.emergency)
            Icon(ContrailIcons.Alert, null, tint = colors.emergency, modifier = Modifier.size(ContrailTheme.sizes.iconSmall))
            Text(
                "SQUAWK ${aircraft.squawk ?: "----"}",
                style = ContrailTheme.type.numeric,
                color = colors.textPrimary,
            )
            Text(
                "${LiveAircraft.emergencyLabel(aircraft.squawk) ?: "Emergency"} · ${aircraft.displayCallsign}",
                style = ContrailTheme.type.label,
                color = colors.textSecondary,
                maxLines = 1,
            )
        }
    }
}

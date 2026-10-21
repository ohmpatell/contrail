package com.contrail.contrail.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.contrail.contrail.core.designsystem.component.GlassLevel
import com.contrail.contrail.core.designsystem.component.GlassSurface
import com.contrail.contrail.core.designsystem.component.LocalChromeHaze
import com.contrail.contrail.core.designsystem.component.pressScale
import com.contrail.contrail.core.designsystem.theme.ContrailTheme

/**
 * Floating frosted tab bar. The glowing indicator springs between tabs so the eye
 * follows where you are going.
 */
@Composable
fun GlassNavBar(
    selected: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ContrailTheme.colors
    val motion = ContrailTheme.motion
    val haptic = LocalHapticFeedback.current
    val destinations = TopLevelDestination.entries
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(ContrailTheme.sizes.navBarHeight),
        shape = ContrailTheme.shapes.chip,
        level = GlassLevel.Strong,
        hazeState = LocalChromeHaze.current,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 6.dp)) {
            val itemWidth = maxWidth / destinations.size
            val indicatorX by animateDpAsState(itemWidth * selected.ordinal, motion.bouncy(), label = "indicator")
            val accent = colors.accent
            Box(
                Modifier
                    .offset { IntOffset(indicatorX.roundToPx(), 0) }
                    .width(itemWidth)
                    .fillMaxHeight()
                    .clip(ContrailTheme.shapes.chip)
                    .background(
                        Brush.verticalGradient(listOf(accent.copy(alpha = 0.24f), accent.copy(alpha = 0.10f))),
                    )
                    .border(ContrailTheme.sizes.hairline, accent.copy(alpha = 0.35f), ContrailTheme.shapes.chip)
                    .drawBehind {
                        drawCircle(
                            Brush.radialGradient(
                                listOf(accent.copy(alpha = 0.35f), Color.Transparent),
                                center = Offset(size.width / 2, size.height),
                                radius = size.width * 0.7f,
                            ),
                        )
                    },
            )
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Start) {
                destinations.forEach { destination ->
                    NavItem(
                        destination = destination,
                        selected = destination == selected,
                        modifier = Modifier.width(itemWidth).fillMaxHeight(),
                        onClick = {
                            if (destination != selected) {
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                onSelect(destination)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    destination: TopLevelDestination,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = ContrailTheme.colors
    val motion = ContrailTheme.motion
    val interaction = remember { MutableInteractionSource() }
    val tint by animateColorAsState(if (selected) colors.accent else colors.textTertiary, motion.fade(200), label = "navTint")
    val labelColor by animateColorAsState(if (selected) colors.textPrimary else colors.textTertiary, motion.fade(200), label = "navLabel")
    val lift by animateFloatAsState(if (selected) 1f else 0f, motion.bouncy(), label = "navLift")
    Column(
        modifier = modifier
            .pressScale(interaction)
            .clickable(interaction, indication = null, role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            destination.icon(),
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(ContrailTheme.sizes.icon)
                .graphicsLayer {
                    translationY = -2.dp.toPx() * lift
                    val s = 1f + 0.08f * lift
                    scaleX = s
                    scaleY = s
                },
        )
        Spacer(Modifier.height(3.dp))
        Text(destination.label, style = ContrailTheme.type.eyebrow.copy(letterSpacing = ContrailTheme.type.label.letterSpacing), color = labelColor, maxLines = 1)
    }
}

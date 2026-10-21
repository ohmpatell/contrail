package com.contrail.contrail.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import kotlinx.coroutines.delay

data class UpcomingFeature(val icon: ImageVector, val title: String, val body: String)

/**
 * Layout shared by tabs that are still on the roadmap: a big display title, a glass hero
 * illustration, and the features that are coming, each rising into place in sequence.
 */
@Composable
fun TabScaffold(
    title: String,
    subtitle: String,
    eyebrow: String,
    features: List<UpcomingFeature>,
    modifier: Modifier = Modifier,
    accent: Color = ContrailTheme.colors.accent,
    hero: @Composable () -> Unit,
) {
    val spacing = ContrailTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = spacing.gutter)
            .padding(top = spacing.xl, bottom = spacing.navBarClearance),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                LivePulse(color = accent)
                Eyebrow(eyebrow, color = accent)
            }
            Text(title, style = ContrailTheme.type.displayLarge, color = ContrailTheme.colors.textPrimary)
            Text(subtitle, style = ContrailTheme.type.body, color = ContrailTheme.colors.textSecondary)
        }
        RiseIn(index = 0) {
            GlassSurface(Modifier.fillMaxWidth(), glow = accent.copy(alpha = 0.6f)) { hero() }
        }
        Spacer(Modifier.height(spacing.xs))
        Eyebrow("What's coming")
        features.forEachIndexed { i, feature ->
            RiseIn(index = i + 1) { FeatureRow(feature, accent) }
        }
    }
}

@Composable
private fun FeatureRow(feature: UpcomingFeature, accent: Color) {
    val spacing = ContrailTheme.spacing
    GlassSurface(Modifier.fillMaxWidth(), shape = ContrailTheme.shapes.medium) {
        Row(
            Modifier.padding(spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            GlassSurface(Modifier.size(ContrailTheme.sizes.touchTarget - spacing.xs), shape = ContrailTheme.shapes.small, glow = accent) {
                Icon(
                    feature.icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.align(Alignment.Center).size(ContrailTheme.sizes.icon),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(feature.title, style = ContrailTheme.type.title, color = ContrailTheme.colors.textPrimary)
                Text(feature.body, style = ContrailTheme.type.bodySmall, color = ContrailTheme.colors.textSecondary)
            }
        }
    }
}

/** Content rises and fades into place, staggered by [index]. */
@Composable
fun RiseIn(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val motion = ContrailTheme.motion
    val progress = remember { Animatable(if (motion.reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!motion.reduced) {
            delay(80L + index * 70L)
            progress.animateTo(1f, motion.gentle())
        }
    }
    Box(
        modifier.graphicsLayer {
            val p = progress.value
            alpha = p.coerceIn(0f, 1f)
            translationY = (1f - p) * 24f * density
        },
    ) { content() }
}

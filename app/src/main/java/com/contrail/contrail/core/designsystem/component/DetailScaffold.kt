package com.contrail.contrail.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * Shared layout for pages opened from anywhere (aircraft, airport, settings): the aurora
 * canvas, a frosted back button and a scrolling column. [hero] sits under the top bar.
 */
@Composable
fun DetailScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    accent: Color = ContrailTheme.colors.accent,
    actions: @Composable RowScope.() -> Unit = {},
    hero: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = ContrailTheme.spacing
    val backdrop = rememberHazeState()
    CompositionLocalProvider(LocalBackdropHaze provides backdrop) {
        Box(modifier.fillMaxSize()) {
            AuroraBackground(Modifier.fillMaxSize().hazeSource(backdrop))
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = spacing.xxxl),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    GlassIconButton(ContrailIcons.Back, "Back", onBack)
                    Box(Modifier.weight(1f))
                    actions()
                }
                hero?.invoke()
                Column(
                    Modifier.padding(horizontal = spacing.gutter).padding(top = spacing.md),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    if (eyebrow != null) Eyebrow(eyebrow, color = accent)
                    Text(title, style = ContrailTheme.type.displayMedium, color = ContrailTheme.colors.textPrimary)
                }
                Column(
                    Modifier.padding(horizontal = spacing.gutter).padding(top = spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.lg),
                    content = content,
                )
            }
        }
    }
}

/** A titled glass card section used on detail pages. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = ContrailTheme.spacing
    GlassSurface(modifier.fillMaxWidth(), shape = ContrailTheme.shapes.medium) {
        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow(title, Modifier.weight(1f))
                trailing?.invoke()
            }
            content()
        }
    }
}

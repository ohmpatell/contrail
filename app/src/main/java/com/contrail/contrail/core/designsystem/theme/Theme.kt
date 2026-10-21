package com.contrail.contrail.core.designsystem.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

private val LocalColors = staticCompositionLocalOf { NightCockpitColors }
private val LocalType = staticCompositionLocalOf { ContrailType }
private val LocalSpacing = staticCompositionLocalOf { ContrailSpacing() }
private val LocalShapes = staticCompositionLocalOf { ContrailShapes() }
private val LocalSizes = staticCompositionLocalOf { ContrailSizes() }
private val LocalMotion = staticCompositionLocalOf { ContrailMotion(reduced = false) }

/** Entry point for every design token. Feature code styles itself only through this. */
object ContrailTheme {
    val colors: ContrailColors
        @Composable @ReadOnlyComposable get() = LocalColors.current
    val type: ContrailTypography
        @Composable @ReadOnlyComposable get() = LocalType.current
    val spacing: ContrailSpacing
        @Composable @ReadOnlyComposable get() = LocalSpacing.current
    val shapes: ContrailShapes
        @Composable @ReadOnlyComposable get() = LocalShapes.current
    val sizes: ContrailSizes
        @Composable @ReadOnlyComposable get() = LocalSizes.current
    val motion: ContrailMotion
        @Composable @ReadOnlyComposable get() = LocalMotion.current
}

/**
 * Night cockpit theme. [accent] lets a screen glow in an airline's livery colour.
 * [reducedMotion] defaults to the system "remove animations" setting.
 */
@Composable
fun ContrailTheme(
    accent: Color? = null,
    reducedMotion: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val inPreview = LocalInspectionMode.current
    val reduced = reducedMotion ?: remember(context) {
        !inPreview && ContrailMotion.isReducedMotion(context)
    }
    val colors = remember(accent) {
        if (accent == null) NightCockpitColors else NightCockpitColors.copy(accent = accent)
    }
    val material = remember(colors) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            secondary = colors.accentDeep,
            background = colors.canvas,
            onBackground = colors.textPrimary,
            surface = colors.canvasRaised,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.canvasRaised,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.hairline,
            error = colors.statusCancelled,
        )
    }
    val type = ContrailType
    val materialType = remember {
        Typography(
            displayLarge = type.displayLarge,
            displayMedium = type.displayMedium,
            displaySmall = type.displaySmall,
            headlineMedium = type.headline,
            titleMedium = type.title,
            bodyLarge = type.body,
            bodyMedium = type.body,
            bodySmall = type.bodySmall,
            labelLarge = type.label,
            labelMedium = type.label,
            labelSmall = type.eyebrow,
        )
    }
    MaterialTheme(colorScheme = material, typography = materialType) {
        CompositionLocalProvider(
            LocalColors provides colors,
            LocalType provides type,
            LocalMotion provides ContrailMotion(reduced),
            LocalContentColor provides colors.textPrimary,
            LocalTextSelectionColors provides TextSelectionColors(
                handleColor = colors.accent,
                backgroundColor = colors.accent.copy(alpha = 0.3f),
            ),
            content = content,
        )
    }
}

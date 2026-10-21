package com.contrail.contrail.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Raw palette for the "night cockpit" look. Feature code never reads these directly:
 * it goes through [ContrailColors] via `ContrailTheme.colors`.
 */
internal object Palette {
    val Ink950 = Color(0xFF03060D)
    val Ink900 = Color(0xFF060B16)
    val Ink850 = Color(0xFF0A1122)
    val Ink800 = Color(0xFF0F182E)
    val Ink700 = Color(0xFF18233F)

    val Frost100 = Color(0xFFF2F6FF)
    val Frost300 = Color(0xFFB9C4DE)
    val Frost500 = Color(0xFF7D89A8)
    val Frost700 = Color(0xFF4A5573)

    val AuroraTeal = Color(0xFF0E5A66)
    val AuroraIndigo = Color(0xFF222A7A)
    val AuroraViolet = Color(0xFF4B2378)

    val Contrail = Color(0xFF8FD8FF)
    val ContrailDeep = Color(0xFF3AA6F0)

    val Green = Color(0xFF3EE0A1)
    val Amber = Color(0xFFFFC14D)
    val Red = Color(0xFFFF5468)
    val Sky = Color(0xFF4CC4FF)
}

@Immutable
data class ContrailColors(
    val canvas: Color,
    val canvasRaised: Color,
    val canvasSunken: Color,
    val auroraA: Color,
    val auroraB: Color,
    val auroraC: Color,
    val glassTint: Color,
    val glassTintStrong: Color,
    val glassBorderTop: Color,
    val glassBorderBottom: Color,
    val glassHighlight: Color,
    val scrim: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDisabled: Color,
    val hairline: Color,
    val accent: Color,
    val accentDeep: Color,
    val onAccent: Color,
    val statusOnTime: Color,
    val statusDelayed: Color,
    val statusCancelled: Color,
    val statusAirborne: Color,
    val emergency: Color,
    val passport: Color,
    val flapTile: Color,
    val flapTileEdge: Color,
)

val NightCockpitColors = ContrailColors(
    canvas = Palette.Ink900,
    canvasRaised = Palette.Ink850,
    canvasSunken = Palette.Ink950,
    auroraA = Palette.AuroraTeal,
    auroraB = Palette.AuroraIndigo,
    auroraC = Palette.AuroraViolet,
    glassTint = Color.White.copy(alpha = 0.12f),
    glassTintStrong = Color.White.copy(alpha = 0.18f),
    glassBorderTop = Color.White.copy(alpha = 0.28f),
    glassBorderBottom = Color.White.copy(alpha = 0.05f),
    glassHighlight = Color.White.copy(alpha = 0.10f),
    scrim = Palette.Ink950.copy(alpha = 0.55f),
    textPrimary = Palette.Frost100,
    textSecondary = Palette.Frost300,
    textTertiary = Palette.Frost500,
    textDisabled = Palette.Frost700,
    hairline = Color.White.copy(alpha = 0.08f),
    accent = Palette.Contrail,
    accentDeep = Palette.ContrailDeep,
    onAccent = Palette.Ink950,
    statusOnTime = Palette.Green,
    statusDelayed = Palette.Amber,
    statusCancelled = Palette.Red,
    statusAirborne = Palette.Sky,
    emergency = Palette.Red,
    passport = Color(0xFFB08CFF),
    flapTile = Color(0xFF111A30),
    flapTileEdge = Color(0xFF02040A),
)

/**
 * The radar altitude ramp: ground grey → low teal → mid blue → cruise violet → high magenta.
 * Stops are in feet. Used by both the MapLibre layers (as an interpolate expression) and Compose.
 */
object AltitudeRamp {
    val Ground = Color(0xFF8790A6)

    val stops: List<Pair<Int, Color>> = listOf(
        0 to Color(0xFF2FE0C4),
        5_000 to Color(0xFF2CC9E8),
        12_000 to Color(0xFF3D9BFF),
        22_000 to Color(0xFF5B74FF),
        32_000 to Color(0xFF9A5CFF),
        40_000 to Color(0xFFE04FD6),
        45_000 to Color(0xFFFF6FC8),
    )

    /** Colour for an altitude in feet; `null` means on the ground. */
    fun colorFor(altitudeFt: Int?): Color {
        if (altitudeFt == null) return Ground
        val first = stops.first()
        if (altitudeFt <= first.first) return first.second
        for (i in 1 until stops.size) {
            val (hiAlt, hiColor) = stops[i]
            if (altitudeFt <= hiAlt) {
                val (loAlt, loColor) = stops[i - 1]
                val t = (altitudeFt - loAlt).toFloat() / (hiAlt - loAlt)
                return lerpColor(loColor, hiColor, t)
            }
        }
        return stops.last().second
    }

    private fun lerpColor(a: Color, b: Color, t: Float) = Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = a.alpha + (b.alpha - a.alpha) * t,
    )
}

/** Badge gradients for the rarity tiers: gold holographic, silver shimmer, blue. */
object RarityPalette {
    val Legendary = listOf(Color(0xFFF5C451), Color(0xFFFFE9A8), Color(0xFFE0A93A))
    val Rare = listOf(Color(0xFFD4DCEC), Color(0xFFF4F7FF), Color(0xFF9AA6C2))
    val Uncommon = listOf(Color(0xFF6FB6FF), Color(0xFFA8D6FF), Color(0xFF3D8BFF))
}

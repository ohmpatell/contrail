package com.contrail.contrail.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** The looks you can pick in Settings. */
enum class ThemeChoice(val label: String, val blurb: String) {
    NightCockpit("Night Cockpit", "Deep navy glass and aurora. The original."),
    Chart("Chart", "Cream paper, magenta airways, printed type. Like a sectional chart."),
    GoldenHour("Golden Hour", "Follows the real sun where you are: blue hour, gold, day, night."),
    GlassCockpit("Glass Cockpit", "Black glass, magenta and cyan, tape-style numbers. A flight deck display."),
}

/** A paper aeronautical chart: cream stock, magenta for airways, chart blue for water and links. */
val ChartColors = ContrailColors(
    canvas = Color(0xFFF2EDDF),
    canvasRaised = Color(0xFFFAF7EE),
    canvasSunken = Color(0xFFE6DFCB),
    auroraA = Color(0xFFCFE3E6),
    auroraB = Color(0xFFD9DCF0),
    auroraC = Color(0xFFEBD3E3),
    glassTint = Color.White.copy(alpha = 0.55f),
    glassTintStrong = Color.White.copy(alpha = 0.72f),
    glassBorderTop = Color.White.copy(alpha = 0.9f),
    glassBorderBottom = Color(0xFF1B2238).copy(alpha = 0.08f),
    glassHighlight = Color.White.copy(alpha = 0.4f),
    scrim = Color(0xFF1B2238).copy(alpha = 0.3f),
    textPrimary = Color(0xFF1B2238),
    textSecondary = Color(0xFF46506A),
    textTertiary = Color(0xFF7A8096),
    textDisabled = Color(0xFFA9ADBA),
    hairline = Color(0xFF1B2238).copy(alpha = 0.1f),
    accent = Color(0xFFB0287A),
    accentDeep = Color(0xFF1E5AA0),
    onAccent = Color.White,
    statusOnTime = Color(0xFF1E8A5A),
    statusDelayed = Color(0xFFB7740F),
    statusCancelled = Color(0xFFC42B3C),
    statusAirborne = Color(0xFF1E6FBF),
    emergency = Color(0xFFC42B3C),
    passport = Color(0xFF6B4BC8),
    flapTile = Color(0xFF1E2540),
    flapTileEdge = Color(0xFF0A0E1C),
    flapInk = Color(0xFFF4EFE0),
    isLight = true,
    mapStyle = "map/contrail_chart.json",
)

/** A flight-deck display: black glass, magenta for what's active, cyan for what's selected. */
val GlassCockpitColors = ContrailColors(
    canvas = Color(0xFF000000),
    canvasRaised = Color(0xFF0A0E0C),
    canvasSunken = Color(0xFF000000),
    auroraA = Color(0xFF00331F),
    auroraB = Color(0xFF00242E),
    auroraC = Color(0xFF2A0A2A),
    glassTint = Color.White.copy(alpha = 0.08f),
    glassTintStrong = Color.White.copy(alpha = 0.13f),
    glassBorderTop = Color(0xFF31E3FF).copy(alpha = 0.28f),
    glassBorderBottom = Color.White.copy(alpha = 0.04f),
    glassHighlight = Color.White.copy(alpha = 0.06f),
    scrim = Color.Black.copy(alpha = 0.6f),
    textPrimary = Color(0xFFEAFBF1),
    textSecondary = Color(0xFF8FB5A0),
    textTertiary = Color(0xFF5C7A6A),
    textDisabled = Color(0xFF34463C),
    hairline = Color(0xFF3EE6A0).copy(alpha = 0.14f),
    accent = Color(0xFFFF5CE1),
    accentDeep = Color(0xFF31E3FF),
    onAccent = Color.Black,
    statusOnTime = Color(0xFF3BF08A),
    statusDelayed = Color(0xFFFFC23D),
    statusCancelled = Color(0xFFFF4D5E),
    statusAirborne = Color(0xFF31E3FF),
    emergency = Color(0xFFFF4D5E),
    passport = Color(0xFFC08CFF),
    flapTile = Color(0xFF0C120F),
    flapTileEdge = Color.Black,
    flapInk = Color(0xFFEAFBF1),
    mapStyle = "map/contrail_glass.json",
)

/**
 * Golden Hour: the night cockpit tinted by the real sun. Keyframes by solar elevation, blended
 * smoothly in between: night, blue hour, golden hour, day.
 */
object GoldenHour {
    private val blueHour = NightCockpitColors.copy(
        canvas = Color(0xFF0A0E24), canvasRaised = Color(0xFF10153A), canvasSunken = Color(0xFF060818),
        auroraA = Color(0xFF2B2F7A), auroraB = Color(0xFF5A2A7A), auroraC = Color(0xFF8A3A6A),
        accent = Color(0xFFFF9EC4), accentDeep = Color(0xFFB57CFF),
    )
    private val golden = NightCockpitColors.copy(
        canvas = Color(0xFF1A1020), canvasRaised = Color(0xFF24162A), canvasSunken = Color(0xFF0E0812),
        auroraA = Color(0xFF7A3A1E), auroraB = Color(0xFF9A4A2A), auroraC = Color(0xFF5A2A5A),
        accent = Color(0xFFFFB45C), accentDeep = Color(0xFFFF7A59),
    )
    private val day = NightCockpitColors.copy(
        canvas = Color(0xFF0B1A33), canvasRaised = Color(0xFF12264A), canvasSunken = Color(0xFF07112A),
        auroraA = Color(0xFF1E6A8A), auroraB = Color(0xFF2A5AB0), auroraC = Color(0xFF3A7AC0),
        accent = Color(0xFF7FD4FF), accentDeep = Color(0xFF3AA6F0),
    )

    /** Elevation in degrees → palette. Below −12° is full night, above 20° full day. */
    private val keys = listOf(-12.0 to NightCockpitColors, -6.0 to blueHour, 4.0 to golden, 20.0 to day)

    fun colors(sunElevationDeg: Double?): ContrailColors {
        val e = sunElevationDeg ?: return NightCockpitColors
        if (e <= keys.first().first) return keys.first().second
        if (e >= keys.last().first) return keys.last().second
        val i = keys.indexOfLast { it.first <= e }
        val (e0, a) = keys[i]
        val (e1, b) = keys[i + 1]
        return blend(a, b, ((e - e0) / (e1 - e0)).toFloat())
    }

    /** Short name for the current phase, shown under the theme in Settings. */
    fun phase(sunElevationDeg: Double?): String = when {
        sunElevationDeg == null -> "Open Sky once so Contrail knows where the sun is"
        sunElevationDeg < -12 -> "Night"
        sunElevationDeg < -3 -> "Blue hour"
        sunElevationDeg < 10 -> "Golden hour"
        else -> "Day"
    }
}

/** Every colour moves from [a] to [b]; flags and assets follow whichever side is nearer. */
fun blend(a: ContrailColors, b: ContrailColors, t: Float): ContrailColors {
    fun c(x: Color, y: Color) = lerp(x, y, t.coerceIn(0f, 1f))
    val near = if (t < 0.5f) a else b
    return ContrailColors(
        canvas = c(a.canvas, b.canvas), canvasRaised = c(a.canvasRaised, b.canvasRaised), canvasSunken = c(a.canvasSunken, b.canvasSunken),
        auroraA = c(a.auroraA, b.auroraA), auroraB = c(a.auroraB, b.auroraB), auroraC = c(a.auroraC, b.auroraC),
        glassTint = c(a.glassTint, b.glassTint), glassTintStrong = c(a.glassTintStrong, b.glassTintStrong),
        glassBorderTop = c(a.glassBorderTop, b.glassBorderTop), glassBorderBottom = c(a.glassBorderBottom, b.glassBorderBottom),
        glassHighlight = c(a.glassHighlight, b.glassHighlight), scrim = c(a.scrim, b.scrim),
        textPrimary = c(a.textPrimary, b.textPrimary), textSecondary = c(a.textSecondary, b.textSecondary),
        textTertiary = c(a.textTertiary, b.textTertiary), textDisabled = c(a.textDisabled, b.textDisabled),
        hairline = c(a.hairline, b.hairline), accent = c(a.accent, b.accent), accentDeep = c(a.accentDeep, b.accentDeep),
        onAccent = c(a.onAccent, b.onAccent), statusOnTime = c(a.statusOnTime, b.statusOnTime),
        statusDelayed = c(a.statusDelayed, b.statusDelayed), statusCancelled = c(a.statusCancelled, b.statusCancelled),
        statusAirborne = c(a.statusAirborne, b.statusAirborne), emergency = c(a.emergency, b.emergency),
        passport = c(a.passport, b.passport), flapTile = c(a.flapTile, b.flapTile), flapTileEdge = c(a.flapTileEdge, b.flapTileEdge),
        flapInk = c(a.flapInk, b.flapInk), isLight = near.isLight, mapStyle = near.mapStyle,
    )
}

/** The palette for a choice. Golden Hour needs the sun's elevation where you are. */
fun colorsFor(choice: ThemeChoice, sunElevationDeg: Double? = null): ContrailColors = when (choice) {
    ThemeChoice.NightCockpit -> NightCockpitColors
    ThemeChoice.Chart -> ChartColors
    ThemeChoice.GoldenHour -> GoldenHour.colors(sunElevationDeg)
    ThemeChoice.GlassCockpit -> GlassCockpitColors
}

/** Glass Cockpit reads its headlines off a display, so they switch to the mono face. */
fun typeFor(choice: ThemeChoice): ContrailTypography = when (choice) {
    ThemeChoice.GlassCockpit -> ContrailType.copy(
        hero = ContrailType.hero.copy(fontFamily = NumericFamily, letterSpacing = ContrailType.numericLarge.letterSpacing),
        displayLarge = ContrailType.displayLarge.copy(fontFamily = NumericFamily, letterSpacing = ContrailType.numericLarge.letterSpacing),
        displayMedium = ContrailType.displayMedium.copy(fontFamily = NumericFamily, letterSpacing = ContrailType.numericLarge.letterSpacing),
        displaySmall = ContrailType.displaySmall.copy(fontFamily = NumericFamily, letterSpacing = ContrailType.numeric.letterSpacing),
        headline = ContrailType.headline.copy(fontFamily = NumericFamily, letterSpacing = ContrailType.numeric.letterSpacing),
    )
    else -> ContrailType
}

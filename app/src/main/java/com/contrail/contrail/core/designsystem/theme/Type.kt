package com.contrail.contrail.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.contrail.contrail.R

private fun variableFamily(resId: Int, vararg weights: FontWeight) = FontFamily(
    weights.map { weight ->
        Font(
            resId = resId,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    },
)

/** Space Grotesk: flight numbers, airport codes, screen titles. */
val DisplayFamily = variableFamily(
    R.font.space_grotesk,
    FontWeight.Light, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold,
)

/** Inter: body text and labels. */
val UiFamily = variableFamily(
    R.font.inter,
    FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold,
)

/** JetBrains Mono: times, altitudes, speeds, squawks. */
val NumericFamily = variableFamily(
    R.font.jetbrains_mono,
    FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold,
)

private val TrimBoth = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)

@Immutable
data class ContrailTypography(
    val hero: TextStyle,
    val displayLarge: TextStyle,
    val displayMedium: TextStyle,
    val displaySmall: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val bodySmall: TextStyle,
    val label: TextStyle,
    val eyebrow: TextStyle,
    val numericLarge: TextStyle,
    val numeric: TextStyle,
    val numericSmall: TextStyle,
    val flap: TextStyle,
)

val ContrailType = ContrailTypography(
    hero = TextStyle(
        fontFamily = DisplayFamily, fontWeight = FontWeight.Bold,
        fontSize = 64.sp, lineHeight = 64.sp, letterSpacing = (-0.04).em, lineHeightStyle = TrimBoth,
    ),
    displayLarge = TextStyle(
        fontFamily = DisplayFamily, fontWeight = FontWeight.Bold,
        fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-0.035).em, lineHeightStyle = TrimBoth,
    ),
    displayMedium = TextStyle(
        fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.02).em, lineHeightStyle = TrimBoth,
    ),
    displaySmall = TextStyle(
        fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = (-0.01).em,
    ),
    headline = TextStyle(
        fontFamily = DisplayFamily, fontWeight = FontWeight.Medium,
        fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.005).em,
    ),
    title = TextStyle(
        fontFamily = UiFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp,
    ),
    body = TextStyle(
        fontFamily = UiFamily, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = UiFamily, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 18.sp,
    ),
    label = TextStyle(
        fontFamily = UiFamily, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.01.em,
    ),
    eyebrow = TextStyle(
        fontFamily = UiFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.14.em,
    ),
    numericLarge = TextStyle(
        fontFamily = NumericFamily, fontWeight = FontWeight.Medium,
        fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.02).em,
    ),
    numeric = TextStyle(
        fontFamily = NumericFamily, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 20.sp,
    ),
    numericSmall = TextStyle(
        fontFamily = NumericFamily, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp,
    ),
    flap = TextStyle(
        fontFamily = NumericFamily, fontWeight = FontWeight.Bold,
        fontSize = 22.sp, lineHeight = 22.sp, lineHeightStyle = TrimBoth,
    ),
)

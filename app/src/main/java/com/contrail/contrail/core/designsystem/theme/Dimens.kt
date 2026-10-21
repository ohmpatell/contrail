package com.contrail.contrail.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class ContrailSpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 28.dp,
    val xxxl: Dp = 40.dp,
    /** Horizontal page gutter. */
    val gutter: Dp = 20.dp,
    /** Height reserved at the bottom of every tab for the floating nav bar. */
    val navBarClearance: Dp = 104.dp,
)

@Immutable
data class ContrailShapes(
    val chip: RoundedCornerShape = RoundedCornerShape(50),
    val small: RoundedCornerShape = RoundedCornerShape(12.dp),
    val medium: RoundedCornerShape = RoundedCornerShape(20.dp),
    val large: RoundedCornerShape = RoundedCornerShape(28.dp),
    val sheet: RoundedCornerShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    val flapTile: RoundedCornerShape = RoundedCornerShape(5.dp),
)

@Immutable
data class ContrailSizes(
    val hairline: Dp = 1.dp,
    val iconSmall: Dp = 16.dp,
    val icon: Dp = 22.dp,
    val iconLarge: Dp = 28.dp,
    val touchTarget: Dp = 48.dp,
    val navBarHeight: Dp = 68.dp,
    val glassBlur: Dp = 28.dp,
    val flapTileWidth: Dp = 22.dp,
    val flapTileHeight: Dp = 32.dp,
)

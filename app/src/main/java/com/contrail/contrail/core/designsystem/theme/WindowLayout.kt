package com.contrail.contrail.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How much room the window has. Tablets, unfolded foldables and phones in landscape are
 * [wide]: the tab bar becomes a rail on the left and screens lay out side by side.
 */
@Immutable
data class WindowLayout(val width: Dp, val height: Dp) {
    val wide: Boolean get() = width >= 840.dp || (width >= 600.dp && width > height)

    /** Room for big list-and-detail layouts (tablets, not phones on their side). */
    val roomy: Boolean get() = width >= 840.dp

    /** Reading width for phone-shaped pages on a big screen. */
    val readingWidth: Dp get() = 720.dp

    /** Space the floating nav rail takes on the left, margin included. */
    val railInset: Dp get() = if (wide) 104.dp else 0.dp

    companion object {
        val Phone = WindowLayout(411.dp, 891.dp)
    }
}

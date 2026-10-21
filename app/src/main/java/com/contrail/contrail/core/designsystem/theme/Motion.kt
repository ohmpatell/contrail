package com.contrail.contrail.core.designsystem.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable

/**
 * Motion tokens. Every animated value in the app goes through these so the
 * system "remove animations" setting turns all of them into instant snaps.
 */
@Immutable
data class ContrailMotion(val reduced: Boolean) {

    /** Quick, decisive UI response: chips, toggles, selection indicators. */
    fun <T> snappy(): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = 0.82f, stiffness = 720f)

    /** Larger surfaces: sheets, cards, panels. */
    fun <T> gentle(): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow)

    /** Playful overshoot for things that "land": the nav indicator, badges. */
    fun <T> bouncy(): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = 0.62f, stiffness = 420f)

    /** Fades and colour changes. */
    fun <T> fade(durationMillis: Int = 260): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(durationMillis)

    /** One split-flap step. */
    val flapStepMillis: Int get() = if (reduced) 0 else 70

    /** Delay between neighbouring split-flap characters. */
    val flapStaggerMillis: Int get() = if (reduced) 0 else 38

    /** Long ambient loops (aurora drift, pulses). Zero when reduced. */
    val ambientEnabled: Boolean get() = !reduced

    fun <T> orSnap(spec: AnimationSpec<T>): AnimationSpec<T> = if (reduced) snap() else spec

    companion object {
        /** True when the user has turned animations off in Developer options or Accessibility. */
        fun isReducedMotion(context: Context): Boolean = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

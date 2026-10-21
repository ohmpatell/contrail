package com.contrail.contrail.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import com.contrail.contrail.core.designsystem.theme.ContrailMotion
import kotlinx.serialization.Serializable

/** The tab shell: always the bottom of the back stack. */
@Serializable
data object ShellKey : NavKey

@Serializable
data class AircraftKey(val hex: String) : NavKey

@Serializable
data class AirportKey(val icao: String) : NavKey

@Serializable
data object SettingsKey : NavKey

/** How any screen opens a detail page or goes back. */
interface Navigator {
    fun openAircraft(hex: String)
    fun openAirport(icao: String)
    fun openSettings()
    fun back()
}

val LocalNavigator = staticCompositionLocalOf<Navigator> {
    object : Navigator {
        override fun openAircraft(hex: String) = Unit
        override fun openAirport(icao: String) = Unit
        override fun openSettings() = Unit
        override fun back() = Unit
    }
}

/**
 * Detail pages slide in *over* the shell instead of replacing it, so the radar keeps its
 * live map, camera and polling while you look at an aircraft or airport.
 */
class DetailOverlayStrategy<T : Any>(private val motion: ContrailMotion) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        if (entries.size < 2) return null
        val top = entries.last()
        if (top.metadata[DETAIL] != true) return null
        return DetailScene(top, entries.dropLast(1), motion)
    }

    companion object {
        const val DETAIL = "contrail.detail"
        val detail = mapOf(DETAIL to true)
    }
}

private class DetailScene<T : Any>(
    private val entry: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
    private val motion: ContrailMotion,
) : OverlayScene<T> {
    override val key: Any = entry.contentKey
    override val entries: List<NavEntry<T>> = listOf(entry)
    override val overlaidEntries: List<NavEntry<T>> = previousEntries

    /** 0 = off-screen to the right, 1 = in place. */
    private val progress = Animatable(if (motion.reduced) 1f else 0f)

    override val content: @Composable () -> Unit = {
        LaunchedEffect(Unit) { progress.animateTo(1f, motion.gentle()) }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = progress.value
                    translationX = (1f - p) * size.width * 0.35f
                    alpha = p.coerceIn(0f, 1f)
                },
        ) { entry.Content() }
    }

    override suspend fun onRemove() {
        progress.animateTo(0f, motion.snappy())
    }

    override fun equals(other: Any?): Boolean = other is DetailScene<*> && other.key == key && other.previousEntries == previousEntries
    override fun hashCode(): Int = key.hashCode() * 31 + previousEntries.hashCode()
}

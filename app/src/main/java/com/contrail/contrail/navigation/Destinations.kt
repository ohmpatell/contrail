package com.contrail.contrail.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
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

@Serializable
data class TripKey(val id: Long) : NavKey

@Serializable
data class FlightKey(val segmentId: Long) : NavKey

@Serializable
data object ImportKey : NavKey

/** Log or edit a lounge visit. [phase] is "Departure" or "Arrival"; [visitId] 0 means a new visit. */
@Serializable
data class LoungeKey(val segmentId: Long, val phase: String, val visitId: Long = 0) : NavKey

@Serializable
data class YearInReviewKey(val year: Int) : NavKey

/** Add a flight by hand; [segmentId] other than 0 edits that flight. */
@Serializable
data class AddFlightKey(val segmentId: Long = 0) : NavKey

@Serializable
data object LogImportKey : NavKey

/** Every flight you've taken, newest first. */
@Serializable
data object LogbookKey : NavKey

@Serializable
data object StampBookKey : NavKey

@Serializable
data object SkyViewKey : NavKey

@Serializable
data object ShareCardKey : NavKey

/** Cabin mode for one of your flights. */
@Serializable
data class CabinKey(val segmentId: Long) : NavKey

/** Every moment, as a timeline. */
@Serializable
data object MomentsKey : NavKey

/** Write a moment ([momentId] empty) or edit one, optionally starting from a flight or a plane. */
@Serializable
data class AddMomentKey(val momentId: String = "", val segmentId: Long = 0, val registration: String = "") : NavKey

/** My Hangar: your pinned planes. */
@Serializable
data object HangarKey : NavKey

/** One pinned plane: its story, photo and last week of flights. */
@Serializable
data class HangarPlaneKey(val registration: String) : NavKey

/** How any screen opens a detail page or goes back. */
interface Navigator {
    fun openAircraft(hex: String)
    fun openAirport(icao: String)
    fun openSettings()
    fun openTrip(id: Long)
    fun openFlight(segmentId: Long)
    fun openImport()
    fun openLounge(segmentId: Long, phase: String, visitId: Long = 0)
    fun openYearInReview(year: Int)
    fun openAddFlight(segmentId: Long = 0)
    fun openLogImport()
    fun openLogbook()
    fun openStampBook()
    fun openSkyView()
    fun openShareCard()
    fun openHangar()
    fun openMoments()
    fun openCabin(segmentId: Long)
    fun openAddMoment(momentId: String = "", segmentId: Long = 0, registration: String = "")
    fun openHangarPlane(registration: String)
    /** Closes every page, switches to the radar and flies to the plane. */
    fun showOnRadar(hex: String?, lat: Double, lon: Double)
    fun back()
}

val LocalNavigator = staticCompositionLocalOf<Navigator> {
    object : Navigator {
        override fun openAircraft(hex: String) = Unit
        override fun openAirport(icao: String) = Unit
        override fun openSettings() = Unit
        override fun openTrip(id: Long) = Unit
        override fun openFlight(segmentId: Long) = Unit
        override fun openImport() = Unit
        override fun openLounge(segmentId: Long, phase: String, visitId: Long) = Unit
        override fun openYearInReview(year: Int) = Unit
        override fun openAddFlight(segmentId: Long) = Unit
        override fun openLogImport() = Unit
        override fun openLogbook() = Unit
        override fun openStampBook() = Unit
        override fun openSkyView() = Unit
        override fun openShareCard() = Unit
        override fun openHangar() = Unit
        override fun openMoments() = Unit
        override fun openCabin(segmentId: Long) = Unit
        override fun openAddMoment(momentId: String, segmentId: Long, registration: String) = Unit
        override fun openHangarPlane(registration: String) = Unit
        override fun showOnRadar(hex: String?, lat: Double, lon: Double) = Unit
        override fun back() = Unit
    }
}

/**
 * Detail pages slide in *over* the shell instead of replacing it, so the radar keeps its
 * live map, camera and polling while you look at an aircraft or airport.
 *
 * One scene, under one key, draws the shell and every detail page above it, newest on top.
 * With an overlay scene per page, NavDisplay (navigation3 1.2.0) kept the existing overlay when
 * a page was opened from another page and drew the new one underneath, so Logbook → flight and
 * flight → Edit opened pages nobody could see.
 */
class DetailOverlayStrategy<T : Any>(private val motion: ContrailMotion) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        if (entries.isEmpty() || entries.first().metadata[DETAIL] == true) return null
        return StackScene(entries, motion)
    }

    companion object {
        const val DETAIL = "contrail.detail"
        val detail = mapOf(DETAIL to true)
    }
}

/** One key for every back stack, so NavDisplay never swaps scenes: pages animate inside [StackScene]. */
private object StackKey

private class StackScene<T : Any>(
    override val entries: List<NavEntry<T>>,
    private val motion: ContrailMotion,
) : Scene<T> {
    override val key: Any = StackKey
    override val previousEntries: List<NavEntry<T>> = entries.dropLast(1)

    override val content: @Composable () -> Unit = {
        val base = entries.first()
        Box(Modifier.fillMaxSize()) {
            key(base.contentKey) { base.Content() }
            DetailStack(entries.drop(1), motion)
        }
    }

    // Same back stack, same scene; a new stack is a new scene (same key, so no transition).
    override fun equals(other: Any?): Boolean = other is StackScene<*> && other.entries == entries
    override fun hashCode(): Int = entries.hashCode()
}

/** Pages that left the back stack but are still sliding out. Plain state: changes are announced through [tick]. */
private class StackMemory<T : Any> {
    var last: List<NavEntry<T>> = emptyList()
    val leaving = mutableListOf<NavEntry<T>>()
    val tick = mutableIntStateOf(0)
}

/**
 * Draws the detail pages bottom to top. Only the top page and the one under it are composed
 * (their state and view models are kept by the entry decorators while they stay in the back
 * stack); a page that was just popped keeps drawing on top until it has slid away.
 */
@Composable
private fun <T : Any> DetailStack(details: List<NavEntry<T>>, motion: ContrailMotion) {
    val memory = remember { StackMemory<T>() }
    memory.tick.intValue // re-read so a finished exit redraws
    val current = details.map { it.contentKey }.toSet()
    memory.last.filter { it.contentKey !in current && memory.leaving.none { l -> l.contentKey == it.contentKey } }.forEach { memory.leaving += it }
    memory.leaving.removeAll { it.contentKey in current }
    memory.last = details

    val visible = details.takeLast(2)
    Box(Modifier.fillMaxSize()) {
        visible.forEach { entry ->
            key(entry.contentKey) {
                DetailPage(entry, leaving = false, motion = motion, onGone = {})
            }
        }
        memory.leaving.toList().forEach { entry ->
            key(entry.contentKey) {
                DetailPage(entry, leaving = true, motion = motion) {
                    memory.leaving.removeAll { it.contentKey == entry.contentKey }
                    memory.tick.intValue++
                }
            }
        }
    }
}

@Composable
private fun <T : Any> DetailPage(entry: NavEntry<T>, leaving: Boolean, motion: ContrailMotion, onGone: () -> Unit) {
    /** 0 = off-screen to the right, 1 = in place. A page leaving starts where it is. */
    val progress = remember { Animatable(if (motion.reduced || leaving) 1f else 0f) }
    LaunchedEffect(leaving) {
        if (leaving) {
            progress.animateTo(0f, motion.snappy())
            onGone()
        } else {
            progress.animateTo(1f, motion.gentle())
        }
    }
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

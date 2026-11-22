package com.contrail.contrail.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** Lets a tab ask the shell to get out of the way, e.g. while the radar's aircraft sheet is up. */
@Stable
class ShellChrome {
    var navBarHidden by mutableStateOf(false)
}

val LocalShellChrome = staticCompositionLocalOf { ShellChrome() }

/** Ask the shell to switch tabs from outside Compose: a notification or a widget tap. */
/** A flight page asked for from outside the app (the Live Update, a widget). */
object FlightRequests {
    val requested = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)
}

/** A plane to show on the radar, asked for from another page (My Hangar). */
data class RadarFocus(val hex: String?, val lat: Double, val lon: Double)

object RadarRequests {
    val focus = kotlinx.coroutines.flow.MutableStateFlow<RadarFocus?>(null)
}

/** A pinned plane's page asked for from a Hangar alert. */
object HangarRequests {
    val requested = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
}

object TabRequests {
    val requested = kotlinx.coroutines.flow.MutableStateFlow<TopLevelDestination?>(null)

    fun request(name: String?) {
        TopLevelDestination.entries.firstOrNull { it.name == name }?.let { requested.value = it }
    }
}

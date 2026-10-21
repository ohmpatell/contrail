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

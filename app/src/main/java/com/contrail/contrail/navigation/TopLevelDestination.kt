package com.contrail.contrail.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.contrail.contrail.core.designsystem.icon.ContrailIcons

/** The five tabs, in tab-bar order. */
enum class TopLevelDestination(val label: String, val icon: () -> ImageVector) {
    Trips("Trips", { ContrailIcons.Trips }),
    Radar("Radar", { ContrailIcons.Radar }),
    Sky("Sky", { ContrailIcons.Sky }),
    Airports("Airports", { ContrailIcons.Airports }),
    Passport("Passport", { ContrailIcons.Passport }),
    ;

    companion object {
        /** Radar is the start tab until Trips has real content (see DECISIONS.md). */
        val Start = Radar
    }
}

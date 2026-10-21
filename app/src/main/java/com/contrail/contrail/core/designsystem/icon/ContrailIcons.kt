package com.contrail.contrail.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Contrail's own icon set: 24-unit grid, 1.6 stroke, round caps. Hand-drawn so the
 * app never falls back to the stock Material look.
 */
object ContrailIcons {

    val Trips: ImageVector by lazy {
        strokeIcon(
            "Trips",
            // Boarding pass with tear notches and a perforation line.
            "M4 6.5h16a1.5 1.5 0 0 1 1.5 1.5v2.2a1.8 1.8 0 0 0 0 3.6V16a1.5 1.5 0 0 1-1.5 1.5H4A1.5 1.5 0 0 1 2.5 16v-2.2a1.8 1.8 0 0 0 0-3.6V8A1.5 1.5 0 0 1 4 6.5z",
            "M15.5 7v1.2M15.5 11.4v1.2M15.5 15.8V17",
            "M6 12.6l2.6-.9 1.7-2.3.9.3-.7 1.7 1.9-.6.6-.7.6.2-.3 1.1",
        )
    }

    val Radar: ImageVector by lazy {
        strokeIcon(
            "Radar",
            "M12 3a9 9 0 1 0 9 9",
            "M12 7a5 5 0 1 0 5 5",
            "M12 12l6.4-6.4",
            filled = listOf("M12 10.6a1.4 1.4 0 1 1 0 2.8 1.4 1.4 0 0 1 0-2.8z", "M17.4 14.6a1.1 1.1 0 1 1 0 2.2 1.1 1.1 0 0 1 0-2.2z"),
        )
    }

    val Sky: ImageVector by lazy {
        strokeIcon(
            "Sky",
            // Binoculars: what's overhead, worth looking up for.
            "M7 12a3.6 3.6 0 1 1 0 7.2A3.6 3.6 0 0 1 7 12zM17 12a3.6 3.6 0 1 1 0 7.2 3.6 3.6 0 0 1 0-7.2z",
            "M3.4 15.6V8.2c0-1.3.9-2.3 2.2-2.3h1.6c1.2 0 2.2 1 2.2 2.3v5.2M20.6 15.6V8.2c0-1.3-.9-2.3-2.2-2.3h-1.6c-1.2 0-2.2 1-2.2 2.3v5.2",
            "M9.4 10.6h5.2",
            "M6.4 4.4V3.2M17.6 4.4V3.2",
        )
    }

    val Passport: ImageVector by lazy {
        strokeIcon(
            "Passport",
            "M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18z",
            "M3.6 9h16.8M3.6 15h16.8",
            "M12 3c2.4 2.5 3.6 5.5 3.6 9s-1.2 6.5-3.6 9c-2.4-2.5-3.6-5.5-3.6-9s1.2-6.5 3.6-9z",
        )
    }

    val Logbook: ImageVector by lazy {
        strokeIcon(
            "Logbook",
            "M4.5 8h2.6l1.6-2.2h6.6L16.9 8h2.6A1.5 1.5 0 0 1 21 9.5v8a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 17.5v-8A1.5 1.5 0 0 1 4.5 8z",
            "M12 10a3.5 3.5 0 1 1 0 7 3.5 3.5 0 0 1 0-7z",
        )
    }

    val Airports: ImageVector by lazy {
        strokeIcon(
            "Airports",
            // Control tower: cab, shaft, base.
            "M7.5 5.5h9l-1.2 4.5H8.7z",
            "M10 10v8.5M14 10v8.5",
            "M4 21h16M8 21l1.2-2.5h5.6L16 21",
            "M12 3.2v2.3",
        )
    }

    val Settings: ImageVector by lazy {
        strokeIcon(
            "Settings",
            "M12 9a3 3 0 1 1 0 6 3 3 0 0 1 0-6z",
            "M12 2.8l1.6 2.3 2.7-.7.8 2.7 2.6 1-.6 2.7L21 12.3l-1.9 2 .6 2.7-2.6 1-.8 2.7-2.7-.7L12 22.2l-1.6-2.2-2.7.7-.8-2.7-2.6-1 .6-2.7L3 12.3l1.9-1.6-.6-2.7 2.6-1 .8-2.7 2.7.7z",
        )
    }

    val Search: ImageVector by lazy { strokeIcon("Search", "M10.5 4a6.5 6.5 0 1 1 0 13 6.5 6.5 0 0 1 0-13z", "M15.4 15.4L20 20") }

    val Star: ImageVector by lazy {
        strokeIcon("Star", "M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1 5.8L12 16.8l-5.2 2.8 1-5.8-4.3-4.1 5.9-.8z")
    }

    val StarFilled: ImageVector by lazy {
        strokeIcon("StarFilled", filled = listOf("M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1 5.8L12 16.8l-5.2 2.8 1-5.8-4.3-4.1 5.9-.8z"))
    }

    val Check: ImageVector by lazy { strokeIcon("Check", "M5 12.5l4.5 4.5L19 7.5") }

    val News: ImageVector by lazy {
        strokeIcon(
            "News",
            "M5 4.5h11.5a1.5 1.5 0 0 1 1.5 1.5v13.5H6.5A2 2 0 0 1 4.5 17.5V5a.5.5 0 0 1 .5-.5z",
            "M18 9h1.5a1 1 0 0 1 1 1v8a1.5 1.5 0 0 1-3 0",
            "M7.5 8h7.5M7.5 11.5h7.5M7.5 15h4.5",
        )
    }

    val Back: ImageVector by lazy { strokeIcon("Back", "M14.5 5.5L8 12l6.5 6.5") }

    val Clock: ImageVector by lazy { strokeIcon("Clock", "M12 3.5a8.5 8.5 0 1 1 0 17 8.5 8.5 0 0 1 0-17z", "M12 7.5V12l3 2") }

    val Headset: ImageVector by lazy {
        strokeIcon("Headset", "M4.5 14v-2a7.5 7.5 0 0 1 15 0v2", "M4.5 14h2.5v5H5.5a1 1 0 0 1-1-1zM19.5 14H17v5h1.5a1 1 0 0 0 1-1z")
    }

    val Filter: ImageVector by lazy {
        strokeIcon(
            "Filter",
            "M4 7h9M17 7h3M4 17h3M11 17h9",
            "M15 5a2 2 0 1 1 0 4 2 2 0 0 1 0-4zM9 15a2 2 0 1 1 0 4 2 2 0 0 1 0-4z",
        )
    }

    val Layers: ImageVector by lazy {
        strokeIcon(
            "Layers",
            "M12 4l8.5 4.5L12 13 3.5 8.5z",
            "M3.5 12.5L12 17l8.5-4.5",
            "M3.5 16.2L12 20.7l8.5-4.5",
        )
    }

    val Locate: ImageVector by lazy {
        strokeIcon(
            "Locate",
            "M12 5.5a6.5 6.5 0 1 1 0 13 6.5 6.5 0 0 1 0-13z",
            "M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3",
            filled = listOf("M12 10a2 2 0 1 1 0 4 2 2 0 0 1 0-4z"),
        )
    }

    val Follow: ImageVector by lazy {
        strokeIcon(
            "Follow",
            "M4 8V5.5A1.5 1.5 0 0 1 5.5 4H8M16 4h2.5A1.5 1.5 0 0 1 20 5.5V8M20 16v2.5a1.5 1.5 0 0 1-1.5 1.5H16M8 20H5.5A1.5 1.5 0 0 1 4 18.5V16",
            filled = listOf(
                "M12 7.2c.5 0 .8.5.8 1.2v2.4l3.8 2.2v1l-3.8-1.1v2.3l1.1.9v.8l-1.9-.4-1.9.4v-.8l1.1-.9v-2.3l-3.8 1.1v-1l3.8-2.2V8.4c0-.7.3-1.2.8-1.2z",
            ),
        )
    }

    val Close: ImageVector by lazy { strokeIcon("Close", "M6.5 6.5l11 11M17.5 6.5l-11 11") }

    val ChevronRight: ImageVector by lazy { strokeIcon("ChevronRight", "M9.5 5.5L16 12l-6.5 6.5") }

    val ArrowRight: ImageVector by lazy { strokeIcon("ArrowRight", "M4.5 12h15M14 6.5l5.5 5.5-5.5 5.5") }

    val ExternalLink: ImageVector by lazy {
        strokeIcon("ExternalLink", "M14 4.5h5.5V10M19.5 4.5L11 13", "M17 14v4.5a1 1 0 0 1-1 1H5.5a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1H10")
    }

    val Sparkle: ImageVector by lazy {
        strokeIcon("Sparkle", "M12 3.5l1.9 5.2 5.1 1.8-5.1 1.9L12 17.5l-1.9-5.1L5 10.5l5.1-1.8z", "M18.5 16.5l.7 1.8 1.8.7-1.8.7-.7 1.8-.7-1.8-1.8-.7 1.8-.7z")
    }

    val Shield: ImageVector by lazy {
        strokeIcon("Shield", "M12 3l7 2.8v5.4c0 4.4-3 8-7 9.8-4-1.8-7-5.4-7-9.8V5.8z", "M9 12l2 2 4-4.2")
    }

    val Alert: ImageVector by lazy {
        strokeIcon("Alert", "M12 3.8l9 15.6H3z", "M12 9.5v4.4", filled = listOf("M12 15.6a1.1 1.1 0 1 1 0 2.2 1.1 1.1 0 0 1 0-2.2z"))
    }

    val Signal: ImageVector by lazy {
        strokeIcon("Signal", "M5 18.5a10 10 0 0 1 0-13M19 5.5a10 10 0 0 1 0 13M8.2 15.4a5.5 5.5 0 0 1 0-6.8M15.8 8.6a5.5 5.5 0 0 1 0 6.8", filled = listOf("M12 10.4a1.6 1.6 0 1 1 0 3.2 1.6 1.6 0 0 1 0-3.2z"))
    }

    val Plane: ImageVector by lazy {
        ImageVector.Builder("Plane", 24.dp, 24.dp, 24f, 24f).apply {
            addPath(
                pathData = addPathNodes(
                    "M12 1.8c.9 0 1.5 1 1.5 2.4v4.9l8 4.7v2.1l-8-2.4v4.9l2.4 1.8v1.7L12 21.1l-3.9.8v-1.7l2.4-1.8v-4.9l-8 2.4v-2.1l8-4.7V4.2c0-1.4.6-2.4 1.5-2.4z",
                ),
                fill = SolidColor(Color.White),
            )
        }.build()
    }

    private fun strokeIcon(
        name: String,
        vararg strokes: String,
        filled: List<String> = emptyList(),
    ): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        strokes.forEach { d ->
            addPath(
                pathData = addPathNodes(d),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        filled.forEach { d -> addPath(pathData = addPathNodes(d), fill = SolidColor(Color.White)) }
    }.build()
}

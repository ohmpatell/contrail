package com.contrail.contrail.core.designsystem.livery

import androidx.compose.ui.graphics.Color
import com.contrail.contrail.core.data.reference.AircraftTypes
import com.contrail.contrail.core.data.reference.Silhouette

/** Paint for each zone of a side-profile render. */
data class Livery(
    val fuselage: Color,
    val belly: Color,
    val cheatline: Color?,
    val tail: Color,
    val engines: Color,
    val windows: Color = Color(0xFF1B2333),
    /** Draw the airline's tail art (logostream `tail` variant) over the fin. */
    val useTailArt: Boolean = true,
)

/**
 * Livery config. Hand-tuned schemes for the airlines you fly most; everyone else gets a
 * clean white scheme tinted with their brand colour.
 */
object Liveries {
    private val curated = mapOf(
        // Etihad "Facets of Abu Dhabi": sand fuselage, gold engines, geometric tail.
        "ETD" to Livery(Color(0xFFE9DFCD), Color(0xFFD8CBB2), null, Color(0xFF8C6A3D), Color(0xFFB08A4E)),
        // Cathay Pacific: white fuselage, jade brushwing on the tail, grey belly.
        "CPA" to Livery(Color(0xFFF4F6F5), Color(0xFFB7BDBA), Color(0xFF0A6E5E), Color(0xFF0A6E5E), Color(0xFFD9DEDC)),
        // Air Canada: white over black belly, black tail with the red maple leaf.
        "ACA" to Livery(Color(0xFFF5F6F7), Color(0xFF1B1D21), null, Color(0xFF15171A), Color(0xFF15171A)),
        "ROU" to Livery(Color(0xFFF5F6F7), Color(0xFF1B1D21), null, Color(0xFFB21E35), Color(0xFF2A2D33)),
        "WJA" to Livery(Color(0xFFF7F8F9), Color(0xFF0D2F4F), Color(0xFF00A3AD), Color(0xFF0D2F4F), Color(0xFF0D2F4F)),
        "UAE" to Livery(Color(0xFFF7F7F6), Color(0xFFE6E4E0), Color(0xFFD71921), Color(0xFFF2F2F0), Color(0xFFDADAD8)),
        "QTR" to Livery(Color(0xFFF2F1EF), Color(0xFFE0DEDB), null, Color(0xFF5C0632), Color(0xFF5C0632)),
        "BAW" to Livery(Color(0xFFF5F6F8), Color(0xFF1F3A6B), Color(0xFFC8102E), Color(0xFFF5F6F8), Color(0xFF1F3A6B)),
        "DLH" to Livery(Color(0xFFF7F7F7), Color(0xFFE5E5E5), null, Color(0xFF05164D), Color(0xFF05164D)),
    )

    fun forAirline(icao: String?, brandHex: String? = null, accentArgb: Long? = null): Livery {
        icao?.uppercase()?.let { curated[it] }?.let { return it }
        val brand = brandHex?.let(::parseHex) ?: accentArgb?.let { Color(it) } ?: Color(0xFF5C6B8A)
        return Livery(
            fuselage = Color(0xFFF4F6F8),
            belly = Color(0xFFD9DEE5),
            cheatline = brand,
            tail = brand,
            engines = Color(0xFFC9CFD8),
            useTailArt = icao != null,
        )
    }

    fun parseHex(hex: String): Color? = runCatching {
        val h = hex.trim().removePrefix("#")
        val v = h.toLong(16)
        if (h.length == 6) Color(0xFF000000 or v) else Color(v)
    }.getOrNull()
}

/** Shape parameters for one consistent side-profile style. Lengths are fractions of the canvas width. */
data class Profile(
    val length: Float,
    val height: Float,
    val noseLength: Float,
    val tailcone: Float,
    val hump: Float = 0f,
    val doubleDeck: Boolean = false,
    val finHeight: Float,
    val finChord: Float,
    val tTail: Boolean = false,
    val engines: EngineLayout,
    val engineSize: Float,
    val highWing: Boolean = false,
    val windows: Boolean = true,
    val propeller: Boolean = false,
    val smoothNose: Boolean = false,
)

enum class EngineLayout { UnderWing, UnderWingQuad, Rear, Tail, Props, None }

object Profiles {
    fun forType(typeCode: String?, category: String? = null): Profile {
        val code = typeCode?.uppercase()
        return when (AircraftTypes.silhouetteFor(code, category)) {
            Silhouette.Jumbo -> Profile(0.92f, 0.17f, 0.07f, 0.16f, doubleDeck = true, finHeight = 0.24f, finChord = 0.17f, engines = EngineLayout.UnderWingQuad, engineSize = 0.07f)
            Silhouette.Quad -> if (code?.startsWith("B74") == true) {
                Profile(0.94f, 0.14f, 0.08f, 0.15f, hump = if (code == "B748") 0.34f else 0.26f, finHeight = 0.24f, finChord = 0.16f, engines = EngineLayout.UnderWingQuad, engineSize = 0.065f)
            } else {
                Profile(0.95f, 0.12f, 0.08f, 0.16f, finHeight = 0.22f, finChord = 0.15f, engines = EngineLayout.UnderWingQuad, engineSize = 0.055f)
            }
            Silhouette.WideTri -> Profile(0.92f, 0.13f, 0.08f, 0.15f, finHeight = 0.21f, finChord = 0.14f, engines = EngineLayout.Tail, engineSize = 0.07f)
            Silhouette.Widebody -> Profile(
                if (code?.startsWith("B77") == true || code == "A35K") 0.95f else 0.9f, 0.13f, 0.08f, 0.15f,
                finHeight = 0.22f, finChord = 0.15f, engines = EngineLayout.UnderWing,
                engineSize = if (code?.startsWith("B77") == true) 0.085f else 0.075f,
                smoothNose = code?.startsWith("B78") == true || code?.startsWith("A35") == true,
            )
            Silhouette.Narrowbody, Silhouette.Generic -> Profile(0.9f, 0.115f, 0.08f, 0.15f, finHeight = 0.22f, finChord = 0.15f, engines = EngineLayout.UnderWing, engineSize = 0.07f)
            Silhouette.Regional -> Profile(0.86f, 0.115f, 0.08f, 0.15f, finHeight = 0.23f, finChord = 0.15f, engines = EngineLayout.UnderWing, engineSize = 0.065f)
            Silhouette.RearJet -> Profile(0.88f, 0.105f, 0.08f, 0.14f, finHeight = 0.24f, finChord = 0.12f, tTail = true, engines = EngineLayout.Rear, engineSize = 0.06f)
            Silhouette.Bizjet -> Profile(0.8f, 0.1f, 0.1f, 0.16f, finHeight = 0.22f, finChord = 0.12f, tTail = true, engines = EngineLayout.Rear, engineSize = 0.055f, smoothNose = true)
            Silhouette.TurbopropQuad -> Profile(0.88f, 0.14f, 0.07f, 0.18f, finHeight = 0.28f, finChord = 0.15f, engines = EngineLayout.Props, engineSize = 0.06f, highWing = true, propeller = true)
            Silhouette.Turboprop -> Profile(0.86f, 0.115f, 0.07f, 0.16f, finHeight = 0.26f, finChord = 0.14f, tTail = code?.startsWith("DH8") == true, engines = EngineLayout.Props, engineSize = 0.06f, highWing = true, propeller = true)
            Silhouette.TurbopropSingle, Silhouette.Light, Silhouette.LightTwin, Silhouette.Glider ->
                Profile(0.7f, 0.12f, 0.1f, 0.22f, finHeight = 0.2f, finChord = 0.13f, engines = EngineLayout.Props, engineSize = 0.05f, highWing = true, windows = false, propeller = true)
            Silhouette.Fighter -> Profile(0.78f, 0.08f, 0.16f, 0.12f, finHeight = 0.2f, finChord = 0.14f, engines = EngineLayout.None, engineSize = 0f, windows = false, smoothNose = true)
            Silhouette.Helicopter -> Profile(0.62f, 0.2f, 0.1f, 0.3f, finHeight = 0.14f, finChord = 0.08f, engines = EngineLayout.None, engineSize = 0f, windows = false)
        }
    }
}

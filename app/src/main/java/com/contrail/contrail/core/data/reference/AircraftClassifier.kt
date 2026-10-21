package com.contrail.contrail.core.data.reference

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStream

/** Which radar icon represents an aircraft: shape, engine count and layout. */
enum class Silhouette(
    /** Icon size in dp at icon-size 1. Encodes the class's relative size on the map. */
    val baseSizeDp: Float,
) {
    Jumbo(41f),          // A380: giant four-engine widebody
    Quad(37f),           // 747, A340, An-124: four-engine widebody
    WideTri(35f),        // MD-11, DC-10, L-1011: two wing engines plus a tail engine
    Widebody(35f),       // 777, A350, 787, A330, 767: widebody twin
    Narrowbody(29f),     // A320 family, 737, 757, A220: underwing twin
    RearJet(27f),        // CRJ, ERJ-145, MD-80, 717, Fokker 100: rear-engine twin, T-tail
    Regional(27f),       // E-Jets, Superjet: small underwing twin
    Bizjet(23f),         // Gulfstream, Citation, Global: slim rear-engine jet
    Fighter(22f),        // Fast jets
    TurbopropQuad(31f),  // C-130, A400M, Dash 7
    Turboprop(25f),      // Dash 8, ATR, Saab 340, King Air
    TurbopropSingle(21f),// Caravan, PC-12, TBM
    LightTwin(20f),      // Baron, Seneca, DA42
    Light(18f),          // Cessna 172, Cherokee, SR22
    Helicopter(22f),
    Glider(22f),
    Generic(28f),
}

/** One ICAO Doc 8643 entry: model name, descriptor (e.g. `L2J`), wake category (L/M/H/J). */
data class TypeDesignator(val name: String, val descriptor: String, val wake: String) {
    val kind: Char get() = descriptor.getOrElse(0) { 'L' }
    val engines: Int get() = descriptor.getOrNull(1)?.digitToIntOrNull() ?: 2
    val propulsion: Char get() = descriptor.getOrElse(2) { 'J' }
}

/**
 * Every ICAO type designator, from the bundled ICAO Doc 8643 list (via the Mictronics
 * readsb database). Loaded lazily from whatever source the app installs.
 */
object TypeDesignators {
    @Volatile private var source: (() -> InputStream)? = null
    private val table: Map<String, TypeDesignator> by lazy { load() }

    /** Called once by the app (assets) or by tests (the same file on disk). */
    fun install(open: () -> InputStream) {
        source = open
    }

    /** `ZZZZ` is ICAO's "no designator assigned", which tells us nothing. */
    fun lookup(code: String?): TypeDesignator? =
        code?.uppercase()?.takeUnless { it == "ZZZZ" }?.let { table[it] }

    val size: Int get() = table.size

    private fun load(): Map<String, TypeDesignator> {
        val open = source ?: return emptyMap()
        val root = open().bufferedReader().use { Json.parseToJsonElement(it.readText()) } as JsonObject
        return root.mapValues { (_, v) ->
            val a = v as JsonArray
            TypeDesignator(a[0].jsonPrimitive.content, a[1].jsonPrimitive.content, a[2].jsonPrimitive.content)
        }
    }
}

/** Maps a type code (and ADS-B emitter category) to a radar icon class and relative size. */
object AircraftClassifier {

    private val fighter = Regex(
        """\bF-\d|\bF/A-|\bMIG-|SUKHOI SU-|TYPHOON|RAFALE|GRIPEN|MIRAGE|TORNADO|HARRIER|\bT-38|\bHAWK\b|ALPHA JET|\bL-39|\bA-10|\bF-35|\bF-22|LIGHTNING II|\bT-7\b""",
    )
    private val bizjet = Regex(
        """CITATION|GULFSTREAM|CHALLENGER|GLOBAL|LEARJET|FALCON|PHENOM|PRAETOR|LEGACY|HAWKER|HONDAJET|PREMIER|ECLIPSE|VISION|PC-24|WESTWIND|ASTRA|SABRELINER|JETSTAR|BEECHJET|LINEAGE|\bG-?[1-8]\d0\b|\bCJ\d""",
    )
    private val rearEngine = Regex(
        """REGIONAL JET|\bCRJ|ERJ-1[34]\d|\bMD-8|\bMD-9|\bDC-9|\b717\b|FOKKER 70|FOKKER 100|\bF-28|ARJ21|\bTU-134|\bYAK-4[02]|\bIL-62|\bVC-10|\bBAC 1-11""",
    )
    private val eJet = Regex("""ERJ-1[79]\d|ERJ-190|E-?19\d|E-?17\d|SUPERJET|\bE2\b""")

    /** Relative size within a class, from wingspan, for types common enough to matter. */
    private val scale = mapOf(
        "A388" to 1.0f, "B748" to 1.0f, "B744" to 0.95f, "A346" to 0.98f, "A343" to 0.93f, "A124" to 1.05f,
        "B77W" to 1.05f, "B779" to 1.08f, "B778" to 1.08f, "B77L" to 1.02f, "B772" to 1.0f, "A35K" to 1.05f,
        "A359" to 1.03f, "B78X" to 0.98f, "B789" to 0.98f, "B788" to 0.95f, "A339" to 1.0f, "A333" to 0.98f,
        "A332" to 0.98f, "B764" to 0.92f, "B763" to 0.9f, "B762" to 0.88f, "A310" to 0.86f,
        "A321" to 1.04f, "A21N" to 1.04f, "B739" to 1.03f, "B39M" to 1.03f, "B3XM" to 1.04f, "B753" to 1.06f,
        "B752" to 1.05f, "A319" to 0.95f, "A19N" to 0.95f, "B737" to 0.95f, "B37M" to 0.95f, "B736" to 0.93f,
        "A318" to 0.9f, "BCS1" to 0.9f, "BCS3" to 0.94f, "E195" to 1.05f, "E295" to 1.08f, "E75L" to 0.97f,
        "E170" to 0.93f, "CRJ2" to 0.85f, "CRJ7" to 0.95f, "CRJ9" to 1.0f, "E145" to 0.9f, "E135" to 0.86f,
        "GLF6" to 1.1f, "GL7T" to 1.12f, "GLEX" to 1.08f, "C510" to 0.78f, "E50P" to 0.8f, "SF50" to 0.7f,
        "DH8D" to 1.12f, "AT76" to 1.1f, "AT72" to 1.1f, "AT45" to 1.0f, "DH8A" to 0.92f, "B190" to 0.8f,
    )

    fun scaleFor(typeCode: String?): Float = typeCode?.let { scale[it.uppercase()] } ?: 1f

    /** Curated table first, then the ICAO descriptor, then the emitter category. */
    fun classify(typeCode: String?, category: String?): Silhouette {
        AircraftTypes.byIcao(typeCode)?.let { return it.silhouette }
        TypeDesignators.lookup(typeCode)?.let { return fromDesignator(it) }
        return fromCategory(category)
    }

    fun fromDesignator(t: TypeDesignator): Silhouette {
        val name = t.name.uppercase()
        if (t.kind == 'H' || t.kind == 'G') return Silhouette.Helicopter
        if (t.kind == 'T') return Silhouette.Turboprop
        return when (t.propulsion) {
            'J' -> when {
                fighter.containsMatchIn(name) -> Silhouette.Fighter
                t.engines >= 4 -> if (t.wake == "J") Silhouette.Jumbo else Silhouette.Quad
                t.engines == 3 -> if (t.wake == "H") Silhouette.WideTri else Silhouette.Bizjet
                t.engines == 1 -> if (t.wake == "L") Silhouette.Bizjet else Silhouette.Fighter
                t.wake == "H" -> Silhouette.Widebody
                t.wake == "L" -> Silhouette.Bizjet
                bizjet.containsMatchIn(name) -> Silhouette.Bizjet
                rearEngine.containsMatchIn(name) -> Silhouette.RearJet
                eJet.containsMatchIn(name) -> Silhouette.Regional
                else -> Silhouette.Narrowbody
            }
            'T' -> when {
                t.engines >= 4 -> Silhouette.TurbopropQuad
                t.engines >= 2 -> Silhouette.Turboprop
                else -> Silhouette.TurbopropSingle
            }
            else -> when {
                name.contains("GLIDER") || t.wake == "-" -> Silhouette.Glider
                t.engines >= 2 -> Silhouette.LightTwin
                else -> Silhouette.Light
            }
        }
    }

    /** ADS-B emitter categories (DO-260B). */
    fun fromCategory(category: String?): Silhouette = when (category?.uppercase()) {
        "A1" -> Silhouette.Light
        "A2" -> Silhouette.Bizjet
        "A3", "A4" -> Silhouette.Narrowbody
        "A5" -> Silhouette.Widebody
        "A6" -> Silhouette.Fighter
        "A7" -> Silhouette.Helicopter
        "B1" -> Silhouette.Glider
        "B4", "B6" -> Silhouette.Light
        else -> Silhouette.Generic
    }

    /**
     * Surface vehicles and obstacles (C1–C5), and ground targets with no aircraft type, are
     * not aircraft and never belong on the radar.
     */
    fun isClutter(category: String?, typeCode: String?, onGround: Boolean): Boolean {
        val c = category?.uppercase()
        if (c != null && c.startsWith("C") && c != "C0") return true
        return onGround && typeCode.isNullOrBlank()
    }
}

package com.contrail.contrail.core.data.reference

/**
 * One airline's identity. [accentArgb] is the colour sampled from its livery and is the
 * glow a screen takes on when it is about this airline.
 */
data class Airline(
    val icao: String,
    val iata: String,
    val name: String,
    val accentArgb: Long,
    val cargo: Boolean = false,
)

/** Curated livery config. Extend freely; unknown airlines fall back to the default accent. */
object Airlines {
    private val all = listOf(
        Airline("ETD", "EY", "Etihad Airways", 0xFFD2AE6D),
        Airline("CPA", "CX", "Cathay Pacific", 0xFF2BB59A),
        Airline("ACA", "AC", "Air Canada", 0xFFF0384F),
        Airline("ROU", "RV", "Air Canada Rouge", 0xFFE0405A),
        Airline("JZA", "QK", "Jazz", 0xFFF0384F),
        Airline("WJA", "WS", "WestJet", 0xFF2CC1C4),
        Airline("WEN", "WR", "WestJet Encore", 0xFF2CC1C4),
        Airline("POE", "PD", "Porter Airlines", 0xFF6F8FE0),
        Airline("PTR", "PD", "Porter Airlines", 0xFF6F8FE0),
        Airline("ASP", "", "AirSprint", 0xFFB9C4DE),
        Airline("EJA", "1I", "NetJets", 0xFFB9C4DE),
        Airline("LXJ", "", "Flexjet", 0xFFB9C4DE),
        Airline("CGL", "", "Canadian North", 0xFFF0A23D),
        Airline("TSO", "", "Air Transat", 0xFF38C3F0),
        Airline("TSC", "TS", "Air Transat", 0xFF38C3F0),
        Airline("FLE", "F8", "Flair Airlines", 0xFFA6D650),
        Airline("SWG", "WG", "Sunwing", 0xFFFFB534),
        Airline("CJT", "W8", "Cargojet", 0xFFF05A5A, cargo = true),
        Airline("UAE", "EK", "Emirates", 0xFFF0434B),
        Airline("QTR", "QR", "Qatar Airways", 0xFFC2477A),
        Airline("SVA", "SV", "Saudia", 0xFF35B07B),
        Airline("BAW", "BA", "British Airways", 0xFF6B95E8),
        Airline("VIR", "VS", "Virgin Atlantic", 0xFFF03D62),
        Airline("DLH", "LH", "Lufthansa", 0xFFFFC533),
        Airline("AFR", "AF", "Air France", 0xFF6A8CF0),
        Airline("KLM", "KL", "KLM", 0xFF35B9F0),
        Airline("EIN", "EI", "Aer Lingus", 0xFF2FB49A),
        Airline("IBE", "IB", "Iberia", 0xFFF0424F),
        Airline("SWR", "LX", "Swiss", 0xFFF0424F),
        Airline("AUA", "OS", "Austrian", 0xFFF0424F),
        Airline("SAS", "SK", "SAS", 0xFF6F84F0),
        Airline("FIN", "AY", "Finnair", 0xFF6F8FF0),
        Airline("ICE", "FI", "Icelandair", 0xFFF2C33D),
        Airline("TAP", "TP", "TAP Air Portugal", 0xFF4FCB7F),
        Airline("THY", "TK", "Turkish Airlines", 0xFFF0424F),
        Airline("ELY", "LY", "El Al", 0xFF6F8FF0),
        Airline("ETH", "ET", "Ethiopian Airlines", 0xFF46C46B),
        Airline("AIC", "AI", "Air India", 0xFFE8475F),
        Airline("AXB", "IX", "Air India Express", 0xFFE8475F),
        Airline("IGO", "6E", "IndiGo", 0xFF4A62F0),
        Airline("PAL", "PR", "Philippine Airlines", 0xFF4A78E8),
        Airline("CEB", "5J", "Cebu Pacific", 0xFFFFC533),
        Airline("HKE", "UO", "HK Express", 0xFFC04FA8),
        Airline("SIA", "SQ", "Singapore Airlines", 0xFFFFB347),
        Airline("ANA", "NH", "All Nippon Airways", 0xFF5A8CF0),
        Airline("JAL", "JL", "Japan Airlines", 0xFFF04747),
        Airline("KAL", "KE", "Korean Air", 0xFF5CC6F0),
        Airline("EVA", "BR", "EVA Air", 0xFF4DBB8A),
        Airline("CAL", "CI", "China Airlines", 0xFFEC93A8),
        Airline("CCA", "CA", "Air China", 0xFFF0475A),
        Airline("CES", "MU", "China Eastern", 0xFF6F8AF0),
        Airline("CSN", "CZ", "China Southern", 0xFF38B6F0),
        Airline("QFA", "QF", "Qantas", 0xFFF04747),
        Airline("ANZ", "NZ", "Air New Zealand", 0xFF9FD8EE),
        Airline("UAL", "UA", "United Airlines", 0xFF5C8FFF),
        Airline("AAL", "AA", "American Airlines", 0xFF4DA6F0),
        Airline("DAL", "DL", "Delta Air Lines", 0xFFF0475E),
        Airline("SWA", "WN", "Southwest Airlines", 0xFFFFC233),
        Airline("JBU", "B6", "JetBlue", 0xFF5C8FF0),
        Airline("ASA", "AS", "Alaska Airlines", 0xFF5CC0DA),
        Airline("NKS", "NK", "Spirit Airlines", 0xFFFFE34D),
        Airline("FFT", "F9", "Frontier Airlines", 0xFF35C285),
        Airline("SKW", "OO", "SkyWest", 0xFF6F8FE0),
        Airline("RPA", "YX", "Republic Airways", 0xFF6F8FE0),
        Airline("ENY", "MQ", "Envoy", 0xFF4DA6F0),
        Airline("EDV", "9E", "Endeavor Air", 0xFFF0475E),
        Airline("JIA", "OH", "PSA Airlines", 0xFF4DA6F0),
        Airline("PDT", "PT", "Piedmont Airlines", 0xFF4DA6F0),
        Airline("AMX", "AM", "Aeroméxico", 0xFF6F8FF0),
        Airline("AVA", "AV", "Avianca", 0xFFF0473D),
        Airline("LAN", "LA", "LATAM", 0xFFF0477A),
        Airline("CMP", "CM", "Copa Airlines", 0xFF6F8FF0),
        Airline("RYR", "FR", "Ryanair", 0xFFF5D547),
        Airline("EZY", "U2", "easyJet", 0xFFFF7A2E),
        Airline("WZZ", "W6", "Wizz Air", 0xFFE04AA8),
        Airline("FDX", "FX", "FedEx", 0xFF9A6CF5, cargo = true),
        Airline("UPS", "5X", "UPS Airlines", 0xFFFFC147, cargo = true),
        Airline("GTI", "5Y", "Atlas Air", 0xFF5C9AF0, cargo = true),
        Airline("CLX", "CV", "Cargolux", 0xFFF0475A, cargo = true),
        Airline("ABX", "GB", "ABX Air", 0xFF6F8FE0, cargo = true),
        Airline("BOX", "3S", "AeroLogic", 0xFFFFC533, cargo = true),
        Airline("CKS", "K4", "Kalitta Air", 0xFFF0475A, cargo = true),
    ).associateBy { it.icao }

    fun byIcao(icao: String?): Airline? = icao?.uppercase()?.let { all[it] ?: directory.byIcao[it] }

    private val byIataCode = all.values.filter { it.iata.isNotBlank() }.groupBy { it.iata }

    /**
     * The airline for an IATA code: the curated list first, then the wider directory. IATA
     * codes are reused, so the first listed (or the one still flying) wins.
     */
    fun byIata(iata: String?): Airline? = iata?.uppercase()?.let { byIataCode[it]?.firstOrNull() ?: directory.byIata[it] }

    /** The airline's name, or the code when nobody knows it. */
    fun nameFor(iata: String?, fallback: String? = null): String = byIata(iata)?.name ?: fallback ?: iata.orEmpty()

    private class Directory(val byIcao: Map<String, Airline>, val byIata: Map<String, Airline>)

    @Volatile private var source: (() -> java.io.InputStream)? = null
    private val directory: Directory by lazy { loadDirectory() }

    /**
     * The wider OpenFlights airline list (bundled, ODbL), so imported flight logs on any airline
     * get a name and a callsign. Each gets a steady accent hue from its code; the curated list
     * above keeps the hand-picked livery colours.
     */
    fun install(open: () -> java.io.InputStream) {
        source = open
    }

    private fun loadDirectory(): Directory {
        val open = source ?: return Directory(emptyMap(), emptyMap())
        val rows = runCatching {
            open().bufferedReader().use { kotlinx.serialization.json.Json.parseToJsonElement(it.readText()) } as kotlinx.serialization.json.JsonArray
        }.getOrNull() ?: return Directory(emptyMap(), emptyMap())
        val list = rows.map { r ->
            val a = r as kotlinx.serialization.json.JsonArray
            fun str(i: Int) = (a[i] as kotlinx.serialization.json.JsonPrimitive).content
            Airline(str(0), str(1), str(2), hueAccent(str(0)))
        }
        return Directory(list.associateBy { it.icao }, list.filter { it.iata.isNotBlank() }.distinctBy { it.iata }.associateBy { it.iata })
    }

    /** A bright, readable accent picked from the code, so each airline keeps its own glow. */
    internal fun hueAccent(code: String): Long {
        val hue = (code.hashCode().toLong() and 0xFFFF) % 360
        val hsv = floatArrayOf(hue.toFloat(), 0.55f, 0.95f)
        // HSV → RGB without android.graphics, so it runs in unit tests.
        val c = hsv[2] * hsv[1]
        val x = c * (1 - kotlin.math.abs((hsv[0] / 60f) % 2 - 1))
        val m = hsv[2] - c
        val (r, g, b) = when ((hsv[0] / 60f).toInt()) {
            0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
        }
        fun ch(v: Float) = ((v + m) * 255).toLong().coerceIn(0, 255)
        return (0xFFL shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    /**
     * Airline ICAO designator from an ATC callsign: three letters followed by a digit
     * (`ETD141`, `CPA889`). Registrations used as callsigns (`CFWLV`, `N194V`) return null.
     */
    fun icaoFromCallsign(callsign: String?): String? {
        val c = callsign?.trim()?.uppercase() ?: return null
        if (c.length < 4) return null
        val prefix = c.substring(0, 3)
        if (!prefix.all { it in 'A'..'Z' } || !c[3].isDigit()) return null
        return prefix
    }

    fun fromCallsign(callsign: String?): Airline? = byIcao(icaoFromCallsign(callsign))

    /**
     * The ATC callsign for a marketed flight number: EY 141 → ETD141, AC 015 → ACA15.
     * Leading zeros are dropped, as ATC callsigns do. Null when the airline isn't in the table.
     */
    fun callsignFor(iata: String, flightNumber: String): String? {
        val icao = byIata(iata)?.icao ?: return null
        val number = flightNumber.trim().uppercase().trimStart('0').ifEmpty { return null }
        return icao + number
    }
}

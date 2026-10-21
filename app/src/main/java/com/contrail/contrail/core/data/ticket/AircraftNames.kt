package com.contrail.contrail.core.data.ticket

/** Turns the aircraft wording on tickets ("Boeing 787-9", "Airbus A350-1000", "77W") into ICAO designators. */
object AircraftNames {
    /** IATA equipment codes, as printed in itinerary tables. */
    private val iataCodes = mapOf(
        "388" to "A388", "380" to "A388", "359" to "A359", "351" to "A35K", "35K" to "A35K",
        "333" to "A333", "332" to "A332", "339" to "A339", "338" to "A338",
        "321" to "A321", "32Q" to "A21N", "32N" to "A20N", "320" to "A320", "319" to "A319", "223" to "BCS3", "221" to "BCS1",
        "788" to "B788", "789" to "B789", "781" to "B78X", "78X" to "B78X",
        "77W" to "B77W", "77L" to "B77L", "773" to "B773", "772" to "B772", "779" to "B779",
        "744" to "B744", "74H" to "B748", "763" to "B763",
        "7M8" to "B38M", "7M9" to "B39M", "738" to "B738", "739" to "B739",
        "E75" to "E75L", "E90" to "E190", "E95" to "E195", "295" to "E295", "DH4" to "DH8D", "AT7" to "AT76",
    )

    private val names = listOf(
        Regex("A\\s?380") to "A388",
        Regex("A\\s?350-?1000|A35K") to "A35K",
        Regex("A\\s?350(-?900)?") to "A359",
        Regex("A\\s?330-?900(\\s?neo)?|A330neo", RegexOption.IGNORE_CASE) to "A339",
        Regex("A\\s?330-?300") to "A333",
        Regex("A\\s?330-?200") to "A332",
        Regex("A\\s?321\\s?(neo|XLR|LR)", RegexOption.IGNORE_CASE) to "A21N",
        Regex("A\\s?321") to "A321",
        Regex("A\\s?320\\s?neo", RegexOption.IGNORE_CASE) to "A20N",
        Regex("A\\s?320") to "A320",
        Regex("A\\s?319") to "A319",
        Regex("A\\s?220-?300") to "BCS3",
        Regex("A\\s?220-?100") to "BCS1",
        Regex("787-?10") to "B78X",
        Regex("787-?9") to "B789",
        Regex("787-?8") to "B788",
        Regex("777-?9|777X", RegexOption.IGNORE_CASE) to "B779",
        Regex("777-?300\\s?ER", RegexOption.IGNORE_CASE) to "B77W",
        Regex("777-?200\\s?LR", RegexOption.IGNORE_CASE) to "B77L",
        Regex("777-?300") to "B773",
        Regex("777-?200") to "B772",
        Regex("747-?8") to "B748",
        Regex("747-?400") to "B744",
        Regex("737\\s?MAX\\s?8|737-8\\b", RegexOption.IGNORE_CASE) to "B38M",
        Regex("737\\s?MAX\\s?9|737-9\\b", RegexOption.IGNORE_CASE) to "B39M",
        Regex("737-?800") to "B738",
        Regex("737-?900") to "B739",
        Regex("E-?175|Embraer\\s?175", RegexOption.IGNORE_CASE) to "E75L",
        Regex("E-?190|Embraer\\s?190", RegexOption.IGNORE_CASE) to "E190",
        Regex("Dash\\s?8|Q400|DHC-8-400", RegexOption.IGNORE_CASE) to "DH8D",
    )

    private val labelled = Regex(
        "(?:Aircraft|Equipment|Aircraft type|Plane)\\s*(?:type)?\\s*[:\\-]?\\s*([A-Za-z0-9 \\-]{3,30})",
        RegexOption.IGNORE_CASE,
    )
    private val maker = Regex("(Airbus|Boeing|Embraer|De Havilland|Bombardier|ATR)\\s+([A-Za-z0-9 \\-]{2,20})", RegexOption.IGNORE_CASE)

    /** Finds the first aircraft named in [text], as an ICAO designator. */
    fun find(text: String): String? {
        maker.find(text)?.let { m -> fromName(m.value)?.let { return it } }
        labelled.find(text)?.let { m ->
            val v = m.groupValues[1].trim()
            fromName(v)?.let { return it }
            iataCodes[v.take(3).uppercase()]?.let { return it }
        }
        return null
    }

    fun fromName(name: String): String? = names.firstOrNull { (re, _) -> re.containsMatchIn(name) }?.second
}

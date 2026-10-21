package com.contrail.contrail.core.data.reference

/** Rarity tiers from DESIGN.md, based on how many are still flying with passengers. */
enum class Rarity(val label: String) {
    Legendary("Legendary"),
    Rare("Rare"),
    Uncommon("Uncommon"),
    Common("Common"),
}

data class AircraftType(
    val icao: String,
    val name: String,
    val silhouette: Silhouette,
    val rarity: Rarity = Rarity.Common,
    val military: Boolean = false,
)

/**
 * Curated ICAO type designators. The rarity column is the editable starting point the
 * design doc describes; update it a few times a year.
 */
object AircraftTypes {
    private val all: Map<String, AircraftType> = buildList {
        fun t(icao: String, name: String, s: Silhouette, r: Rarity = Rarity.Common, mil: Boolean = false) =
            add(AircraftType(icao, name, s, r, mil))
        val jumbo = Silhouette.Jumbo
        val quad = Silhouette.Quad
        val tri = Silhouette.WideTri
        val wide = Silhouette.Widebody
        val narrow = Silhouette.Narrowbody
        val rear = Silhouette.RearJet
        val regional = Silhouette.Regional
        val prop = Silhouette.Turboprop
        val prop1 = Silhouette.TurbopropSingle
        val prop4 = Silhouette.TurbopropQuad
        val biz = Silhouette.Bizjet
        val light = Silhouette.Light
        val twin = Silhouette.LightTwin
        val heli = Silhouette.Helicopter
        val fighter = Silhouette.Fighter

        // Legendary
        t("B748", "Boeing 747-8", quad, Rarity.Legendary)
        t("B744", "Boeing 747-400", quad, Rarity.Legendary)
        t("B742", "Boeing 747-200", quad, Rarity.Legendary)
        t("B743", "Boeing 747-300", quad, Rarity.Legendary)
        t("B74S", "Boeing 747SP", quad, Rarity.Legendary)
        t("A342", "Airbus A340-200", quad, Rarity.Legendary)
        t("A343", "Airbus A340-300", quad, Rarity.Legendary)
        t("A345", "Airbus A340-500", quad, Rarity.Legendary)
        t("A346", "Airbus A340-600", quad, Rarity.Legendary)
        t("A3ST", "Airbus Beluga", wide, Rarity.Legendary)
        t("A337", "Airbus BelugaXL", wide, Rarity.Legendary)
        t("A124", "Antonov An-124", quad, Rarity.Legendary)
        t("IL96", "Ilyushin Il-96", quad, Rarity.Legendary)
        t("DC10", "McDonnell Douglas DC-10", tri, Rarity.Legendary)
        t("CONC", "Concorde", narrow, Rarity.Legendary)

        // Rare
        t("A388", "Airbus A380-800", jumbo, Rarity.Rare)
        t("B772", "Boeing 777-200", wide, Rarity.Rare)
        t("B77L", "Boeing 777-200LR", wide, Rarity.Rare)
        t("A332", "Airbus A330-200", wide, Rarity.Rare)
        t("B762", "Boeing 767-200", wide, Rarity.Rare)
        t("B764", "Boeing 767-400", wide, Rarity.Rare)
        t("MD11", "McDonnell Douglas MD-11", tri, Rarity.Rare)
        t("A310", "Airbus A310", wide, Rarity.Rare)
        t("B752", "Boeing 757-200", narrow, Rarity.Rare)
        t("B753", "Boeing 757-300", narrow, Rarity.Rare)
        t("B712", "Boeing 717", rear, Rarity.Rare)
        t("MD88", "McDonnell Douglas MD-88", rear, Rarity.Rare)
        t("A318", "Airbus A318", narrow, Rarity.Rare)

        // Uncommon
        t("B778", "Boeing 777-8", wide, Rarity.Uncommon)
        t("B779", "Boeing 777-9", wide, Rarity.Uncommon)
        t("BCS1", "Airbus A220-100", narrow, Rarity.Uncommon)
        t("BCS3", "Airbus A220-300", narrow, Rarity.Uncommon)
        t("A338", "Airbus A330-800neo", wide, Rarity.Uncommon)
        t("A339", "Airbus A330-900neo", wide, Rarity.Uncommon)
        t("E290", "Embraer E190-E2", regional, Rarity.Uncommon)
        t("E295", "Embraer E195-E2", regional, Rarity.Uncommon)
        t("B39M", "Boeing 737 MAX 9", narrow, Rarity.Common)
        t("B3XM", "Boeing 737 MAX 10", narrow, Rarity.Uncommon)

        // Common widebodies
        t("B77W", "Boeing 777-300ER", wide)
        t("B773", "Boeing 777-300", wide, Rarity.Rare)
        t("B77F", "Boeing 777 Freighter", wide)
        t("B788", "Boeing 787-8", wide)
        t("B789", "Boeing 787-9", wide)
        t("B78X", "Boeing 787-10", wide)
        t("B763", "Boeing 767-300", wide)
        t("A333", "Airbus A330-300", wide)
        t("A359", "Airbus A350-900", wide)
        t("A35K", "Airbus A350-1000", wide)

        // Common narrowbodies
        t("A319", "Airbus A319", narrow)
        t("A320", "Airbus A320", narrow)
        t("A321", "Airbus A321", narrow)
        t("A19N", "Airbus A319neo", narrow)
        t("A20N", "Airbus A320neo", narrow)
        t("A21N", "Airbus A321neo", narrow)
        t("B733", "Boeing 737-300", narrow)
        t("B734", "Boeing 737-400", narrow)
        t("B735", "Boeing 737-500", narrow)
        t("B736", "Boeing 737-600", narrow)
        t("B737", "Boeing 737-700", narrow)
        t("B738", "Boeing 737-800", narrow)
        t("B739", "Boeing 737-900", narrow)
        t("B37M", "Boeing 737 MAX 7", narrow)
        t("B38M", "Boeing 737 MAX 8", narrow)

        // Regional jets
        t("E170", "Embraer E170", regional)
        t("E75L", "Embraer E175", regional)
        t("E75S", "Embraer E175", regional)
        t("E190", "Embraer E190", regional)
        t("E195", "Embraer E195", regional)
        t("E135", "Embraer ERJ-135", rear)
        t("E145", "Embraer ERJ-145", rear)
        t("CRJ2", "Bombardier CRJ200", rear)
        t("CRJ7", "Bombardier CRJ700", rear)
        t("CRJ9", "Bombardier CRJ900", rear)
        t("CRJX", "Bombardier CRJ1000", rear)

        // Turboprops
        t("DH8A", "De Havilland Dash 8-100", prop)
        t("DH8B", "De Havilland Dash 8-200", prop)
        t("DH8C", "De Havilland Dash 8-300", prop)
        t("DH8D", "De Havilland Dash 8-400", prop)
        t("AT43", "ATR 42-300", prop)
        t("AT45", "ATR 42-500", prop)
        t("AT72", "ATR 72", prop)
        t("AT75", "ATR 72-500", prop)
        t("AT76", "ATR 72-600", prop)
        t("DHC6", "De Havilland Twin Otter", prop)
        t("DHC7", "De Havilland Dash 7", prop4, Rarity.Rare)
        t("SF34", "Saab 340", prop)
        t("B190", "Beechcraft 1900", prop)
        t("PC12", "Pilatus PC-12", prop1)
        t("C208", "Cessna Caravan", prop1)
        t("BE20", "Beechcraft King Air 200", prop)
        t("BE9L", "Beechcraft King Air 90", prop)
        t("TBM9", "Daher TBM 900", prop1)
        t("SW4", "Fairchild Metro", prop)

        // Business jets
        t("GLF4", "Gulfstream IV", biz)
        t("GLF5", "Gulfstream V", biz)
        t("GLF6", "Gulfstream G650", biz)
        t("GA5C", "Gulfstream G500", biz)
        t("GA6C", "Gulfstream G600", biz)
        t("GA7C", "Gulfstream G700", biz)
        t("GLEX", "Bombardier Global Express", biz)
        t("GL5T", "Bombardier Global 5000", biz)
        t("GL7T", "Bombardier Global 7500", biz)
        t("CL30", "Bombardier Challenger 300", biz)
        t("CL35", "Bombardier Challenger 350", biz)
        t("CL60", "Bombardier Challenger 600", biz)
        t("FA7X", "Dassault Falcon 7X", biz)
        t("FA8X", "Dassault Falcon 8X", biz)
        t("FA6X", "Dassault Falcon 6X", biz)
        t("F900", "Dassault Falcon 900", biz)
        t("F2TH", "Dassault Falcon 2000", biz)
        t("C25A", "Cessna Citation CJ2", biz)
        t("C25B", "Cessna Citation CJ3", biz)
        t("C25C", "Cessna Citation CJ4", biz)
        t("C510", "Cessna Citation Mustang", biz)
        t("C525", "Cessna CitationJet", biz)
        t("C560", "Cessna Citation V", biz)
        t("C56X", "Cessna Citation Excel", biz)
        t("C680", "Cessna Citation Sovereign", biz)
        t("C68A", "Cessna Citation Latitude", biz)
        t("C700", "Cessna Citation Longitude", biz)
        t("E55P", "Embraer Phenom 300", biz)
        t("E50P", "Embraer Phenom 100", biz)
        t("LJ45", "Learjet 45", biz)
        t("LJ75", "Learjet 75", biz)
        t("G150", "Gulfstream G150", biz)
        t("G280", "Gulfstream G280", biz)
        t("PC24", "Pilatus PC-24", biz)
        t("HDJT", "HondaJet", biz)

        // Light aircraft
        t("C152", "Cessna 152", light)
        t("C172", "Cessna 172 Skyhawk", light)
        t("C177", "Cessna 177 Cardinal", light)
        t("C182", "Cessna 182 Skylane", light)
        t("C206", "Cessna 206", light)
        t("P28A", "Piper Cherokee", light)
        t("P28R", "Piper Arrow", light)
        t("PA34", "Piper Seneca", twin)
        t("PA44", "Piper Seminole", twin)
        t("SR20", "Cirrus SR20", light)
        t("SR22", "Cirrus SR22", light)
        t("SF50", "Cirrus Vision Jet", biz)
        t("DA40", "Diamond DA40", light)
        t("DA42", "Diamond DA42", twin)
        t("DV20", "Diamond DV20", light)
        t("M20P", "Mooney M20", light)
        t("BE36", "Beechcraft Bonanza", light)
        t("BE58", "Beechcraft Baron", twin)
        t("WW24", "IAI Westwind", biz)

        // Helicopters
        t("EC35", "Airbus H135", heli)
        t("EC45", "Airbus H145", heli)
        t("EC30", "Airbus H130", heli)
        t("AS50", "Airbus AS350", heli)
        t("B06", "Bell 206", heli)
        t("B407", "Bell 407", heli)
        t("B429", "Bell 429", heli)
        t("S76", "Sikorsky S-76", heli)
        t("S92", "Sikorsky S-92", heli)
        t("R44", "Robinson R44", heli)
        t("R22", "Robinson R22", heli)
        t("A139", "Leonardo AW139", heli)
        t("A169", "Leonardo AW169", heli)

        // Military
        t("C17", "Boeing C-17 Globemaster", wide, Rarity.Rare, mil = true)
        t("C130", "Lockheed C-130 Hercules", prop4, Rarity.Uncommon, mil = true)
        t("C30J", "Lockheed C-130J Super Hercules", prop4, Rarity.Uncommon, mil = true)
        t("K35R", "Boeing KC-135 Stratotanker", quad, Rarity.Rare, mil = true)
        t("KC46", "Boeing KC-46 Pegasus", wide, Rarity.Rare, mil = true)
        t("A400", "Airbus A400M", prop4, Rarity.Rare, mil = true)
        t("P8", "Boeing P-8 Poseidon", narrow, Rarity.Rare, mil = true)
        t("E3TF", "Boeing E-3 Sentry", quad, Rarity.Legendary, mil = true)
        t("H60", "Sikorsky UH-60 Black Hawk", heli, Rarity.Uncommon, mil = true)
        t("F16", "General Dynamics F-16", fighter, Rarity.Rare, mil = true)
        t("F18S", "Boeing F/A-18 Super Hornet", fighter, Rarity.Rare, mil = true)
        t("F35", "Lockheed F-35", fighter, Rarity.Rare, mil = true)
        t("B52", "Boeing B-52", quad, Rarity.Legendary, mil = true)
    }.associateBy { it.icao }

    fun byIcao(icao: String?): AircraftType? = icao?.let { all[it.uppercase()] }

    /**
     * Rarity for a type flown by a given operator. A 747 freighter is a common sight at
     * cargo hubs, so cargo operators drop the passenger-based tier to uncommon.
     */
    fun rarityFor(typeIcao: String?, operator: Airline?): Rarity {
        val type = byIcao(typeIcao) ?: return Rarity.Common
        if (operator?.cargo == true && type.rarity < Rarity.Uncommon) return Rarity.Uncommon
        return type.rarity
    }

    /** Map icon class: curated table, then the ICAO designator list, then the emitter category. */
    fun silhouetteFor(typeIcao: String?, category: String?): Silhouette =
        AircraftClassifier.classify(typeIcao, category)

    /** Model name from the curated table, else the ICAO designator list. */
    fun nameFor(typeIcao: String?): String? =
        byIcao(typeIcao)?.name ?: TypeDesignators.lookup(typeIcao)?.name?.let(::titleCase)

    /** "BOEING 737-800" -> "Boeing 737-800": manufacturer words are all caps in Doc 8643. */
    internal fun titleCase(raw: String): String = raw.split(' ').joinToString(" ") { w ->
        val letters = w.filter { it.isLetter() }
        if (letters.length > 2 && letters.all { it.isUpperCase() } && w.none { it.isDigit() }) {
            w.lowercase().replaceFirstChar { it.uppercase() }
        } else {
            w
        }
    }

    /** Distinct types known to the app, for the filter sheet. */
    val known: Collection<AircraftType> get() = all.values
}

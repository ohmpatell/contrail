package com.contrail.contrail.core.data.reference

/**
 * A starting list of lounges at your main airports. No free lounge database has an API, so
 * this is curated by hand; OpenStreetMap fills in other airports, and lounges you add are
 * remembered. Lounges open, close and get renamed, so treat it as a list to correct.
 */
object Lounges {
    data class Curated(val airport: String, val terminal: String?, val name: String)

    val all: List<Curated> = listOf(
        // Toronto Pearson
        Curated("YYZ", "1", "Air Canada Signature Suite"),
        Curated("YYZ", "1", "Air Canada Maple Leaf Lounge (International)"),
        Curated("YYZ", "1", "Air Canada Maple Leaf Lounge (Transborder)"),
        Curated("YYZ", "1", "Air Canada Maple Leaf Lounge (Domestic)"),
        Curated("YYZ", "1", "Plaza Premium Lounge (International)"),
        Curated("YYZ", "1", "Plaza Premium Lounge (Domestic)"),
        Curated("YYZ", "3", "Plaza Premium Lounge (International)"),
        // Abu Dhabi, Terminal A
        Curated("AUH", "A", "Etihad First Class Lounge"),
        Curated("AUH", "A", "Etihad Business Class Lounge"),
        Curated("AUH", "A", "Etihad Premium Lounge (Pier)"),
        Curated("AUH", "A", "Al Reem Lounge"),
        // Hong Kong
        Curated("HKG", "1", "Cathay Pacific The Pier, First"),
        Curated("HKG", "1", "Cathay Pacific The Pier, Business"),
        Curated("HKG", "1", "Cathay Pacific The Wing, First"),
        Curated("HKG", "1", "Cathay Pacific The Wing, Business"),
        Curated("HKG", "1", "Cathay Pacific The Deck"),
        Curated("HKG", "1", "Cathay Pacific The Bridge"),
        Curated("HKG", "1", "Plaza Premium Lounge (East Hall)"),
        Curated("HKG", "1", "Plaza Premium Lounge (West Hall)"),
        Curated("HKG", "1", "SilverKris Lounge"),
        Curated("HKG", "1", "Qantas Hong Kong Lounge"),
        Curated("HKG", "1", "Emirates Lounge"),
        Curated("HKG", "1", "Club Autus"),
        // Manila
        Curated("MNL", "3", "Cathay Pacific Lounge"),
        Curated("MNL", "3", "SilverKris Lounge"),
        Curated("MNL", "3", "PAGSS Premium Lounge"),
        Curated("MNL", "3", "Marhaba Lounge"),
        Curated("MNL", "1", "PAL Mabuhay Lounge"),
        Curated("MNL", "1", "PAGSS Lounge"),
        // Delhi
        Curated("DEL", "3", "Encalm Privé (International)"),
        Curated("DEL", "3", "Encalm Lounge (Domestic)"),
        Curated("DEL", "3", "Air India Maharaja Lounge"),
        // Mumbai
        Curated("BOM", "2", "Adani Lounge (International)"),
        Curated("BOM", "2", "Adani Lounge (Domestic)"),
        Curated("BOM", "2", "Travel Club Lounge"),
        Curated("BOM", "2", "Air India Maharaja Lounge"),
    )

    fun at(airport: String): List<Curated> = all.filter { it.airport.equals(airport, ignoreCase = true) }

    /** Stable key for a lounge: "HKG:the pier first". */
    fun key(airport: String, name: String): String =
        airport.uppercase() + ":" + name.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
}

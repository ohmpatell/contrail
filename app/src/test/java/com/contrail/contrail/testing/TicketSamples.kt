package com.contrail.contrail.testing

import com.contrail.contrail.core.data.ticket.AirportCodes
import com.contrail.contrail.core.data.ticket.AirportDirectory
import com.contrail.contrail.core.data.ticket.AirportInfo
import java.time.ZoneId

/**
 * Made-up tickets in the layouts each airline uses. The names, booking references and ticket
 * numbers are invented; the layouts follow the real documents (as their PDF text or a pasted
 * email comes out).
 */
object TicketSamples {

    /** Etihad e-ticket itinerary/receipt PDF: one table row per flight, terminals on the line below. */
    val etihadEticket = """
        ETIHAD AIRWAYS
        ELECTRONIC TICKET ITINERARY / RECEIPT

        Passenger Name: PATEL/OHM MR
        Booking Reference: K7Q2LM
        Ticket Number: 607 2412345678
        Issued by: Etihad Airways Abu Dhabi        Date of Issue: 02SEP26
        Etihad Guest: 100234567890 (Silver)

        FLIGHT   DATE      FROM                 TO                    DEPART   ARRIVE    CLASS  STATUS
        EY 140   12DEC26   TORONTO (YYZ)        ABU DHABI (AUH)       22:10    19:55+1   Z      OK
                 Terminal 3                     Terminal A
                 Business Studio   Seat 7K   Baggage 2PC   Aircraft: Boeing 787-9
                 Fare Basis: ZRCAAE02

        Abu Dhabi Stopover: 2 nights. Hotel: Yas Island Rotana (Etihad Holidays)

        EY 218   15DEC26   ABU DHABI (AUH)      DELHI (DEL)           02:35    07:50     Z      OK
                 Terminal A                     Terminal 3
                 Business Studio   Seat 3A   Baggage 2PC   Aircraft: Airbus A321neo
                 Fare Basis: ZRCAAE02

        Check-in closes 60 minutes before departure. Boarding gate closes 20 minutes before departure.
        Carriage is subject to the conditions of contract of Etihad Airways PJSC.
    """.trimIndent()

    /** Cathay Pacific confirmation email, pasted: a trip summary, then one block per flight. */
    val cathayEmail = """
        From: Cathay Pacific <noreply@notification.cathaypacific.com>
        Sent: Tue 08 Sep 2026 14:33
        Subject: Your booking is confirmed - 6XJ4PD

        Thank you for choosing Cathay Pacific.

        Booking reference: 6XJ4PD

        Toronto (YYZ) - Manila (MNL)
        One way · 1 Adult

        Passenger(s)
        MR OHM PATEL      Asia Miles membership: 1234567890

        Flight 1   CX 829
        Toronto - Hong Kong
        Departs  Sat 19 Dec 2026  01:15   Toronto Pearson International Airport (YYZ), Terminal 3
        Arrives  Sun 20 Dec 2026  05:00   Hong Kong International Airport (HKG), Terminal 1
        Cabin class: Business (I)    Seat: 15A
        Aircraft: Airbus A350-1000   Flight time 14h 45m

        Connection in Hong Kong: 3h 35m

        Flight 2   CX 907
        Hong Kong - Manila
        Departs  Sun 20 Dec 2026  08:35   Hong Kong International Airport (HKG), Terminal 1
        Arrives  Sun 20 Dec 2026  10:45   Ninoy Aquino International Airport (MNL), Terminal 3
        Cabin class: Business (I)    Seat: 11K
        Aircraft: Airbus A330-300   Flight time 2h 10m

        Manage your booking at cathaypacific.com.
    """.trimIndent()

    /** Air Canada booking confirmation: the route and date come before the flight number. */
    val airCanadaEmail = """
        AIR CANADA
        Booking Confirmation

        Booking reference: AB12CD
        Booking date: September 14, 2026

        Passengers
        Ohm Patel    Aeroplan: 123 456 789    Ticket number: 014 2412345678

        Depart: Toronto (YYZ) to Vancouver (YVR)
        Tue, Nov 17, 2026
        08:00  Toronto Pearson Int'l, Terminal 1
        10:21  Vancouver Int'l, Main terminal
        AC 103   Operated by Air Canada   Boeing 787-9   Duration 5h 21m
        Economy (Standard)   Booking class: K   Seat: 32A

        Return: Vancouver (YVR) to Toronto (YYZ)
        Sun, Nov 22, 2026
        23:45  Vancouver Int'l, Main terminal
        07:15 +1 day  Toronto Pearson Int'l, Terminal 1
        AC 126   Operated by Air Canada   Airbus A330-300   Duration 4h 30m
        Economy (Standard)   Booking class: K   Seat: 28C

        Baggage: 1 checked bag included. Check-in opens 24 hours before departure.
    """.trimIndent()

    /** What ML Kit text recognition returns for a Cathay mobile boarding pass screenshot. */
    val cathayBoardingPassOcr = """
        CATHAY PACIFIC
        BOARDING PASS
        NAME
        PATEL/OHM MR
        FROM
        HONG KONG
        HKG
        TO
        MANILA
        MNL
        FLIGHT
        CX 907
        DATE
        20 DEC
        BOARDING TIME
        07:55
        DEPARTURE
        08:35
        GATE
        23
        SEAT
        11K
        CLASS
        Business
        Terminal 1
    """.trimIndent()

    val airports: Map<String, AirportInfo> = listOf(
        AirportInfo("YYZ", "CYYZ", "Toronto", "CA", ZoneId.of("America/Toronto"), 43.6772, -79.6306),
        AirportInfo("YVR", "CYVR", "Vancouver", "CA", ZoneId.of("America/Vancouver"), 49.1939, -123.1844),
        AirportInfo("AUH", "OMAA", "Abu Dhabi", "AE", ZoneId.of("Asia/Dubai"), 24.4330, 54.6511),
        AirportInfo("DEL", "VIDP", "New Delhi", "IN", ZoneId.of("Asia/Kolkata"), 28.5665, 77.1031),
        AirportInfo("BOM", "VABB", "Mumbai", "IN", ZoneId.of("Asia/Kolkata"), 19.0887, 72.8679),
        AirportInfo("HKG", "VHHH", "Hong Kong", "HK", ZoneId.of("Asia/Hong_Kong"), 22.3080, 113.9185),
        AirportInfo("MNL", "RPLL", "Manila", "PH", ZoneId.of("Asia/Manila"), 14.5086, 121.0198),
        AirportInfo("LHR", "EGLL", "London", "GB", ZoneId.of("Europe/London"), 51.4700, -0.4543),
        AirportInfo("YUL", "CYUL", "Montreal", "CA", ZoneId.of("America/Toronto"), 45.4706, -73.7408),
        AirportInfo("FRA", "EDDF", "Frankfurt", "DE", ZoneId.of("Europe/Berlin"), 50.0379, 8.5622),
    ).associateBy { it.iata }

    val directory = AirportDirectory { airports[it.uppercase()] }
    val codes = AirportCodes { it in airports }
}

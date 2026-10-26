package com.contrail.contrail.core.data

import com.contrail.contrail.core.data.logbook.FlightLogParser
import com.contrail.contrail.core.data.logbook.FlightLogResolver
import com.contrail.contrail.core.data.logbook.LogFormat
import com.contrail.contrail.core.data.trip.Cabin
import com.contrail.contrail.core.data.trip.FlightReason
import com.contrail.contrail.core.data.trip.SeatType
import com.contrail.contrail.testing.TicketSamples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class FlightLogTest {
    private val icao: (String) -> String? = { c -> TicketSamples.airports.values.firstOrNull { it.icao == c }?.iata }

    /** Made-up rows in the exact myFlightradar24 export layout. */
    private val fr24 = """
        Date,Flight number,From,To,Dep time,Arr time,Duration,Airline,Aircraft,Registration,Seat number,Seat type,Flight class,Flight reason,Note,Dep_id,Arr_id,Airline_id,Aircraft_id
        2024-03-17,AC103,Toronto / Toronto Pearson International (YYZ/CYYZ),Vancouver / Vancouver International (YVR/CYVR),08:00:00,10:21:00,05:21:00,Air Canada (AC/ACA),Boeing 787-9 Dreamliner (B789),C-FGDZ,32A,1,1,1,"Window, wing view",2546,2662,13,1124
        2024-03-22,AC126,Vancouver / Vancouver International (YVR/CYVR),Toronto / Toronto Pearson International (YYZ/CYYZ),23:45:00,07:15:00,04:30:00,Air Canada (AC/ACA),Airbus A330-300 (A333),,28C,3,4,2,,2662,2546,13,1088
        2019-07,,Toronto / Toronto Pearson International (YYZ/CYYZ),London / Heathrow (LHR/EGLL),00:00:00,00:00:00,00:00:00, (/), (),,,0,0,0,,2546,1,0,0
        2023-12-01,EY140,Toronto / Toronto Pearson International (YYZ/CYYZ),Somewhere / Unknown Field (XXX/ZZZZ),22:10:00,19:55:00,12:45:00,Etihad Airways (EY/ETD),Boeing 787-9 (B789),A6-BLT,7K,1,2,1,,1,2,3,4
    """.trimIndent()

    @Test
    fun `reads a myFlightradar24 export`() {
        val log = FlightLogParser.parse(fr24)
        assertEquals(LogFormat.MyFlightradar24, log.format)
        assertEquals(4, log.flights.size)
        val a = log.flights[0]
        assertEquals("AC", a.airlineIata)
        assertEquals("103", a.flightNumber)
        assertEquals("YYZ", a.originIata)
        assertEquals("CYVR", a.destinationIcao)
        assertEquals("B789", a.aircraftType)
        assertEquals("C-FGDZ", a.registration)
        assertEquals(SeatType.Window, a.seatType)
        assertEquals(Cabin.Economy, a.cabin)
        assertEquals(FlightReason.Leisure, a.reason)
        assertEquals("Window, wing view", a.note)
        assertEquals(Cabin.PremiumEconomy, log.flights[1].cabin)
        assertEquals(SeatType.Aisle, log.flights[1].seatType)
        // Month-only date with 00:00 times: the day and times are unknown.
        val rough = log.flights[2]
        assertTrue(rough.roughDate)
        assertNull(rough.departure)
        assertNull(rough.airlineIata)
        assertNull(rough.aircraftType)
    }

    @Test
    fun `resolves times, estimates the unknown and skips unknown airports`() {
        val preview = FlightLogResolver.preview(FlightLogParser.parse(fr24), TicketSamples.directory, icao, existing = emptySet())
        assertEquals(3, preview.flights.size)
        assertEquals(setOf("XXX"), preview.unknownAirports)
        assertEquals(1, preview.skipped)
        val (lhr, yvr, back) = preview.flights
        assertEquals("LHR", lhr.destination)
        assertTrue(lhr.timesEstimated)
        // About 5,700 km: 30 minutes plus about 7 hours.
        assertTrue(lhr.duration.toMinutes() in 420L..480L)
        assertFalse(yvr.timesEstimated)
        assertEquals(LocalDateTime.parse("2024-03-17T10:21"), yvr.arrivalLocal)
        // The overnight return lands the next day.
        assertEquals(LocalDate.parse("2024-03-23"), back.arrivalLocal.toLocalDate())
        // The round trip splits into out and back; the 2019 flight is its own trip.
        assertEquals(listOf("Toronto to London", "Toronto to Vancouver", "Vancouver to Toronto"), preview.trips.map { it.title })
    }

    @Test
    fun `skips flights already in Contrail`() {
        val preview = FlightLogResolver.preview(FlightLogParser.parse(fr24), TicketSamples.directory, icao, existing = setOf("2024-03-17|YYZ|YVR"))
        assertEquals(1, preview.duplicates)
        assertEquals(2, preview.flights.size)
    }

    @Test
    fun `reads Flighty and OpenFlights layouts`() {
        val flighty = """
            Date,Airline,Flight,From,To,Dep Terminal,Dep Gate,Arr Terminal,Arr Gate,Canceled,Diverted To,Gate Departure (Scheduled),Gate Departure (Actual),Take off (Scheduled),Take off (Actual),Landing (Scheduled),Landing (Actual),Gate Arrival (Scheduled),Gate Arrival (Actual),Aircraft Type Name,Tail Number,PNR,Seat,Seat Type,Cabin Class,Flight Reason,Notes,Flight Flighty ID
            2025-12-19,CPA,829,YYZ,HKG,3,E71,1,,false,,2025-12-19T01:15,,2025-12-19T01:40,,2025-12-20T04:40,,2025-12-20T05:00,2025-12-20T05:12,Airbus A350-1000,B-LXA,6XJ4PD,15A,WINDOW,BUSINESS,PERSONAL,,abc
            2025-12-20,CPA,907,HKG,MNL,1,,3,,true,,2025-12-20T08:35,,,,,,2025-12-20T10:45,,Airbus A330-300,,,11K,AISLE,BUSINESS,,,def
        """.trimIndent()
        val log = FlightLogParser.parse(flighty)
        assertEquals(LogFormat.Flighty, log.format)
        assertEquals(1, log.flights.size) // The cancelled flight is left out.
        val f = log.flights.single()
        assertEquals("CPA", f.airlineIcao)
        assertEquals("CX", f.airlineIata)
        assertEquals("A35K", f.aircraftType)
        assertEquals(LocalDateTime.parse("2025-12-19T01:15"), f.departure)
        // The actual gate arrival wins over the scheduled one.
        assertEquals(LocalDateTime.parse("2025-12-20T05:12"), f.arrival)
        assertEquals(Cabin.Business, f.cabin)
        assertEquals("3", f.departureTerminal)

        val openFlights = """
            Date,From,To,Flight_Number,Airline,Distance,Duration,Seat,Seat_Type,Class,Reason,Plane,Registration,Trip,Note,From_OID,To_OID,Airline_OID,Plane_OID
            2018-06-02 09:30:00,YUL,FRA,AC874,Air Canada,5850,07:15,,A,Y,B,Boeing 777-300ER,,,,1,2,3,4
        """.trimIndent()
        val of = FlightLogParser.parse(openFlights)
        assertEquals(LogFormat.OpenFlights, of.format)
        val o = of.flights.single()
        assertEquals("AC", o.airlineIata)
        assertEquals("874", o.flightNumber)
        assertEquals("B77W", o.aircraftType)
        assertEquals(SeatType.Aisle, o.seatType)
        assertEquals(FlightReason.Business, o.reason)
        assertEquals(java.time.Duration.ofMinutes(435), o.duration)
    }

    @Test
    fun `parses quoted CSV with commas and line breaks`() {
        val rows = FlightLogParser.csv("a,b,c\r\n1,\"x, y\",\"line\nbreak \"\"quoted\"\"\"\r\n")
        assertEquals(listOf("a", "b", "c"), rows[0])
        assertEquals(listOf("1", "x, y", "line\nbreak \"quoted\""), rows[1])
    }
}

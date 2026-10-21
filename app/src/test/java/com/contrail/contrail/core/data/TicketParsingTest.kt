package com.contrail.contrail.core.data

import com.contrail.contrail.core.data.ticket.BcbpParser
import com.contrail.contrail.core.data.ticket.ImportSource
import com.contrail.contrail.core.data.ticket.TicketTextParser
import com.contrail.contrail.core.data.ticket.TripAssembler
import com.contrail.contrail.core.data.trip.Cabin
import com.contrail.contrail.testing.TicketSamples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TicketParsingTest {
    private val today = LocalDate.of(2026, 10, 21)
    private val parser = TicketTextParser(TicketSamples.codes)

    @Test
    fun `etihad e-ticket gives both flights, the stopover hotel and the booking`() {
        val t = parser.parse(TicketSamples.etihadEticket, today, ImportSource.Pdf)
        assertEquals("K7Q2LM", t.pnr)
        assertEquals("Ohm Patel", t.passengerName)
        assertEquals("607 2412345678", t.ticketNumber)
        assertEquals(2, t.segments.size)

        val ey140 = t.segments[0]
        assertEquals("EY", ey140.airlineIata)
        assertEquals("140", ey140.flightNumber)
        assertEquals("YYZ", ey140.origin)
        assertEquals("AUH", ey140.destination)
        assertEquals(LocalDate.of(2026, 12, 12), ey140.date)
        assertEquals(LocalTime.of(22, 10), ey140.departureTime)
        assertEquals(LocalTime.of(19, 55), ey140.arrivalTime)
        assertEquals(LocalDate.of(2026, 12, 13), ey140.arrivalDate)
        assertEquals("3", ey140.departureTerminal)
        assertEquals("A", ey140.arrivalTerminal)
        assertEquals(Cabin.Business, ey140.cabin)
        assertEquals("Z", ey140.fareClass)
        assertEquals("7K", ey140.seat)
        assertEquals("B789", ey140.aircraftType)
        assertTrue("hotel on the Abu Dhabi stopover", ey140.hotelAfter)

        val ey218 = t.segments[1]
        assertEquals("218", ey218.flightNumber)
        assertEquals("AUH", ey218.origin)
        assertEquals("DEL", ey218.destination)
        assertEquals(LocalDate.of(2026, 12, 15), ey218.date)
        assertEquals(LocalTime.of(2, 35), ey218.departureTime)
        assertEquals(LocalTime.of(7, 50), ey218.arrivalTime)
        assertEquals("A", ey218.departureTerminal)
        assertEquals("3", ey218.arrivalTerminal)
        assertEquals("3A", ey218.seat)
        assertEquals("A21N", ey218.aircraftType)
    }

    @Test
    fun `cathay email skips the trip summary and reads each flight block`() {
        val t = parser.parse(TicketSamples.cathayEmail, today, ImportSource.Text)
        assertEquals("6XJ4PD", t.pnr)
        assertEquals("Ohm Patel", t.passengerName)
        assertEquals(2, t.segments.size)

        val cx829 = t.segments[0]
        assertEquals("CX", cx829.airlineIata)
        assertEquals("829", cx829.flightNumber)
        assertEquals("YYZ", cx829.origin)
        assertEquals("HKG", cx829.destination)
        assertEquals(LocalDate.of(2026, 12, 19), cx829.date)
        assertEquals(LocalTime.of(1, 15), cx829.departureTime)
        assertEquals(LocalDate.of(2026, 12, 20), cx829.arrivalDate)
        assertEquals(LocalTime.of(5, 0), cx829.arrivalTime)
        assertEquals("3", cx829.departureTerminal)
        assertEquals("1", cx829.arrivalTerminal)
        assertEquals(Cabin.Business, cx829.cabin)
        assertEquals("I", cx829.fareClass)
        assertEquals("15A", cx829.seat)
        assertEquals("A35K", cx829.aircraftType)

        val cx907 = t.segments[1]
        assertEquals("HKG", cx907.origin)
        assertEquals("MNL", cx907.destination)
        assertEquals(LocalDate.of(2026, 12, 20), cx907.date)
        assertEquals(LocalTime.of(8, 35), cx907.departureTime)
        assertEquals(LocalTime.of(10, 45), cx907.arrivalTime)
        assertEquals("1", cx907.departureTerminal)
        assertEquals("3", cx907.arrivalTerminal)
        assertEquals("11K", cx907.seat)
        assertEquals("A333", cx907.aircraftType)
    }

    @Test
    fun `air canada email reads routes printed before the flight number`() {
        val t = parser.parse(TicketSamples.airCanadaEmail, today, ImportSource.Text)
        assertEquals("AB12CD", t.pnr)
        assertEquals("Ohm Patel", t.passengerName)
        assertEquals("014 2412345678", t.ticketNumber)
        assertEquals(2, t.segments.size)

        val ac103 = t.segments[0]
        assertEquals("AC", ac103.airlineIata)
        assertEquals("103", ac103.flightNumber)
        assertEquals("YYZ", ac103.origin)
        assertEquals("YVR", ac103.destination)
        assertEquals(LocalDate.of(2026, 11, 17), ac103.date)
        assertEquals(LocalTime.of(8, 0), ac103.departureTime)
        assertEquals(LocalTime.of(10, 21), ac103.arrivalTime)
        assertEquals("1", ac103.departureTerminal)
        assertNull(ac103.arrivalTerminal)
        assertEquals(Cabin.Economy, ac103.cabin)
        assertEquals("K", ac103.fareClass)
        assertEquals("32A", ac103.seat)
        assertEquals("B789", ac103.aircraftType)
        assertEquals("Air Canada", ac103.operatedBy)

        val ac126 = t.segments[1]
        assertEquals("YVR", ac126.origin)
        assertEquals("YYZ", ac126.destination)
        assertEquals(LocalDate.of(2026, 11, 22), ac126.date)
        assertEquals(LocalTime.of(23, 45), ac126.departureTime)
        assertEquals(LocalDate.of(2026, 11, 23), ac126.arrivalDate)
        assertEquals("1", ac126.arrivalTerminal)
        assertEquals("28C", ac126.seat)
        assertEquals("A333", ac126.aircraftType)
    }

    @Test
    fun `boarding pass OCR ignores the boarding time and reads gate and seat`() {
        val t = parser.parse(TicketSamples.cathayBoardingPassOcr, today, ImportSource.Image)
        assertEquals("Ohm Patel", t.passengerName)
        val s = t.segments.single()
        assertEquals("907", s.flightNumber)
        assertEquals("HKG", s.origin)
        assertEquals("MNL", s.destination)
        assertEquals(LocalDate.of(2026, 12, 20), s.date)
        assertEquals(LocalTime.of(8, 35), s.departureTime)
        assertNull("no arrival time on a boarding pass", s.arrivalTime)
        assertEquals("23", s.gate)
        assertEquals("11K", s.seat)
        assertEquals(Cabin.Business, s.cabin)
        assertEquals("1", s.departureTerminal)
        assertEquals(listOf("arrival time"), s.missing)
    }

    @Test
    fun `iata example boarding pass`() {
        val t = BcbpParser.parse("M1DESMARAIS/LUC       EABC123 YULFRAAC 0834 326J001A0025 100", today)!!
        assertEquals("ABC123", t.pnr)
        assertEquals("Luc Desmarais", t.passengerName)
        val s = t.segments.single()
        assertEquals("AC", s.airlineIata)
        assertEquals("834", s.flightNumber)
        assertEquals("YUL", s.origin)
        assertEquals("FRA", s.destination)
        assertEquals(LocalDate.of(2026, 11, 22), s.date)
        assertEquals("J", s.fareClass)
        assertEquals(Cabin.Business, s.cabin)
        assertEquals("1A", s.seat)
    }

    @Test
    fun `two-leg cathay boarding pass with conditional section`() {
        val raw = bcbp(
            name = "PATEL/OHM MR",
            legs = listOf(
                Leg("6XJ4PD", "YYZ", "HKG", "CX", "0829", 353, 'I', "015A", "0042"),
                Leg("6XJ4PD", "HKG", "MNL", "CX", "0907", 354, 'I', "011K", "0007"),
            ),
            issue = "6352", // issued Dec 18, 2026
            ticket = "1602412345678",
        )
        val t = BcbpParser.parse(raw, today)!!
        assertEquals("6XJ4PD", t.pnr)
        assertEquals("Ohm Patel", t.passengerName)
        assertEquals("160 2412345678", t.ticketNumber)
        assertEquals(2, t.segments.size)
        assertEquals(LocalDate.of(2026, 12, 19), t.segments[0].date)
        assertEquals(LocalDate.of(2026, 12, 20), t.segments[1].date)
        assertEquals("15A", t.segments[0].seat)
        assertEquals("907", t.segments[1].flightNumber)
        assertEquals(Cabin.Business, t.segments[1].cabin)
    }

    @Test
    fun `barcode and pdf text of the same ticket merge into one draft`() {
        val text = parser.parse(TicketSamples.cathayEmail, today, ImportSource.Pdf)
        val barcode = BcbpParser.parse(
            bcbp("PATEL/OHM MR", listOf(Leg("6XJ4PD", "HKG", "MNL", "CX", "0907", 354, 'I', "011K", "0007")), null, null),
            today,
        )!!
        val merged = barcode.merge(text)
        assertEquals(2, merged.segments.size)
        assertEquals("829", merged.segments[0].flightNumber)
        assertEquals(LocalTime.of(8, 35), merged.segments[1].departureTime)
        assertEquals(setOf(ImportSource.Barcode, ImportSource.Pdf), merged.sources)
    }

    @Test
    fun `round trip splits into outbound and return, stopover on the way stays`() {
        val dir = TicketSamples.directory
        val ac = parser.parse(TicketSamples.airCanadaEmail, today, ImportSource.Text).segments
            .mapIndexed { i, d -> TripAssembler.resolve(d, dir, i)!! }
        assertEquals(setOf(1), TripAssembler.autoSplits(ac, dir))
        assertEquals("Toronto to Vancouver", TripAssembler.title(TripAssembler.group(ac, setOf(1))[0], dir))

        val ey = parser.parse(TicketSamples.etihadEticket, today, ImportSource.Pdf).segments
            .mapIndexed { i, d -> TripAssembler.resolve(d, dir, i)!! }
        assertEquals(emptySet<Int>(), TripAssembler.autoSplits(ey, dir))
        assertEquals("Toronto to New Delhi", TripAssembler.title(ey, dir))
    }

    @Test
    fun `arrival date comes from time zones when the ticket has no +1`() {
        val dir = TicketSamples.directory
        val cx = parser.parse(TicketSamples.cathayEmail, today, ImportSource.Text).segments
            .map { it.copy(arrivalDate = null) }
            .mapIndexed { i, d -> TripAssembler.resolve(d, dir, i)!! }
        // YYZ 01:15 Dec 19 (UTC-5) → HKG 05:00 (UTC+8): 14h45 later, Dec 20 local.
        assertEquals(LocalDate.of(2026, 12, 20), cx[0].arrivalLocal.toLocalDate())
        assertEquals(14 * 60 + 45L, cx[0].duration.toMinutes())
        assertEquals(LocalDate.of(2026, 12, 20), cx[1].arrivalLocal.toLocalDate())
    }

    private data class Leg(
        val pnr: String, val from: String, val to: String, val carrier: String, val flight: String,
        val julian: Int, val compartment: Char, val seat: String, val sequence: String,
    )

    /** Builds a BCBP string with correct field widths and hex section sizes. */
    private fun bcbp(name: String, legs: List<Leg>, issue: String?, ticket: String?): String = buildString {
        append('M').append(legs.size).append(name.padEnd(20)).append('E')
        legs.forEachIndexed { i, l ->
            append(l.pnr.padEnd(7)).append(l.from).append(l.to).append(l.carrier.padEnd(3))
            append(l.flight.padEnd(5)).append(l.julian.toString().padStart(3, '0')).append(l.compartment)
            append(l.seat).append(l.sequence.padEnd(5)).append('1')
            val repeated = if (ticket != null) ticket.substring(0, 3) + ticket.substring(3) + "0 " + l.carrier.padEnd(3) else ""
            val repeatedSection = if (repeated.isNotEmpty()) "%02X".format(repeated.length) + repeated else "00"
            val variable = if (i == 0) {
                val unique = "1WW" + (issue ?: "    ") + "B" + l.carrier.padEnd(3)
                ">6" + "%02X".format(unique.length) + unique + repeatedSection
            } else {
                repeatedSection
            }
            append("%02X".format(variable.length)).append(variable)
        }
    }
}

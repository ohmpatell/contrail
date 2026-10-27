package com.contrail.contrail.testing

import com.contrail.contrail.core.data.logbook.FlightLogParser
import com.contrail.contrail.core.data.logbook.FlightLogResolver
import com.contrail.contrail.core.data.ticket.AirportDirectory
import com.contrail.contrail.core.data.ticket.AirportInfo
import com.contrail.contrail.core.data.trip.ConnectionRules
import com.contrail.contrail.core.data.trip.Trip
import java.time.ZoneId

/**
 * Eight years of made-up flying, written as a myFlightradar24 export and run through the real
 * importer, so Passport screenshots show full charts, stamps and medals.
 */
object PassportFixtures {
    val places: Map<String, AirportInfo> = TicketSamples.airports + listOf(
        AirportInfo("JFK", "KJFK", "New York", "US", ZoneId.of("America/New_York"), 40.6413, -73.7781),
        AirportInfo("NRT", "RJAA", "Tokyo", "JP", ZoneId.of("Asia/Tokyo"), 35.7720, 140.3929),
        AirportInfo("SIN", "WSSS", "Singapore", "SG", ZoneId.of("Asia/Singapore"), 1.3644, 103.9915),
        AirportInfo("SYD", "YSSY", "Sydney", "AU", ZoneId.of("Australia/Sydney"), -33.9399, 151.1753),
        AirportInfo("CDG", "LFPG", "Paris", "FR", ZoneId.of("Europe/Paris"), 49.0097, 2.5479),
        AirportInfo("DXB", "OMDB", "Dubai", "AE", ZoneId.of("Asia/Dubai"), 25.2532, 55.3657),
        AirportInfo("YYC", "CYYC", "Calgary", "CA", ZoneId.of("America/Edmonton"), 51.1215, -114.0076),
        AirportInfo("LAX", "KLAX", "Los Angeles", "US", ZoneId.of("America/Los_Angeles"), 33.9416, -118.4085),
        AirportInfo("CUN", "MMUN", "Cancún", "MX", ZoneId.of("America/Cancun"), 21.0365, -86.8771),
        AirportInfo("KEF", "BIKF", "Reykjavík", "IS", ZoneId.of("Atlantic/Reykjavik"), 63.9850, -22.6056),
    ).associateBy { it.iata }

    val directory = AirportDirectory { places[it.uppercase()] }

    private const val HEADER = "Date,Flight number,From,To,Dep time,Arr time,Duration,Airline,Aircraft,Registration,Seat number,Seat type,Flight class,Flight reason,Note,Dep_id,Arr_id,Airline_id,Aircraft_id"

    private fun ap(code: String) = places.getValue(code).let { "${it.city} / ${it.city} (${it.iata}/${it.icao})" }

    private val rows = listOf(
        "2018-07-02|AC856|YYZ|LHR|21:30|09:40|07:10|Air Canada (AC/ACA)|Boeing 777-300ER (B77W)|C-FIVQ|34K|1|1|1",
        "2018-07-16|AC849|LHR|YYZ|12:55|15:45|07:50|Air Canada (AC/ACA)|Boeing 787-9 (B789)|C-FGDZ|30A|1|1|1",
        "2019-03-09|WS2416|YYZ|CUN|07:00|10:35|03:35|WestJet (WS/WJA)|Boeing 737-800 (B738)|C-GWSV|12F|1|1|1",
        "2019-03-16|WS2417|CUN|YYZ|11:45|15:40|03:55|WestJet (WS/WJA)|Boeing 737-800 (B738)|C-GWSZ|14A|1|1|1",
        "2019-08-20|EY140|YYZ|AUH|22:10|19:55|12:45|Etihad Airways (EY/ETD)|Boeing 787-9 (B789)|A6-BLT|7K|1|2|1",
        "2019-08-22|EY218|AUH|DEL|02:35|07:50|03:45|Etihad Airways (EY/ETD)|Airbus A321neo (A21N)||3A|1|2|1",
        "2019-09-05|AI187|DEL|YYZ|04:00|09:25|14:55|Air India (AI/AIC)|Boeing 777-300ER (B77W)|VT-ALJ|22A|1|1|1",
        "2020-01-30|AC33|YVR|SYD|23:40|09:55|15:15|Air Canada (AC/ACA)|Boeing 787-9 (B789)|C-FNOH|2K|1|2|1",
        "2020-02-12|AC34|SYD|YVR|11:15|06:35|14:20|Air Canada (AC/ACA)|Boeing 787-9 (B789)|C-FNOH|2A|1|2|1",
        "2020-01-29|AC115|YYZ|YVR|17:00|19:05|05:05|Air Canada (AC/ACA)|Airbus A330-300 (A333)|C-GFAF|21C|3|1|1",
        "2020-02-13|AC102|YVR|YYZ|08:00|15:30|04:30|Air Canada (AC/ACA)|Boeing 787-8 (B788)||23A|1|1|1",
        "2021-12-31|AC1046|YYZ|YYC|19:00|21:20|04:20|Air Canada (AC/ACA)|Airbus A220-300 (BCS3)|C-GJYC|5A|1|1|1",
        "2022-01-05|AC1049|YYC|YYZ|13:00|18:35|03:35|Air Canada (AC/ACA)|Airbus A220-300 (BCS3)||6F|1|1|1",
        "2022-06-10|FI602|YYZ|KEF|20:40|06:15|05:35|Icelandair (FI/ICE)|Boeing 757-200 (B752)|TF-FIV|18A|1|1|1",
        "2022-06-14|FI542|KEF|CDG|07:40|13:00|03:20|Icelandair (FI/ICE)|Boeing 737 MAX 8 (B38M)||9A|1|1|1",
        "2022-06-22|AF356|CDG|YYZ|13:35|15:40|08:05|Air France (AF/AFR)|Airbus A350-900 (A359)|F-HTYE|33A|1|1|1",
        "2023-04-03|CX829|YYZ|HKG|01:15|05:00|15:45|Cathay Pacific (CX/CPA)|Airbus A350-1000 (A35K)|B-LXA|15A|1|2|1",
        "2023-04-03|CX907|HKG|MNL|08:35|10:45|02:10|Cathay Pacific (CX/CPA)|Airbus A330-300 (A333)|B-LAD|11K|1|2|1",
        "2023-04-18|CX902|MNL|HKG|11:50|14:05|02:15|Cathay Pacific (CX/CPA)|Airbus A330-300 (A333)||11A|1|2|1",
        "2023-04-19|CX826|HKG|YYZ|00:40|03:45|15:05|Cathay Pacific (CX/CPA)|Airbus A350-1000 (A35K)|B-LXA|15K|1|2|1",
        "2023-10-14|EK242|YYZ|DXB|21:55|19:20|12:25|Emirates (EK/UAE)|Airbus A380-800 (A388)|A6-EUF|85A|1|2|1",
        "2023-10-17|EK432|DXB|SIN|02:50|14:20|07:30|Emirates (EK/UAE)|Boeing 777-300ER (B77W)||31A|1|1|1",
        "2023-10-24|SQ12|SIN|NRT|23:05|07:10|07:05|Singapore Airlines (SQ/SIA)|Boeing 777-300ER (B77W)|9V-SWL|41K|1|1|1",
        "2023-10-30|AC10|NRT|YYZ|17:10|16:50|12:40|Air Canada (AC/ACA)|Boeing 787-9 (B789)|C-FVNB|28K|1|4|1",
        "2024-03-17|AC103|YYZ|YVR|08:00|10:21|05:21|Air Canada (AC/ACA)|Boeing 787-9 (B789)|C-FGDZ|32A|1|1|2",
        "2024-03-22|AC126|YVR|YYZ|23:45|07:15|04:30|Air Canada (AC/ACA)|Airbus A330-300 (A333)||28C|3|1|2",
        "2024-05-09|AC761|YYZ|LAX|09:30|11:55|05:25|Air Canada (AC/ACA)|Boeing 737 MAX 8 (B38M)||17A|1|1|1",
        "2024-05-14|AC790|LAX|YYZ|22:45|06:35|04:50|Air Canada (AC/ACA)|Boeing 737 MAX 8 (B38M)||18F|1|1|1",
        "2024-09-02|AC408|YYZ|YUL|07:00|08:15|01:15|Air Canada (AC/ACA)|Airbus A220-300 (BCS3)||12A|1|1|2",
        "2024-09-03|AC417|YUL|YYZ|18:00|19:22|01:22|Air Canada (AC/ACA)|Airbus A220-300 (BCS3)||12F|1|1|2",
        "2025-02-14|EY142|YYZ|AUH|22:30|20:15|12:45|Etihad Airways (EY/ETD)|Boeing 787-10 (B78X)|A6-BMA|7A|1|2|1",
        "2025-02-17|EY206|AUH|BOM|03:00|07:40|03:10|Etihad Airways (EY/ETD)|Airbus A321neo (A21N)||2A|1|2|1",
        "2025-03-02|EY205|BOM|AUH|09:15|10:55|03:10|Etihad Airways (EY/ETD)|Airbus A321neo (A21N)||2F|1|2|1",
        "2025-03-03|EY141|AUH|YYZ|09:25|15:10|13:45|Etihad Airways (EY/ETD)|Boeing 787-10 (B78X)|A6-BMA|7K|1|2|1",
        "2025-07-04|AC844|YYZ|FRA|22:15|11:50|07:35|Air Canada (AC/ACA)|Boeing 787-9 (B789)||40A|1|1|1",
        "2025-07-12|LH470|FRA|YYZ|13:30|15:55|08:25|Lufthansa (LH/DLH)|Boeing 747-8 (B748)|D-ABYA|86A|1|1|1",
        "2026-03-17|AC103|YYZ|YVR|08:00|10:21|05:21|Air Canada (AC/ACA)|Boeing 787-9 (B789)|C-FGDZ|32A|1|1|1",
        "2026-03-22|AC126|YVR|YYZ|23:45|07:15|04:30|Air Canada (AC/ACA)|Airbus A330-300 (A333)||28C|3|1|1",
        "2026-08-10|CX829|YYZ|HKG|01:15|05:00|15:45|Cathay Pacific (CX/CPA)|Airbus A350-1000 (A35K)|B-LXJ|11A|1|2|1",
        "2026-08-24|CX826|HKG|YYZ|00:40|03:45|15:05|Cathay Pacific (CX/CPA)|Airbus A350-1000 (A35K)|B-LXA|11K|1|2|1",
    )

    val csv: String = buildString {
        appendLine(HEADER)
        rows.forEach { r ->
            val f = r.split('|')
            appendLine(listOf(f[0], f[1], "\"${ap(f[2])}\"", "\"${ap(f[3])}\"", "${f[4]}:00", "${f[5]}:00", "${f[6]}:00", "\"${f[7]}\"", "\"${f[8]}\"", f[9], f[10], f[11], f[12], f[13], "", "1", "2", "3", "4").joinToString(","))
        }
    }

    /** The history grouped into trips, ids assigned. */
    val trips: List<Trip> = run {
        val preview = FlightLogResolver.preview(FlightLogParser.parse(csv), directory, { null }, emptySet())
        var nextId = 1000L
        preview.trips.mapIndexed { t, p ->
            val segs = p.segments.mapIndexed { i, s -> s.copy(id = nextId++, tripId = t + 100L, ordinal = i) }
            Trip(t + 100L, p.title, null, null, segs, ConnectionRules.connections(segs, emptyMap()))
        }
    }
}

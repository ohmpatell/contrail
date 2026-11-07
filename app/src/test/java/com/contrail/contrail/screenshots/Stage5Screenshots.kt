package com.contrail.contrail.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.contrail.contrail.core.data.ticket.AirportDirectory
import com.contrail.contrail.core.data.ticket.AirportInfo
import com.contrail.contrail.core.data.trip.FlightSegment
import com.contrail.contrail.feature.widgets.Blip
import com.contrail.contrail.feature.widgets.OverheadSnapshot
import com.contrail.contrail.feature.widgets.WidgetArt
import com.contrail.contrail.feature.widgets.Widgets
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime
import java.time.ZoneId

/** The three home-screen widgets at phone and tablet sizes, painted onto one sheet. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class Stage5Screenshots {
    private val toronto = ZoneId.of("America/Toronto")
    private val abuDhabi = ZoneId.of("Asia/Dubai")
    private val dir = AirportDirectory { code ->
        mapOf(
            "YYZ" to AirportInfo("YYZ", "CYYZ", "Toronto", "CA", toronto, 43.677, -79.630),
            "AUH" to AirportInfo("AUH", "OMAA", "Abu Dhabi", "AE", abuDhabi, 24.433, 54.651),
            "LHR" to AirportInfo("LHR", "EGLL", "London", "GB", ZoneId.of("Europe/London"), 51.470, -0.454),
        )[code]
    }
    private val flight = FlightSegment(
        id = 1, tripId = 1, ordinal = 0, airlineIata = "EY", flightNumber = "140", origin = "YYZ", destination = "AUH",
        departureLocal = LocalDateTime.of(2026, 11, 12, 22, 10), arrivalLocal = LocalDateTime.of(2026, 11, 13, 19, 55),
        departureZone = toronto, arrivalZone = abuDhabi, departureTerminal = "3", gate = "C32", seat = "32K", aircraftType = "B789",
    )
    private val beforeDeparture = LocalDateTime.of(2026, 11, 12, 19, 40).atZone(toronto).toInstant()
    private val midFlight = LocalDateTime.of(2026, 11, 13, 4, 0).atZone(toronto).toInstant()

    private val snapshot = OverheadSnapshot(
        blips = listOf(
            Blip(48.0, 3.2, 11_500, 250f, closest = true),
            Blip(120.0, 9.0, 34_000, 80f),
            Blip(210.0, 14.0, 2_400, 60f),
            Blip(300.0, 6.5, 38_000, 290f),
            Blip(15.0, 19.0, 24_000, 180f),
            Blip(250.0, 21.0, null, 0f),
        ),
        callsign = "ACA1117", type = "Boeing 737 MAX 8", altitudeFt = 11_500, distanceKm = 3.2, bearingDeg = 48.0,
        count = 6, radiusKm = 25, spotName = "Mississauga (home)", atMs = beforeDeparture.toEpochMilli(),
    )

    @Test fun widgets() {
        val art = WidgetArt(ApplicationProvider.getApplicationContext())
        val board = Widgets.board(flight, dir, beforeDeparture)
        val airborne = Widgets.board(flight, dir, midFlight)
        val scope = Widgets.scope(snapshot)
        val ring = Widgets.ring(
            listOf(flight.copy(departureLocal = LocalDateTime.of(2026, 3, 2, 22, 10), arrivalLocal = LocalDateTime.of(2026, 3, 3, 19, 55)),
                flight.copy(id = 2, origin = "AUH", destination = "LHR", departureLocal = LocalDateTime.of(2026, 3, 9, 8, 0), arrivalLocal = LocalDateTime.of(2026, 3, 9, 12, 30), departureZone = abuDhabi, arrivalZone = ZoneId.of("Europe/London"))),
            dir,
            midFlight,
        )
        // Rows: phone 4×2, phone 2×2, tablet 5×3.
        val tiles = listOf(
            listOf(art.board(330f, 160f, board), art.board(160f, 160f, board), art.board(420f, 240f, airborne)),
            listOf(art.scope(330f, 160f, scope), art.scope(160f, 160f, scope), art.scope(420f, 240f, scope)),
            listOf(art.yearRing(330f, 160f, ring), art.yearRing(160f, 160f, ring), art.board(420f, 240f, Widgets.board(null, dir, midFlight))),
        )
        sheet(tiles).captureRoboImage("src/test/screenshots/widgets.png")
    }

    private fun sheet(rows: List<List<Bitmap>>): Bitmap {
        val gap = 40
        val w = rows.maxOf { r -> r.sumOf { it.width } + gap * (r.size + 1) }
        val h = rows.sumOf { r -> r.maxOf { it.height } } + gap * (rows.size + 1)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(0xFF3A4660.toInt())
        var y = gap
        rows.forEach { r ->
            var x = gap
            r.forEach { c.drawBitmap(it, x.toFloat(), y.toFloat(), null); x += it.width + gap }
            y += r.maxOf { it.height } + gap
        }
        return out
    }
}

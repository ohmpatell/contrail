package com.contrail.contrail.core.data.trip

import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.data.model.LiveAircraft
import java.time.Duration
import java.time.Instant

/** Where a flight is in its day, from boarding to the gate. */
enum class FlightPhase(val label: String) {
    Upcoming("Upcoming"),
    Boarding("Boarding"),
    Departing("Departing"),
    Airborne("In the air"),
    Landing("Landing"),
    Landed("Landed"),
}

/**
 * One moment of a flight, for the Live Update and widgets.
 *
 * [fraction] runs 0 to 1 across the flight: by distance when the aircraft is live on ADS-B, by the
 * timetable otherwise. [eta] is always an estimate unless it is the ticket's own arrival time.
 */
data class FlightProgress(
    val phase: FlightPhase,
    val fraction: Float,
    val eta: Instant,
    /** True when [eta] comes from the live position rather than the ticket. */
    val live: Boolean,
    val remainingKm: Double? = null,
    val altitudeFt: Int? = null,
    val groundSpeedKt: Int? = null,
) {
    /** The moment the countdown runs to: departure until wheels-up, then arrival. */
    fun target(segment: FlightSegment): Instant = if (phase <= FlightPhase.Departing) segment.departure else eta
}

/** A point on the map, kept separate from the airport directory so the logic stays testable. */
data class LatLon(val lat: Double, val lon: Double)

object FlightProgressCalc {
    /** The Live Update appears this long before departure… */
    val SHOW_BEFORE: Duration = Duration.ofHours(3)
    /** …and stays this long after arrival, so "Landed" is seen. */
    val SHOW_AFTER: Duration = Duration.ofMinutes(30)
    private val BOARDING: Duration = Duration.ofMinutes(40)
    private val TAXI_OUT: Duration = Duration.ofMinutes(15)
    private val APPROACH: Duration = Duration.ofMinutes(20)
    /** Matches the radar's ETA rule: straight-line distance at ground speed plus 10 minutes. */
    private const val ARRIVAL_PAD_MS = 10 * 60_000L
    private const val AT_AIRPORT_M = 6_000.0
    private const val LANDING_KM = 90.0

    fun isInWindow(segment: FlightSegment, now: Instant): Boolean =
        !now.isBefore(segment.departure - SHOW_BEFORE) && now.isBefore(segment.arrival + SHOW_AFTER)

    fun compute(
        segment: FlightSegment,
        now: Instant,
        origin: LatLon? = null,
        destination: LatLon? = null,
        live: LiveAircraft? = null,
    ): FlightProgress {
        if (live != null && origin != null && destination != null) {
            fromLive(segment, now, origin, destination, live)?.let { return it }
        }
        return fromTimetable(segment, now)
    }

    private fun fromTimetable(s: FlightSegment, now: Instant): FlightProgress {
        val dep = s.departure
        val arr = s.arrival
        val phase = when {
            now.isBefore(dep - BOARDING) -> FlightPhase.Upcoming
            now.isBefore(dep) -> FlightPhase.Boarding
            now.isBefore(dep + TAXI_OUT) -> FlightPhase.Departing
            now.isBefore(arr - APPROACH) -> FlightPhase.Airborne
            now.isBefore(arr) -> FlightPhase.Landing
            else -> FlightPhase.Landed
        }
        val total = Duration.between(dep, arr).toMillis().coerceAtLeast(1)
        val done = Duration.between(dep, now).toMillis().toFloat() / total
        return FlightProgress(phase, done.coerceIn(0f, 1f), arr, live = false)
    }

    private fun fromLive(s: FlightSegment, now: Instant, o: LatLon, d: LatLon, ac: LiveAircraft): FlightProgress? {
        val toDest = Geo.distanceM(ac.lat, ac.lon, d.lat, d.lon)
        val fromOrigin = Geo.distanceM(o.lat, o.lon, ac.lat, ac.lon)
        val gs = ac.groundSpeedKt?.toInt()
        if (ac.onGround) {
            return when {
                toDest < AT_AIRPORT_M -> FlightProgress(FlightPhase.Landed, 1f, minOf(now, s.arrival), live = true, remainingKm = 0.0)
                fromOrigin < AT_AIRPORT_M -> FlightProgress(
                    if ((gs ?: 0) > 4) FlightPhase.Departing else FlightPhase.Boarding,
                    0f, s.arrival, live = true, groundSpeedKt = gs,
                )
                // On the ground somewhere else (a diversion, or a stale callsign): trust the timetable.
                else -> null
            }
        }
        val fraction = (fromOrigin / (fromOrigin + toDest).coerceAtLeast(1.0)).toFloat()
        val eta = if ((ac.groundSpeedKt ?: 0f) > 60f) {
            Instant.ofEpochMilli(now.toEpochMilli() + (toDest / (ac.groundSpeedKt!! * Geo.MPS_PER_KNOT) * 1000).toLong() + ARRIVAL_PAD_MS)
        } else {
            s.arrival
        }
        val descending = (ac.verticalRateFpm ?: 0) < -300
        val phase = if (toDest / 1000 < LANDING_KM && (descending || (ac.altitudeFt ?: 0) < 10_000)) FlightPhase.Landing else FlightPhase.Airborne
        return FlightProgress(phase, fraction.coerceIn(0f, 1f), eta, live = true, remainingKm = toDest / 1000, altitudeFt = ac.altitudeFt, groundSpeedKt = gs)
    }

    /** "1h 05m", "35m", "now": short enough for the status-bar chip. */
    fun shortDuration(d: Duration): String {
        val m = d.toMinutes().coerceAtLeast(0)
        return when {
            m == 0L -> "now"
            m < 60 -> "${m}m"
            else -> "${m / 60}h ${"%02d".format(m % 60)}m"
        }
    }
}

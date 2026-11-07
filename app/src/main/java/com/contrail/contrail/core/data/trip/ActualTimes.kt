package com.contrail.contrail.core.data.trip

import com.contrail.contrail.core.database.SegmentEntity
import com.contrail.contrail.core.data.model.LiveAircraft
import java.time.Instant
import java.time.ZoneId

/**
 * Writes real takeoff and touchdown times as Contrail watches your flight on ADS-B. The
 * airline's runway report (AeroDataBox) is more exact, so these only fill gaps.
 */
object ActualTimes {
    private const val MAX_CLIMB_MIN = 25.0

    /** The flight as it should be saved, or null when nothing new was seen. */
    fun observe(seg: SegmentEntity, ac: LiveAircraft?, progress: FlightProgress, nowMs: Long): SegmentEntity? {
        var s = seg
        if (ac != null && !ac.onGround && s.actualDepartureUtcMs == null && progress.phase >= FlightPhase.Departing) {
            // First seen airborne: work back along the climb to estimate wheels-up.
            val alt = ac.altitudeFt ?: 0
            val vr = ac.verticalRateFpm ?: 0
            val climbMin = if (vr > 300 && alt > 0) (alt.toDouble() / vr).coerceAtMost(MAX_CLIMB_MIN) else 0.0
            s = s.copy(actualDepartureUtcMs = nowMs - (climbMin * 60_000).toLong(), actualSource = s.actualSource ?: "adsb")
        }
        if (progress.live && progress.phase == FlightPhase.Landed && s.actualArrivalUtcMs == null && s.actualDepartureUtcMs != null) {
            s = s.copy(actualArrivalUtcMs = nowMs, actualSource = s.actualSource ?: "adsb")
        }
        s = replaceEstimates(s)
        return s.takeIf { it != seg }
    }

    /**
     * A flight whose times were only estimated (from a log import or distance) takes the real
     * ones once both ends are known, so the log and Passport show what happened.
     */
    fun replaceEstimates(s: SegmentEntity): SegmentEntity {
        val dep = s.actualDepartureUtcMs ?: return s
        val arr = s.actualArrivalUtcMs ?: return s
        if (!s.timesEstimated) return s
        fun local(ms: Long, zone: String) = Instant.ofEpochMilli(ms).atZone(ZoneId.of(zone)).toLocalDateTime().withSecond(0).withNano(0).toString()
        return s.copy(
            departureLocal = local(dep, s.departureZone), arrivalLocal = local(arr, s.arrivalZone),
            departureUtcMs = dep, arrivalUtcMs = arr, timesEstimated = false,
        )
    }
}

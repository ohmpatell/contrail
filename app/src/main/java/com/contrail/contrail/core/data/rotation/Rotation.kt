package com.contrail.contrail.core.data.rotation

import com.contrail.contrail.core.data.model.LiveAircraft
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** Relaxed / maybe / tight thresholds for the cockpit-visit badge, editable in Settings. */
data class TurnThresholds(val relaxedMinutes: Int = 120, val maybeMinutes: Int = 60)

enum class TurnBadge(val label: String, val advice: String) {
    Relaxed("Relaxed", "Good time to ask"),
    Maybe("Maybe", "Ask politely, accept a no"),
    Tight("Tight", "Don't ask"),
}

/** How long one past day's aircraft stayed at your destination after this flight number landed. */
@Serializable
data class TurnaroundDay(
    val date: String,
    val icao24: String? = null,
    val arrivedSec: Long? = null,
    /** Minutes on the ground before its next departure; null when it stayed 18 h or more. */
    val groundMinutes: Int? = null,
    /** True when the flight was seen arriving but didn't leave again within 18 hours. */
    val stayed: Boolean = false,
    val nextCallsign: String? = null,
    val nextTo: String? = null,
) {
    val seen: Boolean get() = icao24 != null
    val localDate: LocalDate get() = LocalDate.parse(date)
}

data class TurnaroundEstimate(val days: List<TurnaroundDay>) {
    val seenDays: List<TurnaroundDay> get() = days.filter { it.seen }

    /** The median ground time over the days it was seen; aircraft that stayed count as very long. */
    val typicalMinutes: Int?
        get() {
            val values = seenDays.map { it.groundMinutes ?: Int.MAX_VALUE }.sorted()
            if (values.isEmpty()) return null
            return values[values.size / 2].takeIf { it != Int.MAX_VALUE }
        }

    /** Most days the aircraft stays (overnight or longer) at your destination. */
    val usuallyStays: Boolean get() = seenDays.isNotEmpty() && seenDays.count { it.stayed } * 2 > seenDays.size

    /** The next leg flown most often after this flight. */
    val usualNextLeg: Pair<String, String?>?
        get() = seenDays.mapNotNull { d -> d.nextCallsign?.let { it to d.nextTo } }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

    fun badge(t: TurnThresholds): TurnBadge? {
        if (usuallyStays) return TurnBadge.Relaxed
        val m = typicalMinutes ?: return null
        return when {
            m >= t.relaxedMinutes -> TurnBadge.Relaxed
            m >= t.maybeMinutes -> TurnBadge.Maybe
            else -> TurnBadge.Tight
        }
    }
}

/** A leg the aircraft flew before yours (OpenSky history). Airports are IATA when known. */
data class EarlierLeg(
    val callsign: String?,
    val from: String?,
    val to: String?,
    val departedSec: Long,
    val arrivedSec: Long,
)

/** Where your aircraft is right now. */
data class LivePosition(
    val aircraft: LiveAircraft,
    val callsign: String?,
    val from: String?,
    val to: String?,
    /** Its current flight is heading to your departure airport: it's your inbound. */
    val isInbound: Boolean,
    /** Estimated arrival at its destination (great-circle distance at current ground speed, plus 10 minutes). */
    val etaMs: Long?,
)

data class Rotation(
    /** Built from sample data because OpenSky isn't set up yet. */
    val sample: Boolean,
    val tail: String?,
    val hex: String?,
    val now: LivePosition?,
    val earlierLegs: List<EarlierLeg>,
    val turnaround: TurnaroundEstimate?,
    val error: String? = null,
)

package com.contrail.contrail.feature.radar

import com.contrail.contrail.core.data.model.LiveAircraft
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Number formatting for radar readouts. Locale-fixed so numbers always read like an instrument. */
object RadarFormat {
    private val locale = Locale.US

    data class Reading(val value: String, val unit: String?)

    fun altitude(a: LiveAircraft): Reading = when {
        a.onGround -> Reading("GND", null)
        a.altitudeFt == null -> Reading("—", null)
        else -> Reading(String.format(locale, "%,d", a.altitudeFt), "ft")
    }

    fun flightLevel(altitudeFt: Int?): String? =
        altitudeFt?.takeIf { it >= 18_000 }?.let { "FL" + (it / 100).toString().padStart(3, '0') }

    fun speed(a: LiveAircraft): Reading =
        a.groundSpeedKt?.let { Reading(it.roundToInt().toString(), "kt") } ?: Reading("—", null)

    /** Signed, rounded to the nearest 64 fpm the transponder reports in. */
    fun verticalRate(fpm: Int?): Reading {
        if (fpm == null) return Reading("—", null)
        if (abs(fpm) < 64) return Reading("0", "fpm")
        val sign = if (fpm > 0) "+" else "−"
        return Reading(sign + String.format(locale, "%,d", abs(fpm)), "fpm")
    }

    fun heading(a: LiveAircraft): Reading =
        a.trackDeg?.let { Reading(it.roundToInt().mod(360).toString().padStart(3, '0'), "°") } ?: Reading("—", null)

    fun squawk(a: LiveAircraft): String = a.squawk ?: "----"

    fun ageLabel(nowMs: Long, thenMs: Long): String {
        val s = ((nowMs - thenMs) / 1000).coerceAtLeast(0)
        return when {
            s < 5 -> "just now"
            s < 60 -> "${s}s ago"
            s < 3600 -> "${s / 60}m ago"
            else -> "${s / 3600}h ago"
        }
    }
}

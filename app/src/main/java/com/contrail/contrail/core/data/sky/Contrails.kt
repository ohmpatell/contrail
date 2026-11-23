package com.contrail.contrail.core.data.sky

import kotlin.math.exp
import kotlin.math.ln

/** Whether a plane at one height leaves a contrail, and for how long. */
enum class ContrailOutlook(val label: String) {
    None("No contrails"),
    ShortLived("Short-lived contrails"),
    Possible("Contrails may linger"),
    LongLasting("Long-lasting contrails"),
}

/** One cruise height in one hour of the forecast. */
data class ContrailLevel(
    /** Pressure level, hPa (300 ≈ FL300, 250 ≈ FL340, 200 ≈ FL390). */
    val hPa: Int,
    val flightLevel: Int,
    val tempC: Double,
    /** Relative humidity over water, as the model gives it (%). */
    val rhWater: Double,
    /** The same air's humidity over ice (%): what decides whether a contrail lasts. */
    val rhIce: Double,
    /** The warmest air a contrail can still form in at this height and humidity (°C). */
    val thresholdC: Double,
    val outlook: ContrailOutlook,
)

/**
 * The Schmidt-Appleman criterion (Schumann 1996): the engine's hot, wet exhaust mixing with cold
 * air saturates over water, and a contrail forms, when the air is colder than a threshold set by
 * the pressure, the humidity and the engines' efficiency. It lasts when the air is saturated over
 * ice. Forecast humidity this high is the weakest part of any weather model, so the bands are
 * honest rather than precise.
 */
object ContrailPhysics {
    private const val EI_H2O = 1.25 // kg of water per kg of fuel
    private const val CP = 1004.0 // J/(kg K)
    private const val EPSILON = 0.622
    private const val Q = 43.2e6 // J/kg, kerosene
    /** Overall propulsion efficiency of a modern turbofan. */
    private const val ETA = 0.35

    /** Saturation vapour pressure over water, hPa (Magnus, WMO constants). */
    fun eSatWater(tC: Double): Double = 6.112 * exp(17.62 * tC / (243.12 + tC))

    /** Saturation vapour pressure over ice, hPa. */
    fun eSatIce(tC: Double): Double = 6.112 * exp(22.46 * tC / (272.62 + tC))

    /** Humidity over ice from humidity over water at the same temperature. */
    fun rhIce(rhWater: Double, tC: Double): Double = rhWater * eSatWater(tC) / eSatIce(tC)

    /** The mixing-line slope G in Pa/K at [hPa]. */
    fun g(hPa: Int): Double = EI_H2O * CP * hPa * 100.0 / (EPSILON * Q * (1 - ETA))

    /** The threshold temperature (°C) at [hPa] for air at [rhWater] %: colder than this, a contrail forms. */
    fun thresholdC(hPa: Int, rhWater: Double): Double {
        val gg = g(hPa)
        val x = ln(gg - 0.053)
        val tLm = -46.46 + 9.43 * x + 0.72 * x * x
        // Correct for air that isn't saturated: solve T = T_LM - (e(T_LM) - U·e(T)) / G by iteration.
        val u = (rhWater / 100.0).coerceIn(0.0, 1.0)
        var t = tLm
        repeat(20) { t = tLm - (eSatWater(tLm) - u * eSatWater(t)) * 100.0 / gg }
        return t
    }

    fun level(hPa: Int, tempC: Double, rhWater: Double): ContrailLevel {
        val threshold = thresholdC(hPa, rhWater)
        val ice = rhIce(rhWater, tempC)
        val outlook = when {
            tempC > threshold -> ContrailOutlook.None
            ice >= 100 -> ContrailOutlook.LongLasting
            ice >= 85 -> ContrailOutlook.Possible
            else -> ContrailOutlook.ShortLived
        }
        return ContrailLevel(hPa, flightLevelFor(hPa), tempC, rhWater, ice, threshold, outlook)
    }

    /** Standard-atmosphere flight level for a pressure level. */
    fun flightLevelFor(hPa: Int): Int = when (hPa) {
        300 -> 300
        250 -> 340
        200 -> 390
        else -> ((44_330.0 * (1 - Math.pow(hPa / 1013.25, 1 / 5.255)) * 3.28084) / 100).toInt()
    }

    /** The level nearest a plane's altitude, for "this one should be leaving a trail". */
    fun nearest(levels: List<ContrailLevel>, altitudeFt: Int?): ContrailLevel? {
        val alt = altitudeFt ?: return null
        if (alt < 25_000) return null
        return levels.minByOrNull { kotlin.math.abs(it.flightLevel * 100 - alt) }
    }
}

/** The contrail forecast for one place: each cruise level, hour by hour. */
data class ContrailForecast(
    val hours: List<Hour>,
    val fetchedAtMs: Long,
) {
    data class Hour(val timeMs: Long, val levels: List<ContrailLevel>) {
        /** The best (longest-lasting) outlook across cruise levels. */
        val best: ContrailOutlook get() = levels.maxOfOrNull { it.outlook } ?: ContrailOutlook.None
    }

    fun at(nowMs: Long): Hour? = hours.lastOrNull { it.timeMs <= nowMs } ?: hours.firstOrNull()
}

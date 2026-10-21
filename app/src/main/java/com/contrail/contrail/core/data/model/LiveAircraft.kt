package com.contrail.contrail.core.data.model

import com.contrail.contrail.core.data.reference.AircraftType
import com.contrail.contrail.core.data.reference.AircraftTypes
import com.contrail.contrail.core.data.reference.Airline
import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.core.data.reference.Rarity
import com.contrail.contrail.core.data.reference.Silhouette

/** One aircraft's latest known state from a live ADS-B feed. */
data class LiveAircraft(
    val hex: String,
    val callsign: String?,
    val registration: String?,
    val typeCode: String?,
    val typeDescription: String?,
    val category: String?,
    val lat: Double,
    val lon: Double,
    /** Barometric altitude in feet; `null` when unknown or on the ground. */
    val altitudeFt: Int?,
    val geometricAltitudeFt: Int?,
    val onGround: Boolean,
    val groundSpeedKt: Float?,
    val trackDeg: Float?,
    /** Rate of turn, degrees per second (positive = right). */
    val trackRateDegS: Float?,
    val verticalRateFpm: Int?,
    val squawk: String?,
    val emergency: String?,
    val military: Boolean,
    /** Wall-clock time (ms) the position was valid. */
    val positionTimeMs: Long,
    /** Wall-clock time (ms) we received it. */
    val fetchedAtMs: Long,
    val source: String,
) {
    val airline: Airline? get() = Airlines.fromCallsign(callsign)
    val type: AircraftType? get() = AircraftTypes.byIcao(typeCode)
    val rarity: Rarity get() = AircraftTypes.rarityFor(typeCode, airline)
    val silhouette: Silhouette get() = AircraftTypes.silhouetteFor(typeCode, category)

    /** Emergency squawks (hijack, radio failure, general emergency) or a declared emergency. */
    val isEmergency: Boolean
        get() = squawk in EMERGENCY_SQUAWKS || (emergency != null && emergency != "none")

    val displayCallsign: String get() = callsign?.trim()?.takeIf { it.isNotEmpty() } ?: registration ?: hex.uppercase()

    val typeName: String? get() = type?.name ?: typeDescription

    companion object {
        val EMERGENCY_SQUAWKS = setOf("7500", "7600", "7700")

        fun emergencyLabel(squawk: String?): String? = when (squawk) {
            "7500" -> "Hijack"
            "7600" -> "Radio failure"
            "7700" -> "Emergency"
            else -> null
        }
    }
}

data class TrailPoint(
    val hex: String,
    val timeMs: Long,
    val lat: Double,
    val lon: Double,
    val altitudeFt: Int?,
    val onGround: Boolean,
    val verticalRateFpm: Int?,
)

data class AirportRef(
    val iata: String?,
    val icao: String?,
    val name: String,
    val city: String?,
    val countryIso: String?,
    val lat: Double,
    val lon: Double,
) {
    val code: String get() = iata ?: icao ?: "???"
}

/** A route derived from the callsign. Never treated as confirmed. */
data class FlightRoute(
    val callsign: String,
    val origin: AirportRef,
    val destination: AirportRef,
    val airlineName: String?,
)

/** A Planespotters photo. Credit and link must always be shown with it. */
data class AircraftPhoto(
    val thumbnailUrl: String,
    val largeUrl: String,
    val pageUrl: String,
    val photographer: String,
)

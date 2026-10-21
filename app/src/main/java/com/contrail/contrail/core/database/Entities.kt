package com.contrail.contrail.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.contrail.contrail.core.data.model.AircraftPhoto
import com.contrail.contrail.core.data.model.AirportRef
import com.contrail.contrail.core.data.model.FlightRoute
import com.contrail.contrail.core.data.model.LiveAircraft
import com.contrail.contrail.core.data.model.TrailPoint

@Entity(tableName = "live_aircraft", indices = [Index("fetchedAtMs")])
data class LiveAircraftEntity(
    @PrimaryKey val hex: String,
    val callsign: String?,
    val registration: String?,
    val typeCode: String?,
    val typeDescription: String?,
    val category: String?,
    val lat: Double,
    val lon: Double,
    val altitudeFt: Int?,
    val geometricAltitudeFt: Int?,
    val onGround: Boolean,
    val groundSpeedKt: Float?,
    val trackDeg: Float?,
    val trackRateDegS: Float?,
    val verticalRateFpm: Int?,
    val squawk: String?,
    val emergency: String?,
    val military: Boolean,
    val positionTimeMs: Long,
    val fetchedAtMs: Long,
    val source: String,
)

fun LiveAircraft.toEntity() = LiveAircraftEntity(
    hex, callsign, registration, typeCode, typeDescription, category, lat, lon, altitudeFt, geometricAltitudeFt,
    onGround, groundSpeedKt, trackDeg, trackRateDegS, verticalRateFpm, squawk, emergency, military,
    positionTimeMs, fetchedAtMs, source,
)

fun LiveAircraftEntity.toModel() = LiveAircraft(
    hex, callsign, registration, typeCode, typeDescription, category, lat, lon, altitudeFt, geometricAltitudeFt,
    onGround, groundSpeedKt, trackDeg, trackRateDegS, verticalRateFpm, squawk, emergency, military,
    positionTimeMs, fetchedAtMs, source,
)

@Entity(tableName = "trail_point", primaryKeys = ["hex", "timeMs"], indices = [Index("timeMs")])
data class TrailPointEntity(
    val hex: String,
    val timeMs: Long,
    val lat: Double,
    val lon: Double,
    val altitudeFt: Int?,
    val onGround: Boolean,
    val verticalRateFpm: Int?,
    val trackDeg: Float? = null,
)

fun TrailPointEntity.toModel() = TrailPoint(hex, timeMs, lat, lon, altitudeFt, onGround, verticalRateFpm)

/** Cached callsign route. [found] = false caches a miss so we don't re-ask too often. */
@Entity(tableName = "route_cache")
data class RouteEntity(
    @PrimaryKey val callsign: String,
    val found: Boolean,
    val fetchedAtMs: Long,
    val airlineName: String? = null,
    val originIata: String? = null,
    val originIcao: String? = null,
    val originName: String? = null,
    val originCity: String? = null,
    val originCountry: String? = null,
    val originLat: Double? = null,
    val originLon: Double? = null,
    val destIata: String? = null,
    val destIcao: String? = null,
    val destName: String? = null,
    val destCity: String? = null,
    val destCountry: String? = null,
    val destLat: Double? = null,
    val destLon: Double? = null,
) {
    fun toModel(): FlightRoute? {
        if (!found) return null
        return FlightRoute(
            callsign = callsign,
            origin = AirportRef(originIata, originIcao, originName ?: "", originCity, originCountry, originLat ?: 0.0, originLon ?: 0.0),
            destination = AirportRef(destIata, destIcao, destName ?: "", destCity, destCountry, destLat ?: 0.0, destLon ?: 0.0),
            airlineName = airlineName,
        )
    }

    companion object {
        fun miss(callsign: String, now: Long) = RouteEntity(callsign, found = false, fetchedAtMs = now)

        fun from(route: FlightRoute, now: Long) = RouteEntity(
            callsign = route.callsign.uppercase(),
            found = true,
            fetchedAtMs = now,
            airlineName = route.airlineName,
            originIata = route.origin.iata, originIcao = route.origin.icao, originName = route.origin.name,
            originCity = route.origin.city, originCountry = route.origin.countryIso,
            originLat = route.origin.lat, originLon = route.origin.lon,
            destIata = route.destination.iata, destIcao = route.destination.icao, destName = route.destination.name,
            destCity = route.destination.city, destCountry = route.destination.countryIso,
            destLat = route.destination.lat, destLon = route.destination.lon,
        )
    }
}

@Entity(tableName = "photo_cache")
data class PhotoEntity(
    @PrimaryKey val hex: String,
    val found: Boolean,
    val fetchedAtMs: Long,
    val thumbnailUrl: String? = null,
    val largeUrl: String? = null,
    val pageUrl: String? = null,
    val photographer: String? = null,
) {
    fun toModel(): AircraftPhoto? =
        if (found && largeUrl != null && pageUrl != null && photographer != null) {
            AircraftPhoto(thumbnailUrl ?: largeUrl, largeUrl, pageUrl, photographer)
        } else {
            null
        }
}

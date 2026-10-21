package com.contrail.contrail.core.network.traffic

import com.contrail.contrail.core.data.model.LiveAircraft
import com.contrail.contrail.core.network.RequestThrottle
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Any service speaking the readsb JSON API (adsb.lol, adsb.fi, airplanes.live). They differ
 * only in URL shape, which [urlFor] builds from latitude, longitude and radius in nm.
 */
class ReadsbApiSource(
    override val name: String,
    private val client: HttpClient,
    private val urlFor: (lat: String, lon: String, radiusNm: Int) -> String,
    private val throttle: RequestThrottle = RequestThrottle(minIntervalMs = 1_100),
    private val clock: () -> Long = System::currentTimeMillis,
) : LiveTrafficSource {

    override suspend fun fetchAround(lat: Double, lon: Double, radiusNm: Int): LiveSnapshot {
        val radius = radiusNm.coerceIn(1, MAX_RADIUS_NM)
        val url = urlFor(String.format(Locale.US, "%.4f", lat), String.format(Locale.US, "%.4f", lon), radius)
        val response = throttle.run { client.get(url) }
        if (response.status == HttpStatusCode.TooManyRequests) {
            val retryAfterS = response.headers[HttpHeaders.RetryAfter]?.toLongOrNull() ?: DEFAULT_RETRY_AFTER_S
            throw LiveTrafficException("$name rate-limited us", retryAfterMs = retryAfterS * 1000)
        }
        if (!response.status.isSuccess()) {
            throw LiveTrafficException("$name returned HTTP ${response.status.value}")
        }
        val receivedAt = clock()
        val body: ReadsbResponse = response.body()
        return LiveSnapshot(
            aircraft = body.ac.orEmpty().mapNotNull { it.toLiveAircraft(receivedAt, name) },
            receivedAtMs = receivedAt,
            source = name,
        )
    }

    companion object {
        /** The point queries are capped at 250 nautical miles. */
        const val MAX_RADIUS_NM = 250
        private const val DEFAULT_RETRY_AFTER_S = 120L

        /** adsb.lol: 4/s, 60/min and 500/day per client, so it is the backup feed. */
        fun adsbLol(client: HttpClient) = ReadsbApiSource("adsb.lol", client, urlFor = { lat, lon, r ->
            "https://api.adsb.lol/v2/point/$lat/$lon/$r"
        })

        /** adsb.fi open data: 1 request per second, personal use, cite adsb.fi. */
        fun adsbFi(client: HttpClient) = ReadsbApiSource("adsb.fi", client, urlFor = { lat, lon, r ->
            "https://opendata.adsb.fi/api/v3/lat/$lat/lon/$lon/dist/$r"
        })

        /** Needs approval from airplanes.live since 2026 (HTTP 403 otherwise); kept for later. */
        fun airplanesLive(client: HttpClient) = ReadsbApiSource("airplanes.live", client, urlFor = { lat, lon, r ->
            "https://api.airplanes.live/v2/point/$lat/$lon/$r"
        })
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class ReadsbResponse(
    // adsb.lol and airplanes.live call it "ac", adsb.fi "aircraft".
    @JsonNames("aircraft") val ac: List<ReadsbAircraft>? = null,
    /** Milliseconds on adsb.lol, fractional seconds on adsb.fi; unused but must parse. */
    val now: Double? = null,
    val total: Int? = null,
    val msg: String? = null,
)

@Serializable
internal data class ReadsbAircraft(
    val hex: String,
    val flight: String? = null,
    @SerialName("r") val registration: String? = null,
    @SerialName("t") val type: String? = null,
    val desc: String? = null,
    @SerialName("alt_baro") val altBaro: JsonElement? = null,
    @SerialName("alt_geom") val altGeom: Int? = null,
    val gs: Float? = null,
    val track: Float? = null,
    @SerialName("true_heading") val trueHeading: Float? = null,
    @SerialName("track_rate") val trackRate: Float? = null,
    @SerialName("baro_rate") val baroRate: Int? = null,
    @SerialName("geom_rate") val geomRate: Int? = null,
    val squawk: String? = null,
    val emergency: String? = null,
    val category: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    @SerialName("seen_pos") val seenPos: Float? = null,
    val seen: Float? = null,
    val dbFlags: Int? = null,
) {
    fun toLiveAircraft(receivedAt: Long, source: String): LiveAircraft? {
        val la = lat ?: return null
        val lo = lon ?: return null
        val alt = altBaro as? JsonPrimitive
        val onGround = alt?.isString == true && alt.content.equals("ground", ignoreCase = true)
        val ageMs = ((seenPos ?: seen ?: 0f) * 1000f).roundToLong()
        return LiveAircraft(
            hex = hex.removePrefix("~").lowercase(),
            callsign = flight?.trim()?.takeIf { it.isNotEmpty() },
            registration = registration?.trim()?.takeIf { it.isNotEmpty() },
            typeCode = type?.trim()?.uppercase()?.takeIf { it.isNotEmpty() },
            typeDescription = desc,
            category = category,
            lat = la,
            lon = lo,
            altitudeFt = if (onGround) null else alt?.intOrNull ?: altGeom,
            geometricAltitudeFt = altGeom,
            onGround = onGround,
            groundSpeedKt = gs,
            trackDeg = track ?: trueHeading,
            trackRateDegS = trackRate,
            verticalRateFpm = baroRate ?: geomRate,
            squawk = squawk,
            emergency = emergency,
            military = dbFlags?.and(1) == 1,
            positionTimeMs = receivedAt - ageMs,
            fetchedAtMs = receivedAt,
            source = source,
        )
    }
}

package com.contrail.contrail.core.network.photo

import com.contrail.contrail.core.data.model.AircraftPhoto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Real photos of an exact airframe. */
interface PhotoSource {
    /** Latest photo for the airframe, or null if none exists. Throws on network errors. */
    suspend fun photoFor(hex: String, registration: String?): AircraftPhoto?
}

/** Planespotters.net public photo API: free, no key, credit and link-back required. */
class PlanespottersPhotoSource(
    private val client: HttpClient,
    private val baseUrl: String = "https://api.planespotters.net/pub/photos",
) : PhotoSource {

    override suspend fun photoFor(hex: String, registration: String?): AircraftPhoto? {
        lookup("$baseUrl/hex/${hex.lowercase().encodeURLPathPart()}")?.let { return it }
        if (registration.isNullOrBlank()) return null
        return lookup("$baseUrl/reg/${registration.trim().uppercase().encodeURLPathPart()}")
    }

    private suspend fun lookup(url: String): AircraftPhoto? {
        val response = client.get(url)
        if (!response.status.isSuccess()) error("Planespotters returned HTTP ${response.status.value}")
        val body: PlanespottersResponse = response.body()
        val photo = body.photos.firstOrNull() ?: return null
        return AircraftPhoto(
            thumbnailUrl = photo.thumbnail?.src ?: photo.thumbnailLarge.src,
            largeUrl = photo.thumbnailLarge.src,
            pageUrl = photo.link,
            photographer = photo.photographer,
        )
    }
}

@Serializable
internal data class PlanespottersResponse(val photos: List<PlanespottersPhoto> = emptyList())

@Serializable
internal data class PlanespottersPhoto(
    val id: String,
    val thumbnail: PlanespottersImage? = null,
    @SerialName("thumbnail_large") val thumbnailLarge: PlanespottersImage,
    val link: String,
    val photographer: String,
)

@Serializable
internal data class PlanespottersImage(val src: String)

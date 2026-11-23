package com.contrail.contrail.core.network.wiki

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/** A short description of a place from Wikipedia. */
data class WikiSummary(val title: String, val description: String?, val extract: String)

/**
 * Wikipedia's free REST summaries (CC BY-SA), for the places below a flight. Requests carry the
 * app's User-Agent, as Wikimedia asks. Disambiguation pages are skipped rather than guessed at.
 */
class WikipediaSource(private val client: HttpClient, private val baseUrl: String = "https://en.wikipedia.org/api/rest_v1") {
    suspend fun summary(title: String): WikiSummary? {
        val response = client.get("$baseUrl/page/summary/${title.replace(' ', '_').encodeURLPathPart()}")
        if (!response.status.isSuccess()) return null
        val body: WikiPage = response.body()
        if (body.type == "disambiguation" || body.extract.isNullOrBlank()) return null
        return WikiSummary(body.title, body.description, firstSentences(body.extract, 2))
    }

    companion object {
        /** The first [n] sentences, so a blurb stays a blurb. */
        fun firstSentences(text: String, n: Int): String {
            val parts = Regex("(?<=[.!?])\\s+(?=[A-Z])").split(text.trim())
            return parts.take(n).joinToString(" ")
        }
    }
}

@Serializable
internal data class WikiPage(val type: String? = null, val title: String = "", val description: String? = null, val extract: String? = null)

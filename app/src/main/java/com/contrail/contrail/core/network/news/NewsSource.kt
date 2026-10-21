package com.contrail.contrail.core.network.news

import android.util.Xml
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLQueryComponent
import io.ktor.http.isSuccess
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class NewsArticle(
    val title: String,
    val summary: String,
    val link: String,
    val source: String,
    val imageUrl: String?,
    val publishedAtMs: Long,
)

/** A news feed: the general aviation mix, or a search for one airline or airport. */
sealed interface NewsFeed {
    val key: String

    data object Aviation : NewsFeed {
        override val key = "aviation"
    }

    /** [query] is a display name like "Etihad Airways" or "Toronto Pearson". */
    data class About(val query: String) : NewsFeed {
        override val key = "about:" + query.lowercase()
    }
}

/** Free aviation RSS: publisher feeds for the general mix, Google News RSS for searches. */
class RssNewsSource(private val client: HttpClient) {

    data class Publisher(val name: String, val url: String)

    val publishers = listOf(
        Publisher("Simple Flying", "https://simpleflying.com/feed/"),
        Publisher("AeroTime", "https://www.aerotime.aero/feed"),
        Publisher("FlightGlobal", "https://www.flightglobal.com/rss"),
        Publisher("AirlineGeeks", "https://airlinegeeks.com/feed/"),
        Publisher("Aviation Week", "https://aviationweek.com/rss.xml"),
    )

    suspend fun fetch(feed: NewsFeed): List<NewsArticle> = when (feed) {
        NewsFeed.Aviation -> publishers.flatMap { p -> runCatching { fetchFeed(p.url, p.name) }.getOrDefault(emptyList()) }
        is NewsFeed.About -> {
            val q = "\"${feed.query}\" (airline OR airport OR aircraft OR flight)".encodeURLQueryComponent()
            fetchFeed("https://news.google.com/rss/search?q=$q&hl=en-CA&gl=CA&ceid=CA:en", publisher = null)
        }
    }.distinctBy { it.link }.sortedByDescending { it.publishedAtMs }

    private suspend fun fetchFeed(url: String, publisher: String?): List<NewsArticle> {
        val response = client.get(url)
        if (!response.status.isSuccess()) return emptyList()
        return RssParser.parse(response.bodyAsText(), publisher)
    }
}

/** Minimal RSS 2.0 / Atom reader: title, link, summary, source, image, date. */
object RssParser {
    fun parse(xml: String, publisher: String?): List<NewsArticle> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(StringReader(xml))
        }
        val out = ArrayList<NewsArticle>()
        var inItem = false
        var title = ""
        var link = ""
        var summary = ""
        var source: String? = null
        var image: String? = null
        var date = 0L
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (val name = parser.name) {
                    "item", "entry" -> {
                        inItem = true
                        title = ""; link = ""; summary = ""; source = null; image = null; date = 0L
                    }
                    else -> if (inItem) when (name) {
                        "title" -> title = clean(parser.nextText())
                        "link" -> {
                            val href = parser.getAttributeValue(null, "href")
                            link = href ?: parser.nextText().trim()
                        }
                        "description", "summary", "content:encoded" -> if (summary.isEmpty()) {
                            val raw = parser.nextText()
                            if (image == null) image = firstImage(raw)
                            summary = clean(raw).take(280)
                        }
                        "pubDate", "published", "updated", "dc:date" -> if (date == 0L) date = parseDate(parser.nextText())
                        "source" -> source = clean(parser.nextText())
                        "media:content", "media:thumbnail", "enclosure" -> {
                            val u = parser.getAttributeValue(null, "url")
                            val type = parser.getAttributeValue(null, "type")
                            if (image == null && u != null && (type == null || type.startsWith("image"))) image = u
                        }
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "item" || parser.name == "entry") {
                    inItem = false
                    if (title.isNotBlank() && link.isNotBlank()) {
                        // Google News titles end with " - Publisher".
                        val pub = publisher ?: source ?: title.substringAfterLast(" - ", "").ifBlank { "News" }
                        val cleanTitle = if (publisher == null && title.endsWith(" - $pub")) title.removeSuffix(" - $pub") else title
                        out += NewsArticle(cleanTitle, summary, link, pub, image, date)
                    }
                }
            }
            event = parser.next()
        }
        return out
    }

    private val tags = Regex("<[^>]+>")
    private val img = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    private fun firstImage(html: String): String? = img.find(html)?.groupValues?.get(1)

    internal fun clean(s: String): String = tags.replace(s, " ")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&#8217;", "’").replace("&#8216;", "‘")
        .replace("&#8220;", "“").replace("&#8221;", "”").replace("&#8211;", "–").replace("&#038;", "&")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
        .replace(Regex("\\s+"), " ").trim()

    internal fun parseDate(raw: String): Long {
        val s = raw.trim()
        return runCatching { ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }
            .getOrDefault(0L)
    }
}

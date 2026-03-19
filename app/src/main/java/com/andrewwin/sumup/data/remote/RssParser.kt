package com.andrewwin.sumup.data.remote

import com.andrewwin.sumup.data.local.entities.Article
import android.util.Log
import com.prof18.rssparser.RssParser as ProfRssParser
import com.prof18.rssparser.RssParserBuilder
import com.prof18.rssparser.model.RssChannel
import com.prof18.rssparser.model.RssItem
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

class RssParser @Inject constructor(
    okHttpClient: OkHttpClient
) {
    private val tag = "RssParser"
    private val parser: ProfRssParser = RssParserBuilder(callFactory = okHttpClient).build()
    private val rssDateFormats = listOf(
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).apply { isLenient = false },
        SimpleDateFormat("EEE, dd MMM yy HH:mm:ss Z", Locale.US).apply { isLenient = false },
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { isLenient = false }
    )

    suspend fun parseUrl(url: String, sourceId: Long): List<Article> {
        return runCatching {
            Log.d(tag, "parseUrl: $url")
            val channel = parser.getRssChannel(url)
            val mapped = mapChannel(channel, sourceId)
            Log.d(tag, "parseUrl: items=${mapped.size} title=${channel.title.orEmpty()}")
            mapped
        }.getOrElse { e ->
            Log.e(tag, "parseUrl failed for $url: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun parseXml(xml: String, sourceId: Long): List<Article> {
        return runCatching {
            val channel = parser.parse(xml)
            mapChannel(channel, sourceId)
        }.getOrElse { e ->
            Log.e(tag, "parseXml failed: ${e.message}", e)
            emptyList()
        }
    }

    private fun mapChannel(channel: RssChannel, sourceId: Long): List<Article> {
        return channel.items.orEmpty().mapNotNull { item ->
            mapItem(item, sourceId)
        }
    }

    private fun mapItem(item: RssItem, sourceId: Long): Article? {
        val title = item.title.orEmpty()
        val link = item.link.orEmpty()
        val guid = item.guid.orEmpty()
        val description = item.description.orEmpty()
        val content = item.content.orEmpty()
        val pubDate = parseRssDate(item.pubDate.orEmpty())
        val rawUrl = if (guid.startsWith("http")) guid else link
        if (rawUrl.isBlank()) return null
        val cleanUrl = rawUrl.substringBefore("#").substringBefore("?")

        val mediaUrl = extractImageFromHtml(content.ifBlank { description })

        return Article(
            sourceId = sourceId,
            title = title,
            content = if (content.isNotBlank()) content else description,
            mediaUrl = mediaUrl,
            url = cleanUrl,
            publishedAt = if (pubDate == 0L) System.currentTimeMillis() else pubDate
        )
    }

    private fun parseRssDate(dateString: String): Long {
        val trimmed = dateString.trim()
        val formats = when {
            Regex(",\\s\\d{2}\\s\\p{Alpha}{3}\\s\\d{2}\\s").containsMatchIn(trimmed) ->
                listOf(rssDateFormats[1], rssDateFormats[0], rssDateFormats[2])
            Regex(",\\s\\d{2}\\s\\p{Alpha}{3}\\s\\d{4}\\s").containsMatchIn(trimmed) ->
                listOf(rssDateFormats[0], rssDateFormats[1], rssDateFormats[2])
            else -> rssDateFormats
        }
        for (format in formats) {
            try {
                val parsed = format.parse(trimmed)
                if (parsed != null) {
                    val ts = parsed.time
                    return if (ts >= MIN_REASONABLE_TIMESTAMP) ts else 0L
                }
            } catch (_: Exception) { }
        }
        return 0L
    }

    private fun extractImageFromHtml(html: String): String? {
        val regex = Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        return regex.find(html)?.groups?.get(1)?.value
    }

    companion object {
        private const val MIN_REASONABLE_TIMESTAMP = 946684800000L // 2000-01-01
    }
}

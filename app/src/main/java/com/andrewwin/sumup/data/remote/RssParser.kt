package com.andrewwin.sumup.data.remote

import com.andrewwin.sumup.data.local.entities.Article
import com.prof18.rssparser.RssParserBuilder
import com.prof18.rssparser.model.RssChannel
import com.prof18.rssparser.model.RssItem
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import com.prof18.rssparser.RssParser as ProfRssParser

class RssParser @Inject constructor(
    okHttpClient: OkHttpClient
) {
    private val tag = "RssParser"
    private val parser: ProfRssParser = RssParserBuilder(callFactory = okHttpClient).build()
    private val formattersThreadLocal = ThreadLocal.withInitial {
        listOf(
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US),
            SimpleDateFormat("EEE, dd MMM yy HH:mm:ss Z", Locale.US),
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        ).onEach { it.timeZone = java.util.TimeZone.getTimeZone("UTC") }
    }

    suspend fun parseUrl(url: String, sourceId: Long): List<Article> {
        return runCatching {
            val channel = parser.getRssChannel(url)
            val mapped = mapChannel(channel, sourceId)
            mapped
        }.getOrElse { e ->
            emptyList()
        }
    }

    suspend fun parseXml(xml: String, sourceId: Long): List<Article> {
        return runCatching {
            val channel = parser.parse(xml)
            mapChannel(channel, sourceId)
        }.getOrElse { e ->
            emptyList()
        }
    }

    private fun mapChannel(channel: com.prof18.rssparser.model.RssChannel, sourceId: Long): List<Article> {
        return channel.items.orEmpty().mapNotNull { item ->
            mapItem(item, sourceId)
        }
    }

    private fun mapItem(item: com.prof18.rssparser.model.RssItem, sourceId: Long): Article? {
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
            content = if (description.isNotBlank()) description else content,
            mediaUrl = mediaUrl,
            url = cleanUrl,
            publishedAt = if (pubDate == 0L) System.currentTimeMillis() else pubDate
        )
    }

    private fun parseRssDate(dateString: String): Long {
        val trimmed = dateString.trim()
        val formatters = formattersThreadLocal.get()
        for (f in formatters) {
            val date = runCatching { f.parse(trimmed) }.getOrNull()
            if (date != null) {
                val time = date.time
                if (time >= MIN_REASONABLE_TIMESTAMP) return time
            }
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







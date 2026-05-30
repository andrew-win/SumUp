package com.andrewwin.sumup.data.remote.sources.rss

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.remote.sources.ArticleStableKeyFactory
import com.prof18.rssparser.RssParser
import com.prof18.rssparser.RssParserBuilder
import com.prof18.rssparser.model.RssChannel
import com.prof18.rssparser.model.RssItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

class RssParser @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val parser: RssParser = RssParserBuilder(callFactory = okHttpClient).build()
    private val formattersThreadLocal = ThreadLocal.withInitial {
        listOf(
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US),
            SimpleDateFormat("EEE, dd MMM yy HH:mm:ss Z", Locale.US),
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        ).onEach { it.timeZone = TimeZone.getTimeZone("UTC") }
    }

    suspend fun parseUrlResult(url: String, sourceId: Long): Result<List<Article>> {
        return runCatching {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("RSS request failed with code ${response.code}")
                }

                val xml = response.body?.string().orEmpty()
                if (xml.isBlank()) return@runCatching emptyList()

                withContext(Dispatchers.Default) {
                    val channel = parser.parse(xml)
                    mapChannel(channel, sourceId)
                }
            }
        }
    }


    suspend fun parseChannelTitleUrl(url: String): String? {
        return runCatching {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val xml = response.body?.string().orEmpty()
                if (xml.isBlank()) return@use null
                withContext(Dispatchers.Default) {
                    parser.parse(xml).title.orEmpty().cleanChannelTitle()
                }
            }
        }.getOrNull()
    }

    suspend fun parseChannelTitleXml(xml: String): String? {
        return runCatching {
            withContext(Dispatchers.Default) {
                parser.parse(xml).title.orEmpty().cleanChannelTitle()
            }
        }.getOrNull()
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
        val cleanUrl = rawUrl.substringBefore("#")

        val mediaUrl = extractImageFromHtml(content.ifBlank { description })

        return Article(
            stableArticleKey = ArticleStableKeyFactory.buildRssKey(
                sourceId = sourceId,
                guid = guid,
                url = cleanUrl
            ),
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
        val formatters = formattersThreadLocal.get().orEmpty()
        for (formatter in formatters) {
            val date = runCatching { formatter.parse(trimmed) }.getOrNull()
            if (date != null) {
                val time = date.time
                if (time >= MIN_REASONABLE_TIMESTAMP) return time
            }
        }
        return 0L
    }

    private fun extractImageFromHtml(html: String): String? =
        IMAGE_SRC_REGEX.find(html)?.groups?.get(1)?.value

    private fun String.cleanChannelTitle(): String? {
        val cleaned = trim()
            .removeSurrounding("<![CDATA[", "]]>")
            .trim()
        return cleaned.ifBlank { null }
    }

    companion object {
        private const val MIN_REASONABLE_TIMESTAMP = 946684800000L
        private val IMAGE_SRC_REGEX = Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    }
}
package com.andrewwin.sumup.data.remote

import com.andrewwin.sumup.data.local.dao.SourceHttpCacheDao
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.SourceHttpCache
import com.prof18.rssparser.RssParserBuilder
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import com.prof18.rssparser.RssParser as ProfRssParser

class RssParser @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val sourceHttpCacheDao: SourceHttpCacheDao? = null
) {
    private val parser: ProfRssParser = RssParserBuilder(callFactory = okHttpClient).build()
    private val formattersThreadLocal = ThreadLocal.withInitial {
        listOf(
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US),
            SimpleDateFormat("EEE, dd MMM yy HH:mm:ss Z", Locale.US),
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        ).onEach { it.timeZone = java.util.TimeZone.getTimeZone("UTC") }
    }
    private val responseHeadersCache = ConcurrentHashMap<String, CachedRssHeaders>()

    suspend fun parseUrl(url: String, sourceId: Long): List<Article> =
        parseUrlResult(url, sourceId).getOrDefault(emptyList())

    suspend fun parseUrlResult(url: String, sourceId: Long): Result<List<Article>> {
        return runCatching {
            val requestBuilder = Request.Builder().url(url)
            val cachedHeaders = responseHeadersCache[url] ?: sourceHttpCacheDao
                ?.getByUrl(url)
                ?.let { cached ->
                    CachedRssHeaders(
                        etag = cached.etag,
                        lastModified = cached.lastModified
                    )
                }
            cachedHeaders?.etag?.let { requestBuilder.header(HEADER_IF_NONE_MATCH, it) }
            cachedHeaders?.lastModified?.let { requestBuilder.header(HEADER_IF_MODIFIED_SINCE, it) }
            if (cachedHeaders != null) {
                responseHeadersCache[url] = cachedHeaders
            }

            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (response.code == HTTP_NOT_MODIFIED) {
                    return@runCatching emptyList()
                }
                if (!response.isSuccessful) {
                    error("RSS request failed with code ${response.code}")
                }

                val responseHeaders = CachedRssHeaders(
                    etag = response.header(HEADER_ETAG),
                    lastModified = response.header(HEADER_LAST_MODIFIED)
                )
                responseHeadersCache[url] = responseHeaders
                sourceHttpCacheDao?.upsert(
                    SourceHttpCache(
                        url = url,
                        etag = responseHeaders.etag,
                        lastModified = responseHeaders.lastModified
                    )
                )
                val xml = response.body?.string().orEmpty()
                if (xml.isBlank()) return@runCatching emptyList()

                val channel = parser.parse(xml)
                mapChannel(channel, sourceId)
            }
        }
    }

    suspend fun parseXml(xml: String, sourceId: Long): List<Article> {
        return runCatching {
            val channel = parser.parse(xml)
            mapChannel(channel, sourceId)
        }.getOrElse {
            emptyList()
        }
    }

    suspend fun parseChannelTitleUrl(url: String): String? {
        return runCatching {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val xml = response.body?.string().orEmpty()
                if (xml.isBlank()) return@use null
                parser.parse(xml).title.orEmpty().cleanChannelTitle()
            }
        }.getOrNull()
    }

    suspend fun parseChannelTitleXml(xml: String): String? {
        return runCatching {
            parser.parse(xml).title.orEmpty().cleanChannelTitle()
        }.getOrNull()
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

    private data class CachedRssHeaders(
        val etag: String?,
        val lastModified: String?
    )

    companion object {
        private const val HTTP_NOT_MODIFIED = 304
        private const val HEADER_ETAG = "ETag"
        private const val HEADER_LAST_MODIFIED = "Last-Modified"
        private const val HEADER_IF_NONE_MATCH = "If-None-Match"
        private const val HEADER_IF_MODIFIED_SINCE = "If-Modified-Since"
        private const val MIN_REASONABLE_TIMESTAMP = 946684800000L
        private val IMAGE_SRC_REGEX = Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    }
}

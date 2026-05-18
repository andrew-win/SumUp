package com.andrewwin.sumup.data.remote

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.local.entities.SourceType
import io.github.thoroldvix.api.TranscriptApiFactory
import io.github.thoroldvix.api.TranscriptContent
import io.github.thoroldvix.api.YoutubeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import javax.inject.Inject

class RemoteArticleDataSource @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val displayNameOkHttpClient: OkHttpClient,
    private val rssParser: RssParser,
    private val telegramParser: TelegramParser,
    private val youtubeParser: YouTubeParser
) {

    private val displayNameRssParser = RssParser(displayNameOkHttpClient)

    private inner class OkHttpYoutubeClient(private val client: OkHttpClient) : YoutubeClient {
        override fun get(url: String, headers: Map<String, String>): String {
            val request = Request.Builder().url(url).apply {
                headers.forEach { (name, value) -> addHeader(name, value) }
            }.build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("YouTube request failed")
                return response.body?.string() ?: ""
            }
        }

        override fun post(url: String, json: String): String {
            val body = RequestBody.create("application/json".toMediaType(), json)
            val request = Request.Builder().url(url).post(body).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("YouTube request failed")
                return response.body?.string() ?: ""
            }
        }
    }

    private val youtubeTranscriptApi = TranscriptApiFactory.createWithClient(OkHttpYoutubeClient(okHttpClient))

    suspend fun fetchArticles(
        source: Source,
        oldestAllowedPublishedAt: Long? = null
    ): List<Article> = withContext(Dispatchers.IO) {
        val startedAt = NewsParsingLogger.now()
        val safeUrl = NewsParsingLogger.safeUrl(source.url)
        NewsParsingLogger.debug {
            "source_fetch_start sourceId=${source.id} type=${source.type} url=$safeUrl"
        }
        val articles = runCatching {
            when (source.type) {
                SourceType.RSS -> fetchRssArticles(source.id, source.url)
                SourceType.TELEGRAM -> fetchTelegramArticles(source.id, source.url, oldestAllowedPublishedAt)
                SourceType.YOUTUBE -> fetchYouTubeArticles(source.id, source.url)
            }
        }.getOrElse { error ->
            NewsParsingLogger.error(error) {
                "source_fetch_error sourceId=${source.id} type=${source.type} url=$safeUrl " +
                    "durationMs=${NewsParsingLogger.elapsedMs(startedAt)} error=${error.javaClass.simpleName}"
            }
            emptyList()
        }
        NewsParsingLogger.debug {
            "source_fetch_complete sourceId=${source.id} type=${source.type} url=$safeUrl " +
                "articles=${articles.size} durationMs=${NewsParsingLogger.elapsedMs(startedAt)}"
        }
        articles
    }

    suspend fun fetchYouTubeChannelDisplayName(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val youtubeUrl = buildYouTubeFeedUrl(url)
            val request = Request.Builder().url(youtubeUrl).build()
            displayNameOkHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.byteStream()
                    if (body != null) {
                        return@withContext youtubeParser.parseChannelDisplayName(body)
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchTelegramChannelDisplayName(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val telegramUrl = buildTelegramChannelPreviewUrl(url)
            val request = Request.Builder().url(telegramUrl).build()
            displayNameOkHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.string()
                    ?.let(telegramParser::parseChannelDisplayName)
                    ?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchRssChannelDisplayName(url: String): String? = withContext(Dispatchers.IO) {
        val httpsUrl = if (url.startsWith("http://")) "https://${url.removePrefix("http://")}" else url
        val primary = displayNameRssParser.parseChannelTitleUrl(httpsUrl)
        if (!primary.isNullOrBlank() || httpsUrl == url) return@withContext primary
        displayNameRssParser.parseChannelTitleUrl(url)
    }

    private suspend fun fetchRssArticles(sourceId: Long, url: String): List<Article> {
        val httpsUrl = if (url.startsWith("http://")) "https://${url.removePrefix("http://")}" else url
        val primaryStartedAt = NewsParsingLogger.now()
        val primary = rssParser.parseUrlResult(httpsUrl, sourceId)
        NewsParsingLogger.debug {
            "rss_primary_complete sourceId=$sourceId url=${NewsParsingLogger.safeUrl(httpsUrl)} " +
                "success=${primary.isSuccess} articles=${primary.getOrNull()?.size ?: 0} " +
                "durationMs=${NewsParsingLogger.elapsedMs(primaryStartedAt)} " +
                "error=${primary.exceptionOrNull()?.javaClass?.simpleName.orEmpty()}"
        }
        return if (primary.isSuccess || httpsUrl == url) {
            primary.getOrDefault(emptyList())
        } else {
            val fallbackStartedAt = NewsParsingLogger.now()
            val fallback = rssParser.parseUrlResult(url, sourceId)
            NewsParsingLogger.debug {
                "rss_fallback_complete sourceId=$sourceId url=${NewsParsingLogger.safeUrl(url)} " +
                    "success=${fallback.isSuccess} articles=${fallback.getOrNull()?.size ?: 0} " +
                    "durationMs=${NewsParsingLogger.elapsedMs(fallbackStartedAt)} " +
                    "error=${fallback.exceptionOrNull()?.javaClass?.simpleName.orEmpty()}"
            }
            fallback.getOrDefault(emptyList())
        }
    }

    private fun fetchTelegramArticles(
        sourceId: Long,
        url: String,
        oldestAllowedPublishedAt: Long?
    ): List<Article> {
        return try {
            val startedAt = NewsParsingLogger.now()
            val telegramUrl = buildTelegramChannelPreviewUrl(url)
            val articlesByKey = linkedMapOf<String, Article>()
            var pageArticles = fetchTelegramPageArticles(telegramUrl, sourceId, pageIndex = 0)
            addTelegramArticles(articlesByKey, pageArticles)

            var oldestMessageId = findOldestTelegramMessageId(pageArticles)
            var pageCount = 0
            var stopReason = TELEGRAM_STOP_FIRST_PAGE_ENOUGH
            while (
                oldestAllowedPublishedAt != null &&
                shouldFetchOlderTelegramPage(pageArticles, oldestAllowedPublishedAt) &&
                oldestMessageId != null &&
                pageCount < TELEGRAM_MAX_EXTRA_PAGES
            ) {
                val nextUrl = buildTelegramBeforeUrl(telegramUrl, oldestMessageId)
                pageArticles = fetchTelegramPageArticles(nextUrl, sourceId, pageIndex = pageCount + 1)
                val previousSize = articlesByKey.size
                addTelegramArticles(articlesByKey, pageArticles)
                if (articlesByKey.size == previousSize) {
                    stopReason = TELEGRAM_STOP_NO_NEW_ARTICLES
                    break
                }
                oldestMessageId = findOldestTelegramMessageId(pageArticles)
                pageCount++
            }
            if (oldestAllowedPublishedAt == null) {
                stopReason = TELEGRAM_STOP_NO_CUTOFF
            } else if (!shouldFetchOlderTelegramPage(pageArticles, oldestAllowedPublishedAt)) {
                stopReason = TELEGRAM_STOP_REACHED_CUTOFF
            } else if (oldestMessageId == null) {
                stopReason = TELEGRAM_STOP_NO_OLDER_ID
            } else if (pageCount >= TELEGRAM_MAX_EXTRA_PAGES) {
                stopReason = TELEGRAM_STOP_PAGE_LIMIT
            }

            val articles = articlesByKey.values.sortedByDescending { it.publishedAt }
            NewsParsingLogger.debug {
                "telegram_complete sourceId=$sourceId pages=${pageCount + 1} articles=${articles.size} " +
                    "stopReason=$stopReason durationMs=${NewsParsingLogger.elapsedMs(startedAt)}"
            }
            articles
        } catch (e: Exception) {
            NewsParsingLogger.error(e) {
                "telegram_error sourceId=$sourceId url=${NewsParsingLogger.safeUrl(url)} error=${e.javaClass.simpleName}"
            }
            emptyList()
        }
    }

    private fun fetchTelegramPageArticles(url: String, sourceId: Long, pageIndex: Int): List<Article> {
        val startedAt = NewsParsingLogger.now()
        val request = Request.Builder().url(url).build()
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    NewsParsingLogger.warning {
                        "telegram_page_complete sourceId=$sourceId pageIndex=$pageIndex " +
                            "status=${response.code} articles=0 durationMs=${NewsParsingLogger.elapsedMs(startedAt)}"
                    }
                    return emptyList()
                }
                val html = response.body?.string() ?: return emptyList()
                val articles = telegramParser.parse(html, sourceId)
                NewsParsingLogger.debug {
                    "telegram_page_complete sourceId=$sourceId pageIndex=$pageIndex " +
                        "status=${response.code} htmlChars=${html.length} articles=${articles.size} " +
                        "durationMs=${NewsParsingLogger.elapsedMs(startedAt)}"
                }
                articles
            }
        } catch (e: Exception) {
            NewsParsingLogger.error(e) {
                "telegram_page_error sourceId=$sourceId pageIndex=$pageIndex " +
                    "url=${NewsParsingLogger.safeUrl(url)} durationMs=${NewsParsingLogger.elapsedMs(startedAt)} " +
                    "error=${e.javaClass.simpleName}"
            }
            emptyList()
        }
    }

    private fun addTelegramArticles(
        articlesByKey: MutableMap<String, Article>,
        articles: List<Article>
    ) {
        articles.forEach { article ->
            val key = article.stableArticleKey.ifBlank { article.url }
            if (key.isNotBlank()) {
                articlesByKey[key] = article
            }
        }
    }

    private fun shouldFetchOlderTelegramPage(
        articles: List<Article>,
        oldestAllowedPublishedAt: Long
    ): Boolean {
        val oldestPublishedAt = articles.minOfOrNull { it.publishedAt } ?: return false
        return oldestPublishedAt > oldestAllowedPublishedAt
    }

    private fun findOldestTelegramMessageId(articles: List<Article>): Long? {
        return articles
            .mapNotNull { extractTelegramMessageId(it.url) }
            .minOrNull()
    }

    private fun extractTelegramMessageId(url: String): Long? {
        return url
            .substringBefore("?")
            .trimEnd('/')
            .substringAfterLast("/")
            .toLongOrNull()
            ?.takeIf { it > 0L }
    }

    private fun buildTelegramBeforeUrl(baseUrl: String, beforeMessageId: Long): String {
        val cleanBaseUrl = baseUrl.substringBefore("?").trimEnd('/')
        return "$cleanBaseUrl?before=$beforeMessageId"
    }

    private fun buildTelegramChannelPreviewUrl(url: String): String {
        if (url.contains("/s/")) return url
        val channelName = url.trim()
            .removeSuffix("/")
            .substringBefore("?")
            .substringAfterLast("/")
            .removePrefix("@")
        return "https://t.me/s/$channelName"
    }

    private fun fetchYouTubeArticles(sourceId: Long, url: String): List<Article> {
        val startedAt = NewsParsingLogger.now()
        return try {
            val youtubeUrl = buildYouTubeFeedUrl(url)
            val request = Request.Builder().url(youtubeUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.byteStream()
                    if (body != null) {
                        val articles = youtubeParser.parse(body, sourceId)
                        NewsParsingLogger.debug {
                            "youtube_feed_complete sourceId=$sourceId url=${NewsParsingLogger.safeUrl(youtubeUrl)} " +
                                "status=${response.code} articles=${articles.size} " +
                                "durationMs=${NewsParsingLogger.elapsedMs(startedAt)}"
                        }
                        return articles
                    }
                }
                NewsParsingLogger.warning {
                    "youtube_feed_complete sourceId=$sourceId url=${NewsParsingLogger.safeUrl(youtubeUrl)} " +
                        "status=${response.code} articles=0 durationMs=${NewsParsingLogger.elapsedMs(startedAt)}"
                }
            }
            emptyList()
        } catch (e: Exception) {
            NewsParsingLogger.error(e) {
                "youtube_feed_error sourceId=$sourceId url=${NewsParsingLogger.safeUrl(url)} " +
                    "durationMs=${NewsParsingLogger.elapsedMs(startedAt)} error=${e.javaClass.simpleName}"
            }
            emptyList()
        }
    }

    private fun buildYouTubeFeedUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.contains("feeds/videos.xml") && trimmed.contains("channel_id=")) return trimmed

        val channelId = when {
            "/channel/" in trimmed -> trimmed.substringAfter("/channel/").substringBefore("?").substringBefore("/")
            "channel_id=" in trimmed -> trimmed.substringAfter("channel_id=").substringBefore("&")
            else -> trimmed.substringAfterLast("/").substringBefore("?")
        }.trim()
        return "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
    }

    suspend fun fetchFullContent(url: String, type: SourceType): String? =
        withContext(Dispatchers.IO) {
            if (type != SourceType.RSS && type != SourceType.YOUTUBE) return@withContext null

            try {
                if (type == SourceType.YOUTUBE) {
                    val videoId = when {
                        url.contains("v=") -> url.substringAfter("v=").substringBefore("&")
                        url.contains("youtu.be/") -> url.substringAfter("youtu.be/")
                            .substringBefore("?")

                        else -> url.substringAfterLast("/")
                    }
                    val transcriptList = youtubeTranscriptApi.listTranscripts(videoId)
                    val transcript = runCatching {
                        transcriptList.findGeneratedTranscript("uk", "ru", "en")
                    }.getOrElse {
                        transcriptList.findTranscript("uk", "ru", "en")
                    }
                    val fetched = transcript.fetch()
                    val transcriptText = formatYoutubeTranscriptByTiming(fetched)
                    return@withContext transcriptText
                }

                if (!url.startsWith("https://", true) && !url.startsWith("http://", true)) {
                    return@withContext null
                }

                val response = okHttpClient.newCall(
                    Request.Builder()
                        .url(url)
                        .header(HEADER_USER_AGENT, USER_AGENT_VALUE)
                        .header(HEADER_ACCEPT_LANGUAGE, ACCEPT_LANGUAGE_VALUE)
                        .build()
                ).execute()
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string()
                return@withContext body
            } catch (e: Exception) {
                null
            }
        }

    private fun formatYoutubeTranscriptByTiming(transcript: TranscriptContent): String {
        val fragments = transcript.content
            .filter { !it.text.isNullOrBlank() }
            .sortedBy { it.start }
        if (fragments.isEmpty()) return ""

        val blocks = mutableListOf<String>()
        val current = StringBuilder()
        var blockStart = fragments.first().start
        var blockEnd = blockStart

        fragments.forEach { fragment ->
            val normalized = normalizeTranscriptFragment(fragment.text)
            if (normalized.isBlank()) return@forEach

            val fragmentEnd = fragment.start + fragment.dur
            val shouldFlush =
                current.isNotEmpty() &&
                    (fragment.start - blockEnd > YT_BLOCK_GAP_SECONDS ||
                        fragmentEnd - blockStart >= YT_BLOCK_WINDOW_SECONDS ||
                        current.length >= YT_BLOCK_MAX_CHARS)

            if (shouldFlush) {
                blocks += finalizeTranscriptBlock(current.toString())
                current.clear()
                blockStart = fragment.start
            }

            if (current.isNotEmpty()) current.append(' ')
            current.append(normalized)
            blockEnd = fragmentEnd
        }

        if (current.isNotEmpty()) {
            blocks += finalizeTranscriptBlock(current.toString())
        }

        return blocks.joinToString(separator = "\n\n")
    }

    private fun normalizeTranscriptFragment(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val withoutTags = raw.replace(Regex("<[^>]+>"), " ")
        return withoutTags
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun finalizeTranscriptBlock(rawBlock: String): String {
        val cleaned = rawBlock
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isEmpty()) return ""

        return if (cleaned.last() == '.' || cleaned.last() == '!' || cleaned.last() == '?') {
            cleaned
        } else {
            "$cleaned."
        }
    }

    private companion object {
        private const val HEADER_USER_AGENT = "User-Agent"
        private const val HEADER_ACCEPT_LANGUAGE = "Accept-Language"
        private const val USER_AGENT_VALUE =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        private const val ACCEPT_LANGUAGE_VALUE = "uk-UA,uk;q=0.9,en-US;q=0.8,en;q=0.7"
        private const val YT_BLOCK_WINDOW_SECONDS = 22.0
        private const val YT_BLOCK_GAP_SECONDS = 2.2
        private const val YT_BLOCK_MAX_CHARS = 260
        private const val TELEGRAM_MAX_EXTRA_PAGES = 5
        private const val TELEGRAM_STOP_FIRST_PAGE_ENOUGH = "first_page_enough"
        private const val TELEGRAM_STOP_NO_CUTOFF = "no_cutoff"
        private const val TELEGRAM_STOP_REACHED_CUTOFF = "reached_cutoff"
        private const val TELEGRAM_STOP_NO_OLDER_ID = "no_older_id"
        private const val TELEGRAM_STOP_NO_NEW_ARTICLES = "no_new_articles"
        private const val TELEGRAM_STOP_PAGE_LIMIT = "page_limit"
    }
}



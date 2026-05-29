package com.andrewwin.sumup.data.remote.telegram

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.remote.NewsParsingLogger
import com.andrewwin.sumup.data.remote.RemoteFullContent
import com.andrewwin.sumup.data.remote.RemoteSourceDataSource
import com.andrewwin.sumup.data.remote.TelegramParser
import com.andrewwin.sumup.domain.entities.ai.RemoteContentFetchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

class TelegramRemoteDataSource(
    private val okHttpClient: OkHttpClient,
    private val displayNameOkHttpClient: OkHttpClient,
    private val telegramParser: TelegramParser
) : RemoteSourceDataSource {

    override suspend fun fetchArticles(
        source: Source,
        oldestAllowedPublishedAt: Long?,
        latestKnownArticleUrl: String?
    ): List<Article> = withContext(Dispatchers.IO) {
        try {
            val channelStartedAt = NewsParsingLogger.now()
            val telegramUrl = buildTelegramChannelPreviewUrl(source.url)
            val safeTelegramUrl = NewsParsingLogger.safeUrl(telegramUrl)
            val latestKnownMessageId = extractTelegramMessageId(latestKnownArticleUrl.orEmpty())
            val articlesByKey = linkedMapOf<String, Article>()
            var totalRequestMs = 0L
            var totalBodyReadMs = 0L
            var totalMetadataParseMs = 0L
            var totalArticleParseMs = 0L
            var currentUrl = telegramUrl
            var currentBeforeCursor: String? = null
            var pageResult = fetchTelegramPageArticles(
                url = currentUrl,
                sourceId = source.id,
                pageIndex = 0,
                oldestAllowedPublishedAt = oldestAllowedPublishedAt,
                latestKnownMessageId = latestKnownMessageId
            )
            totalRequestMs += pageResult.requestMs
            totalBodyReadMs += pageResult.bodyReadMs
            totalMetadataParseMs += pageResult.metadataParseMs
            totalArticleParseMs += pageResult.articleParseMs
            var pageArticles = pageResult.articles
            addTelegramArticles(articlesByKey, pageArticles)
            logTelegramBeforeFlow(
                safeTelegramUrl = safeTelegramUrl,
                pageIndex = 0,
                currentBeforeCursor = currentBeforeCursor,
                latestKnownMessageId = latestKnownMessageId,
                oldestAllowedPublishedAt = oldestAllowedPublishedAt,
                pageMetadata = pageResult.pageMetadata,
                nextPageCursor = pageResult.nextPageCursor
            )
            val newestFetchedMessageId = pageResult.pageMetadata.newestMessageId ?: 0L
            var pageCount = 1
            var stopReason = TELEGRAM_STOP_FIRST_PAGE_ENOUGH

            if (latestKnownMessageId != null && newestFetchedMessageId > 0L && newestFetchedMessageId <= latestKnownMessageId) {
                stopReason = TELEGRAM_STOP_NO_NEWER_THAN_KNOWN
                NewsParsingLogger.telegramBeforeStop(
                    safeUrl = safeTelegramUrl,
                    pageIndex = 0,
                    reason = stopReason,
                    currentBeforeCursor = currentBeforeCursor,
                    nextBeforeCursor = pageResult.nextPageCursor,
                    oldestMessageId = pageResult.pageMetadata.oldestMessageId,
                    newestMessageId = pageResult.pageMetadata.newestMessageId,
                    latestKnownMessageId = latestKnownMessageId
                )
                val articles = articlesByKey.values.sortedByDescending { it.publishedAt }
                NewsParsingLogger.telegramChannelProfile(
                    safeUrl = safeTelegramUrl,
                    pageCount = pageCount,
                    totalArticles = articles.size,
                    stopReason = stopReason,
                    requestMs = totalRequestMs,
                    bodyReadMs = totalBodyReadMs,
                    metadataParseMs = totalMetadataParseMs,
                    articleParseMs = totalArticleParseMs,
                    totalMs = NewsParsingLogger.elapsedMs(channelStartedAt)
                )
                return@withContext articles
            }

            while (
                oldestAllowedPublishedAt != null &&
                shouldFetchOlderTelegramPage(pageResult.pageMetadata, oldestAllowedPublishedAt) &&
                shouldFetchTelegramPageBeforeKnownMessage(pageResult.pageMetadata, latestKnownMessageId) &&
                pageResult.nextPageCursor != null &&
                pageCount - 1 < TELEGRAM_MAX_EXTRA_PAGES
            ) {
                currentBeforeCursor = pageResult.nextPageCursor
                currentUrl = buildTelegramBeforeUrl(telegramUrl, currentBeforeCursor)
                pageResult = fetchTelegramPageArticles(
                    url = currentUrl,
                    sourceId = source.id,
                    pageIndex = pageCount,
                    oldestAllowedPublishedAt = oldestAllowedPublishedAt,
                    latestKnownMessageId = latestKnownMessageId
                )
                totalRequestMs += pageResult.requestMs
                totalBodyReadMs += pageResult.bodyReadMs
                totalMetadataParseMs += pageResult.metadataParseMs
                totalArticleParseMs += pageResult.articleParseMs
                pageArticles = pageResult.articles
                logTelegramBeforeFlow(
                    safeTelegramUrl = safeTelegramUrl,
                    pageIndex = pageCount,
                    currentBeforeCursor = currentBeforeCursor,
                    latestKnownMessageId = latestKnownMessageId,
                    oldestAllowedPublishedAt = oldestAllowedPublishedAt,
                    pageMetadata = pageResult.pageMetadata,
                    nextPageCursor = pageResult.nextPageCursor
                )
                val previousSize = articlesByKey.size
                addTelegramArticles(articlesByKey, pageArticles)
                pageCount++
                if (articlesByKey.size == previousSize) {
                    stopReason = TELEGRAM_STOP_NO_NEW_ARTICLES
                    NewsParsingLogger.telegramBeforeStop(
                        safeUrl = safeTelegramUrl,
                        pageIndex = pageCount - 1,
                        reason = stopReason,
                        currentBeforeCursor = currentBeforeCursor,
                        nextBeforeCursor = pageResult.nextPageCursor,
                        oldestMessageId = pageResult.pageMetadata.oldestMessageId,
                        newestMessageId = pageResult.pageMetadata.newestMessageId,
                        latestKnownMessageId = latestKnownMessageId
                    )
                    break
                }
            }
            if (stopReason == TELEGRAM_STOP_FIRST_PAGE_ENOUGH) {
                stopReason = when {
                    oldestAllowedPublishedAt == null -> TELEGRAM_STOP_NO_CUTOFF
                    !shouldFetchOlderTelegramPage(pageResult.pageMetadata, oldestAllowedPublishedAt) -> TELEGRAM_STOP_REACHED_CUTOFF
                    !shouldFetchTelegramPageBeforeKnownMessage(pageResult.pageMetadata, latestKnownMessageId) -> TELEGRAM_STOP_REACHED_KNOWN_MESSAGE
                    pageResult.nextPageCursor == null -> TELEGRAM_STOP_NO_OLDER_ID
                    pageCount - 1 >= TELEGRAM_MAX_EXTRA_PAGES -> TELEGRAM_STOP_PAGE_LIMIT
                    else -> TELEGRAM_STOP_FIRST_PAGE_ENOUGH
                }
                NewsParsingLogger.telegramBeforeStop(
                    safeUrl = safeTelegramUrl,
                    pageIndex = pageCount - 1,
                    reason = stopReason,
                    currentBeforeCursor = currentBeforeCursor,
                    nextBeforeCursor = pageResult.nextPageCursor,
                    oldestMessageId = pageResult.pageMetadata.oldestMessageId,
                    newestMessageId = pageResult.pageMetadata.newestMessageId,
                    latestKnownMessageId = latestKnownMessageId
                )
            }
            val articles = articlesByKey.values.sortedByDescending { it.publishedAt }
            NewsParsingLogger.telegramChannelProfile(
                safeUrl = safeTelegramUrl,
                pageCount = pageCount,
                totalArticles = articles.size,
                stopReason = stopReason,
                requestMs = totalRequestMs,
                bodyReadMs = totalBodyReadMs,
                metadataParseMs = totalMetadataParseMs,
                articleParseMs = totalArticleParseMs,
                totalMs = NewsParsingLogger.elapsedMs(channelStartedAt)
            )
            articles
        } catch (e: Exception) {
            NewsParsingLogger.error(e) {
                "telegram_error sourceId=${source.id} url=${NewsParsingLogger.safeUrl(source.url)} error=${e.javaClass.simpleName}"
            }
            emptyList()
        }
    }

    override suspend fun fetchDisplayName(url: String): String? = withContext(Dispatchers.IO) {
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

    override suspend fun fetchFullContent(url: String): RemoteFullContent? = withContext(Dispatchers.IO) {
        try {
            val telegramUrl = buildTelegramChannelPreviewUrl(url)
            val request = Request.Builder().url(telegramUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext RemoteFullContent(null, RemoteContentFetchStatus.FETCH_FAILED)
                val html = response.body.string()
                val articles = telegramParser.parse(html, 0L)
                val text = articles.joinToString("\n\n") { it.content }
                RemoteFullContent(text = text, status = RemoteContentFetchStatus.SUCCESS)
            }
        } catch (e: Exception) {
            RemoteFullContent(null, RemoteContentFetchStatus.FETCH_FAILED)
        }
    }

    private fun fetchTelegramPageArticles(
        url: String,
        sourceId: Long,
        pageIndex: Int,
        oldestAllowedPublishedAt: Long?,
        latestKnownMessageId: Long?
    ): TelegramPageResult {
        val pageStartedAt = NewsParsingLogger.now()
        val requestStartedAt = NewsParsingLogger.now()
        val request = Request.Builder().url(url).build()
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                val requestMs = NewsParsingLogger.elapsedMs(requestStartedAt)
                if (!response.isSuccessful) {
                    val result = TelegramPageResult.empty(statusCode = response.code, requestMs = requestMs)
                    NewsParsingLogger.telegramPageProfile(
                        safeUrl = NewsParsingLogger.safeUrl(url),
                        pageIndex = pageIndex,
                        statusCode = response.code,
                        htmlChars = 0,
                        messageCount = 0,
                        relevantMessages = 0,
                        articlesCount = 0,
                        requestMs = result.requestMs,
                        bodyReadMs = result.bodyReadMs,
                        documentParseMs = result.documentParseMs,
                        metadataParseMs = result.metadataParseMs,
                        articleParseMs = result.articleParseMs,
                        totalMs = NewsParsingLogger.elapsedMs(pageStartedAt)
                    )
                    return result
                }
                val bodyReadStartedAt = NewsParsingLogger.now()
                val html = response.body.string()
                val bodyReadMs = NewsParsingLogger.elapsedMs(bodyReadStartedAt)
                val documentParseStartedAt = NewsParsingLogger.now()
                val document = Jsoup.parse(html)
                val documentParseMs = NewsParsingLogger.elapsedMs(documentParseStartedAt)
                val articleParseStartedAt = NewsParsingLogger.now()
                val pageScanResult = telegramParser.scanPage(
                    document = document,
                    sourceId = sourceId,
                    oldestAllowedPublishedAt = oldestAllowedPublishedAt,
                    latestKnownMessageId = latestKnownMessageId
                )
                val articleParseMs = NewsParsingLogger.elapsedMs(articleParseStartedAt)
                val pageMetadata = pageScanResult.metadata
                val shouldParseArticles =
                    pageMetadata.hasRelevantMessage &&
                        pageMetadata.hasMessageNewerThanKnown
                val articles = pageScanResult.articles
                if (!shouldParseArticles) {
                    val skipReason = when {
                        !pageMetadata.hasRelevantMessage -> TELEGRAM_SKIP_ARTICLE_PARSE_NO_RELEVANT_BY_CUTOFF
                        !pageMetadata.hasMessageNewerThanKnown -> TELEGRAM_SKIP_ARTICLE_PARSE_NO_NEWER_THAN_KNOWN
                        else -> TELEGRAM_SKIP_ARTICLE_PARSE_UNKNOWN
                    }
                    NewsParsingLogger.telegramBeforeStop(
                        safeUrl = NewsParsingLogger.safeUrl(url),
                        pageIndex = pageIndex,
                        reason = skipReason,
                        currentBeforeCursor = extractTelegramBeforeCursor(url),
                        nextBeforeCursor = pageMetadata.nextBeforeCursor ?: pageMetadata.oldestMessageId?.toString(),
                        oldestMessageId = pageMetadata.oldestMessageId,
                        newestMessageId = pageMetadata.newestMessageId,
                        latestKnownMessageId = latestKnownMessageId
                    )
                }
                val result = TelegramPageResult(
                    articles = articles,
                    pageMetadata = pageMetadata,
                    nextPageCursor = pageScanResult.nextPageCursor,
                    statusCode = response.code,
                    requestMs = requestMs,
                    bodyReadMs = bodyReadMs,
                    documentParseMs = documentParseMs,
                    metadataParseMs = 0L,
                    articleParseMs = articleParseMs
                )
                NewsParsingLogger.telegramPageProfile(
                    safeUrl = NewsParsingLogger.safeUrl(url),
                    pageIndex = pageIndex,
                    statusCode = result.statusCode,
                    htmlChars = html.length,
                    messageCount = pageMetadata.messageCount,
                    relevantMessages = if (shouldParseArticles) articles.size else 0,
                    articlesCount = articles.size,
                    requestMs = result.requestMs,
                    bodyReadMs = result.bodyReadMs,
                    documentParseMs = result.documentParseMs,
                    metadataParseMs = result.metadataParseMs,
                    articleParseMs = result.articleParseMs,
                    totalMs = NewsParsingLogger.elapsedMs(pageStartedAt)
                )
                result
            }
        } catch (e: Exception) {
            NewsParsingLogger.error(e) {
                "telegram_page_error sourceId=$sourceId pageIndex=$pageIndex " +
                    "url=${NewsParsingLogger.safeUrl(url)} " +
                    "error=${e.javaClass.simpleName}"
            }
            TelegramPageResult.empty()
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
        pageMetadata: TelegramParser.TelegramPageMetadata,
        oldestAllowedPublishedAt: Long
    ): Boolean {
        val oldestPublishedAt = pageMetadata.oldestPublishedAt ?: return false
        return oldestPublishedAt > oldestAllowedPublishedAt
    }

    private fun shouldFetchTelegramPageBeforeKnownMessage(
        pageMetadata: TelegramParser.TelegramPageMetadata,
        latestKnownMessageId: Long?
    ): Boolean {
        if (latestKnownMessageId == null) return true
        val oldestFetchedMessageId = pageMetadata.oldestMessageId ?: return false
        return oldestFetchedMessageId > latestKnownMessageId
    }

    private fun extractTelegramMessageId(url: String): Long? {
        return url
            .substringBefore("?")
            .trimEnd('/')
            .substringAfterLast("/")
            .toLongOrNull()
            ?.takeIf { it > 0L }
    }

    private fun buildTelegramBeforeUrl(baseUrl: String, beforeCursor: String): String {
        val cleanBaseUrl = baseUrl.substringBefore("?").trimEnd('/')
        return "$cleanBaseUrl?before=$beforeCursor"
    }

    private fun extractTelegramBeforeCursor(url: String): String? {
        return Regex("[?&]before=([^&#]+)")
            .find(url)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
    }

    private fun logTelegramBeforeFlow(
        safeTelegramUrl: String,
        pageIndex: Int,
        currentBeforeCursor: String?,
        latestKnownMessageId: Long?,
        oldestAllowedPublishedAt: Long?,
        pageMetadata: TelegramParser.TelegramPageMetadata,
        nextPageCursor: String?
    ) {
        val nextCursorSource = when {
            pageMetadata.nextBeforeCursor != null -> TELEGRAM_CURSOR_SOURCE_HTML
            pageMetadata.oldestMessageId != null -> TELEGRAM_CURSOR_SOURCE_OLDEST_MESSAGE_ID
            else -> TELEGRAM_CURSOR_SOURCE_NONE
        }
        val shouldFetchOlderPage = oldestAllowedPublishedAt != null &&
            shouldFetchOlderTelegramPage(pageMetadata, oldestAllowedPublishedAt)
        val shouldFetchBeforeKnownMessage = shouldFetchTelegramPageBeforeKnownMessage(
            pageMetadata = pageMetadata,
            latestKnownMessageId = latestKnownMessageId
        )
        NewsParsingLogger.telegramBeforeFlow(
            safeUrl = safeTelegramUrl,
            pageIndex = pageIndex,
            currentBeforeCursor = currentBeforeCursor,
            nextBeforeCursor = nextPageCursor,
            oldestMessageId = pageMetadata.oldestMessageId,
            newestMessageId = pageMetadata.newestMessageId,
            latestKnownMessageId = latestKnownMessageId,
            nextCursorSource = nextCursorSource,
            shouldFetchOlderPage = shouldFetchOlderPage,
            shouldFetchBeforeKnownMessage = shouldFetchBeforeKnownMessage
        )
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

    private data class TelegramPageResult(
        val articles: List<Article>,
        val pageMetadata: TelegramParser.TelegramPageMetadata,
        val nextPageCursor: String?,
        val statusCode: Int,
        val requestMs: Long,
        val bodyReadMs: Long,
        val documentParseMs: Long,
        val metadataParseMs: Long,
        val articleParseMs: Long
    ) {
        companion object {
            fun empty(
                statusCode: Int = 0,
                requestMs: Long = 0L,
                bodyReadMs: Long = 0L,
                documentParseMs: Long = 0L,
                metadataParseMs: Long = 0L,
                articleParseMs: Long = 0L
            ): TelegramPageResult {
                return TelegramPageResult(
                    articles = emptyList(),
                    pageMetadata = TelegramParser.TelegramPageMetadata(messageCount = 0),
                    nextPageCursor = null,
                    statusCode = statusCode,
                    requestMs = requestMs,
                    bodyReadMs = bodyReadMs,
                    documentParseMs = documentParseMs,
                    metadataParseMs = metadataParseMs,
                    articleParseMs = articleParseMs
                )
            }
        }
    }

    private companion object {
        private const val TELEGRAM_MAX_EXTRA_PAGES = 5
        private const val TELEGRAM_STOP_FIRST_PAGE_ENOUGH = "first_page_enough"
        private const val TELEGRAM_STOP_NO_NEWER_THAN_KNOWN = "no_newer_than_known"
        private const val TELEGRAM_STOP_NO_CUTOFF = "no_cutoff"
        private const val TELEGRAM_STOP_REACHED_CUTOFF = "reached_cutoff"
        private const val TELEGRAM_STOP_REACHED_KNOWN_MESSAGE = "reached_known_message"
        private const val TELEGRAM_STOP_NO_OLDER_ID = "no_older_id"
        private const val TELEGRAM_STOP_NO_NEW_ARTICLES = "no_new_articles"
        private const val TELEGRAM_STOP_PAGE_LIMIT = "page_limit"
        private const val TELEGRAM_SKIP_ARTICLE_PARSE_NO_RELEVANT_BY_CUTOFF = "skip_article_parse_no_relevant_by_cutoff"
        private const val TELEGRAM_SKIP_ARTICLE_PARSE_NO_NEWER_THAN_KNOWN = "skip_article_parse_no_newer_than_known"
        private const val TELEGRAM_SKIP_ARTICLE_PARSE_UNKNOWN = "skip_article_parse_unknown"
        private const val TELEGRAM_CURSOR_SOURCE_HTML = "html"
        private const val TELEGRAM_CURSOR_SOURCE_OLDEST_MESSAGE_ID = "oldest_message_id"
        private const val TELEGRAM_CURSOR_SOURCE_NONE = "none"
    }
}

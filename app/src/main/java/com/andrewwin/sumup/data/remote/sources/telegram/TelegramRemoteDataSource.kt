package com.andrewwin.sumup.data.remote.sources.telegram

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.remote.sources.RemoteFullContent
import com.andrewwin.sumup.data.remote.sources.RemoteSourceDataSource
import com.andrewwin.sumup.domain.ai.model.RemoteContentFetchStatus
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

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
            val telegramUrl = buildTelegramChannelPreviewUrl(source.url)
            val latestKnownMessageId = extractTelegramMessageId(latestKnownArticleUrl.orEmpty())
            val articlesByKey = linkedMapOf<String, Article>()
            var currentUrl = telegramUrl
            var pageResult = fetchTelegramPageArticles(
                url = currentUrl,
                sourceId = source.id,
                pageIndex = 0,
                oldestAllowedPublishedAt = oldestAllowedPublishedAt,
                latestKnownMessageId = latestKnownMessageId
            )
            var pageArticles = pageResult.articles
            addTelegramArticles(articlesByKey, pageArticles)
            val newestFetchedMessageId = pageResult.pageMetadata.newestMessageId ?: 0L
            var pageCount = 1

            if (latestKnownMessageId != null && newestFetchedMessageId > 0L && newestFetchedMessageId <= latestKnownMessageId) {
                return@withContext articlesByKey.values.sortedByDescending { it.publishedAt }
            }

            while (
                oldestAllowedPublishedAt != null &&
                shouldFetchOlderTelegramPage(pageResult.pageMetadata, oldestAllowedPublishedAt) &&
                shouldFetchTelegramPageBeforeKnownMessage(pageResult.pageMetadata, latestKnownMessageId) &&
                pageResult.nextPageCursor != null &&
                pageCount - 1 < TELEGRAM_MAX_EXTRA_PAGES
            ) {
                val currentBeforeCursor = pageResult.nextPageCursor
                currentUrl = buildTelegramBeforeUrl(telegramUrl, currentBeforeCursor)
                pageResult = fetchTelegramPageArticles(
                    url = currentUrl,
                    sourceId = source.id,
                    pageIndex = pageCount,
                    oldestAllowedPublishedAt = oldestAllowedPublishedAt,
                    latestKnownMessageId = latestKnownMessageId
                )
                pageArticles = pageResult.articles
                val previousSize = articlesByKey.size
                addTelegramArticles(articlesByKey, pageArticles)
                pageCount++
                if (articlesByKey.size == previousSize) {
                    break
                }
            }
            articlesByKey.values.sortedByDescending { it.publishedAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun fetchDisplayName(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val telegramUrl = buildTelegramChannelPreviewUrl(url)
            val request = Request.Builder().url(telegramUrl).build()
            displayNameOkHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val document = response.body?.let { body ->
                    parseTelegramDocument(body = body, baseUrl = telegramUrl)
                } ?: return@withContext null
                withContext(Default) {
                    document.selectFirst("title")
                        ?.text()
                        ?.replace(TELEGRAM_TITLE_SUFFIX_REGEX, "")
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?.takeUnless { it.equals(TELEGRAM_DEFAULT_TITLE, ignoreCase = true) }
                }
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
                if (!response.isSuccessful) return@withContext RemoteFullContent(
                    null,
                    RemoteContentFetchStatus.FETCH_FAILED
                )
                val document = parseTelegramDocument(
                    body = response.body,
                    baseUrl = telegramUrl
                )
                val articles = withContext(Default) {
                    telegramParser.parse(document, 0L)
                }
                val text = articles.joinToString("\n\n") { it.content }
                RemoteFullContent(text = text, status = RemoteContentFetchStatus.SUCCESS)
            }
        } catch (e: Exception) {
            RemoteFullContent(null, RemoteContentFetchStatus.FETCH_FAILED)
        }
    }

    private suspend fun fetchTelegramPageArticles(
        url: String,
        sourceId: Long,
        pageIndex: Int,
        oldestAllowedPublishedAt: Long?,
        latestKnownMessageId: Long?
    ): TelegramPageResult {
        val request = Request.Builder().url(url).build()
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return TelegramPageResult.empty()
                }
                val document = parseTelegramDocument(
                    body = response.body,
                    baseUrl = url
                )
                val pageScanResult = withContext(Default) {
                    telegramParser.scanPage(
                        document = document,
                        sourceId = sourceId,
                        oldestAllowedPublishedAt = oldestAllowedPublishedAt,
                        latestKnownMessageId = latestKnownMessageId
                    )
                }
                val pageMetadata = pageScanResult.metadata
                val articles = pageScanResult.articles
                TelegramPageResult(
                    articles = articles,
                    pageMetadata = pageMetadata,
                    nextPageCursor = pageScanResult.nextPageCursor
                )
            }
        } catch (e: Exception) {
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

    private fun parseTelegramDocument(
        body: ResponseBody,
        baseUrl: String
    ): Document {
        return body.byteStream().use { inputStream ->
            Jsoup.parse(inputStream, null, baseUrl)
        }
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
        val nextPageCursor: String?
    ) {
        companion object {
            fun empty(): TelegramPageResult {
                return TelegramPageResult(
                    articles = emptyList(),
                    pageMetadata = TelegramParser.TelegramPageMetadata(messageCount = 0),
                    nextPageCursor = null
                )
            }
        }
    }

    private companion object {
        private const val TELEGRAM_MAX_EXTRA_PAGES = 5
        private const val TELEGRAM_DEFAULT_TITLE = "Telegram Messenger"
        private val TELEGRAM_TITLE_SUFFIX_REGEX = Regex("\\s+[–-]\\s*Telegram\\s*$", RegexOption.IGNORE_CASE)
    }
}

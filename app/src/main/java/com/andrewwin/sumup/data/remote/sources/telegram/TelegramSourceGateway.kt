package com.andrewwin.sumup.data.remote.sources.telegram

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.remote.sources.RemoteFullContent
import com.andrewwin.sumup.data.remote.sources.RemoteSourceGateway
import com.andrewwin.sumup.domain.ai.model.RemoteContentFetchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.withContext

class TelegramSourceGateway(
    private val telegramFetcher: TelegramFetcher,
    private val displayNameTelegramFetcher: TelegramFetcher,
    private val telegramParser: TelegramParser
) : RemoteSourceGateway {

    override suspend fun fetchArticles(
        source: Source,
        oldestAllowedPublishedAt: Long?
    ): List<Article> = withContext(Dispatchers.IO) {
        try {
            val telegramUrl = buildTelegramChannelPreviewUrl(source.url)
            val articlesByKey = linkedMapOf<String, Article>()
            var currentUrl = telegramUrl
            var pageResult = fetchTelegramPageArticles(
                url = currentUrl,
                sourceId = source.id,
                oldestAllowedPublishedAt = oldestAllowedPublishedAt
            )
            var pageArticles = pageResult.articles
            addTelegramArticles(articlesByKey, pageArticles)
            var pageCount = 1

            while (
                oldestAllowedPublishedAt != null &&
                shouldFetchOlderTelegramPage(pageResult.pageMetadata, oldestAllowedPublishedAt) &&
                pageResult.nextPageCursor != null &&
                pageCount - 1 < TELEGRAM_MAX_EXTRA_PAGES
            ) {
                val currentBeforeCursor = pageResult.nextPageCursor
                currentUrl = buildTelegramBeforeUrl(telegramUrl, currentBeforeCursor)
                pageResult = fetchTelegramPageArticles(
                    url = currentUrl,
                    sourceId = source.id,
                    oldestAllowedPublishedAt = oldestAllowedPublishedAt
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
            val document = displayNameTelegramFetcher.fetchDocument(telegramUrl).getOrNull()
                ?: return@withContext null
            withContext(Default) {
                document.selectFirst("title")
                    ?.text()
                    ?.replace(TELEGRAM_TITLE_SUFFIX_REGEX, "")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.takeUnless { it.equals(TELEGRAM_DEFAULT_TITLE, ignoreCase = true) }
                }
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun fetchFullContent(url: String): RemoteFullContent? = withContext(Dispatchers.IO) {
        try {
            val telegramUrl = buildTelegramChannelPreviewUrl(url)
            val document = telegramFetcher.fetchDocument(telegramUrl).getOrNull()
                ?: return@withContext RemoteFullContent(null, RemoteContentFetchStatus.FETCH_FAILED)
            val articles = withContext(Default) {
                telegramParser.parseDocument(document, 0L)
            }
            val text = articles.joinToString("\n\n") { it.content }
            RemoteFullContent(text = text, status = RemoteContentFetchStatus.SUCCESS)
        } catch (e: Exception) {
            RemoteFullContent(null, RemoteContentFetchStatus.FETCH_FAILED)
        }
    }

    private suspend fun fetchTelegramPageArticles(
        url: String,
        sourceId: Long,
        oldestAllowedPublishedAt: Long?
    ): TelegramPageResult {
        val document = telegramFetcher.fetchDocument(url).getOrNull()
            ?: return TelegramPageResult.empty()
        val pageScanResult = withContext(Default) {
            telegramParser.parsePage(
                document = document,
                sourceId = sourceId,
                oldestAllowedPublishedAt = oldestAllowedPublishedAt
            )
        }
        return TelegramPageResult(
            articles = pageScanResult.articles,
            pageMetadata = pageScanResult.metadata,
            nextPageCursor = pageScanResult.nextPageCursor
        )
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

    private fun buildTelegramBeforeUrl(baseUrl: String, beforeCursor: String): String {
        val cleanBaseUrl = baseUrl.substringBefore("?").trimEnd('/')
        return "$cleanBaseUrl?before=$beforeCursor"
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

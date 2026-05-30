package com.andrewwin.sumup.data.remote.sources

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.local.entities.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class RemoteArticleDataSource @Inject constructor(
    private val handlers: Map<SourceType, RemoteSourceDataSource>
) {

    suspend fun fetchArticles(
        source: Source,
        oldestAllowedPublishedAt: Long? = null,
        latestKnownArticleUrl: String? = null
    ): List<Article> = withContext(Dispatchers.IO) {
        runCatching {
            getHandler(source.type).fetchArticles(
                source = source,
                oldestAllowedPublishedAt = oldestAllowedPublishedAt,
                latestKnownArticleUrl = latestKnownArticleUrl
            )
        }.getOrElse { error ->
            emptyList()
        }
    }

    suspend fun fetchYouTubeChannelDisplayName(url: String): String? =
        getHandler(SourceType.YOUTUBE).fetchDisplayName(url)

    suspend fun fetchTelegramChannelDisplayName(url: String): String? =
        getHandler(SourceType.TELEGRAM).fetchDisplayName(url)

    suspend fun fetchRssChannelDisplayName(url: String): String? =
        getHandler(SourceType.RSS).fetchDisplayName(url)

    suspend fun fetchFullContent(url: String, type: SourceType): RemoteFullContent? =
        getHandler(type).fetchFullContent(url)

    private fun getHandler(type: SourceType): RemoteSourceDataSource {
        return handlers[type] ?: throw IllegalArgumentException("No handler for source type: $type")
    }
}

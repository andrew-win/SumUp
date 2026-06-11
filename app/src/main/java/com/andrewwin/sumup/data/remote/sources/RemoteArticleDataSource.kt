package com.andrewwin.sumup.data.remote.sources

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.local.entities.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class RemoteArticleDataSource @Inject constructor(
    private val gateways: Map<SourceType, RemoteSourceGateway>
) {

    suspend fun fetchArticles(
        source: Source,
        oldestAllowedPublishedAt: Long? = null,
        refreshBoundary: SourceRefreshBoundary = SourceRefreshBoundary.Empty
    ): List<Article> = withContext(Dispatchers.IO) {
        runCatching {
            getGateway(source.type).fetchArticles(
                source = source,
                oldestAllowedPublishedAt = oldestAllowedPublishedAt,
                refreshBoundary = refreshBoundary
            )
        }.getOrElse { error ->
            emptyList()
        }
    }

    suspend fun fetchDisplayName(url: String, type: SourceType): String? =
        getGateway(type).fetchDisplayName(url)

    suspend fun fetchFullContent(url: String, type: SourceType): RemoteFullContent? =
        getGateway(type).fetchFullContent(url)

    private fun getGateway(type: SourceType): RemoteSourceGateway {
        return gateways[type] ?: throw IllegalArgumentException("No gateway for source type: $type")
    }
}

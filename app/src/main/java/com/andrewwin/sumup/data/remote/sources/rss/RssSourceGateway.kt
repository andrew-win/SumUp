package com.andrewwin.sumup.data.remote.sources.rss

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.remote.sources.RemoteFullContent
import com.andrewwin.sumup.data.remote.sources.RemoteSourceGateway
import com.andrewwin.sumup.data.remote.sources.SourceRefreshBoundary
import com.andrewwin.sumup.domain.ai.model.RemoteContentFetchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RssSourceGateway(
    private val rssFetcher: RssFetcher,
    private val displayNameRssFetcher: RssFetcher,
    private val rssParser: RssParser
) : RemoteSourceGateway {

    override suspend fun fetchArticles(
        source: Source,
        oldestAllowedPublishedAt: Long?,
        refreshBoundary: SourceRefreshBoundary
    ): List<Article> = withContext(Dispatchers.IO) {
        val url = source.url
        val httpsUrl = if (url.startsWith("http://")) "https://${url.removePrefix("http://")}" else url
        val primary = fetchArticlesXml(httpsUrl, source.id, refreshBoundary)
        if (primary.isSuccess || httpsUrl == url) {
            primary.getOrDefault(emptyList())
        } else {
            fetchArticlesXml(url, source.id, refreshBoundary).getOrDefault(emptyList())
        }
    }

    override suspend fun fetchDisplayName(url: String): String? = withContext(Dispatchers.IO) {
        val httpsUrl = if (url.startsWith("http://")) "https://${url.removePrefix("http://")}" else url
        val primary = fetchChannelTitle(httpsUrl)
        if (!primary.isNullOrBlank() || httpsUrl == url) return@withContext primary
        fetchChannelTitle(url)
    }

    override suspend fun fetchFullContent(url: String): RemoteFullContent? = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://", true) && !url.startsWith("http://", true)) {
            return@withContext RemoteFullContent(null, RemoteContentFetchStatus.FETCH_FAILED)
        }

        rssFetcher.fetchFullContent(url)
            .fold(
                onSuccess = { body -> RemoteFullContent(text = body, status = RemoteContentFetchStatus.SUCCESS) },
                onFailure = { RemoteFullContent(null, RemoteContentFetchStatus.FETCH_FAILED) }
            )
    }

    private suspend fun fetchArticlesXml(
        url: String,
        sourceId: Long,
        refreshBoundary: SourceRefreshBoundary
    ): Result<List<Article>> {
        val xml = rssFetcher.fetchBody(url).getOrElse { return Result.failure(it) }
        return rssParser.parseArticlesXml(xml, sourceId, refreshBoundary)
    }

    private suspend fun fetchChannelTitle(url: String): String? {
        val xml = displayNameRssFetcher.fetchBody(url).getOrNull() ?: return null
        return rssParser.parseChannelTitleXml(xml)
    }
}

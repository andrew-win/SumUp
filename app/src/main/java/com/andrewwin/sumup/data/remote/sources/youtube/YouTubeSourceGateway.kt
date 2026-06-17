package com.andrewwin.sumup.data.remote.sources.youtube

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.remote.sources.RemoteFullContent
import com.andrewwin.sumup.data.remote.sources.RemoteSourceGateway
import com.andrewwin.sumup.data.remote.sources.SourceRefreshBoundary
import com.andrewwin.sumup.domain.ai.model.RemoteContentFetchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class YouTubeSourceGateway(
    private val youtubeFetcher: YouTubeFetcher,
    private val displayNameYouTubeFetcher: YouTubeFetcher,
    private val youtubeParser: YouTubeParser
) : RemoteSourceGateway {

    override suspend fun fetchArticles(
        source: Source,
        oldestAllowedPublishedAt: Long?,
        refreshBoundary: SourceRefreshBoundary
    ): List<Article> = withContext(Dispatchers.IO) {
        try {
            val youtubeUrl = buildYouTubeFeedUrl(source.url)
            val parseResult = youtubeFetcher.fetchFeedStream(youtubeUrl) { stream ->
                youtubeParser.parseFeed(
                    inputStream = stream,
                    sourceId = source.id,
                    oldestAllowedPublishedAt = oldestAllowedPublishedAt,
                    refreshBoundary = refreshBoundary
                )
            }.getOrNull()
                ?: return@withContext emptyList()
            val metadata = parseResult.metadata
            if (!metadata.hasRelevantEntry) {
                return@withContext emptyList()
            }
            parseResult.articles
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun fetchDisplayName(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val youtubeUrl = buildYouTubeFeedUrl(url)
            displayNameYouTubeFetcher.fetchFeedStream(youtubeUrl) { stream ->
                youtubeParser.parseChannelDisplayName(stream)
            }.getOrNull()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun fetchFullContent(url: String): RemoteFullContent? = withContext(Dispatchers.IO) {
        try {
            val videoId = youtubeParser.extractVideoId(url)
                ?: return@withContext RemoteFullContent(
                    text = null,
                    status = RemoteContentFetchStatus.YT_FAILED
                )
            youtubeFetcher.fetchTranscript(videoId)
                .fold(
                    onSuccess = { result ->
                        RemoteFullContent(
                            text = youtubeParser.formatTranscriptByTiming(result.transcriptContent),
                            status = result.status
                        )
                    },
                    onFailure = {
                        RemoteFullContent(
                            text = null,
                            status = RemoteContentFetchStatus.YT_FAILED
                        )
                    }
                )
        } catch (e: Exception) {
            RemoteFullContent(
                text = null,
                status = RemoteContentFetchStatus.YT_FAILED
            )
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
}

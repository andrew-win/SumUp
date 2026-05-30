package com.andrewwin.sumup.data.remote.sources.youtube

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.remote.sources.RemoteFullContent
import com.andrewwin.sumup.data.remote.sources.RemoteSourceDataSource
import com.andrewwin.sumup.domain.entities.ai.RemoteContentFetchStatus
import io.github.thoroldvix.api.TranscriptApiFactory
import io.github.thoroldvix.api.TranscriptContent
import io.github.thoroldvix.api.YoutubeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class YouTubeRemoteDataSource(
    private val okHttpClient: OkHttpClient,
    private val displayNameOkHttpClient: OkHttpClient,
    private val youtubeParser: YouTubeParser
) : RemoteSourceDataSource {

    private class OkHttpYoutubeClient(private val client: OkHttpClient) : YoutubeClient {
        override fun get(url: String, headers: Map<String, String>): String {
            val request = Request.Builder().url(url).apply {
                headers.forEach { (name, value) -> addHeader(name, value) }
            }.build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("YouTube request failed")
                return response.body.string()
            }
        }

        override fun post(url: String, json: String): String {
            val body = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("YouTube request failed")
                return response.body.string()
            }
        }
    }

    private val youtubeTranscriptApi = TranscriptApiFactory.createWithClient(OkHttpYoutubeClient(okHttpClient))

    override suspend fun fetchArticles(
        source: Source,
        oldestAllowedPublishedAt: Long?,
        latestKnownArticleUrl: String?
    ): List<Article> = withContext(Dispatchers.IO) {
        try {
            val url = source.url
            val youtubeUrl = buildYouTubeFeedUrl(url)
            val latestKnownVideoId = extractYouTubeVideoId(latestKnownArticleUrl)
            val request = Request.Builder().url(youtubeUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val parseResult = youtubeParser.parseFeed(
                        inputStream = response.body.byteStream(),
                        sourceId = source.id,
                        oldestAllowedPublishedAt = oldestAllowedPublishedAt,
                        latestKnownVideoId = latestKnownVideoId
                    )
                    val metadata = parseResult.metadata
                    if (!metadata.hasRelevantEntry) {
                        return@withContext emptyList()
                    }
                    if (!metadata.hasNewerThanKnownEntry) {
                        return@withContext emptyList()
                    }
                    return@withContext parseResult.articles
                }
            }
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun fetchDisplayName(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val youtubeUrl = buildYouTubeFeedUrl(url)
            val request = Request.Builder().url(youtubeUrl).build()
            displayNameOkHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body.byteStream()
                    return@withContext youtubeParser.parseChannelDisplayName(body)
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun fetchFullContent(url: String): RemoteFullContent? = withContext(Dispatchers.IO) {
        try {
            val videoId = when {
                url.contains("v=") -> url.substringAfter("v=").substringBefore("&")
                url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore("?")
                else -> url.substringAfterLast("/")
            }
            val transcriptList = youtubeTranscriptApi.listTranscripts(videoId)
            val generatedTranscript = runCatching {
                transcriptList.findGeneratedTranscript("uk", "ru", "en")
            }
            val transcript = generatedTranscript.getOrElse {
                runCatching { transcriptList.findTranscript("uk", "ru", "en") }
                    .getOrElse {
                        return@withContext RemoteFullContent(
                            text = null,
                            status = RemoteContentFetchStatus.YT_FAILED
                        )
                    }
            }
            val fetched = transcript.fetch()
            val transcriptText = formatYoutubeTranscriptByTiming(fetched)
            RemoteFullContent(
                text = transcriptText,
                status = if (generatedTranscript.isSuccess) {
                    RemoteContentFetchStatus.YT_GENERATED
                } else {
                    RemoteContentFetchStatus.YT_MANUAL
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

    private fun extractYouTubeVideoId(url: String?): String? {
        val value = url?.trim().orEmpty()
        if (value.isBlank()) return null
        val watchId = Regex("[?&]v=([^?&#]+)").find(value)?.groupValues?.getOrNull(1)
        if (!watchId.isNullOrBlank()) return watchId
        val shortId = Regex("youtu\\.be/([^?&#/]+)").find(value)?.groupValues?.getOrNull(1)
        if (!shortId.isNullOrBlank()) return shortId
        val shortsId = Regex("/shorts/([^?&#/]+)").find(value)?.groupValues?.getOrNull(1)
        if (!shortsId.isNullOrBlank()) return shortsId
        return null
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
        private const val YT_BLOCK_WINDOW_SECONDS = 22.0
        private const val YT_BLOCK_GAP_SECONDS = 2.2
        private const val YT_BLOCK_MAX_CHARS = 260
    }
}

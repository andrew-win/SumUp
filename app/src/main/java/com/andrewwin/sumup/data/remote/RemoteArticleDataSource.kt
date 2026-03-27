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
    private val rssParser: RssParser,
    private val telegramParser: TelegramParser,
    private val youtubeParser: YouTubeParser,
    private val websiteParser: WebsiteParser,
    private val headlessBrowserHtmlFetcher: HeadlessBrowserHtmlFetcher
) {

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

    suspend fun fetchArticles(source: Source): List<Article> = withContext(Dispatchers.IO) {
        when (source.type) {
            SourceType.RSS -> fetchRssArticles(source.id, source.url)
            SourceType.TELEGRAM -> fetchTelegramArticles(source.id, source.url)
            SourceType.YOUTUBE -> fetchYouTubeArticles(source.id, source.url)
            SourceType.WEBSITE -> fetchWebsiteArticles(source)
        }
    }

    private suspend fun fetchWebsiteArticles(source: Source): List<Article> {
        val titleSelector = source.titleSelector?.trim().orEmpty()
        if (titleSelector.isBlank()) {
            return emptyList()
        }

        return try {
            val html = if (source.useHeadlessBrowser) {
                headlessBrowserHtmlFetcher.fetchHtml(source.url).orEmpty()
            } else {
                val request = Request.Builder()
                    .url(source.url)
                    .header(HEADER_USER_AGENT, USER_AGENT_VALUE)
                    .header(HEADER_ACCEPT_LANGUAGE, ACCEPT_LANGUAGE_VALUE)
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return emptyList()
                    }
                    response.body?.string().orEmpty()
                }
            }
            val parsed = websiteParser.parse(
                sourceId = source.id,
                sourceUrl = source.url,
                html = html,
                titleSelector = titleSelector,
                postLinkSelector = source.postLinkSelector,
                descriptionSelector = source.descriptionSelector,
                dateSelector = source.dateSelector
            )
            parsed
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchRssArticles(sourceId: Long, url: String): List<Article> {
        val httpsUrl = if (url.startsWith("http://")) "https://${url.removePrefix("http://")}" else url
        val primary = rssParser.parseUrl(httpsUrl, sourceId)
        return if (primary.isNotEmpty() || httpsUrl == url) {
            primary
        } else {
            rssParser.parseUrl(url, sourceId)
        }
    }

    private fun fetchTelegramArticles(sourceId: Long, url: String): List<Article> {
        return try {
            val telegramUrl = if (url.contains("/s/")) url else "https://t.me/s/${url.substringAfterLast("/")}"
            val request = Request.Builder().url(telegramUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string()
                    if (html != null) return telegramParser.parse(html, sourceId)
                }
            }
            emptyList()
        } catch (e: Exception) { emptyList() }
    }

    private fun fetchYouTubeArticles(sourceId: Long, url: String): List<Article> {
        return try {
            val youtubeUrl = if (url.contains("videos.xml")) url else "https://www.youtube.com/feeds/videos.xml?channel_id=${url.substringAfterLast("/")}"
            val request = Request.Builder().url(youtubeUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.byteStream()
                    if (body != null) return youtubeParser.parse(body, sourceId)
                }
            }
            emptyList()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun fetchFullContent(url: String, type: SourceType): String? =
        withContext(Dispatchers.IO) {
            if (type != SourceType.RSS && type != SourceType.YOUTUBE && type != SourceType.WEBSITE) return@withContext null

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
        private const val TAG_WEBSITE_FETCH = "WebsiteFetch"
        private const val TAG_FULL_CONTENT = "WebsiteFullContent"
        private const val DEBUG_TITLE_PREVIEW_LEN = 80
        private const val YT_BLOCK_WINDOW_SECONDS = 22.0
        private const val YT_BLOCK_GAP_SECONDS = 2.2
        private const val YT_BLOCK_MAX_CHARS = 260
    }
}



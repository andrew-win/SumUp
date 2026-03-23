package com.andrewwin.sumup.data.remote.datasource

import android.util.Log
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.remote.RssParser
import com.andrewwin.sumup.data.remote.TelegramParser
import com.andrewwin.sumup.data.remote.HeadlessBrowserHtmlFetcher
import com.andrewwin.sumup.data.remote.WebsiteParser
import com.andrewwin.sumup.data.remote.YouTubeParser
import io.github.thoroldvix.api.TranscriptApiFactory
import io.github.thoroldvix.api.TranscriptFormatters
import io.github.thoroldvix.api.YoutubeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
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
            val body = okhttp3.RequestBody.create("application/json".toMediaType(), json)
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
            Log.d(TAG_WEBSITE_FETCH, "skip: sourceId=${source.id}, reason=blank_title_selector")
            return emptyList()
        }
        Log.d(
            TAG_WEBSITE_FETCH,
            "start: sourceId=${source.id}, url=${source.url}, titleSelector=$titleSelector, postLinkSelector=${source.postLinkSelector}, descriptionSelector=${source.descriptionSelector}, dateSelector=${source.dateSelector}"
        )
        return try {
            val html = if (source.useHeadlessBrowser) {
                Log.d(TAG_WEBSITE_FETCH, "mode=headless_browser, sourceId=${source.id}")
                headlessBrowserHtmlFetcher.fetchHtml(source.url).orEmpty()
            } else {
                val request = Request.Builder()
                    .url(source.url)
                    .header(HEADER_USER_AGENT, USER_AGENT_VALUE)
                    .header(HEADER_ACCEPT_LANGUAGE, ACCEPT_LANGUAGE_VALUE)
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    Log.d(TAG_WEBSITE_FETCH, "http: sourceId=${source.id}, code=${response.code}, successful=${response.isSuccessful}")
                    if (!response.isSuccessful) {
                        Log.d(TAG_WEBSITE_FETCH, "http_failed: sourceId=${source.id}, code=${response.code}")
                        return emptyList()
                    }
                    response.body?.string().orEmpty()
                }
            }
            Log.d(TAG_WEBSITE_FETCH, "html_received: sourceId=${source.id}, length=${html.length}")
            val parsed = websiteParser.parse(
                sourceId = source.id,
                sourceUrl = source.url,
                html = html,
                titleSelector = titleSelector,
                postLinkSelector = source.postLinkSelector,
                descriptionSelector = source.descriptionSelector,
                dateSelector = source.dateSelector
            )
            Log.d(TAG_WEBSITE_FETCH, "parsed: sourceId=${source.id}, count=${parsed.size}")
            parsed.take(MAX_DEBUG_ITEMS_TO_LOG).forEachIndexed { index, article ->
                Log.d(
                    TAG_WEBSITE_FETCH,
                    "item[$index]: sourceId=${source.id}, title=${article.title.take(DEBUG_TITLE_PREVIEW_LEN)}, url=${article.url}, publishedAt=${article.publishedAt}, contentLen=${article.content.length}"
                )
            }
            parsed
        } catch (e: Exception) {
            Log.e(TAG_WEBSITE_FETCH, "error: sourceId=${source.id}, url=${source.url}, message=${e.message}", e)
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

    suspend fun fetchFullContent(url: String, type: SourceType): String? = withContext(Dispatchers.IO) {
        if (type != SourceType.RSS && type != SourceType.YOUTUBE && type != SourceType.WEBSITE) return@withContext null
        Log.d(TAG_FULL_CONTENT, "start: type=$type, url=$url")
        
        try {
            if (type == SourceType.YOUTUBE) {
                val videoId = when {
                    url.contains("v=") -> url.substringAfter("v=").substringBefore("&")
                    url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore("?")
                    else -> url.substringAfterLast("/")
                }
                val transcriptList = youtubeTranscriptApi.listTranscripts(videoId)
                val transcript = transcriptList.findTranscript("uk", "ru", "en")
                val transcriptText = TranscriptFormatters.textFormatter().format(transcript.fetch())
                Log.d(TAG_FULL_CONTENT, "youtube_ok: url=$url, length=${transcriptText.length}")
                return@withContext transcriptText
            }

            if (!url.startsWith("https://", true) && !url.startsWith("http://", true)) {
                Log.d(TAG_FULL_CONTENT, "skip_invalid_url: type=$type, url=$url")
                return@withContext null
            }

            val response = okHttpClient.newCall(
                Request.Builder()
                    .url(url)
                    .header(HEADER_USER_AGENT, USER_AGENT_VALUE)
                    .header(HEADER_ACCEPT_LANGUAGE, ACCEPT_LANGUAGE_VALUE)
                    .build()
            ).execute()
            Log.d(TAG_FULL_CONTENT, "http: type=$type, code=${response.code}, successful=${response.isSuccessful}, url=$url")
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string()
            Log.d(TAG_FULL_CONTENT, "http_ok: type=$type, url=$url, length=${body?.length ?: 0}")
            return@withContext body
        } catch (e: Exception) {
            Log.e(TAG_FULL_CONTENT, "error: type=$type, url=$url, message=${e.message}", e)
            null
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
        private const val MAX_DEBUG_ITEMS_TO_LOG = 10
        private const val DEBUG_TITLE_PREVIEW_LEN = 80
    }
}

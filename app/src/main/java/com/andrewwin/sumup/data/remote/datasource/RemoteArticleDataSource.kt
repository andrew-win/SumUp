package com.andrewwin.sumup.data.remote.datasource

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.remote.RssParser
import com.andrewwin.sumup.data.remote.TelegramParser
import com.andrewwin.sumup.data.remote.YouTubeParser
import io.github.thoroldvix.api.YoutubeClient
import io.github.thoroldvix.api.TranscriptApiFactory
import io.github.thoroldvix.api.TranscriptFormatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import net.dankito.readability4j.Readability4J
import javax.inject.Inject

class RemoteArticleDataSource @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val rssParser: RssParser,
    private val telegramParser: TelegramParser,
    private val youtubeParser: YouTubeParser
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

    suspend fun fetchArticles(sourceId: Long, url: String, type: SourceType): List<Article> = withContext(Dispatchers.IO) {
        when (type) {
            SourceType.RSS -> fetchRssArticles(sourceId, url)
            SourceType.TELEGRAM -> fetchTelegramArticles(sourceId, url)
            SourceType.YOUTUBE -> fetchYouTubeArticles(sourceId, url)
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
        if (type != SourceType.RSS && type != SourceType.YOUTUBE) return@withContext null
        
        try {
            if (type == SourceType.YOUTUBE) {
                val videoId = when {
                    url.contains("v=") -> url.substringAfter("v=").substringBefore("&")
                    url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore("?")
                    else -> url.substringAfterLast("/")
                }
                val transcriptList = youtubeTranscriptApi.listTranscripts(videoId)
                val transcript = transcriptList.findTranscript("uk", "ru", "en")
                return@withContext TranscriptFormatters.textFormatter().format(transcript.fetch())
            }

            // Для RSS/Web використовуємо Readability4J для інтелектуального очищення
            val response = okHttpClient.newCall(Request.Builder().url(url).build()).execute()
            if (!response.isSuccessful) return@withContext null
            
            val html = response.body?.string() ?: return@withContext null
            val readability = Readability4J(url, html)
            val article = readability.parse()
            
            val cleanContent = article.content ?: article.textContent
            return@withContext cleanContent
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

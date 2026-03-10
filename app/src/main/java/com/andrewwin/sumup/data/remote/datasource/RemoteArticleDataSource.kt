package com.andrewwin.sumup.data.remote.datasource

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.remote.RssParser
import com.andrewwin.sumup.data.remote.TelegramParser
import com.andrewwin.sumup.data.remote.YouTubeParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

class RemoteArticleDataSource @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val rssParser: RssParser,
    private val telegramParser: TelegramParser,
    private val youtubeParser: YouTubeParser
) {
    suspend fun fetchArticles(sourceId: Long, url: String, type: SourceType): List<Article> = withContext(Dispatchers.IO) {
        when (type) {
            SourceType.RSS -> fetchRssArticles(sourceId, url)
            SourceType.TELEGRAM -> fetchTelegramArticles(sourceId, url)
            SourceType.YOUTUBE -> fetchYouTubeArticles(sourceId, url)
        }
    }

    private fun fetchRssArticles(sourceId: Long, url: String): List<Article> {
        return try {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.byteStream()
                    if (body != null) {
                        return rssParser.parse(body, sourceId)
                    }
                }
            }
            emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun fetchTelegramArticles(sourceId: Long, url: String): List<Article> {
        return try {
            val telegramUrl = if (url.contains("/s/")) url else {
                val handle = url.substringAfterLast("/")
                "https://t.me/s/$handle"
            }
            val request = Request.Builder().url(telegramUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string()
                    if (html != null) {
                        return telegramParser.parse(html, sourceId)
                    }
                }
            }
            emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun fetchYouTubeArticles(sourceId: Long, url: String): List<Article> {
        return try {
            val youtubeUrl = if (url.contains("videos.xml")) url else {
                val channelId = url.substringAfterLast("/")
                "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
            }
            val request = Request.Builder().url(youtubeUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.byteStream()
                    if (body != null) {
                        return youtubeParser.parse(body, sourceId)
                    }
                }
            }
            emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

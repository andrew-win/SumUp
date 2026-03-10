package com.andrewwin.sumup.data.repository

import com.andrewwin.sumup.data.local.dao.ArticleDao
import com.andrewwin.sumup.data.local.dao.SourceDao
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.remote.RssParser
import com.andrewwin.sumup.data.remote.TelegramParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class ArticleRepository(
    private val articleDao: ArticleDao,
    private val sourceDao: SourceDao,
    private val rssParser: RssParser = RssParser(),
    private val telegramParser: TelegramParser = TelegramParser(),
    private val okHttpClient: OkHttpClient = OkHttpClient()
) {
    val enabledArticles: Flow<List<Article>> = articleDao.getEnabledArticles()

    suspend fun refreshArticles() = withContext(Dispatchers.IO) {
        val groups = sourceDao.getGroupsWithSources().first()
        groups.forEach { groupWithSources ->
            if (groupWithSources.group.isEnabled) {
                groupWithSources.sources.forEach { source ->
                    if (source.isEnabled) {
                        when (source.type) {
                            SourceType.RSS -> fetchRssArticles(source.id, source.url)
                            SourceType.TELEGRAM -> fetchTelegramArticles(source.id, source.url)
                            SourceType.YOUTUBE -> { /* TODO */ }
                        }
                    }
                }
            }
        }
        val oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        articleDao.deleteOldArticles(oneWeekAgo)
    }

    private suspend fun fetchRssArticles(sourceId: Long, url: String) {
        try {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.byteStream()
                    if (body != null) {
                        val articles = rssParser.parse(body, sourceId)
                        articleDao.insertArticles(articles)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun fetchTelegramArticles(sourceId: Long, url: String) {
        try {
            val telegramUrl = if (url.contains("/s/")) url else {
                val handle = url.substringAfterLast("/")
                "https://t.me/s/$handle"
            }
            val request = Request.Builder().url(telegramUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string()
                    if (html != null) {
                        val articles = telegramParser.parse(html, sourceId)
                        articleDao.insertArticles(articles)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun updateArticle(article: Article) {
        articleDao.updateArticle(article)
    }
}

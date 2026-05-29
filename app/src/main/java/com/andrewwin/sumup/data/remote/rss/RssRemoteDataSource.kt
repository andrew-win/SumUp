package com.andrewwin.sumup.data.remote.rss

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.remote.RemoteFullContent
import com.andrewwin.sumup.data.remote.RemoteSourceDataSource
import com.andrewwin.sumup.data.remote.RssParser
import com.andrewwin.sumup.domain.entities.ai.RemoteContentFetchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class RssRemoteDataSource(
    private val okHttpClient: OkHttpClient,
    private val displayNameOkHttpClient: OkHttpClient,
    private val rssParser: RssParser
) : RemoteSourceDataSource {

    private val displayNameRssParser = RssParser(displayNameOkHttpClient)

    override suspend fun fetchArticles(
        source: Source,
        oldestAllowedPublishedAt: Long?,
        latestKnownArticleUrl: String?
    ): List<Article> = withContext(Dispatchers.IO) {
        val url = source.url
        val httpsUrl = if (url.startsWith("http://")) "https://${url.removePrefix("http://")}" else url
        val primary = rssParser.parseUrlResult(httpsUrl, source.id)
        if (primary.isSuccess || httpsUrl == url) {
            primary.getOrDefault(emptyList())
        } else {
            val fallback = rssParser.parseUrlResult(url, source.id)
            fallback.getOrDefault(emptyList())
        }
    }

    override suspend fun fetchDisplayName(url: String): String? = withContext(Dispatchers.IO) {
        val httpsUrl = if (url.startsWith("http://")) "https://${url.removePrefix("http://")}" else url
        val primary = displayNameRssParser.parseChannelTitleUrl(httpsUrl)
        if (!primary.isNullOrBlank() || httpsUrl == url) return@withContext primary
        displayNameRssParser.parseChannelTitleUrl(url)
    }

    override suspend fun fetchFullContent(url: String): RemoteFullContent? = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://", true) && !url.startsWith("http://", true)) {
            return@withContext RemoteFullContent(null, RemoteContentFetchStatus.FETCH_FAILED)
        }

        try {
            val response = okHttpClient.newCall(
                Request.Builder()
                    .url(url)
                    .header(HEADER_USER_AGENT, USER_AGENT_VALUE)
                    .header(HEADER_ACCEPT_LANGUAGE, ACCEPT_LANGUAGE_VALUE)
                    .build()
            ).execute()
            if (!response.isSuccessful) return@withContext RemoteFullContent(null, RemoteContentFetchStatus.FETCH_FAILED)
            val body = response.body.string()
            RemoteFullContent(text = body, status = RemoteContentFetchStatus.SUCCESS)
        } catch (e: Exception) {
            RemoteFullContent(null, RemoteContentFetchStatus.FETCH_FAILED)
        }
    }

    private companion object {
        private const val HEADER_USER_AGENT = "User-Agent"
        private const val HEADER_ACCEPT_LANGUAGE = "Accept-Language"
        private const val USER_AGENT_VALUE =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        private const val ACCEPT_LANGUAGE_VALUE = "uk-UA,uk;q=0.9,en-US;q=0.8,en;q=0.7"
    }
}

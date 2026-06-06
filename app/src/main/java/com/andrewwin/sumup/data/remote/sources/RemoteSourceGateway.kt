package com.andrewwin.sumup.data.remote.sources

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.Source

interface RemoteSourceGateway {
    suspend fun fetchArticles(
        source: Source,
        oldestAllowedPublishedAt: Long?
    ): List<Article>

    suspend fun fetchDisplayName(url: String): String?

    suspend fun fetchFullContent(url: String): RemoteFullContent? = null
}

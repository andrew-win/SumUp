package com.andrewwin.sumup.data.remote

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.Source

interface RemoteSourceDataSource {
    suspend fun fetchArticles(
        source: Source,
        oldestAllowedPublishedAt: Long?,
        latestKnownArticleUrl: String?
    ): List<Article>

    suspend fun fetchDisplayName(url: String): String?
    
    suspend fun fetchFullContent(url: String): RemoteFullContent? = null
}

package com.andrewwin.sumup.domain.repository

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.ArticleSimilarity
import kotlinx.coroutines.flow.Flow

interface ArticleRepository {
    val enabledArticles: Flow<List<Article>>
    suspend fun refreshArticles()
    suspend fun updateArticle(article: Article)
    suspend fun getEnabledArticlesOnce(): List<Article>
    suspend fun getEnabledArticlesSince(timestamp: Long): List<Article>
    suspend fun getSourceById(id: Long): com.andrewwin.sumup.data.local.entities.Source?
    suspend fun fetchFullContent(article: Article): String
    suspend fun getSimilaritiesForArticles(articleIds: List<Long>): List<ArticleSimilarity>
    suspend fun upsertSimilarities(items: List<ArticleSimilarity>)
    suspend fun clearAllArticles()
    suspend fun clearEmbeddings()
}

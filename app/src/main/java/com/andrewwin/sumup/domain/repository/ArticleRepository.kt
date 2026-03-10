package com.andrewwin.sumup.domain.repository

import com.andrewwin.sumup.data.local.entities.Article
import kotlinx.coroutines.flow.Flow

interface ArticleRepository {
    val enabledArticles: Flow<List<Article>>
    suspend fun refreshArticles()
    suspend fun updateArticle(article: Article)
    suspend fun getEnabledArticlesOnce(): List<Article>
}

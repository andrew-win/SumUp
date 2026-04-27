package com.andrewwin.sumup.domain.repository

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.ArticleSimilarity
import com.andrewwin.sumup.data.local.entities.SavedArticle
import kotlinx.coroutines.flow.Flow

interface ArticleRepository {
    val enabledArticles: Flow<List<Article>>
    val allArticles: Flow<List<Article>>
    val favoriteArticles: Flow<List<Article>>
    val dataInvalidationSignal: Flow<Long>
    fun triggerDataInvalidation()
    suspend fun refreshArticles()
    suspend fun updateArticle(article: Article)
    suspend fun updateArticles(articles: List<Article>)
    suspend fun setFavoriteByIds(ids: List<Long>, isFavorite: Boolean): Int
    suspend fun getEmbeddingsByIds(ids: List<Long>): Map<Long, ByteArray?>
    suspend fun getArticleEmbeddingsByIds(ids: List<Long>): List<com.andrewwin.sumup.data.local.dao.ArticleEmbedding>
    suspend fun getEnabledArticlesOnce(): List<Article>
    suspend fun getEnabledArticlesSince(timestamp: Long): List<Article>
    suspend fun getSourceById(id: Long): com.andrewwin.sumup.data.local.entities.Source?
    suspend fun fetchFullContent(article: Article): String
    suspend fun getSimilaritiesForArticles(articleIds: List<Long>): List<ArticleSimilarity>
    suspend fun upsertSimilarities(items: List<ArticleSimilarity>)
    suspend fun clearAllArticles()
    suspend fun clearEmbeddings()
    suspend fun clearSimilarities()
    suspend fun clearOldArticlesByAge(days: Int)
    suspend fun getFavoriteArticleUrls(): List<String>
    suspend fun replaceFavoriteArticlesByUrls(urls: List<String>)
    suspend fun mergeFavoriteArticlesByUrls(urls: List<String>)
    suspend fun getSavedArticlesSnapshot(): List<SavedArticle>
    suspend fun replaceSavedArticlesSnapshot(items: List<SavedArticle>)
    suspend fun mergeSavedArticlesSnapshot(items: List<SavedArticle>)
    suspend fun saveFavoriteClusterMapping(articleIds: List<Long>, clusterKey: String?)
    suspend fun clearFavoriteClusterMapping(articleIds: List<Long>)
    suspend fun getFavoriteClusterMappings(articleIds: List<Long>): Map<Long, String>
    suspend fun saveFavoriteSavedAt(articleIds: List<Long>, savedAtMillis: Long = System.currentTimeMillis())
    suspend fun clearFavoriteSavedAt(articleIds: List<Long>)
    suspend fun getFavoriteSavedAt(articleIds: List<Long>): Map<Long, Long>
    suspend fun getFavoriteSimilarities(articleIds: List<Long>): List<ArticleSimilarity>
    suspend fun saveFavoriteClusterScores(scoresByArticleId: Map<Long, Float>)
    suspend fun getFavoriteClusterScores(articleIds: List<Long>): Map<Long, Float>
}







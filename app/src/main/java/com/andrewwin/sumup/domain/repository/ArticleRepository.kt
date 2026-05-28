package com.andrewwin.sumup.domain.repository

import com.andrewwin.sumup.domain.ai.YoutubeSubtitleFetchStatus
import com.andrewwin.sumup.domain.article.Article
import com.andrewwin.sumup.domain.article.ArticleEmbeddingRecord
import com.andrewwin.sumup.domain.article.ArticleSimilarityRecord
import com.andrewwin.sumup.domain.article.SavedArticleSnapshot
import com.andrewwin.sumup.domain.source.Source
import kotlinx.coroutines.flow.Flow

data class FullArticleContent(
    val text: String,
    val youtubeSubtitleStatus: YoutubeSubtitleFetchStatus? = null
)

data class ArticleRefreshResult(
    val changedArticleIds: List<Long> = emptyList(),
    val deletedOldArticlesCount: Int = 0
) {
    val hasMeaningfulChanges: Boolean
        get() = changedArticleIds.isNotEmpty() || deletedOldArticlesCount > 0
}

interface ArticleRepository {
    val enabledArticles: Flow<List<Article>>
    val allArticles: Flow<List<Article>>
    val favoriteArticles: Flow<List<Article>>
    val feedRefreshRequests: Flow<Long>
    fun requestFeedRefresh(timestamp: Long = System.currentTimeMillis())
    suspend fun refreshArticles(): ArticleRefreshResult
    suspend fun updateArticle(article: Article)
    suspend fun updateArticles(articles: List<Article>)
    suspend fun setFavoriteByIds(ids: List<Long>, isFavorite: Boolean): Int
    suspend fun getEmbeddingsByIds(ids: List<Long>): Map<Long, ByteArray?>
    suspend fun getArticleEmbeddingsByIds(ids: List<Long>): List<ArticleEmbeddingRecord>
    suspend fun getEnabledArticlesOnce(): List<Article>
    suspend fun getEnabledArticlesSince(timestamp: Long): List<Article>
    suspend fun getSourceById(id: Long): Source?
    suspend fun fetchFullContent(article: Article): FullArticleContent
    suspend fun getSimilaritiesForArticles(articleIds: List<Long>, strategyKey: String): List<ArticleSimilarityRecord>
    suspend fun getSimilaritiesInsideArticleSet(articleIds: List<Long>, strategyKey: String): List<ArticleSimilarityRecord>
    suspend fun getSimilaritiesInsideArticleSetAboveThreshold(
        articleIds: List<Long>,
        strategyKey: String,
        threshold: Float
    ): List<ArticleSimilarityRecord>
    suspend fun getSimilaritiesTouchingChangedArticles(
        changedArticleIds: List<Long>,
        activeArticleIds: List<Long>,
        strategyKey: String
    ): List<ArticleSimilarityRecord>
    suspend fun upsertSimilarities(items: List<ArticleSimilarityRecord>)
    suspend fun clearAllArticles()
    suspend fun clearEmbeddings()
    suspend fun clearSimilarities()
    suspend fun clearOldArticlesByAge(days: Int)
    suspend fun getFavoriteArticleUrls(): List<String>
    suspend fun replaceFavoriteArticlesByUrls(urls: List<String>)
    suspend fun mergeFavoriteArticlesByUrls(urls: List<String>)
    suspend fun getSavedArticlesSnapshot(): List<SavedArticleSnapshot>
    suspend fun replaceSavedArticlesSnapshot(items: List<SavedArticleSnapshot>)
    suspend fun mergeSavedArticlesSnapshot(items: List<SavedArticleSnapshot>)
    suspend fun saveFavoriteClusterMapping(articleIds: List<Long>, clusterKey: String?)
    suspend fun clearFavoriteClusterMapping(articleIds: List<Long>)
    suspend fun getFavoriteClusterMappings(articleIds: List<Long>): Map<Long, String>
    suspend fun saveFavoriteSavedAt(articleIds: List<Long>, savedAtMillis: Long = System.currentTimeMillis())
    suspend fun clearFavoriteSavedAt(articleIds: List<Long>)
    suspend fun getFavoriteSavedAt(articleIds: List<Long>): Map<Long, Long>
    suspend fun getFavoriteSimilarities(articleIds: List<Long>, strategyKey: String): List<ArticleSimilarityRecord>
    suspend fun saveFavoriteClusterScores(scoresByArticleId: Map<Long, Float>)
    suspend fun getFavoriteClusterScores(articleIds: List<Long>): Map<Long, Float>
}







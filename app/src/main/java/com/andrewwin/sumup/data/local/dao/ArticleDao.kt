package com.andrewwin.sumup.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.andrewwin.sumup.data.local.entities.Article
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    @Query("""
        SELECT
            articles.id,
            articles.stableArticleKey,
            articles.sourceId,
            articles.title,
            articles.content,
            articles.mediaUrl,
            articles.videoId,
            articles.url,
            articles.publishedAt,
            articles.viewCount,
            articles.isRead,
            articles.isFavorite,
            articles.importanceScore
        FROM articles
        INNER JOIN sources ON articles.sourceId = sources.id 
        INNER JOIN source_groups ON sources.groupId = source_groups.id
        WHERE sources.isEnabled = 1 AND source_groups.isEnabled = 1
        ORDER BY articles.publishedAt DESC
    """)
    fun getEnabledArticles(): Flow<List<Article>>

    @Query("""
        SELECT * FROM articles
        ORDER BY publishedAt DESC
    """)
    fun getAllArticles(): Flow<List<Article>>

    @Query("SELECT * FROM articles ORDER BY publishedAt DESC")
    suspend fun getAllArticlesOnce(): List<Article>

    @Query("""
        SELECT * FROM articles
        WHERE isFavorite = 1
        ORDER BY publishedAt DESC
    """)
    fun getFavoriteArticles(): Flow<List<Article>>

    @Query("""
        SELECT
            articles.id,
            articles.stableArticleKey,
            articles.sourceId,
            articles.title,
            articles.content,
            articles.mediaUrl,
            articles.videoId,
            articles.url,
            articles.publishedAt,
            articles.viewCount,
            articles.isRead,
            articles.isFavorite,
            articles.importanceScore
        FROM articles
        INNER JOIN sources ON articles.sourceId = sources.id 
        INNER JOIN source_groups ON sources.groupId = source_groups.id
        WHERE sources.isEnabled = 1 AND source_groups.isEnabled = 1
        ORDER BY articles.publishedAt DESC
    """)
    suspend fun getEnabledArticlesOnce(): List<Article>

    @Query("""
        SELECT
            articles.id,
            articles.stableArticleKey,
            articles.sourceId,
            articles.title,
            articles.content,
            articles.mediaUrl,
            articles.videoId,
            articles.url,
            articles.publishedAt,
            articles.viewCount,
            articles.isRead,
            articles.isFavorite,
            articles.importanceScore
        FROM articles
        INNER JOIN sources ON articles.sourceId = sources.id 
        INNER JOIN source_groups ON sources.groupId = source_groups.id
        WHERE sources.isEnabled = 1 AND source_groups.isEnabled = 1 AND articles.publishedAt >= :timestamp
        ORDER BY articles.publishedAt DESC
    """)
    suspend fun getEnabledArticlesSince(timestamp: Long): List<Article>

    @Query("SELECT * FROM articles WHERE sourceId = :sourceId ORDER BY publishedAt DESC")
    fun getArticlesBySource(sourceId: Long): Flow<List<Article>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArticles(articles: List<Article>): List<Long>

    @Transaction
    suspend fun insertAndUpdateFetchedArticles(articles: List<Article>) {
        if (articles.isEmpty()) return
        insertArticles(articles)
        for (article in articles) {
            updateFetchedArticleByStableArticleKey(
                stableArticleKey = article.stableArticleKey,
                sourceId = article.sourceId,
                title = article.title,
                content = article.content,
                mediaUrl = article.mediaUrl,
                videoId = article.videoId,
                publishedAt = article.publishedAt,
                viewCount = article.viewCount,
                url = article.url
            )
        }
    }

    @Query(
        """
        UPDATE articles
        SET sourceId = :sourceId,
            title = :title,
            content = :content,
            mediaUrl = :mediaUrl,
            videoId = :videoId,
            publishedAt = :publishedAt,
            viewCount = :viewCount,
            url = :url
        WHERE stableArticleKey = :stableArticleKey
        """
    )
    suspend fun updateFetchedArticleByStableArticleKey(
        stableArticleKey: String,
        sourceId: Long,
        title: String,
        content: String,
        mediaUrl: String?,
        videoId: String?,
        publishedAt: Long,
        viewCount: Long,
        url: String
    )

    @Query(
        "UPDATE articles SET mediaUrl = :mediaUrl, videoId = :videoId WHERE stableArticleKey = :stableArticleKey"
    )
    suspend fun updateMediaByStableArticleKey(
        stableArticleKey: String,
        mediaUrl: String?,
        videoId: String?
    )

    @Query(
        """
        UPDATE articles
        SET viewCount = :viewCount,
            importanceScore = :importanceScore,
            mediaUrl = :mediaUrl,
            videoId = :videoId
        WHERE stableArticleKey = :stableArticleKey
        """
    )
    suspend fun updateArticleLiveMetricsByStableArticleKey(
        stableArticleKey: String,
        viewCount: Long,
        importanceScore: Float,
        mediaUrl: String?,
        videoId: String?
    )

    @Update
    suspend fun updateArticle(article: Article)

    @Update
    suspend fun updateArticles(articles: List<Article>)

    @Query("UPDATE articles SET isFavorite = :isFavorite WHERE id IN (:ids)")
    suspend fun setFavoriteByIds(ids: List<Long>, isFavorite: Boolean): Int

    @Query("UPDATE articles SET isFavorite = :isFavorite WHERE url IN (:urls)")
    suspend fun setFavoriteByUrls(urls: List<String>, isFavorite: Boolean): Int

    @Query(
        """
        SELECT
            articles.id AS id,
            articles.stableArticleKey AS stableArticleKey,
            articles.sourceId AS sourceId,
            articles.title AS title,
            articles.content AS content,
            articles.mediaUrl AS mediaUrl,
            articles.videoId AS videoId,
            articles.url AS url,
            articles.publishedAt AS publishedAt,
            articles.viewCount AS viewCount,
            articles.isRead AS isRead,
            articles.isFavorite AS isFavorite,
            articles.importanceScore AS importanceScore,
            sources.name AS sourceName,
            source_groups.name AS groupName
        FROM articles
        LEFT JOIN sources ON sources.id = articles.sourceId
        LEFT JOIN source_groups ON source_groups.id = sources.groupId
        WHERE articles.id IN (:ids)
        """
    )
    suspend fun getArticlesWithMetaByIds(ids: List<Long>): List<ArticleWithMeta>

    @Query(
        """
        SELECT
            articles.id AS articleId,
            saved_articles.savedAt AS savedAt
        FROM articles
        INNER JOIN saved_articles ON saved_articles.url = articles.url
        WHERE articles.id IN (:articleIds)
        """
    )
    suspend fun getFavoriteSavedAtByArticleIds(articleIds: List<Long>): List<ArticleSavedAtRow>

    @Query("SELECT id FROM articles WHERE id IN (:ids)")
    suspend fun getExistingArticleIds(ids: List<Long>): List<Long>

    @Query("SELECT stableArticleKey FROM articles WHERE stableArticleKey IN (:stableArticleKeys)")
    suspend fun getExistingStableArticleKeys(stableArticleKeys: List<String>): List<String>

    @Query("SELECT * FROM articles WHERE stableArticleKey IN (:stableArticleKeys)")
    suspend fun getArticlesByStableArticleKeys(stableArticleKeys: List<String>): List<Article>

    @Query("SELECT id FROM articles WHERE stableArticleKey IN (:stableArticleKeys)")
    suspend fun getArticleIdsByStableArticleKeys(stableArticleKeys: List<String>): List<Long>

    @Query(
        """
        SELECT url FROM articles
        WHERE sourceId = :sourceId
        ORDER BY publishedAt DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun getLatestArticleUrlBySourceId(sourceId: Long): String?

    @Delete
    suspend fun deleteArticle(article: Article)

    @Query("DELETE FROM articles")
    suspend fun deleteAllArticles()

    @Query("DELETE FROM articles WHERE publishedAt < :timestamp")
    suspend fun deleteOldArticles(timestamp: Long): Int

    @Query("SELECT COUNT(*) FROM articles WHERE publishedAt >= :timestamp")
    suspend fun countArticlesNewerThan(timestamp: Long): Int

    @Query("SELECT COUNT(*) FROM articles WHERE publishedAt < :timestamp")
    suspend fun countArticlesOlderThan(timestamp: Long): Int

    @Query("SELECT url FROM articles WHERE isFavorite = 1")
    suspend fun getFavoriteArticleUrls(): List<String>

    @Query("UPDATE articles SET isFavorite = 0")
    suspend fun clearAllFavorites(): Int

    @Query("UPDATE articles SET isFavorite = 1 WHERE url IN (:urls)")
    suspend fun markFavoritesByUrls(urls: List<String>): Int
}

data class ArticleWithMeta(
    val id: Long,
    val stableArticleKey: String,
    val sourceId: Long,
    val title: String,
    val content: String,
    val mediaUrl: String?,
    val videoId: String?,
    val url: String,
    val publishedAt: Long,
    val viewCount: Long,
    val isRead: Boolean,
    val isFavorite: Boolean,
    val importanceScore: Float,
    val sourceName: String?,
    val groupName: String?
)

data class ArticleSavedAtRow(
    val articleId: Long,
    val savedAt: Long
)

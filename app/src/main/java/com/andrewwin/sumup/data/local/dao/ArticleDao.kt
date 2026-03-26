package com.andrewwin.sumup.data.local.dao

import androidx.room.*
import com.andrewwin.sumup.data.local.entities.Article
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    @Query("""
        SELECT
            articles.id,
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
            NULL AS embedding
        FROM articles
        INNER JOIN sources ON articles.sourceId = sources.id 
        INNER JOIN source_groups ON sources.groupId = source_groups.id
        WHERE sources.isEnabled = 1 AND source_groups.isEnabled = 1
        ORDER BY articles.publishedAt DESC
        LIMIT 200
    """)
    fun getEnabledArticles(): Flow<List<Article>>

    @Query("""
        SELECT
            articles.id,
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
            NULL AS embedding
        FROM articles
        INNER JOIN sources ON articles.sourceId = sources.id 
        INNER JOIN source_groups ON sources.groupId = source_groups.id
        WHERE sources.isEnabled = 1 AND source_groups.isEnabled = 1
        ORDER BY articles.publishedAt DESC
        LIMIT 200
    """)
    suspend fun getEnabledArticlesOnce(): List<Article>

    @Query("""
        SELECT
            articles.id,
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
            NULL AS embedding
        FROM articles
        INNER JOIN sources ON articles.sourceId = sources.id 
        INNER JOIN source_groups ON sources.groupId = source_groups.id
        WHERE sources.isEnabled = 1 AND source_groups.isEnabled = 1 AND articles.publishedAt >= :timestamp
        ORDER BY articles.publishedAt DESC
        LIMIT 200
    """)
    suspend fun getEnabledArticlesSince(timestamp: Long): List<Article>

    @Query("SELECT * FROM articles WHERE sourceId = :sourceId ORDER BY publishedAt DESC")
    fun getArticlesBySource(sourceId: Long): Flow<List<Article>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArticles(articles: List<Article>): List<Long>

    @Query(
        """
        UPDATE articles
        SET sourceId = :sourceId,
            title = :title,
            content = :content,
            mediaUrl = :mediaUrl,
            videoId = :videoId,
            publishedAt = :publishedAt,
            viewCount = :viewCount
        WHERE url = :url
        """
    )
    suspend fun updateFetchedArticleByUrl(
        sourceId: Long,
        title: String,
        content: String,
        mediaUrl: String?,
        videoId: String?,
        publishedAt: Long,
        viewCount: Long,
        url: String
    )

    @Query("UPDATE articles SET mediaUrl = :mediaUrl, videoId = :videoId WHERE url = :url")
    suspend fun updateMediaByUrl(url: String, mediaUrl: String?, videoId: String?)

    @Update
    suspend fun updateArticle(article: Article)

    @Update
    suspend fun updateArticles(articles: List<Article>)

    @Query("SELECT id, embedding FROM articles WHERE id IN (:ids)")
    suspend fun getEmbeddingsByIds(ids: List<Long>): List<ArticleEmbedding>

    @Delete
    suspend fun deleteArticle(article: Article)

    @Query("UPDATE articles SET embedding = NULL")
    suspend fun clearEmbeddings()

    @Query("DELETE FROM articles")
    suspend fun deleteAllArticles()

    @Query("DELETE FROM articles WHERE publishedAt < :timestamp")
    suspend fun deleteOldArticles(timestamp: Long): Int

    @Query("SELECT COUNT(*) FROM articles WHERE publishedAt >= :timestamp")
    suspend fun countArticlesNewerThan(timestamp: Long): Int

    @Query("SELECT COUNT(*) FROM articles WHERE publishedAt < :timestamp")
    suspend fun countArticlesOlderThan(timestamp: Long): Int
}

data class ArticleEmbedding(
    val id: Long,
    val embedding: ByteArray?
)







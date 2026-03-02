package com.andrewwin.sumup.data.local.dao

import androidx.room.*
import com.andrewwin.sumup.data.local.entities.Article
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    @Query("""
        SELECT articles.* FROM articles 
        INNER JOIN sources ON articles.sourceId = sources.id 
        INNER JOIN source_groups ON sources.groupId = source_groups.id
        WHERE sources.isEnabled = 1 AND source_groups.isEnabled = 1
        ORDER BY articles.publishedAt DESC
    """)
    fun getEnabledArticles(): Flow<List<Article>>

    @Query("""
        SELECT articles.* FROM articles 
        INNER JOIN sources ON articles.sourceId = sources.id 
        INNER JOIN source_groups ON sources.groupId = source_groups.id
        WHERE sources.isEnabled = 1 AND source_groups.isEnabled = 1
        ORDER BY articles.publishedAt DESC
    """)
    suspend fun getEnabledArticlesOnce(): List<Article>

    @Query("SELECT * FROM articles WHERE sourceId = :sourceId ORDER BY publishedAt DESC")
    fun getArticlesBySource(sourceId: Long): Flow<List<Article>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArticles(articles: List<Article>)

    @Update
    suspend fun updateArticle(article: Article)

    @Delete
    suspend fun deleteArticle(article: Article)

    @Query("DELETE FROM articles WHERE publishedAt < :timestamp")
    suspend fun deleteOldArticles(timestamp: Long)
}

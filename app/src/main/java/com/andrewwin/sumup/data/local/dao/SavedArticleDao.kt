package com.andrewwin.sumup.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.andrewwin.sumup.data.local.entities.SavedArticle
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedArticleDao {
    @Query("SELECT * FROM saved_articles ORDER BY savedAt DESC")
    fun getSavedArticles(): Flow<List<SavedArticle>>

    @Query("SELECT * FROM saved_articles ORDER BY savedAt DESC")
    suspend fun getSavedArticlesOnce(): List<SavedArticle>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<SavedArticle>)

    @Query("DELETE FROM saved_articles WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>): Int

    @Query("DELETE FROM saved_articles WHERE url IN (:urls)")
    suspend fun deleteByUrls(urls: List<String>): Int

    @Query("DELETE FROM saved_articles")
    suspend fun deleteAll(): Int

    @Query("SELECT url FROM saved_articles")
    suspend fun getSavedUrls(): List<String>

    @Query("UPDATE saved_articles SET clusterKey = :clusterKey WHERE id IN (:ids)")
    suspend fun updateClusterKeyByIds(ids: List<Long>, clusterKey: String?): Int

    @Query("UPDATE saved_articles SET clusterKey = :clusterKey WHERE url IN (:urls)")
    suspend fun updateClusterKeyByUrls(urls: List<String>, clusterKey: String?): Int

    @Query("UPDATE saved_articles SET clusterScore = :score WHERE id = :id")
    suspend fun updateClusterScoreById(id: Long, score: Float): Int

    @Query("UPDATE saved_articles SET clusterScore = :score WHERE url = :url")
    suspend fun updateClusterScoreByUrl(url: String, score: Float): Int
}

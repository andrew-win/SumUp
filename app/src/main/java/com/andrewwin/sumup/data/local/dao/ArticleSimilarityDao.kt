package com.andrewwin.sumup.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.andrewwin.sumup.data.local.entities.ArticleSimilarity

@Dao
interface ArticleSimilarityDao {
    @Query(
        """
        SELECT * FROM article_similarities
        WHERE strategyKey = :strategyKey
            AND (leftArticleId IN (:articleIds) OR rightArticleId IN (:articleIds))
        """
    )
    suspend fun getSimilaritiesForArticles(
        articleIds: List<Long>,
        strategyKey: String
    ): List<ArticleSimilarity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSimilarities(items: List<ArticleSimilarity>)

    @Query("DELETE FROM article_similarities")
    suspend fun deleteAll()
}







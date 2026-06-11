package com.andrewwin.sumup.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.andrewwin.sumup.data.local.entities.ArticleEmbedding

@Dao
interface ArticleEmbeddingDao {
    @Query(
        """
        SELECT articleId AS id, embedding, embeddingType
        FROM article_embeddings
        WHERE articleId IN (:ids)
        """
    )
    suspend fun getEmbeddingsByIds(ids: List<Long>): List<ArticleEmbeddingRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEmbeddings(items: List<ArticleEmbedding>)

    @Query("DELETE FROM article_embeddings")
    suspend fun clearEmbeddings()
}

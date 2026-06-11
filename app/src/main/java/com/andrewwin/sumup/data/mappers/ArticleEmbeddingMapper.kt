package com.andrewwin.sumup.data.mappers

import com.andrewwin.sumup.data.local.dao.ArticleEmbeddingRow
import com.andrewwin.sumup.data.local.entities.ArticleEmbedding
import com.andrewwin.sumup.domain.article.model.ArticleEmbeddingRecord

fun ArticleEmbeddingRow.toDomainRecord(): ArticleEmbeddingRecord = ArticleEmbeddingRecord(
    id = id,
    embedding = embedding,
    embeddingType = embeddingType
)

fun ArticleEmbeddingRecord.toRoomEntity(): ArticleEmbedding? {
    val bytes = embedding ?: return null
    val type = embeddingType?.takeIf { it.isNotBlank() } ?: return null
    return ArticleEmbedding(
        articleId = id,
        embedding = bytes,
        embeddingType = type
    )
}

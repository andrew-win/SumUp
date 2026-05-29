package com.andrewwin.sumup.data.mappers

import com.andrewwin.sumup.data.local.dao.ArticleEmbedding
import com.andrewwin.sumup.domain.entities.article.ArticleEmbeddingRecord

fun ArticleEmbedding.toDomainRecord(): ArticleEmbeddingRecord = ArticleEmbeddingRecord(
    id = id,
    embedding = embedding,
    embeddingType = embeddingType
)

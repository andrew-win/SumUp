package com.andrewwin.sumup.domain.article.model

data class ArticleEmbeddingRecord(
    val id: Long,
    val embedding: ByteArray?,
    val embeddingType: String?
)

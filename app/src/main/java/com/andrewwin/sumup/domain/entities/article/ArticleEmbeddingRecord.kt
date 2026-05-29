package com.andrewwin.sumup.domain.entities.article

data class ArticleEmbeddingRecord(
    val id: Long,
    val embedding: ByteArray?,
    val embeddingType: String?
)

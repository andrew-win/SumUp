package com.andrewwin.sumup.data.local.dao

data class ArticleEmbeddingRow(
    val id: Long,
    val embedding: ByteArray?,
    val embeddingType: String?
)

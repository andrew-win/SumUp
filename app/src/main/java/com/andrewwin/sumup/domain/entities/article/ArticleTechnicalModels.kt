package com.andrewwin.sumup.domain.entities.article

data class ArticleEmbeddingRecord(
    val id: Long,
    val embedding: ByteArray?,
    val embeddingType: String?
)

data class ArticleSimilarityRecord(
    val leftArticleId: Long,
    val rightArticleId: Long,
    val strategyKey: String,
    val score: Float,
    val leftContentSignature: String,
    val rightContentSignature: String,
    val updatedAt: Long = System.currentTimeMillis()
)

data class SavedArticleSnapshot(
    val id: Long = 0,
    val url: String,
    val title: String,
    val content: String,
    val mediaUrl: String? = null,
    val videoId: String? = null,
    val publishedAt: Long,
    val viewCount: Long = 0,
    val sourceName: String? = null,
    val groupName: String? = null,
    val savedAt: Long = System.currentTimeMillis(),
    val clusterKey: String? = null,
    val clusterScore: Float = 0f
)

package com.andrewwin.sumup.domain.article.model

data class Article(
    val id: Long = 0,
    val stableArticleKey: String = "",
    val sourceId: Long,
    val title: String,
    val content: String,
    val mediaUrl: String? = null,
    val videoId: String? = null,
    val url: String,
    val publishedAt: Long,
    val viewCount: Long = 0,
    val isRead: Boolean = false,
    val isFavorite: Boolean = false,
    val importanceScore: Float = 0f,
    val embedding: ByteArray? = null,
    val embeddingType: String? = null
)

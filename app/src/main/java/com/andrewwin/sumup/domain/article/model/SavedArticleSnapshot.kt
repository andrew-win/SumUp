package com.andrewwin.sumup.domain.article.model

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

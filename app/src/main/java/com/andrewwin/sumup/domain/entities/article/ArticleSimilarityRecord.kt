package com.andrewwin.sumup.domain.entities.article

data class ArticleSimilarityRecord(
    val leftArticleId: Long,
    val rightArticleId: Long,
    val strategyKey: String,
    val score: Float,
    val leftContentSignature: String,
    val rightContentSignature: String,
    val updatedAt: Long = System.currentTimeMillis()
)

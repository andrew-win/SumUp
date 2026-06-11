package com.andrewwin.sumup.data.local.dao

data class ArticleSimilarityScoreRow(
    val leftArticleId: Long,
    val rightArticleId: Long,
    val score: Float
)

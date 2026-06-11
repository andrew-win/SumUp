package com.andrewwin.sumup.domain.feed.model

data class ArticlePairScore(
    val leftArticleId: Long,
    val rightArticleId: Long,
    val score: Float
)

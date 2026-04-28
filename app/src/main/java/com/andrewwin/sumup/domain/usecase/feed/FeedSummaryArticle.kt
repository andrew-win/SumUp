package com.andrewwin.sumup.domain.usecase.feed

import com.andrewwin.sumup.data.local.entities.Article

data class FeedSummaryArticle(
    val article: Article,
    val similarArticlesCount: Int,
    val baseImportanceScore: Float
)

package com.andrewwin.sumup.domain.feed

import com.andrewwin.sumup.domain.entities.article.Article

data class FeedSummaryArticle(
    val article: Article,
    val similarArticlesCount: Int,
    val baseImportanceScore: Float
)

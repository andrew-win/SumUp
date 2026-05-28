package com.andrewwin.sumup.domain.feed

import com.andrewwin.sumup.domain.article.Article

data class FeedSummaryArticle(
    val article: Article,
    val similarArticlesCount: Int,
    val baseImportanceScore: Float
)

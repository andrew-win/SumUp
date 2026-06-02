package com.andrewwin.sumup.domain.feed.model

import com.andrewwin.sumup.domain.article.model.Article

data class FeedSummaryArticle(
    val article: Article,
    val similarArticlesCount: Int,
    val baseImportanceScore: Float
)
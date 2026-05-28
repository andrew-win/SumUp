package com.andrewwin.sumup.ui.screen.feed.model

import com.andrewwin.sumup.domain.article.Article
import com.andrewwin.sumup.domain.source.SourceType

data class ArticleUiModel(
    val article: Article,
    val sourceType: SourceType,
    val displayTitle: String,
    val displayContent: String,
    val sourceName: String?,
    val groupName: String?,
    val savedAt: Long? = null
)

data class ArticleClusterUiModel(
    val representative: ArticleUiModel,
    val duplicates: List<Pair<ArticleUiModel, Float>>
)








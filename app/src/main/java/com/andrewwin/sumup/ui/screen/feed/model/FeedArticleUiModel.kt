package com.andrewwin.sumup.ui.screen.feed.model

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.SourceType

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








package com.andrewwin.sumup.ui.screen.feed.model

import com.andrewwin.sumup.domain.article.model.Article
import com.andrewwin.sumup.domain.source.model.SourceType

data class ArticleUiModel(
    val article: Article,
    val sourceType: SourceType,
    val displayTitle: String,
    val displayContent: String,
    val sourceName: String?,
    val groupName: String?,
    val savedAt: Long? = null
)

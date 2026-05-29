package com.andrewwin.sumup.ui.screen.feed.model

import com.andrewwin.sumup.domain.entities.article.Article
import com.andrewwin.sumup.domain.entities.source.SourceType

data class ArticleUiModel(
    val article: Article,
    val sourceType: SourceType,
    val displayTitle: String,
    val displayContent: String,
    val sourceName: String?,
    val groupName: String?,
    val savedAt: Long? = null
)

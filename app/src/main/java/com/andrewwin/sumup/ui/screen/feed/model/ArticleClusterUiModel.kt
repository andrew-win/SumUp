package com.andrewwin.sumup.ui.screen.feed.model

data class ArticleClusterUiModel(
    val representative: ArticleUiModel,
    val duplicates: List<Pair<ArticleUiModel, Float>>
)

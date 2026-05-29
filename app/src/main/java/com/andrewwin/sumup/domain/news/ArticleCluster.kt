package com.andrewwin.sumup.domain.news

import com.andrewwin.sumup.domain.entities.article.Article

data class ArticleCluster(
    val representative: Article,
    val duplicates: List<Pair<Article, Float>>
)

package com.andrewwin.sumup.domain.service

import com.andrewwin.sumup.data.local.entities.Article

data class ArticleCluster(
    val representative: Article,
    val duplicates: List<Pair<Article, Float>>
)

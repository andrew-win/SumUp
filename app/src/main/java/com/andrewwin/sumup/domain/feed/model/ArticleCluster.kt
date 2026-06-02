package com.andrewwin.sumup.domain.feed.model

import com.andrewwin.sumup.domain.article.model.Article

data class ArticleCluster(
    val representative: Article,
    val duplicates: List<Pair<Article, Float>>
)

data class ArticlePairKey(val firstId: Long, val secondId: Long) {
    companion object {
        fun of(id1: Long, id2: Long): ArticlePairKey =
            if (id1 <= id2) ArticlePairKey(id1, id2) else ArticlePairKey(id2, id1)
    }
}
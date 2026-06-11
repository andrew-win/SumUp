package com.andrewwin.sumup.domain.feed.model

fun List<ArticlePairScore>.toPairScoreMap(): Map<ArticlePairKey, Float> {
    return associate { similarity ->
        ArticlePairKey.of(similarity.leftArticleId, similarity.rightArticleId) to similarity.score
    }
}

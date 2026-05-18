package com.andrewwin.sumup.domain.usecase.feed

data class ArticlePairKey(val firstId: Long, val secondId: Long) {
    companion object {
        fun of(id1: Long, id2: Long): ArticlePairKey =
            if (id1 <= id2) ArticlePairKey(id1, id2) else ArticlePairKey(id2, id1)
    }
}

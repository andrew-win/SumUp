package com.andrewwin.sumup.data.remote.sources

import com.andrewwin.sumup.data.local.entities.Article

data class SourceRefreshBoundary(
    val knownStableArticleKeys: Set<String>,
    val knownUrls: Set<String>,
    val knownVideoIds: Set<String>
) {
    fun isKnown(article: Article): Boolean {
        val videoId = article.videoId.orEmpty()
        return (article.stableArticleKey.isNotBlank() && article.stableArticleKey in knownStableArticleKeys) ||
            (article.url.isNotBlank() && article.url in knownUrls) ||
            (videoId.isNotBlank() && videoId in knownVideoIds)
    }

    companion object {
        val Empty = SourceRefreshBoundary(
            knownStableArticleKeys = emptySet(),
            knownUrls = emptySet(),
            knownVideoIds = emptySet()
        )
    }
}

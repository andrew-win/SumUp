package com.andrewwin.sumup.ui.screen.feed

import com.andrewwin.sumup.ui.screen.feed.model.ArticleClusterUiModel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedAiSessionCache @Inject constructor() {
    private val cache = mutableMapOf<String, AiPresentationResult>()

    fun getArticleSummary(articleId: Long): AiPresentationResult? = cache[articleKey(articleId)]

    fun putArticleSummary(articleId: Long, summary: AiPresentationResult) {
        cache[articleKey(articleId)] = summary
    }

    fun getFeedSummary(articleIds: List<Long>): AiPresentationResult? = cache[feedKey(articleIds)]

    fun putFeedSummary(articleIds: List<Long>, summary: AiPresentationResult) {
        cache[feedKey(articleIds)] = summary
    }

    fun getClusterSummary(cluster: ArticleClusterUiModel): AiPresentationResult? = cache[compareKey(cluster)]

    fun putClusterSummary(cluster: ArticleClusterUiModel, summary: AiPresentationResult) {
        cache[compareKey(cluster)] = summary
    }

    fun getClusterSummary(articleIds: List<Long>): AiPresentationResult? = cache[compareKey(articleIds)]

    fun putClusterSummary(articleIds: List<Long>, summary: AiPresentationResult) {
        cache[compareKey(articleIds)] = summary
    }

    private fun articleKey(articleId: Long): String = "$CACHE_VERSION:article:$articleId"

    private fun feedKey(articleIds: List<Long>): String = "$CACHE_VERSION:feed:${articleIds.joinToString(",")}"

    private fun compareKey(cluster: ArticleClusterUiModel): String {
        val duplicateIds = cluster.duplicates.map { it.first.article.id }.sorted()
        return "$CACHE_VERSION:compare:${cluster.representative.article.id}:${duplicateIds.joinToString(",")}"
    }

    private fun compareKey(articleIds: List<Long>): String {
        if (articleIds.isEmpty()) return "$CACHE_VERSION:compare:"
        val representativeId = articleIds.first()
        val duplicateIds = articleIds.drop(1).sorted()
        return "$CACHE_VERSION:compare:$representativeId:${duplicateIds.joinToString(",")}"
    }

    private companion object {
        const val CACHE_VERSION = "v2"
    }
}






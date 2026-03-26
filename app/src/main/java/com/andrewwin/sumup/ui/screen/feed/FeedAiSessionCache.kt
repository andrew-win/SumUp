package com.andrewwin.sumup.ui.screen.feed

import com.andrewwin.sumup.ui.screen.feed.model.ArticleClusterUiModel

class FeedAiSessionCache {
    private val cache = mutableMapOf<String, String>()

    fun getArticleSummary(articleId: Long): String? = cache[articleKey(articleId)]

    fun putArticleSummary(articleId: Long, summary: String) {
        cache[articleKey(articleId)] = summary
    }

    fun getFeedSummary(articleIds: List<Long>): String? = cache[feedKey(articleIds)]

    fun putFeedSummary(articleIds: List<Long>, summary: String) {
        cache[feedKey(articleIds)] = summary
    }

    fun getClusterSummary(cluster: ArticleClusterUiModel): String? = cache[compareKey(cluster)]

    fun putClusterSummary(cluster: ArticleClusterUiModel, summary: String) {
        cache[compareKey(cluster)] = summary
    }

    private fun articleKey(articleId: Long): String = "article:$articleId"

    private fun feedKey(articleIds: List<Long>): String = "feed:${articleIds.joinToString(",")}"

    private fun compareKey(cluster: ArticleClusterUiModel): String {
        val duplicateIds = cluster.duplicates.map { it.first.article.id }.sorted()
        return "compare:${cluster.representative.article.id}:${duplicateIds.joinToString(",")}"
    }
}








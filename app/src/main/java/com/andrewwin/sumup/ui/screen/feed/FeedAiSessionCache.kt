package com.andrewwin.sumup.ui.screen.feed

import com.andrewwin.sumup.domain.support.DebugTrace
import com.andrewwin.sumup.ui.screen.feed.model.ArticleClusterUiModel

class FeedAiSessionCache {
    private val cache = mutableMapOf<String, String>()

    fun getArticleSummary(articleId: Long): String? =
        cache[articleKey(articleId)].also { DebugTrace.d("ai_cache", "article id=$articleId hit=${it != null}") }

    fun putArticleSummary(articleId: Long, summary: String) {
        DebugTrace.d("ai_cache", "article id=$articleId store preview=${DebugTrace.preview(summary)}")
        cache[articleKey(articleId)] = summary
    }

    fun getFeedSummary(articleIds: List<Long>): String? =
        cache[feedKey(articleIds)].also {
            DebugTrace.d("ai_cache", "feed ids=${articleIds.joinToString(",")} hit=${it != null}")
        }

    fun putFeedSummary(articleIds: List<Long>, summary: String) {
        DebugTrace.d(
            "ai_cache",
            "feed ids=${articleIds.joinToString(",")} store preview=${DebugTrace.preview(summary)}"
        )
        cache[feedKey(articleIds)] = summary
    }

    fun getClusterSummary(cluster: ArticleClusterUiModel): String? =
        cache[compareKey(cluster)].also {
            DebugTrace.d(
                "ai_cache",
                "cluster rep=${cluster.representative.article.id} dup=${cluster.duplicates.size} hit=${it != null}"
            )
        }

    fun putClusterSummary(cluster: ArticleClusterUiModel, summary: String) {
        DebugTrace.d(
            "ai_cache",
            "cluster rep=${cluster.representative.article.id} dup=${cluster.duplicates.size} store preview=${DebugTrace.preview(summary)}"
        )
        cache[compareKey(cluster)] = summary
    }

    private fun articleKey(articleId: Long): String = "$CACHE_VERSION:article:$articleId"

    private fun feedKey(articleIds: List<Long>): String = "$CACHE_VERSION:feed:${articleIds.joinToString(",")}"

    private fun compareKey(cluster: ArticleClusterUiModel): String {
        val duplicateIds = cluster.duplicates.map { it.first.article.id }.sorted()
        return "$CACHE_VERSION:compare:${cluster.representative.article.id}:${duplicateIds.joinToString(",")}"
    }

    private companion object {
        const val CACHE_VERSION = "v2"
    }
}








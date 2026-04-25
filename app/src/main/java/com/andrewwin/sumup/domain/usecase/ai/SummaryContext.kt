package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.UserPreferences

sealed class SummaryContext {
    data class SingleArticle(val hasClusterDuplicates: Boolean = false) : SummaryContext()
    data class Feed(val articleCount: Int) : SummaryContext()
    data class ScheduledSummary(val articleCount: Int) : SummaryContext()

    fun pointsPerNews(prefs: UserPreferences): Int = when (this) {
        is SingleArticle, is Feed -> prefs.summaryItemsPerNewsInFeed.coerceAtLeast(1)
        is ScheduledSummary -> prefs.summaryItemsPerNewsInScheduled.coerceAtLeast(1)
    }

    fun extractiveSentencesLimit(prefs: UserPreferences): Int = when (this) {
        is SingleArticle, is Feed -> DEFAULT_FEED_EXTRACTIVE_SENTENCES
        is ScheduledSummary -> prefs.extractiveSentencesInScheduled.coerceAtLeast(1)
    }

    fun cloudNewsLimit(prefs: UserPreferences): Int = when (this) {
        is SingleArticle -> 1
        is Feed -> prefs.summaryNewsInFeedCloud.coerceAtLeast(1)
        is ScheduledSummary -> prefs.summaryNewsInScheduledCloud.coerceAtLeast(1)
    }

    fun extractiveNewsLimit(prefs: UserPreferences): Int = when (this) {
        is SingleArticle -> 1
        is Feed -> prefs.extractiveNewsInFeed.coerceAtLeast(1)
        is ScheduledSummary -> prefs.extractiveNewsInScheduled.coerceAtLeast(1)
    }

    private companion object {
        const val DEFAULT_FEED_EXTRACTIVE_SENTENCES = 5
    }
}










package com.andrewwin.sumup.domain.settings.model

data class FeedSummarySettings(
    val summaryItemsPerNewsInFeed: Int,
    val summaryItemsPerNewsInScheduled: Int,
    val summaryNewsInFeedCloud: Int,
    val summaryNewsInScheduledCloud: Int,
    val extractiveNewsInFeed: Int,
    val extractiveSentencesInScheduled: Int,
    val extractiveNewsInScheduled: Int,
    val aiMaxCharsSingleArticle: Int,
    val aiMaxCharsNewsCluster: Int,
    val aiMaxCharsSingleFeedArticle: Int,
    val aiMaxCharsFeedCluster: Int,
    val aiMaxCharsTotal: Int,
    val isUseFullTextEnabled: Boolean
)

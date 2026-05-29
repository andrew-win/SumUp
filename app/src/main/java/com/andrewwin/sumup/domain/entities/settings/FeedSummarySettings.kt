package com.andrewwin.sumup.domain.entities.settings

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

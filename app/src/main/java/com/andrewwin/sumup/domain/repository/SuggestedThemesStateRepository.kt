package com.andrewwin.sumup.domain.repository

interface SuggestedThemesStateRepository {
    fun getSavedThemeIds(): Set<String>?
    fun getSavedThemeTitlesLegacy(): Set<String>?
    fun getLastRecommendationAt(): Long
    fun getLastFeedRefreshAt(): Long
    fun saveRecommendationState(
        savedThemeIds: Set<String>,
        sourcesHash: Int,
        timestamp: Long
    )
    fun setLastFeedRefreshAt(timestamp: Long)
}







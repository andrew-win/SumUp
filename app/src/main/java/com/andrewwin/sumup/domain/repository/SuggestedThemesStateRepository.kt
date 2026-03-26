package com.andrewwin.sumup.domain.repository

interface SuggestedThemesStateRepository {
    fun getSavedThemeTitles(): Set<String>?
    fun getLastRecommendationAt(): Long
    fun getLastFeedRefreshAt(): Long
    fun saveRecommendationState(
        savedThemeTitles: Set<String>,
        sourcesHash: Int,
        timestamp: Long
    )
    fun setLastFeedRefreshAt(timestamp: Long)
}







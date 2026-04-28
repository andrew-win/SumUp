package com.andrewwin.sumup.data.repository

import android.content.Context
import com.andrewwin.sumup.domain.repository.SuggestedThemesStateRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SuggestedThemesStateRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context
) : SuggestedThemesStateRepository {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getSavedThemeIds(): Set<String>? = prefs.getStringSet(KEY_SAVED_THEME_IDS, null)

    override fun getSavedThemeTitlesLegacy(): Set<String>? = prefs.getStringSet(KEY_SAVED_THEMES, null)

    override fun getLastRecommendationAt(): Long = prefs.getLong(KEY_LAST_RECOMMENDATION_AT, 0L)

    override fun getLastFeedRefreshAt(): Long = prefs.getLong(KEY_LAST_FEED_REFRESH_AT, 0L)

    override fun saveRecommendationState(
        savedThemeIds: Set<String>,
        sourcesHash: Int,
        timestamp: Long
    ) {
        prefs.edit()
            .putInt(KEY_SOURCES_HASH, sourcesHash)
            .putStringSet(KEY_SAVED_THEME_IDS, savedThemeIds)
            .putLong(KEY_LAST_RECOMMENDATION_AT, timestamp)
            .apply()
    }

    override fun setLastFeedRefreshAt(timestamp: Long) {
        prefs.edit()
            .putLong(KEY_LAST_FEED_REFRESH_AT, timestamp)
            .apply()
    }

    private companion object {
        private const val PREFS_NAME = "suggested_themes_prefs"
        private const val KEY_SAVED_THEMES = "savedThemes"
        private const val KEY_SAVED_THEME_IDS = "savedThemeIds"
        private const val KEY_SOURCES_HASH = "sourcesHash"
        private const val KEY_LAST_RECOMMENDATION_AT = "lastRecommendationAt"
        private const val KEY_LAST_FEED_REFRESH_AT = "lastFeedRefreshAt"
    }
}







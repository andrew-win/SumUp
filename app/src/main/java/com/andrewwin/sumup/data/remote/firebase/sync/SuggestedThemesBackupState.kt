package com.andrewwin.sumup.data.remote.firebase.sync

import android.content.SharedPreferences
import com.andrewwin.sumup.worker.sync.CloudSyncWorkerHandler
import org.json.JSONArray
import org.json.JSONObject

data class SuggestedThemesBackupState(
    val savedThemeIds: Set<String>,
    val savedThemeTitlesLegacy: Set<String>,
    val sourcesHash: Int?,
    val lastRecommendationAt: Long,
    val lastFeedRefreshAt: Long
)

fun SharedPreferences.readSuggestedThemesBackupState(): SuggestedThemesBackupState {
    return SuggestedThemesBackupState(
        savedThemeIds = getStringSet(CloudSyncWorkerHandler.KEY_SAVED_THEME_IDS, null).orEmpty().filterNotBlank().toSet(),
        savedThemeTitlesLegacy = getStringSet(CloudSyncWorkerHandler.KEY_SAVED_THEMES, null).orEmpty().filterNotBlank().toSet(),
        sourcesHash = takeIf { contains(CloudSyncWorkerHandler.KEY_SOURCES_HASH) }
            ?.getInt(CloudSyncWorkerHandler.KEY_SOURCES_HASH, 0),
        lastRecommendationAt = getLong(CloudSyncWorkerHandler.KEY_LAST_RECOMMENDATION_AT, 0L),
        lastFeedRefreshAt = getLong(CloudSyncWorkerHandler.KEY_LAST_FEED_REFRESH_AT, 0L)
    )
}

fun JSONObject.putSuggestedThemesBackupState(state: SuggestedThemesBackupState) {
    put(CloudSyncWorkerHandler.KEY_SAVED_THEME_IDS, JSONArray(state.savedThemeIds.toList()))
    put(CloudSyncWorkerHandler.KEY_SAVED_THEMES, JSONArray(state.savedThemeTitlesLegacy.toList()))
    state.sourcesHash?.let { put(CloudSyncWorkerHandler.KEY_SOURCES_HASH, it) }
    put(CloudSyncWorkerHandler.KEY_LAST_RECOMMENDATION_AT, state.lastRecommendationAt)
    put(CloudSyncWorkerHandler.KEY_LAST_FEED_REFRESH_AT, state.lastFeedRefreshAt)
}

fun JSONObject.toSuggestedThemesBackupState(): SuggestedThemesBackupState {
    val savedThemeIds = optJSONArray(CloudSyncWorkerHandler.KEY_SAVED_THEME_IDS).toStringSet()
    val savedThemeTitlesLegacy = optJSONArray(CloudSyncWorkerHandler.KEY_SAVED_THEMES).toStringSet()
    return SuggestedThemesBackupState(
        savedThemeIds = savedThemeIds,
        savedThemeTitlesLegacy = savedThemeTitlesLegacy,
        sourcesHash = takeIf { has(CloudSyncWorkerHandler.KEY_SOURCES_HASH) }
            ?.optInt(CloudSyncWorkerHandler.KEY_SOURCES_HASH),
        lastRecommendationAt = optLong(CloudSyncWorkerHandler.KEY_LAST_RECOMMENDATION_AT, 0L),
        lastFeedRefreshAt = optLong(CloudSyncWorkerHandler.KEY_LAST_FEED_REFRESH_AT, 0L)
    )
}

fun SharedPreferences.Editor.writeSuggestedThemesBackupState(
    state: SuggestedThemesBackupState,
    clearWhenEmpty: Boolean
): SharedPreferences.Editor {
    putStringSet(CloudSyncWorkerHandler.KEY_SAVED_THEME_IDS, state.savedThemeIds.ifEmpty { emptySet() })
    putStringSet(CloudSyncWorkerHandler.KEY_SAVED_THEMES, state.savedThemeTitlesLegacy.ifEmpty { emptySet() })
    if (state.sourcesHash != null) {
        putInt(CloudSyncWorkerHandler.KEY_SOURCES_HASH, state.sourcesHash)
    } else if (clearWhenEmpty) {
        remove(CloudSyncWorkerHandler.KEY_SOURCES_HASH)
    }
    putLong(CloudSyncWorkerHandler.KEY_LAST_RECOMMENDATION_AT, state.lastRecommendationAt)
    putLong(CloudSyncWorkerHandler.KEY_LAST_FEED_REFRESH_AT, state.lastFeedRefreshAt)
    return this
}

private fun JSONArray?.toStringSet(): Set<String> {
    if (this == null) return emptySet()
    return buildSet {
        for (index in 0 until length()) {
            optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}

private fun Iterable<String>.filterNotBlank(): List<String> = mapNotNull { value ->
    value.trim().takeIf { it.isNotBlank() }
}

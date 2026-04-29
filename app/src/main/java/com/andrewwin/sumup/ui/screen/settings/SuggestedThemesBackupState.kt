package com.andrewwin.sumup.ui.screen.settings

import android.content.SharedPreferences
import com.andrewwin.sumup.worker.WorkerContracts
import org.json.JSONArray
import org.json.JSONObject

data class SuggestedThemesBackupState(
    val savedThemeIds: Set<String>,
    val savedThemeTitlesLegacy: Set<String>,
    val sourcesHash: Int?,
    val lastRecommendationAt: Long,
    val lastFeedRefreshAt: Long
)

internal fun SharedPreferences.readSuggestedThemesBackupState(): SuggestedThemesBackupState {
    return SuggestedThemesBackupState(
        savedThemeIds = getStringSet(WorkerContracts.KEY_SAVED_THEME_IDS, null).orEmpty().filterNotBlank().toSet(),
        savedThemeTitlesLegacy = getStringSet(WorkerContracts.KEY_SAVED_THEMES, null).orEmpty().filterNotBlank().toSet(),
        sourcesHash = takeIf { contains(WorkerContracts.KEY_SOURCES_HASH) }
            ?.getInt(WorkerContracts.KEY_SOURCES_HASH, 0),
        lastRecommendationAt = getLong(WorkerContracts.KEY_LAST_RECOMMENDATION_AT, 0L),
        lastFeedRefreshAt = getLong(WorkerContracts.KEY_LAST_FEED_REFRESH_AT, 0L)
    )
}

internal fun JSONObject.putSuggestedThemesBackupState(state: SuggestedThemesBackupState) {
    put(WorkerContracts.KEY_SAVED_THEME_IDS, JSONArray(state.savedThemeIds.toList()))
    put(WorkerContracts.KEY_SAVED_THEMES, JSONArray(state.savedThemeTitlesLegacy.toList()))
    state.sourcesHash?.let { put(WorkerContracts.KEY_SOURCES_HASH, it) }
    put(WorkerContracts.KEY_LAST_RECOMMENDATION_AT, state.lastRecommendationAt)
    put(WorkerContracts.KEY_LAST_FEED_REFRESH_AT, state.lastFeedRefreshAt)
}

internal fun JSONObject.toSuggestedThemesBackupState(): SuggestedThemesBackupState {
    val savedThemeIds = optJSONArray(WorkerContracts.KEY_SAVED_THEME_IDS)
        .toStringSet()
    val savedThemeTitlesLegacy = optJSONArray(WorkerContracts.KEY_SAVED_THEMES)
        .toStringSet()
    return SuggestedThemesBackupState(
        savedThemeIds = savedThemeIds,
        savedThemeTitlesLegacy = savedThemeTitlesLegacy,
        sourcesHash = takeIf { has(WorkerContracts.KEY_SOURCES_HASH) }
            ?.optInt(WorkerContracts.KEY_SOURCES_HASH),
        lastRecommendationAt = optLong(WorkerContracts.KEY_LAST_RECOMMENDATION_AT, 0L),
        lastFeedRefreshAt = optLong(WorkerContracts.KEY_LAST_FEED_REFRESH_AT, 0L)
    )
}

internal fun SharedPreferences.Editor.writeSuggestedThemesBackupState(
    state: SuggestedThemesBackupState,
    clearWhenEmpty: Boolean
): SharedPreferences.Editor {
    putStringSet(WorkerContracts.KEY_SAVED_THEME_IDS, state.savedThemeIds.ifEmpty { emptySet() })
    putStringSet(WorkerContracts.KEY_SAVED_THEMES, state.savedThemeTitlesLegacy.ifEmpty { emptySet() })
    if (state.sourcesHash != null) {
        putInt(WorkerContracts.KEY_SOURCES_HASH, state.sourcesHash)
    } else if (clearWhenEmpty) {
        remove(WorkerContracts.KEY_SOURCES_HASH)
    }
    putLong(WorkerContracts.KEY_LAST_RECOMMENDATION_AT, state.lastRecommendationAt)
    putLong(WorkerContracts.KEY_LAST_FEED_REFRESH_AT, state.lastFeedRefreshAt)
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

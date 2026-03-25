package com.andrewwin.sumup.worker

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.andrewwin.sumup.data.local.entities.AiModelConfig
import com.andrewwin.sumup.data.local.entities.AiModelType
import com.andrewwin.sumup.data.local.entities.AiProvider
import com.andrewwin.sumup.data.local.entities.AppLanguage
import com.andrewwin.sumup.data.local.entities.AppThemeMode
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.ImportedSource
import com.andrewwin.sumup.domain.repository.ImportedSourceGroup
import com.andrewwin.sumup.domain.repository.SourceRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

@HiltWorker
class CloudSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val aiRepository: AiRepository,
    private val sourceRepository: SourceRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val syncPrefs = applicationContext.getSharedPreferences(SYNC_PREFS, 0)
        val enabled = syncPrefs.getBoolean(KEY_SYNC_ENABLED, false)
        if (!enabled) return Result.success()

        val selection = BackupSelection(
            includeSources = syncPrefs.getBoolean(KEY_INCLUDE_SOURCES, true),
            includeSubscriptions = syncPrefs.getBoolean(KEY_INCLUDE_SUBSCRIPTIONS, true),
            includeSettingsNoApi = syncPrefs.getBoolean(KEY_INCLUDE_SETTINGS_NO_API, true),
            includeApiKeys = syncPrefs.getBoolean(KEY_INCLUDE_API_KEYS, true)
        )

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) return Result.success()

        return runCatching {
            val firestore = FirebaseFirestore.getInstance()
            val docRef = firestore.collection(CLOUD_COLLECTION).document(uid)
            val remote = docRef.get().await()
            val remoteUpdatedAt = remote.getLong("updatedAt") ?: 0L
            val lastSyncAt = syncPrefs.getLong(KEY_LAST_SYNC_AT, 0L)

            if (remote.exists() && remoteUpdatedAt > lastSyncAt) {
                val remoteBackup = remote.getString("backup").orEmpty()
                if (remoteBackup.isNotBlank()) {
                    applyBackupJson(JSONObject(remoteBackup), merge = true, selection = selection)
                }
            }

            val localBackup = buildBackupJson(selection)
            val now = System.currentTimeMillis()
            docRef.set(
                mapOf(
                    "backup" to localBackup.toString(),
                    "updatedAt" to now
                )
            ).await()
            syncPrefs.edit().putLong(KEY_LAST_SYNC_AT, now).apply()
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }

    private suspend fun buildBackupJson(selection: BackupSelection): JSONObject {
        val subscriptionsPrefs = applicationContext.getSharedPreferences(SUBSCRIPTIONS_PREFS, 0)
        val prefs = if (selection.includeSettingsNoApi) userPreferencesRepository.preferences.first() else null
        val aiConfigs = if (selection.includeApiKeys) aiRepository.allConfigs.first() else emptyList()
        val groups = if (selection.includeSources) sourceRepository.getGroupsWithSourcesSnapshot() else emptyList()
        val savedThemes = if (selection.includeSubscriptions) {
            subscriptionsPrefs.getStringSet(KEY_SAVED_THEMES, emptySet()).orEmpty()
        } else {
            emptySet()
        }
        val lastRecommendationAt = if (selection.includeSubscriptions) {
            subscriptionsPrefs.getLong(KEY_LAST_RECOMMENDATION_AT, 0L)
        } else {
            0L
        }

        return JSONObject().apply {
            put("schemaVersion", 1)
            put("exportedAt", System.currentTimeMillis())
            put("selection", JSONObject().apply {
                put("sources", selection.includeSources)
                put("subscriptions", selection.includeSubscriptions)
                put("settingsNoApi", selection.includeSettingsNoApi)
                put("apiKeys", selection.includeApiKeys)
            })
            if (prefs != null) put("userPreferences", prefs.toJson())
            if (selection.includeApiKeys) {
                put("aiConfigs", JSONArray().apply { aiConfigs.forEach { put(it.toJson()) } })
            }
            if (selection.includeSources) {
                put("groups", JSONArray().apply {
                    groups.forEach { groupWithSources ->
                        put(JSONObject().apply {
                            put("name", groupWithSources.group.name)
                            put("isEnabled", groupWithSources.group.isEnabled)
                            put("isDeletable", groupWithSources.group.isDeletable)
                            put("sources", JSONArray().apply {
                                groupWithSources.sources.forEach { source -> put(source.toJson()) }
                            })
                        })
                    }
                })
            }
            if (selection.includeSubscriptions) {
                put("subscriptions", JSONObject().apply {
                    put(KEY_SAVED_THEMES, JSONArray(savedThemes.toList()))
                    put(KEY_LAST_RECOMMENDATION_AT, lastRecommendationAt)
                })
            }
        }
    }

    private suspend fun applyBackupJson(root: JSONObject, merge: Boolean, selection: BackupSelection) {
        val subscriptionsPrefs = applicationContext.getSharedPreferences(SUBSCRIPTIONS_PREFS, 0)
        val importedPrefs = root.optJSONObject("userPreferences")?.toUserPreferences()
        val importedConfigs = root.optJSONArray("aiConfigs").toAiConfigs()
        val importedGroups = root.optJSONArray("groups").toImportedGroups()
        val importedSubscriptions = root.optJSONObject("subscriptions")

        if (selection.includeSettingsNoApi && importedPrefs != null) {
            userPreferencesRepository.updatePreferences(importedPrefs.copy(id = 0))
            val languageTag = when (importedPrefs.appLanguage) {
                AppLanguage.UK -> "uk"
                AppLanguage.EN -> "en"
            }
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
        }

        if (selection.includeApiKeys) {
            val existingConfigs = aiRepository.allConfigs.first()
            if (!merge) existingConfigs.forEach { aiRepository.deleteConfig(it) }
            val configsAfterClear = if (merge) existingConfigs else emptyList()
            for (imported in importedConfigs) {
                val matched = configsAfterClear.firstOrNull {
                    it.type == imported.type &&
                        it.provider == imported.provider &&
                        it.modelName.equals(imported.modelName, ignoreCase = true) &&
                        it.name.equals(imported.name, ignoreCase = true)
                }
                if (matched == null) aiRepository.addConfig(imported.copy(id = 0))
                else aiRepository.updateConfig(imported.copy(id = matched.id))
            }
        }

        if (selection.includeSources) {
            sourceRepository.importGroupsWithSources(importedGroups, merge)
        }

        if (selection.includeSubscriptions) {
            if (importedSubscriptions != null) {
                val savedThemes = importedSubscriptions.optJSONArray(KEY_SAVED_THEMES)
                    ?.let { arr ->
                        buildSet {
                            for (i in 0 until arr.length()) {
                                arr.optString(i)?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
                            }
                        }
                    }
                    .orEmpty()
                subscriptionsPrefs.edit()
                    .putStringSet(KEY_SAVED_THEMES, savedThemes)
                    .putLong(KEY_LAST_RECOMMENDATION_AT, importedSubscriptions.optLong(KEY_LAST_RECOMMENDATION_AT, 0L))
                    .apply()
            } else if (!merge) {
                subscriptionsPrefs.edit()
                    .remove(KEY_SAVED_THEMES)
                    .remove(KEY_LAST_RECOMMENDATION_AT)
                    .apply()
            }
        }
    }

    private fun UserPreferences.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("aiStrategy", aiStrategy.name)
        put("isScheduledSummaryEnabled", isScheduledSummaryEnabled)
        put("isScheduledSummaryPushEnabled", isScheduledSummaryPushEnabled)
        put("scheduledHour", scheduledHour)
        put("scheduledMinute", scheduledMinute)
        put("lastWorkRunTimestamp", lastWorkRunTimestamp)
        put("isDeduplicationEnabled", isDeduplicationEnabled)
        put("localDeduplicationThreshold", localDeduplicationThreshold.toDouble())
        put("cloudDeduplicationThreshold", cloudDeduplicationThreshold.toDouble())
        put("minMentions", minMentions)
        put("isHideSingleNewsEnabled", isHideSingleNewsEnabled)
        put("modelPath", modelPath)
        put("isImportanceFilterEnabled", isImportanceFilterEnabled)
        put("isAdaptiveExtractivePreprocessingEnabled", isAdaptiveExtractivePreprocessingEnabled)
        put("adaptiveExtractiveOnlyBelowChars", adaptiveExtractiveOnlyBelowChars)
        put("adaptiveExtractiveCompressAboveChars", adaptiveExtractiveCompressAboveChars)
        put("adaptiveExtractiveCompressionPercent", adaptiveExtractiveCompressionPercent)
        put("summaryItemsPerNewsInFeed", summaryItemsPerNewsInFeed)
        put("summaryItemsPerNewsInScheduled", summaryItemsPerNewsInScheduled)
        put("summaryNewsInFeedExtractive", summaryNewsInFeedExtractive)
        put("summaryNewsInFeedCloud", summaryNewsInFeedCloud)
        put("summaryNewsInScheduledExtractive", summaryNewsInScheduledExtractive)
        put("summaryNewsInScheduledCloud", summaryNewsInScheduledCloud)
        put("extractiveSentencesInFeed", extractiveSentencesInFeed)
        put("extractiveNewsInFeed", extractiveNewsInFeed)
        put("extractiveSentencesInScheduled", extractiveSentencesInScheduled)
        put("extractiveNewsInScheduled", extractiveNewsInScheduled)
        put("showLastSummariesCount", showLastSummariesCount)
        put("showInfographicNewsCount", showInfographicNewsCount)
        put("aiMaxCharsPerArticle", aiMaxCharsPerArticle)
        put("aiMaxCharsPerFeedArticle", aiMaxCharsPerFeedArticle)
        put("aiMaxCharsTotal", aiMaxCharsTotal)
        put("summaryPrompt", summaryPrompt)
        put("isCustomSummaryPromptEnabled", isCustomSummaryPromptEnabled)
        put("isFeedMediaEnabled", isFeedMediaEnabled)
        put("isFeedDescriptionEnabled", isFeedDescriptionEnabled)
        put("appThemeMode", appThemeMode.name)
        put("appLanguage", appLanguage.name)
    }

    private fun JSONObject.toUserPreferences(): UserPreferences {
        val defaults = UserPreferences()
        return UserPreferences(
            id = optInt("id", defaults.id),
            aiStrategy = runCatching { com.andrewwin.sumup.data.local.entities.AiStrategy.valueOf(optString("aiStrategy", defaults.aiStrategy.name)) }
                .getOrDefault(defaults.aiStrategy),
            isScheduledSummaryEnabled = optBoolean("isScheduledSummaryEnabled", defaults.isScheduledSummaryEnabled),
            isScheduledSummaryPushEnabled = optBoolean("isScheduledSummaryPushEnabled", defaults.isScheduledSummaryPushEnabled),
            scheduledHour = optInt("scheduledHour", defaults.scheduledHour),
            scheduledMinute = optInt("scheduledMinute", defaults.scheduledMinute),
            lastWorkRunTimestamp = optLong("lastWorkRunTimestamp", defaults.lastWorkRunTimestamp),
            isDeduplicationEnabled = optBoolean("isDeduplicationEnabled", defaults.isDeduplicationEnabled),
            localDeduplicationThreshold = optDouble("localDeduplicationThreshold", defaults.localDeduplicationThreshold.toDouble()).toFloat(),
            cloudDeduplicationThreshold = optDouble("cloudDeduplicationThreshold", defaults.cloudDeduplicationThreshold.toDouble()).toFloat(),
            minMentions = optInt("minMentions", defaults.minMentions),
            isHideSingleNewsEnabled = optBoolean("isHideSingleNewsEnabled", defaults.isHideSingleNewsEnabled),
            modelPath = optString("modelPath", defaults.modelPath ?: "").takeIf { it.isNotBlank() },
            isImportanceFilterEnabled = optBoolean("isImportanceFilterEnabled", defaults.isImportanceFilterEnabled),
            isAdaptiveExtractivePreprocessingEnabled = optBoolean("isAdaptiveExtractivePreprocessingEnabled", defaults.isAdaptiveExtractivePreprocessingEnabled),
            adaptiveExtractiveOnlyBelowChars = optInt("adaptiveExtractiveOnlyBelowChars", defaults.adaptiveExtractiveOnlyBelowChars),
            adaptiveExtractiveCompressAboveChars = optInt("adaptiveExtractiveCompressAboveChars", defaults.adaptiveExtractiveCompressAboveChars),
            adaptiveExtractiveCompressionPercent = optInt("adaptiveExtractiveCompressionPercent", defaults.adaptiveExtractiveCompressionPercent),
            summaryItemsPerNewsInFeed = optInt("summaryItemsPerNewsInFeed", defaults.summaryItemsPerNewsInFeed),
            summaryItemsPerNewsInScheduled = optInt("summaryItemsPerNewsInScheduled", defaults.summaryItemsPerNewsInScheduled),
            summaryNewsInFeedExtractive = optInt("summaryNewsInFeedExtractive", defaults.summaryNewsInFeedExtractive),
            summaryNewsInFeedCloud = optInt("summaryNewsInFeedCloud", defaults.summaryNewsInFeedCloud),
            summaryNewsInScheduledExtractive = optInt("summaryNewsInScheduledExtractive", defaults.summaryNewsInScheduledExtractive),
            summaryNewsInScheduledCloud = optInt("summaryNewsInScheduledCloud", defaults.summaryNewsInScheduledCloud),
            extractiveSentencesInFeed = optInt("extractiveSentencesInFeed", defaults.extractiveSentencesInFeed),
            extractiveNewsInFeed = optInt("extractiveNewsInFeed", defaults.extractiveNewsInFeed),
            extractiveSentencesInScheduled = optInt("extractiveSentencesInScheduled", defaults.extractiveSentencesInScheduled),
            extractiveNewsInScheduled = optInt("extractiveNewsInScheduled", defaults.extractiveNewsInScheduled),
            showLastSummariesCount = optInt("showLastSummariesCount", defaults.showLastSummariesCount),
            showInfographicNewsCount = optInt("showInfographicNewsCount", defaults.showInfographicNewsCount),
            aiMaxCharsPerArticle = optInt("aiMaxCharsPerArticle", defaults.aiMaxCharsPerArticle),
            aiMaxCharsPerFeedArticle = optInt("aiMaxCharsPerFeedArticle", defaults.aiMaxCharsPerFeedArticle),
            aiMaxCharsTotal = optInt("aiMaxCharsTotal", defaults.aiMaxCharsTotal),
            summaryPrompt = optString("summaryPrompt", defaults.summaryPrompt),
            isCustomSummaryPromptEnabled = optBoolean("isCustomSummaryPromptEnabled", defaults.isCustomSummaryPromptEnabled),
            isFeedMediaEnabled = optBoolean("isFeedMediaEnabled", defaults.isFeedMediaEnabled),
            isFeedDescriptionEnabled = optBoolean("isFeedDescriptionEnabled", defaults.isFeedDescriptionEnabled),
            appThemeMode = runCatching { AppThemeMode.valueOf(optString("appThemeMode", defaults.appThemeMode.name)) }
                .getOrDefault(defaults.appThemeMode),
            appLanguage = runCatching { AppLanguage.valueOf(optString("appLanguage", defaults.appLanguage.name)) }
                .getOrDefault(defaults.appLanguage)
        )
    }

    private fun AiModelConfig.toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("provider", provider.name)
        put("apiKey", apiKey)
        put("modelName", modelName)
        put("isEnabled", isEnabled)
        put("type", type.name)
    }

    private fun JSONArray?.toAiConfigs(): List<AiModelConfig> {
        if (this == null) return emptyList()
        val result = mutableListOf<AiModelConfig>()
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val name = item.optString("name", "").trim()
            val apiKey = item.optString("apiKey", "").trim()
            val modelName = item.optString("modelName", "").trim()
            if (name.isBlank() || apiKey.isBlank() || modelName.isBlank()) continue
            val provider = runCatching { AiProvider.valueOf(item.optString("provider")) }.getOrNull() ?: continue
            val type = runCatching { AiModelType.valueOf(item.optString("type")) }.getOrNull() ?: continue
            result.add(
                AiModelConfig(
                    name = name,
                    provider = provider,
                    apiKey = apiKey,
                    modelName = modelName,
                    isEnabled = item.optBoolean("isEnabled", true),
                    type = type
                )
            )
        }
        return result
    }

    private fun com.andrewwin.sumup.data.local.entities.Source.toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("url", url)
        put("type", type.name)
        put("isEnabled", isEnabled)
        put("footerPattern", footerPattern)
        put("titleSelector", titleSelector)
        put("postLinkSelector", postLinkSelector)
        put("descriptionSelector", descriptionSelector)
        put("dateSelector", dateSelector)
        put("useHeadlessBrowser", useHeadlessBrowser)
    }

    private fun JSONArray?.toImportedGroups(): List<ImportedSourceGroup> {
        if (this == null) return emptyList()
        val groups = mutableListOf<ImportedSourceGroup>()
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val name = item.optString("name", "").trim()
            if (name.isBlank()) continue
            groups.add(
                ImportedSourceGroup(
                    name = name,
                    isEnabled = item.optBoolean("isEnabled", true),
                    isDeletable = item.optBoolean("isDeletable", true),
                    sources = item.optJSONArray("sources").toImportedSources()
                )
            )
        }
        return groups
    }

    private fun JSONArray?.toImportedSources(): List<ImportedSource> {
        if (this == null) return emptyList()
        val sources = mutableListOf<ImportedSource>()
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val name = item.optString("name", "").trim()
            val url = item.optString("url", "").trim()
            val type = runCatching {
                com.andrewwin.sumup.data.local.entities.SourceType.valueOf(item.optString("type"))
            }.getOrNull() ?: continue
            if (name.isBlank() || url.isBlank()) continue
            sources.add(
                ImportedSource(
                    name = name,
                    url = url,
                    type = type,
                    isEnabled = item.optBoolean("isEnabled", true),
                    footerPattern = item.optString("footerPattern", "").takeIf { it.isNotBlank() },
                    titleSelector = item.optString("titleSelector", "").takeIf { it.isNotBlank() },
                    postLinkSelector = item.optString("postLinkSelector", "").takeIf { it.isNotBlank() },
                    descriptionSelector = item.optString("descriptionSelector", "").takeIf { it.isNotBlank() },
                    dateSelector = item.optString("dateSelector", "").takeIf { it.isNotBlank() },
                    useHeadlessBrowser = item.optBoolean("useHeadlessBrowser", false)
                )
            )
        }
        return sources
    }

    private data class BackupSelection(
        val includeSources: Boolean,
        val includeSubscriptions: Boolean,
        val includeSettingsNoApi: Boolean,
        val includeApiKeys: Boolean
    )

    companion object {
        private const val CLOUD_COLLECTION = "user_sync_backups"
        private const val SYNC_PREFS = "sync_prefs"
        private const val SUBSCRIPTIONS_PREFS = "suggested_themes_prefs"
        private const val KEY_SYNC_ENABLED = "sync_enabled"
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
        private const val KEY_INCLUDE_SOURCES = "sync_include_sources"
        private const val KEY_INCLUDE_SUBSCRIPTIONS = "sync_include_subscriptions"
        private const val KEY_INCLUDE_SETTINGS_NO_API = "sync_include_settings_no_api"
        private const val KEY_INCLUDE_API_KEYS = "sync_include_api_keys"
        private const val KEY_SAVED_THEMES = "savedThemes"
        private const val KEY_LAST_RECOMMENDATION_AT = "lastRecommendationAt"
    }
}

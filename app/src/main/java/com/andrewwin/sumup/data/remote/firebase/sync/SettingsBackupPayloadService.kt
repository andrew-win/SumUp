package com.andrewwin.sumup.data.remote.firebase.sync

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.andrewwin.sumup.data.mappers.toDomainModel
import com.andrewwin.sumup.data.mappers.toRoomEntity
import com.andrewwin.sumup.data.security.SecretEncryptionManager
import com.andrewwin.sumup.domain.ai.model.AiModelConfig
import com.andrewwin.sumup.domain.ai.model.normalizedStableKey
import com.andrewwin.sumup.domain.ai.repository.AiModelConfigRepository
import com.andrewwin.sumup.domain.article.repository.ArticleRepository
import com.andrewwin.sumup.domain.source.repository.SourceRepository
import com.andrewwin.sumup.domain.settings.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.settings.model.AppLanguage
import com.andrewwin.sumup.domain.summary.usecase.CreateScheduleSummaryUseCase
import com.andrewwin.sumup.worker.sync.CloudSyncWorkerHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class SettingsBackupPayloadService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val aiModelConfigRepository: AiModelConfigRepository,
    private val articleRepository: ArticleRepository,
    private val sourceRepository: SourceRepository,
    private val createScheduleSummaryUseCase: CreateScheduleSummaryUseCase,
    private val secretEncryptionManager: SecretEncryptionManager
) {
    private val subscriptionsPrefs by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        context.getSharedPreferences(CloudSyncWorkerHandler.SUBSCRIPTIONS_PREFS, 0)
    }

    suspend fun buildBackupJson(
        selection: BackupSelection,
        syncStrategy: SyncConflictStrategy,
        syncOverwritePriority: SyncOverwritePriority,
        importStrategy: SyncConflictStrategy
    ): JSONObject {
        val prefs = if (selection.includeSettingsNoApi) userPreferencesRepository.preferences.first() else null
        val aiConfigs = if (selection.includeApiKeys) aiModelConfigRepository.allConfigs.first() else emptyList()
        val syncPassphrase = if (selection.includeApiKeys) requireSyncPassphrase() else null
        val encryptionSession = syncPassphrase?.let(secretEncryptionManager::createSyncEncryptionSession)
        val groups = if (selection.includeSources) sourceRepository.getGroupsWithSourcesSnapshot() else emptyList()
        val subscriptions = if (selection.includeSubscriptions) subscriptionsPrefs.readSuggestedThemesBackupState() else null
        val savedArticles = if (selection.includeSavedArticles) articleRepository.getSavedArticlesSnapshot() else emptyList()
        return JSONObject().apply {
            put(KEY_SCHEMA_VERSION, BACKUP_SCHEMA_VERSION)
            put(KEY_EXPORTED_AT, System.currentTimeMillis())
            put(KEY_SYNC_STRATEGY, syncStrategy.name)
            put(KEY_SYNC_OVERWRITE_PRIORITY, syncOverwritePriority.name)
            put(KEY_IMPORT_STRATEGY, importStrategy.name)
            put(KEY_SELECTION, JSONObject().apply {
                put(KEY_SOURCES, selection.includeSources)
                put(KEY_SUBSCRIPTIONS, selection.includeSubscriptions)
                put(KEY_SAVED_ARTICLES, selection.includeSavedArticles)
                put(KEY_SETTINGS_NO_API, selection.includeSettingsNoApi)
                put(KEY_API_KEYS, selection.includeApiKeys)
            })
            if (prefs != null) put(KEY_USER_PREFERENCES, prefs.toRoomEntity().toBackupJson())
            if (selection.includeApiKeys) {
                put(KEY_API_KEYS_SALT, encryptionSession?.saltBase64)
                put(KEY_AI_CONFIGS, JSONArray().apply {
                    aiConfigs.forEach { put(it.toBackupJson(encryptionSession!!)) }
                })
            }
            if (selection.includeSources) {
                put(KEY_GROUPS, JSONArray().apply {
                    groups.forEach { groupWithSources ->
                        put(JSONObject().apply {
                            put(KEY_NAME, groupWithSources.group.name)
                            put(KEY_IS_ENABLED, groupWithSources.group.isEnabled)
                            put(KEY_IS_DELETABLE, groupWithSources.group.isDeletable)
                            put(KEY_ORIGIN, groupWithSources.group.origin)
                            put(KEY_SUBSCRIPTION_ID, groupWithSources.group.subscriptionId)
                            put(KEY_SOURCES, JSONArray().apply {
                                groupWithSources.sources.forEach { source -> put(source.toBackupJson()) }
                            })
                        })
                    }
                })
            }
            if (selection.includeSubscriptions) {
                put(KEY_SUBSCRIPTIONS, JSONObject().apply {
                    putSuggestedThemesBackupState(
                        subscriptions ?: SuggestedThemesBackupState(
                            savedThemeIds = emptySet(),
                            savedThemeTitlesLegacy = emptySet(),
                            sourcesHash = null,
                            lastRecommendationAt = 0L,
                            lastFeedRefreshAt = 0L
                        )
                    )
                })
            }
            if (selection.includeSavedArticles) {
                put(KEY_SAVED_ARTICLES, JSONArray().apply {
                    savedArticles.forEach { put(it.toBackupJson()) }
                })
            }
        }
    }

    suspend fun applyBackupJson(root: JSONObject, merge: Boolean, selection: BackupSelection) {
        val importedSyncStrategy = parseSyncConflictStrategy(root.optString(KEY_SYNC_STRATEGY, SyncConflictStrategy.MERGE.name))
        val importedOverwritePriority = parseSyncOverwritePriority(
            root.optString(KEY_SYNC_OVERWRITE_PRIORITY, SyncOverwritePriority.LOCAL.name)
        )
        val importedImportStrategy = parseSyncConflictStrategy(
            root.optString(KEY_IMPORT_STRATEGY, SyncConflictStrategy.MERGE.name)
        )
        val importedPrefs = root.optJSONObject(KEY_USER_PREFERENCES)?.toUserPreferencesFromBackup()
        val importedConfigs = if (selection.includeApiKeys) {
            root.optJSONArray(KEY_AI_CONFIGS).toAiConfigsFromBackup(
                secretEncryptionManager = secretEncryptionManager,
                syncPassphrase = requireSyncPassphrase(),
                syncSaltBase64 = root.optString(KEY_API_KEYS_SALT).takeIf { it.isNotBlank() }
            )
        } else {
            emptyList()
        }
        val importedGroups = root.optJSONArray(KEY_GROUPS).toImportedGroupsFromBackup()
        val importedSubscriptions = root.optJSONObject(KEY_SUBSCRIPTIONS)
        val hasSavedArticlesField = root.has(KEY_SAVED_ARTICLES)
        val rawSavedArticles = root.optJSONArray(KEY_SAVED_ARTICLES)
        val importedSavedArticles = rawSavedArticles.toSavedArticlesFromBackup()
        val importedSavedArticleUrls = rawSavedArticles?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    if (array.optJSONObject(index) != null) continue
                    array.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }.orEmpty()

        if (selection.includeSettingsNoApi && importedPrefs != null) {
            val importedSettings = importedPrefs.copy(id = 0).toDomainModel()
            userPreferencesRepository.updatePreferences(importedSettings)
            applyAppLanguage(importedSettings.appLanguage)
            createScheduleSummaryUseCase(importedSettings.isScheduledSummaryEnabled, importedSettings.scheduledSummaryTimeList)
        }
        if (selection.includeApiKeys && importedConfigs.isNotEmpty()) {
            applyAiConfigs(importedConfigs, merge)
        }
        if (selection.includeSources) {
            sourceRepository.importGroupsWithSources(importedGroups, merge)
        }
        if (selection.includeSubscriptions) {
            if (importedSubscriptions != null) {
                subscriptionsPrefs.edit()
                    .writeSuggestedThemesBackupState(importedSubscriptions.toSuggestedThemesBackupState(), clearWhenEmpty = true)
                    .apply()
            } else if (!merge) {
                subscriptionsPrefs.edit()
                    .remove(CloudSyncWorkerHandler.KEY_SAVED_THEME_IDS)
                    .remove(CloudSyncWorkerHandler.KEY_SAVED_THEMES)
                    .remove(CloudSyncWorkerHandler.KEY_SOURCES_HASH)
                    .remove(CloudSyncWorkerHandler.KEY_LAST_RECOMMENDATION_AT)
                    .remove(CloudSyncWorkerHandler.KEY_LAST_FEED_REFRESH_AT)
                    .apply()
            }
        }
        if (selection.includeSavedArticles && hasSavedArticlesField) {
            if (importedSavedArticles.isNotEmpty()) {
                if (merge) articleRepository.mergeSavedArticlesSnapshot(importedSavedArticles)
                else articleRepository.replaceSavedArticlesSnapshot(importedSavedArticles)
            } else if (merge) {
                articleRepository.mergeFavoriteArticlesByUrls(importedSavedArticleUrls)
            } else {
                articleRepository.replaceFavoriteArticlesByUrls(importedSavedArticleUrls)
            }
        }
        SyncMetadataHolder.lastImportedSyncMetadata = ImportedSyncMetadata(
            strategy = importedSyncStrategy,
            overwritePriority = importedOverwritePriority,
            importStrategy = importedImportStrategy
        )
    }

    fun consumeImportedSyncMetadata(): ImportedSyncMetadata? {
        val metadata = SyncMetadataHolder.lastImportedSyncMetadata
        SyncMetadataHolder.lastImportedSyncMetadata = null
        return metadata
    }

    private suspend fun applyAiConfigs(importedConfigs: List<AiModelConfig>, merge: Boolean) {
        val existingConfigs = aiModelConfigRepository.allConfigs.first()
        val existingByStableKey = existingConfigs.associateBy(::stableAiConfigKey).toMutableMap()
        if (!merge) {
            existingConfigs.forEach { aiModelConfigRepository.deleteConfig(it) }
            importedConfigs.forEach { aiModelConfigRepository.addConfig(it) }
            return
        }
        importedConfigs.forEach { imported ->
            val stableKey = stableAiConfigKey(imported)
            val existing = existingByStableKey[stableKey]
            if (existing == null) {
                aiModelConfigRepository.addConfig(imported)
                existingByStableKey[stableKey] = imported
            } else {
                aiModelConfigRepository.updateConfig(
                    imported.copy(id = existing.id, sortOrder = existing.sortOrder)
                )
            }
        }
    }

    private fun applyAppLanguage(language: AppLanguage) {
        val languageTag = when (language) {
            AppLanguage.UK -> LANGUAGE_TAG_UK
            AppLanguage.EN -> LANGUAGE_TAG_EN
        }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
    }

    private fun requireSyncPassphrase(): String {
        return secretEncryptionManager.getSyncPassphraseOrNull()
            ?: error(SYNC_PASSPHRASE_MISSING_MESSAGE)
    }

    private fun stableAiConfigKey(config: AiModelConfig): String {
        return config.copy(apiKey = config.apiKey.trim()).normalizedStableKey()
    }

    private object SyncMetadataHolder {
        var lastImportedSyncMetadata: ImportedSyncMetadata? = null
    }

    private companion object {
        private const val BACKUP_SCHEMA_VERSION = 1
        private const val KEY_SCHEMA_VERSION = "schemaVersion"
        private const val KEY_EXPORTED_AT = "exportedAt"
        private const val KEY_SYNC_STRATEGY = "syncStrategy"
        private const val KEY_SYNC_OVERWRITE_PRIORITY = "syncOverwritePriority"
        private const val KEY_IMPORT_STRATEGY = "importStrategy"
        private const val KEY_SELECTION = "selection"
        private const val KEY_SOURCES = "sources"
        private const val KEY_SUBSCRIPTIONS = "subscriptions"
        private const val KEY_SAVED_ARTICLES = "savedArticles"
        private const val KEY_SETTINGS_NO_API = "settingsNoApi"
        private const val KEY_API_KEYS = "apiKeys"
        private const val KEY_USER_PREFERENCES = "userPreferences"
        private const val KEY_API_KEYS_SALT = "apiKeysSalt"
        private const val KEY_AI_CONFIGS = "aiConfigs"
        private const val KEY_GROUPS = "groups"
        private const val KEY_NAME = "name"
        private const val KEY_IS_ENABLED = "isEnabled"
        private const val KEY_IS_DELETABLE = "isDeletable"
        private const val KEY_ORIGIN = "origin"
        private const val KEY_SUBSCRIPTION_ID = "subscriptionId"
        private const val LANGUAGE_TAG_UK = "uk"
        private const val LANGUAGE_TAG_EN = "en"
        private const val SYNC_PASSPHRASE_MISSING_MESSAGE = "Sync passphrase is missing."
    }
}

data class ImportedSyncMetadata(
    val strategy: SyncConflictStrategy,
    val overwritePriority: SyncOverwritePriority,
    val importStrategy: SyncConflictStrategy
)

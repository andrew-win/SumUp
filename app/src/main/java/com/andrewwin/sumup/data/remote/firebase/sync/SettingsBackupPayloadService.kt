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
            put("schemaVersion", 1)
            put("exportedAt", System.currentTimeMillis())
            put("syncStrategy", syncStrategy.name)
            put("syncOverwritePriority", syncOverwritePriority.name)
            put("importStrategy", importStrategy.name)
            put("selection", JSONObject().apply {
                put("sources", selection.includeSources)
                put("subscriptions", selection.includeSubscriptions)
                put("savedArticles", selection.includeSavedArticles)
                put("settingsNoApi", selection.includeSettingsNoApi)
                put("apiKeys", selection.includeApiKeys)
            })
            if (prefs != null) put("userPreferences", prefs.toRoomEntity().toBackupJson())
            if (selection.includeApiKeys) {
                put("apiKeysSalt", encryptionSession?.saltBase64)
                put("aiConfigs", JSONArray().apply {
                    aiConfigs.forEach { put(it.toBackupJson(encryptionSession!!)) }
                })
            }
            if (selection.includeSources) {
                put("groups", JSONArray().apply {
                    groups.forEach { groupWithSources ->
                        put(JSONObject().apply {
                            put("name", groupWithSources.group.name)
                            put("isEnabled", groupWithSources.group.isEnabled)
                            put("isDeletable", groupWithSources.group.isDeletable)
                            put("origin", groupWithSources.group.origin)
                            put("subscriptionId", groupWithSources.group.subscriptionId)
                            put("sources", JSONArray().apply {
                                groupWithSources.sources.forEach { source -> put(source.toBackupJson()) }
                            })
                        })
                    }
                })
            }
            if (selection.includeSubscriptions) {
                put("subscriptions", JSONObject().apply {
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
                put("savedArticles", JSONArray().apply {
                    savedArticles.forEach { put(it.toBackupJson()) }
                })
            }
        }
    }

    suspend fun applyBackupJson(root: JSONObject, merge: Boolean, selection: BackupSelection) {
        val importedSyncStrategy = parseSyncConflictStrategy(root.optString("syncStrategy", SyncConflictStrategy.MERGE.name))
        val importedOverwritePriority = parseSyncOverwritePriority(
            root.optString("syncOverwritePriority", SyncOverwritePriority.LOCAL.name)
        )
        val importedImportStrategy = parseSyncConflictStrategy(
            root.optString("importStrategy", SyncConflictStrategy.MERGE.name)
        )
        val importedPrefs = root.optJSONObject("userPreferences")?.toUserPreferencesFromBackup()
        val importedConfigs = if (selection.includeApiKeys) {
            root.optJSONArray("aiConfigs").toAiConfigsFromBackup(
                secretEncryptionManager = secretEncryptionManager,
                syncPassphrase = requireSyncPassphrase(),
                syncSaltBase64 = root.optString("apiKeysSalt").takeIf { it.isNotBlank() }
            )
        } else {
            emptyList()
        }
        val importedGroups = root.optJSONArray("groups").toImportedGroupsFromBackup()
        val importedSubscriptions = root.optJSONObject("subscriptions")
        val hasSavedArticlesField = root.has("savedArticles")
        val rawSavedArticles = root.optJSONArray("savedArticles")
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
            AppLanguage.UK -> "uk"
            AppLanguage.EN -> "en"
        }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
    }

    private fun requireSyncPassphrase(): String {
        return secretEncryptionManager.getSyncPassphraseOrNull()
            ?: error("Sync passphrase is missing.")
    }

    private fun stableAiConfigKey(config: AiModelConfig): String {
        return config.copy(apiKey = config.apiKey.trim()).normalizedStableKey()
    }

    private object SyncMetadataHolder {
        var lastImportedSyncMetadata: ImportedSyncMetadata? = null
    }
}

data class ImportedSyncMetadata(
    val strategy: SyncConflictStrategy,
    val overwritePriority: SyncOverwritePriority,
    val importStrategy: SyncConflictStrategy
)

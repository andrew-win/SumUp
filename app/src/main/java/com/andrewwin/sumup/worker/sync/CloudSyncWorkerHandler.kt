package com.andrewwin.sumup.worker.sync

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.work.ListenableWorker
import com.andrewwin.sumup.data.mappers.toDomainModel
import com.andrewwin.sumup.data.mappers.toRoomEntity
import com.andrewwin.sumup.data.security.SecretEncryptionManager
import com.andrewwin.sumup.domain.sync.model.BackupSelection
import com.andrewwin.sumup.data.remote.firebase.sync.SuggestedThemesBackupState
import com.andrewwin.sumup.domain.sync.model.SyncConflictStrategy
import com.andrewwin.sumup.domain.sync.model.SyncOverwritePriority
import com.andrewwin.sumup.data.remote.firebase.sync.fromBackupJson
import com.andrewwin.sumup.data.remote.firebase.sync.putSuggestedThemesBackupState
import com.andrewwin.sumup.data.remote.firebase.sync.readSuggestedThemesBackupState
import com.andrewwin.sumup.data.remote.firebase.sync.toBackupJson
import com.andrewwin.sumup.data.remote.firebase.sync.toImportedGroupsFromBackup
import com.andrewwin.sumup.data.remote.firebase.sync.toSavedArticlesFromBackup
import com.andrewwin.sumup.data.remote.firebase.sync.toSuggestedThemesBackupState
import com.andrewwin.sumup.data.remote.firebase.sync.toUserPreferencesFromBackup
import com.andrewwin.sumup.data.remote.firebase.sync.writeSuggestedThemesBackupState
import com.andrewwin.sumup.domain.ai.model.AiModelConfig
import com.andrewwin.sumup.domain.ai.model.normalizedStableKey
import com.andrewwin.sumup.domain.ai.repository.AiModelConfigRepository
import com.andrewwin.sumup.domain.article.repository.ArticleRepository
import com.andrewwin.sumup.domain.source.repository.SourceRepository
import com.andrewwin.sumup.domain.settings.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.settings.model.AppLanguage
import com.andrewwin.sumup.domain.summary.usecase.CreateScheduleSummaryUseCase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class CloudSyncWorkerHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val aiModelConfigRepository: AiModelConfigRepository,
    private val articleRepository: ArticleRepository,
    private val sourceRepository: SourceRepository,
    private val createScheduleSummaryUseCase: CreateScheduleSummaryUseCase,
    private val secretEncryptionManager: SecretEncryptionManager
) {
    suspend fun execute(): ListenableWorker.Result {
        logCloudSyncDebug("execute:start")
        val syncPrefs = context.getSharedPreferences(SYNC_PREFS, 0)
        val enabled = syncPrefs.getBoolean(KEY_SYNC_ENABLED, false)
        if (!enabled) {
            logCloudSyncDebug("execute:skip disabled")
            return ListenableWorker.Result.success()
        }
        val syncStrategy = parseSyncConflictStrategy(
            syncPrefs.getString(KEY_SYNC_STRATEGY, SyncConflictStrategy.MERGE.name)
        )
        val syncOverwritePriority = parseSyncOverwritePriority(
            syncPrefs.getString(KEY_SYNC_OVERWRITE_PRIORITY, SyncOverwritePriority.LOCAL.name)
        )

        val selection = BackupSelection(
            includeSources = syncPrefs.getBoolean(KEY_SYNC_INCLUDE_SOURCES, true),
            includeSubscriptions = syncPrefs.getBoolean(KEY_SYNC_INCLUDE_SUBSCRIPTIONS, true),
            includeSavedArticles = syncPrefs.getBoolean(KEY_SYNC_INCLUDE_SAVED_ARTICLES, true),
            includeSettingsNoApi = syncPrefs.getBoolean(KEY_SYNC_INCLUDE_SETTINGS_NO_API, true),
            includeApiKeys = syncPrefs.getBoolean(KEY_SYNC_INCLUDE_API_KEYS, false) &&
                secretEncryptionManager.hasSyncPassphrase()
        )

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            logCloudSyncDebug("execute:skip missing uid")
            return ListenableWorker.Result.success()
        }

        return runCatching {
            val firestore = FirebaseFirestore.getInstance()
            val docRef = firestore.collection(CLOUD_COLLECTION).document(uid)
            logCloudSyncDebug("execute:remote fetch start selection=$selection")
            val remote = docRef.get().await()
            val remoteBackupRaw = remote.getString("backup")
            logCloudSyncDebug(
                "execute:remote fetch complete exists=${remote.exists()} " +
                    "updatedAt=${remote.getLong("updatedAt") ?: 0L} backupLength=${remoteBackupRaw?.length ?: -1}"
            )
            val remoteBackupJson = remoteBackupRaw
                ?.takeIf { it.isNotBlank() }
                ?.let(::JSONObject)
            val remoteUpdatedAt = remote.getLong("updatedAt") ?: 0L
            val lastSyncAt = syncPrefs.getLong(KEY_LAST_SYNC_AT, 0L)
            val mergeMode = syncStrategy == SyncConflictStrategy.MERGE
            val shouldApplyRemote = shouldApplyRemoteBeforePush(
                remoteExists = remote.exists(),
                remoteUpdatedAt = remoteUpdatedAt,
                lastSyncAt = lastSyncAt,
                strategy = syncStrategy,
                overwritePriority = syncOverwritePriority
            )
            logCloudSyncDebug("execute:shouldApplyRemote=$shouldApplyRemote mergeMode=$mergeMode")

            if (shouldApplyRemote) {
                if (remoteBackupJson != null) {
                    logCloudSyncDebug("execute:apply remote start")
                    applyBackupJson(
                        root = remoteBackupJson,
                        merge = mergeMode,
                        selection = selection
                    )
                    logCloudSyncDebug("execute:apply remote complete")
                }
            }

            logCloudSyncDebug("execute:build local backup start")
            val localBackup = buildBackupJson(selection, remoteBackupJson)
            val localBackupJson = localBackup.toString()
            logCloudSyncDebug("execute:build local backup complete length=${localBackupJson.length}")
            require(localBackupJson.isNotBlank()) { "Cloud sync backup JSON is empty." }
            val now = System.currentTimeMillis()
            logCloudSyncDebug("execute:upload start")
            docRef.set(
                mapOf(
                    "backup" to localBackupJson,
                    "updatedAt" to now
                )
            ).await()
            logCloudSyncDebug("execute:upload complete")
            syncPrefs.edit().putLong(KEY_LAST_SYNC_AT, now).apply()
        }.fold(
            onSuccess = {
                logCloudSyncDebug("execute:success")
                ListenableWorker.Result.success()
            },
            onFailure = {
                logCloudSyncError("execute:failure", it)
                ListenableWorker.Result.retry()
            }
        )
    }

    private fun requireSyncPassphrase(): String =
        secretEncryptionManager.getSyncPassphraseOrNull() ?: error("Sync passphrase is missing.")

    private suspend fun buildBackupJson(selection: BackupSelection, remoteBackupRoot: JSONObject? = null): JSONObject {
        logCloudSyncDebug("buildBackupJson:start selection=$selection hasRemoteRoot=${remoteBackupRoot != null}")
        val subscriptionsPrefs = context.getSharedPreferences(SUBSCRIPTIONS_PREFS, 0)
        val prefs = if (selection.includeSettingsNoApi) userPreferencesRepository.preferences.first() else null
        val aiConfigs = if (selection.includeApiKeys) aiModelConfigRepository.allConfigs.first() else emptyList()
        val syncPassphrase = if (selection.includeApiKeys) {
            secretEncryptionManager.getSyncPassphraseOrNull() ?: error("Sync passphrase is missing.")
        } else {
            null
        }
        val syncEncryptionSession = syncPassphrase?.let(secretEncryptionManager::createSyncEncryptionSession)
        val groups = if (selection.includeSources) sourceRepository.getGroupsWithSourcesSnapshot() else emptyList()
        val suggestedThemesBackupState = if (selection.includeSubscriptions) {
            subscriptionsPrefs.readSuggestedThemesBackupState()
        } else {
            null
        }
        val savedArticlesSnapshot = if (selection.includeSavedArticles) {
            articleRepository.getSavedArticlesSnapshot()
        } else {
            emptyList()
        }
        logCloudSyncDebug(
            "buildBackupJson:done hasPrefs=${prefs != null} aiConfigs=${aiConfigs.size} groups=${groups.size} " +
                "savedThemeIds=${suggestedThemesBackupState?.savedThemeIds?.size ?: 0} savedArticles=${savedArticlesSnapshot.size}"
        )

        return JSONObject().apply {
            put("schemaVersion", 1)
            put("exportedAt", System.currentTimeMillis())
            put(
                "syncStrategy",
                context.getSharedPreferences(SYNC_PREFS, 0)
                    .getString(KEY_SYNC_STRATEGY, SyncConflictStrategy.MERGE.name)
            )
            put(
                "syncOverwritePriority",
                context.getSharedPreferences(SYNC_PREFS, 0)
                    .getString(KEY_SYNC_OVERWRITE_PRIORITY, SyncOverwritePriority.LOCAL.name)
            )
            put(
                "importStrategy",
                context.getSharedPreferences(SYNC_PREFS, 0)
                    .getString(KEY_IMPORT_STRATEGY, SyncConflictStrategy.MERGE.name)
            )
            put("selection", JSONObject().apply {
                put("sources", selection.includeSources)
                put("subscriptions", selection.includeSubscriptions)
                put("savedArticles", selection.includeSavedArticles)
                put("settingsNoApi", selection.includeSettingsNoApi)
                put("apiKeys", selection.includeApiKeys)
            })
            if (prefs != null) put("userPreferences", prefs.toRoomEntity().toBackupJson())
            if (selection.includeApiKeys) {
                put("apiKeysSalt", syncEncryptionSession?.saltBase64)
                put("aiConfigs", JSONArray().apply {
                    aiConfigs.forEach { put(it.toBackupJson(syncEncryptionSession!!)) }
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
                        suggestedThemesBackupState ?: SuggestedThemesBackupState(
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
                    savedArticlesSnapshot.forEach { put(it.toBackupJson()) }
                })
            }
        }.also { root ->
            logCloudSyncDebug("buildBackupJson:complete keys=${root.length()}")
        }
    }

    private suspend fun applyBackupJson(root: JSONObject, merge: Boolean, selection: BackupSelection) {
        logCloudSyncDebug(
            "applyBackupJson:start merge=$merge selection=$selection keys=${root.length()} " +
                "hasUserPreferences=${root.has("userPreferences")} hasAiConfigs=${root.has("aiConfigs")} " +
                "hasGroups=${root.has("groups")} hasSubscriptions=${root.has("subscriptions")} " +
                "hasSavedArticles=${root.has("savedArticles")}"
        )
        val importedSyncStrategy = parseSyncConflictStrategy(root.optString("syncStrategy", SyncConflictStrategy.MERGE.name))
        val importedOverwritePriority = parseSyncOverwritePriority(
            root.optString("syncOverwritePriority", SyncOverwritePriority.LOCAL.name)
        )
        val importedImportStrategy = parseSyncConflictStrategy(
            root.optString("importStrategy", SyncConflictStrategy.MERGE.name)
        )
        logCloudSyncDebug(
            "applyBackupJson:defer sync settings strategy=${importedSyncStrategy.name} " +
                "overwrite=${importedOverwritePriority.name} import=${importedImportStrategy.name}"
        )

        val subscriptionsPrefs = context.getSharedPreferences(SUBSCRIPTIONS_PREFS, 0)
        val importedPrefs = root.optJSONObject("userPreferences")?.toUserPreferencesFromBackup()
        val importedAiConfigs = root.optJSONArray("aiConfigs")
        val importedAiConfigsSalt = root.optString("apiKeysSalt").takeIf { it.isNotBlank() }
        val importedGroups = root.optJSONArray("groups").toImportedGroupsFromBackup()
        val importedSubscriptions = root.optJSONObject("subscriptions")
        val hasSavedArticlesField = root.has("savedArticles")
        val rawSavedArticles = root.optJSONArray("savedArticles")
        val importedSavedArticles = rawSavedArticles.toSavedArticlesFromBackup()
        val importedSavedArticleUrls = rawSavedArticles
            ?.let { arr ->
                buildList {
                    for (index in 0 until arr.length()) {
                        if (arr.optJSONObject(index) != null) continue
                        arr.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            }.orEmpty()
        logCloudSyncDebug(
            "applyBackupJson:parsed hasPrefs=${importedPrefs != null} " +
                "importedAiConfigsLength=${importedAiConfigs?.length() ?: -1} groups=${importedGroups.size} " +
                "hasSubscriptions=${importedSubscriptions != null} savedArticles=${importedSavedArticles.size} " +
                "savedArticleUrls=${importedSavedArticleUrls.size}"
        )

        if (selection.includeSettingsNoApi && importedPrefs != null) {
            val importedSettings = importedPrefs.copy(id = 0).toDomainModel()
            userPreferencesRepository.updatePreferences(importedSettings)
            val languageTag = when (importedSettings.appLanguage) {
                AppLanguage.UK -> "uk"
                AppLanguage.EN -> "en"
            }
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
            createScheduleSummaryUseCase(
                importedSettings.isScheduledSummaryEnabled,
                importedSettings.scheduledSummaryTimeList
            )
        }

        if (selection.includeApiKeys && importedAiConfigs != null) {
            val syncPassphrase = requireSyncPassphrase()
            val existingConfigs = aiModelConfigRepository.allConfigs.first()
            val existingConfigKeys = existingConfigs.map(AiModelConfig::normalizedStableKey).toSet()
            
            val toInsert = mutableListOf<AiModelConfig>()
            for (i in 0 until importedAiConfigs.length()) {
                val configJson = importedAiConfigs.optJSONObject(i) ?: continue
                val imported = AiModelConfig.fromBackupJson(
                    configJson,
                    secretEncryptionManager,
                    syncPassphrase,
                    importedAiConfigsSalt
                )
                val normalizedImported = imported.copy(apiKey = imported.apiKey.trim())
                if (merge) {
                    if (normalizedImported.normalizedStableKey() !in existingConfigKeys) {
                        toInsert.add(normalizedImported)
                    } else {
                        val existing = existingConfigs.firstOrNull {
                            it.normalizedStableKey() == normalizedImported.normalizedStableKey()
                        }
                        if (existing != null) {
                            aiModelConfigRepository.updateConfig(
                                normalizedImported.copy(
                                    id = existing.id,
                                    sortOrder = existing.sortOrder
                                )
                            )
                        }
                    }
                } else {
                    toInsert.add(normalizedImported)
                }
            }
            if (!merge) {
                existingConfigs.forEach { aiModelConfigRepository.deleteConfig(it) }
            }
            toInsert.forEach { aiModelConfigRepository.addConfig(it) }
        }
        if (selection.includeSources) {
            sourceRepository.importGroupsWithSources(importedGroups, merge)
        }

        if (selection.includeSubscriptions) {
            if (importedSubscriptions != null) {
                val subscriptionState = importedSubscriptions.toSuggestedThemesBackupState()
                subscriptionsPrefs.edit()
                    .writeSuggestedThemesBackupState(subscriptionState, clearWhenEmpty = true)
                    .apply()
            } else if (!merge) {
                subscriptionsPrefs.edit()
                    .remove(KEY_SAVED_THEME_IDS)
                    .remove(KEY_SAVED_THEMES)
                    .remove(KEY_SOURCES_HASH)
                    .remove(KEY_LAST_RECOMMENDATION_AT)
                    .remove(KEY_LAST_FEED_REFRESH_AT)
                    .apply()
            }
        }

        if (selection.includeSavedArticles && hasSavedArticlesField) {
            if (importedSavedArticles.isNotEmpty()) {
                if (merge) {
                    articleRepository.mergeSavedArticlesSnapshot(importedSavedArticles)
                } else {
                    articleRepository.replaceSavedArticlesSnapshot(importedSavedArticles)
                }
            } else if (merge) {
                articleRepository.mergeFavoriteArticlesByUrls(importedSavedArticleUrls)
            } else {
                articleRepository.replaceFavoriteArticlesByUrls(importedSavedArticleUrls)
            }
        }
        context.getSharedPreferences(SYNC_PREFS, 0)
            .edit()
            .putString(KEY_SYNC_STRATEGY, importedSyncStrategy.name)
            .putString(KEY_SYNC_OVERWRITE_PRIORITY, importedOverwritePriority.name)
            .putString(KEY_IMPORT_STRATEGY, importedImportStrategy.name)
            .apply()
        logCloudSyncDebug("applyBackupJson:sync settings applied at end")
        logCloudSyncDebug("applyBackupJson:complete")
    }

    private fun parseSyncConflictStrategy(rawValue: String?): SyncConflictStrategy {
        return runCatching { SyncConflictStrategy.valueOf(rawValue.orEmpty()) }
            .getOrDefault(SyncConflictStrategy.MERGE)
    }

    private fun parseSyncOverwritePriority(rawValue: String?): SyncOverwritePriority {
        return runCatching { SyncOverwritePriority.valueOf(rawValue.orEmpty()) }
            .getOrDefault(SyncOverwritePriority.LOCAL)
    }

    private fun shouldApplyRemoteBeforePush(
        remoteExists: Boolean,
        remoteUpdatedAt: Long,
        lastSyncAt: Long,
        strategy: SyncConflictStrategy,
        overwritePriority: SyncOverwritePriority
    ): Boolean {
        if (!remoteExists) return false
        if (strategy == SyncConflictStrategy.MERGE) {
            return remoteUpdatedAt > lastSyncAt
        }
        return when (overwritePriority) {
            SyncOverwritePriority.CLOUD -> true
            SyncOverwritePriority.LOCAL -> false
        }
    }

    private fun logCloudSyncDebug(message: String) {
        Log.d(CLOUD_SYNC_LOG_TAG, message)
    }

    private fun logCloudSyncError(message: String, throwable: Throwable) {
        Log.e(CLOUD_SYNC_LOG_TAG, message, throwable)
    }

    companion object {
        private const val CLOUD_SYNC_LOG_TAG = "CloudSyncBackup"

        const val CLOUD_COLLECTION = "user_sync_backups"
        const val SYNC_PREFS = "sync_prefs"
        const val SUBSCRIPTIONS_PREFS = "suggested_themes_prefs"

        const val KEY_SYNC_ENABLED = "sync_enabled"
        const val KEY_SYNC_STRATEGY = "sync_strategy"
        const val KEY_SYNC_OVERWRITE_PRIORITY = "sync_overwrite_priority"
        const val KEY_IMPORT_STRATEGY = "import_strategy"
        const val KEY_LAST_SYNC_AT = "last_sync_at"
        const val KEY_SYNC_INCLUDE_SOURCES = "sync_include_sources"
        const val KEY_SYNC_INCLUDE_SUBSCRIPTIONS = "sync_include_subscriptions"
        const val KEY_SYNC_INCLUDE_SAVED_ARTICLES = "sync_include_saved_articles"
        const val KEY_SYNC_INCLUDE_SETTINGS_NO_API = "sync_include_settings_no_api"
        const val KEY_SYNC_INCLUDE_API_KEYS = "sync_include_api_keys"

        const val KEY_SAVED_THEMES = "savedThemes"
        const val KEY_SAVED_THEME_IDS = "savedThemeIds"
        const val KEY_SOURCES_HASH = "sourcesHash"
        const val KEY_LAST_RECOMMENDATION_AT = "lastRecommendationAt"
        const val KEY_LAST_FEED_REFRESH_AT = "lastFeedRefreshAt"
    }
}

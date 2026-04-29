package com.andrewwin.sumup.worker

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.work.ListenableWorker
import com.andrewwin.sumup.data.security.SecretEncryptionManager
import com.andrewwin.sumup.data.local.entities.AiModelConfig
import com.andrewwin.sumup.domain.repository.AiModelConfigRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.SourceRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.usecase.settings.ScheduleSummaryUseCase
import com.andrewwin.sumup.ui.screen.settings.BackupSelection
import com.andrewwin.sumup.ui.screen.settings.SyncConflictStrategy
import com.andrewwin.sumup.ui.screen.settings.SyncOverwritePriority
import com.andrewwin.sumup.ui.screen.settings.SuggestedThemesBackupState
import com.andrewwin.sumup.ui.screen.settings.fromBackupJson
import com.andrewwin.sumup.ui.screen.settings.putSuggestedThemesBackupState
import com.andrewwin.sumup.ui.screen.settings.readSuggestedThemesBackupState
import com.andrewwin.sumup.ui.screen.settings.toSuggestedThemesBackupState
import com.andrewwin.sumup.ui.screen.settings.toAiConfigsFromBackup
import com.andrewwin.sumup.ui.screen.settings.toBackupJson
import com.andrewwin.sumup.ui.screen.settings.toImportedGroupsFromBackup
import com.andrewwin.sumup.ui.screen.settings.toSavedArticlesFromBackup
import com.andrewwin.sumup.ui.screen.settings.toUserPreferencesFromBackup
import com.andrewwin.sumup.ui.screen.settings.writeSuggestedThemesBackupState
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
    private val scheduleSummaryUseCase: ScheduleSummaryUseCase,
    private val secretEncryptionManager: SecretEncryptionManager
) {
    suspend fun execute(): ListenableWorker.Result {
        logCloudSyncDebug("execute:start")
        val syncPrefs = context.getSharedPreferences(WorkerContracts.SYNC_PREFS, 0)
        val enabled = syncPrefs.getBoolean(WorkerContracts.KEY_SYNC_ENABLED, false)
        if (!enabled) {
            logCloudSyncDebug("execute:skip disabled")
            return ListenableWorker.Result.success()
        }
        val syncStrategy = parseSyncConflictStrategy(
            syncPrefs.getString(WorkerContracts.KEY_SYNC_STRATEGY, SyncConflictStrategy.MERGE.name)
        )
        val syncOverwritePriority = parseSyncOverwritePriority(
            syncPrefs.getString(WorkerContracts.KEY_SYNC_OVERWRITE_PRIORITY, SyncOverwritePriority.LOCAL.name)
        )

        val selection = BackupSelection(
            includeSources = syncPrefs.getBoolean(WorkerContracts.KEY_SYNC_INCLUDE_SOURCES, true),
            includeSubscriptions = syncPrefs.getBoolean(WorkerContracts.KEY_SYNC_INCLUDE_SUBSCRIPTIONS, true),
            includeSavedArticles = syncPrefs.getBoolean(WorkerContracts.KEY_SYNC_INCLUDE_SAVED_ARTICLES, true),
            includeSettingsNoApi = syncPrefs.getBoolean(WorkerContracts.KEY_SYNC_INCLUDE_SETTINGS_NO_API, true),
            includeApiKeys = syncPrefs.getBoolean(WorkerContracts.KEY_SYNC_INCLUDE_API_KEYS, false) &&
                secretEncryptionManager.hasSyncPassphrase()
        )

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            logCloudSyncDebug("execute:skip missing uid")
            return ListenableWorker.Result.success()
        }

        return runCatching {
            val firestore = FirebaseFirestore.getInstance()
            val docRef = firestore.collection(WorkerContracts.CLOUD_COLLECTION).document(uid)
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
            val lastSyncAt = syncPrefs.getLong(WorkerContracts.KEY_LAST_SYNC_AT, 0L)
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
            syncPrefs.edit().putLong(WorkerContracts.KEY_LAST_SYNC_AT, now).apply()
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
        val subscriptionsPrefs = context.getSharedPreferences(WorkerContracts.SUBSCRIPTIONS_PREFS, 0)
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
                context.getSharedPreferences(WorkerContracts.SYNC_PREFS, 0)
                    .getString(WorkerContracts.KEY_SYNC_STRATEGY, SyncConflictStrategy.MERGE.name)
            )
            put(
                "syncOverwritePriority",
                context.getSharedPreferences(WorkerContracts.SYNC_PREFS, 0)
                    .getString(WorkerContracts.KEY_SYNC_OVERWRITE_PRIORITY, SyncOverwritePriority.LOCAL.name)
            )
            put(
                "importStrategy",
                context.getSharedPreferences(WorkerContracts.SYNC_PREFS, 0)
                    .getString(WorkerContracts.KEY_IMPORT_STRATEGY, SyncConflictStrategy.MERGE.name)
            )
            put("selection", JSONObject().apply {
                put("sources", selection.includeSources)
                put("subscriptions", selection.includeSubscriptions)
                put("savedArticles", selection.includeSavedArticles)
                put("settingsNoApi", selection.includeSettingsNoApi)
                put("apiKeys", selection.includeApiKeys)
            })
            if (prefs != null) put("userPreferences", prefs.toBackupJson())
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

        val subscriptionsPrefs = context.getSharedPreferences(WorkerContracts.SUBSCRIPTIONS_PREFS, 0)
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
            userPreferencesRepository.updatePreferences(importedPrefs.copy(id = 0))
            val languageTag = when (importedPrefs.appLanguage) {
                com.andrewwin.sumup.data.local.entities.AppLanguage.UK -> "uk"
                com.andrewwin.sumup.data.local.entities.AppLanguage.EN -> "en"
            }
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
            scheduleSummaryUseCase(
                importedPrefs.isScheduledSummaryEnabled,
                importedPrefs.scheduledHour,
                importedPrefs.scheduledMinute
            )
        }

        if (selection.includeApiKeys && importedAiConfigs != null) {
            val syncPassphrase = requireSyncPassphrase()
            val existingConfigs = aiModelConfigRepository.allConfigs.first()
            val existingConfigKeys = existingConfigs.map {
                it.apiKey.trim()
            }.toSet()
            
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
                    if (normalizedImported.apiKey !in existingConfigKeys) {
                        toInsert.add(normalizedImported)
                    } else {
                        val existing = existingConfigs.firstOrNull {
                            it.apiKey.trim() == normalizedImported.apiKey
                        }
                        if (existing != null) {
                            aiModelConfigRepository.updateConfig(
                                normalizedImported.copy(
                                    id = existing.id,
                                    isUseNow = normalizedImported.isUseNow || existing.isUseNow
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
                    .remove(WorkerContracts.KEY_SAVED_THEME_IDS)
                    .remove(WorkerContracts.KEY_SAVED_THEMES)
                    .remove(WorkerContracts.KEY_SOURCES_HASH)
                    .remove(WorkerContracts.KEY_LAST_RECOMMENDATION_AT)
                    .remove(WorkerContracts.KEY_LAST_FEED_REFRESH_AT)
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
        context.getSharedPreferences(WorkerContracts.SYNC_PREFS, 0)
            .edit()
            .putString(WorkerContracts.KEY_SYNC_STRATEGY, importedSyncStrategy.name)
            .putString(WorkerContracts.KEY_SYNC_OVERWRITE_PRIORITY, importedOverwritePriority.name)
            .putString(WorkerContracts.KEY_IMPORT_STRATEGY, importedImportStrategy.name)
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

    private companion object {
        const val CLOUD_SYNC_LOG_TAG = "CloudSyncBackup"
    }
}

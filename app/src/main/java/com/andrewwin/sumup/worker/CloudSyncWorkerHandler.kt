package com.andrewwin.sumup.worker

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.work.ListenableWorker
import com.andrewwin.sumup.data.security.SecretEncryptionManager
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.SourceRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.support.DebugTrace
import com.andrewwin.sumup.domain.usecase.settings.ScheduleSummaryUseCase
import com.andrewwin.sumup.ui.screen.settings.BackupSelection
import com.andrewwin.sumup.ui.screen.settings.SyncConflictStrategy
import com.andrewwin.sumup.ui.screen.settings.SyncOverwritePriority
import com.andrewwin.sumup.ui.screen.settings.toAiConfigsFromBackup
import com.andrewwin.sumup.ui.screen.settings.toBackupJson
import com.andrewwin.sumup.ui.screen.settings.toImportedGroupsFromBackup
import com.andrewwin.sumup.ui.screen.settings.toSavedArticlesFromBackup
import com.andrewwin.sumup.ui.screen.settings.toUserPreferencesFromBackup
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
    private val aiRepository: AiRepository,
    private val articleRepository: ArticleRepository,
    private val sourceRepository: SourceRepository,
    private val scheduleSummaryUseCase: ScheduleSummaryUseCase,
    private val secretEncryptionManager: SecretEncryptionManager
) {
    suspend fun execute(): ListenableWorker.Result {
        val syncPrefs = context.getSharedPreferences(WorkerContracts.SYNC_PREFS, 0)
        val enabled = syncPrefs.getBoolean(WorkerContracts.KEY_SYNC_ENABLED, false)
        if (!enabled) return ListenableWorker.Result.success()
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
        DebugTrace.d(
            "backup_sync",
            "worker selection strategy=${syncStrategy.name} overwritePriority=${syncOverwritePriority.name} sources=${selection.includeSources} subscriptions=${selection.includeSubscriptions} saved=${selection.includeSavedArticles} settings=${selection.includeSettingsNoApi} apiKeys=${selection.includeApiKeys}"
        )

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) return ListenableWorker.Result.success()

        return runCatching {
            val firestore = FirebaseFirestore.getInstance()
            val docRef = firestore.collection(WorkerContracts.CLOUD_COLLECTION).document(uid)
            val remote = docRef.get().await()
            val remoteBackupJson = remote.getString("backup")
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

            if (shouldApplyRemote) {
                if (remoteBackupJson != null) {
                    applyBackupJson(
                        root = remoteBackupJson,
                        merge = mergeMode,
                        selection = selection
                    )
                }
            }

            val localBackup = buildBackupJson(selection, remoteBackupJson)
            val now = System.currentTimeMillis()
            docRef.set(
                mapOf(
                    "backup" to localBackup.toString(),
                    "updatedAt" to now
                )
            ).await()
            syncPrefs.edit().putLong(WorkerContracts.KEY_LAST_SYNC_AT, now).apply()
        }.fold(
            onSuccess = { ListenableWorker.Result.success() },
            onFailure = { ListenableWorker.Result.retry() }
        )
    }

    private suspend fun buildBackupJson(selection: BackupSelection, remoteBackupRoot: JSONObject? = null): JSONObject {
        val subscriptionsPrefs = context.getSharedPreferences(WorkerContracts.SUBSCRIPTIONS_PREFS, 0)
        val prefs = if (selection.includeSettingsNoApi) userPreferencesRepository.preferences.first() else null
        val aiConfigs = if (selection.includeApiKeys) aiRepository.allConfigs.first() else emptyList()
        val syncPassphrase = if (selection.includeApiKeys) {
            secretEncryptionManager.getSyncPassphraseOrNull() ?: error("Sync passphrase is missing.")
        } else {
            null
        }
        val groups = if (selection.includeSources) sourceRepository.getGroupsWithSourcesSnapshot() else emptyList()
        val savedThemes = if (selection.includeSubscriptions) {
            subscriptionsPrefs.getStringSet(WorkerContracts.KEY_SAVED_THEMES, emptySet()).orEmpty()
        } else {
            emptySet()
        }
        val savedArticlesSnapshot = if (selection.includeSavedArticles) {
            articleRepository.getSavedArticlesSnapshot()
        } else {
            emptyList()
        }
        val lastRecommendationAt = if (selection.includeSubscriptions) {
            subscriptionsPrefs.getLong(WorkerContracts.KEY_LAST_RECOMMENDATION_AT, 0L)
        } else {
            0L
        }
        DebugTrace.d(
            "backup_sync",
            "worker buildBackupJson savedThemes=${savedThemes.size} savedArticles=${savedArticlesSnapshot.size}"
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
                put("aiConfigs", JSONArray().apply {
                    aiConfigs.forEach { put(it.toBackupJson(secretEncryptionManager, syncPassphrase!!)) }
                })
            } else if (remoteBackupRoot?.has("aiConfigs") == true) {
                put("aiConfigs", remoteBackupRoot.optJSONArray("aiConfigs"))
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
                    put(WorkerContracts.KEY_SAVED_THEMES, JSONArray(savedThemes.toList()))
                    put(WorkerContracts.KEY_LAST_RECOMMENDATION_AT, lastRecommendationAt)
                })
            }
            if (selection.includeSavedArticles) {
                put("savedArticles", JSONArray().apply {
                    savedArticlesSnapshot.forEach { put(it.toBackupJson()) }
                })
            }
        }
    }

    private suspend fun applyBackupJson(root: JSONObject, merge: Boolean, selection: BackupSelection) {
        val importedSyncStrategy = parseSyncConflictStrategy(root.optString("syncStrategy", SyncConflictStrategy.MERGE.name))
        val importedOverwritePriority = parseSyncOverwritePriority(
            root.optString("syncOverwritePriority", SyncOverwritePriority.LOCAL.name)
        )
        val importedImportStrategy = parseSyncConflictStrategy(
            root.optString("importStrategy", SyncConflictStrategy.MERGE.name)
        )
        context.getSharedPreferences(WorkerContracts.SYNC_PREFS, 0)
            .edit()
            .putString(WorkerContracts.KEY_SYNC_STRATEGY, importedSyncStrategy.name)
            .putString(WorkerContracts.KEY_SYNC_OVERWRITE_PRIORITY, importedOverwritePriority.name)
            .putString(WorkerContracts.KEY_IMPORT_STRATEGY, importedImportStrategy.name)
            .apply()

        val subscriptionsPrefs = context.getSharedPreferences(WorkerContracts.SUBSCRIPTIONS_PREFS, 0)
        val importedPrefs = root.optJSONObject("userPreferences")?.toUserPreferencesFromBackup()
        val importedConfigs = if (selection.includeApiKeys) {
            root.optJSONArray("aiConfigs").toAiConfigsFromBackup(
                secretEncryptionManager = secretEncryptionManager,
                syncPassphrase = secretEncryptionManager.getSyncPassphraseOrNull()
                    ?: error("Sync passphrase is missing.")
            )
        } else {
            emptyList()
        }
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
        DebugTrace.d(
            "backup_sync",
            "worker applyBackupJson merge=$merge includeSaved=${selection.includeSavedArticles} hasSavedField=$hasSavedArticlesField importedSavedArticles=${importedSavedArticles.size} importedSavedUrlsLegacy=${importedSavedArticleUrls.size}"
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
                val savedThemes = importedSubscriptions.optJSONArray(WorkerContracts.KEY_SAVED_THEMES)
                    ?.let { arr ->
                        buildSet {
                            for (i in 0 until arr.length()) {
                                arr.optString(i)?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
                            }
                        }
                    }
                    .orEmpty()
                subscriptionsPrefs.edit()
                    .putStringSet(WorkerContracts.KEY_SAVED_THEMES, savedThemes)
                    .putLong(
                        WorkerContracts.KEY_LAST_RECOMMENDATION_AT,
                        importedSubscriptions.optLong(WorkerContracts.KEY_LAST_RECOMMENDATION_AT, 0L)
                    )
                    .apply()
            } else if (!merge) {
                subscriptionsPrefs.edit()
                    .remove(WorkerContracts.KEY_SAVED_THEMES)
                    .remove(WorkerContracts.KEY_LAST_RECOMMENDATION_AT)
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
}







package com.andrewwin.sumup.worker

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.work.ListenableWorker
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.SourceRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.usecase.settings.ScheduleSummaryUseCase
import com.andrewwin.sumup.ui.screen.settings.BackupSelection
import com.andrewwin.sumup.ui.screen.settings.toAiConfigsFromBackup
import com.andrewwin.sumup.ui.screen.settings.toBackupJson
import com.andrewwin.sumup.ui.screen.settings.toImportedGroupsFromBackup
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
    private val sourceRepository: SourceRepository,
    private val scheduleSummaryUseCase: ScheduleSummaryUseCase
) {
    suspend fun execute(): ListenableWorker.Result {
        val syncPrefs = context.getSharedPreferences(WorkerContracts.SYNC_PREFS, 0)
        val enabled = syncPrefs.getBoolean(WorkerContracts.KEY_SYNC_ENABLED, false)
        if (!enabled) return ListenableWorker.Result.success()

        val selection = BackupSelection(
            includeSources = syncPrefs.getBoolean(WorkerContracts.KEY_INCLUDE_SOURCES, true),
            includeSubscriptions = syncPrefs.getBoolean(WorkerContracts.KEY_INCLUDE_SUBSCRIPTIONS, true),
            includeSettingsNoApi = syncPrefs.getBoolean(WorkerContracts.KEY_INCLUDE_SETTINGS_NO_API, true),
            includeApiKeys = syncPrefs.getBoolean(WorkerContracts.KEY_INCLUDE_API_KEYS, true)
        )

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) return ListenableWorker.Result.success()

        return runCatching {
            val firestore = FirebaseFirestore.getInstance()
            val docRef = firestore.collection(WorkerContracts.CLOUD_COLLECTION).document(uid)
            val remote = docRef.get().await()
            val remoteUpdatedAt = remote.getLong("updatedAt") ?: 0L
            val lastSyncAt = syncPrefs.getLong(WorkerContracts.KEY_LAST_SYNC_AT, 0L)

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
            syncPrefs.edit().putLong(WorkerContracts.KEY_LAST_SYNC_AT, now).apply()
        }.fold(
            onSuccess = { ListenableWorker.Result.success() },
            onFailure = { ListenableWorker.Result.retry() }
        )
    }

    private suspend fun buildBackupJson(selection: BackupSelection): JSONObject {
        val subscriptionsPrefs = context.getSharedPreferences(WorkerContracts.SUBSCRIPTIONS_PREFS, 0)
        val prefs = if (selection.includeSettingsNoApi) userPreferencesRepository.preferences.first() else null
        val aiConfigs = if (selection.includeApiKeys) aiRepository.allConfigs.first() else emptyList()
        val groups = if (selection.includeSources) sourceRepository.getGroupsWithSourcesSnapshot() else emptyList()
        val savedThemes = if (selection.includeSubscriptions) {
            subscriptionsPrefs.getStringSet(WorkerContracts.KEY_SAVED_THEMES, emptySet()).orEmpty()
        } else {
            emptySet()
        }
        val lastRecommendationAt = if (selection.includeSubscriptions) {
            subscriptionsPrefs.getLong(WorkerContracts.KEY_LAST_RECOMMENDATION_AT, 0L)
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
            if (prefs != null) put("userPreferences", prefs.toBackupJson())
            if (selection.includeApiKeys) {
                put("aiConfigs", JSONArray().apply { aiConfigs.forEach { put(it.toBackupJson()) } })
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
        }
    }

    private suspend fun applyBackupJson(root: JSONObject, merge: Boolean, selection: BackupSelection) {
        val subscriptionsPrefs = context.getSharedPreferences(WorkerContracts.SUBSCRIPTIONS_PREFS, 0)
        val importedPrefs = root.optJSONObject("userPreferences")?.toUserPreferencesFromBackup()
        val importedConfigs = root.optJSONArray("aiConfigs").toAiConfigsFromBackup()
        val importedGroups = root.optJSONArray("groups").toImportedGroupsFromBackup()
        val importedSubscriptions = root.optJSONObject("subscriptions")

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
    }
}







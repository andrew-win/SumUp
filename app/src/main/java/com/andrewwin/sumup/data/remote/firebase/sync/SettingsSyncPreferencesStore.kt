package com.andrewwin.sumup.data.remote.firebase.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.andrewwin.sumup.data.security.SecretEncryptionManager
import com.andrewwin.sumup.worker.CloudSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class SettingsSyncPreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager,
    private val secretEncryptionManager: SecretEncryptionManager
) {
    private val syncPrefs by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        context.getSharedPreferences(SYNC_PREFS, 0)
    }

    fun loadState(): SettingsSyncState {
        var state = SettingsSyncState(
            isCloudSyncEnabled = syncPrefs.getBoolean(KEY_SYNC_ENABLED, false),
            syncIntervalHours = syncPrefs.getInt(KEY_SYNC_INTERVAL_HOURS, SettingsSyncState.DEFAULT_SYNC_INTERVAL_HOURS),
            syncSelection = readSelectionFromPrefs(KEY_PREFIX_SYNC),
            exportSelection = readSelectionFromPrefs(KEY_PREFIX_EXPORT),
            importSelection = readSelectionFromPrefs(KEY_PREFIX_IMPORT),
            syncStrategy = parseSyncConflictStrategy(syncPrefs.getString(KEY_SYNC_STRATEGY, SyncConflictStrategy.MERGE.name)),
            syncOverwritePriority = parseSyncOverwritePriority(
                syncPrefs.getString(KEY_SYNC_OVERWRITE_PRIORITY, SyncOverwritePriority.LOCAL.name)
            ),
            importStrategy = parseSyncConflictStrategy(syncPrefs.getString(KEY_IMPORT_STRATEGY, SyncConflictStrategy.MERGE.name)),
            lastSyncAt = syncPrefs.getLong(KEY_LAST_SYNC_AT, 0L),
            hasSyncPassphrase = secretEncryptionManager.hasSyncPassphrase()
        )
        if (!state.hasSyncPassphrase) {
            state = state.copy(
                syncSelection = state.syncSelection.copy(includeApiKeys = false),
                exportSelection = state.exportSelection.copy(includeApiKeys = false),
                importSelection = state.importSelection.copy(includeApiKeys = false)
            )
            persistSelection(KEY_PREFIX_SYNC, state.syncSelection)
            persistSelection(KEY_PREFIX_EXPORT, state.exportSelection)
            persistSelection(KEY_PREFIX_IMPORT, state.importSelection)
        }
        if (state.isCloudSyncEnabled) {
            scheduleCloudSyncWorker(state.syncIntervalHours)
        }
        return state
    }

    fun updateCloudSyncEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean(KEY_SYNC_ENABLED, enabled).apply()
        if (!enabled) {
            workManager.cancelUniqueWork(CLOUD_SYNC_WORK_NAME)
        }
    }

    fun updateSyncIntervalHours(hours: Int) {
        syncPrefs.edit().putInt(KEY_SYNC_INTERVAL_HOURS, hours).apply()
    }

    fun updateSyncStrategy(strategy: SyncConflictStrategy) {
        syncPrefs.edit().putString(KEY_SYNC_STRATEGY, strategy.name).apply()
    }

    fun updateSyncOverwritePriority(priority: SyncOverwritePriority) {
        syncPrefs.edit().putString(KEY_SYNC_OVERWRITE_PRIORITY, priority.name).apply()
    }

    fun updateImportStrategy(strategy: SyncConflictStrategy) {
        syncPrefs.edit().putString(KEY_IMPORT_STRATEGY, strategy.name).apply()
    }

    fun updateLastSyncAt(timestamp: Long) {
        syncPrefs.edit().putLong(KEY_LAST_SYNC_AT, timestamp).apply()
    }

    fun persistSyncSelection(selection: BackupSelection) = persistSelection(KEY_PREFIX_SYNC, selection)

    fun persistExportSelection(selection: BackupSelection) = persistSelection(KEY_PREFIX_EXPORT, selection)

    fun persistImportSelection(selection: BackupSelection) = persistSelection(KEY_PREFIX_IMPORT, selection)

    fun scheduleCloudSyncWorker(intervalHours: Int) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<CloudSyncWorker>(intervalHours.toLong(), TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniquePeriodicWork(
            CLOUD_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun persistSelection(prefix: String, selection: BackupSelection) {
        syncPrefs.edit()
            .putBoolean(prefKey(prefix, KEY_INCLUDE_SOURCES_SUFFIX), selection.includeSources)
            .putBoolean(prefKey(prefix, KEY_INCLUDE_SUBSCRIPTIONS_SUFFIX), selection.includeSubscriptions)
            .putBoolean(prefKey(prefix, KEY_INCLUDE_SAVED_ARTICLES_SUFFIX), selection.includeSavedArticles)
            .putBoolean(prefKey(prefix, KEY_INCLUDE_SETTINGS_NO_API_SUFFIX), selection.includeSettingsNoApi)
            .putBoolean(prefKey(prefix, KEY_INCLUDE_API_KEYS_SUFFIX), selection.includeApiKeys)
            .apply()
    }

    private fun readSelectionFromPrefs(prefix: String): BackupSelection = BackupSelection(
        includeSources = syncPrefs.getBoolean(prefKey(prefix, KEY_INCLUDE_SOURCES_SUFFIX), true),
        includeSubscriptions = syncPrefs.getBoolean(prefKey(prefix, KEY_INCLUDE_SUBSCRIPTIONS_SUFFIX), true),
        includeSavedArticles = syncPrefs.getBoolean(prefKey(prefix, KEY_INCLUDE_SAVED_ARTICLES_SUFFIX), true),
        includeSettingsNoApi = syncPrefs.getBoolean(prefKey(prefix, KEY_INCLUDE_SETTINGS_NO_API_SUFFIX), true),
        includeApiKeys = syncPrefs.getBoolean(prefKey(prefix, KEY_INCLUDE_API_KEYS_SUFFIX), false)
    )

    private fun prefKey(prefix: String, suffix: String): String = "${prefix}_$suffix"

    companion object {
        private const val CLOUD_SYNC_WORK_NAME = "cloud_sync_periodic"
        private const val SYNC_PREFS = "sync_prefs"
        private const val KEY_SYNC_ENABLED = "sync_enabled"
        private const val KEY_SYNC_INTERVAL_HOURS = "sync_interval_hours"
        private const val KEY_SYNC_STRATEGY = "sync_strategy"
        private const val KEY_SYNC_OVERWRITE_PRIORITY = "sync_overwrite_priority"
        private const val KEY_IMPORT_STRATEGY = "import_strategy"
        private const val KEY_PREFIX_SYNC = "sync"
        private const val KEY_PREFIX_EXPORT = "export"
        private const val KEY_PREFIX_IMPORT = "import"
        private const val KEY_INCLUDE_SOURCES_SUFFIX = "include_sources"
        private const val KEY_INCLUDE_SUBSCRIPTIONS_SUFFIX = "include_subscriptions"
        private const val KEY_INCLUDE_SAVED_ARTICLES_SUFFIX = "include_saved_articles"
        private const val KEY_INCLUDE_SETTINGS_NO_API_SUFFIX = "include_settings_no_api"
        private const val KEY_INCLUDE_API_KEYS_SUFFIX = "include_api_keys"
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
    }
}

fun parseSyncConflictStrategy(rawValue: String?): SyncConflictStrategy {
    return runCatching { SyncConflictStrategy.valueOf(rawValue.orEmpty()) }
        .getOrDefault(SyncConflictStrategy.MERGE)
}

fun parseSyncOverwritePriority(rawValue: String?): SyncOverwritePriority {
    return runCatching { SyncOverwritePriority.valueOf(rawValue.orEmpty()) }
        .getOrDefault(SyncOverwritePriority.LOCAL)
}

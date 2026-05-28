package com.andrewwin.sumup.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.auth.FirebaseSettingsAuthService
import com.andrewwin.sumup.data.security.SecretEncryptionManager
import com.andrewwin.sumup.domain.repository.UserDataSyncRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject

class SettingsSyncService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authService: FirebaseSettingsAuthService,
    private val firestore: FirebaseFirestore,
    private val dispatcherProvider: com.andrewwin.sumup.domain.support.DispatcherProvider,
    private val preferencesStore: SettingsSyncPreferencesStore,
    private val backupPayloadService: SettingsBackupPayloadService,
    private val secretEncryptionManager: SecretEncryptionManager
) : UserDataSyncRepository {
    override suspend fun syncNow(selection: BackupSelection, state: SettingsSyncState): String = withContext(dispatcherProvider.io) {
        val uid = authService.currentUserId()
            ?: error(context.getString(R.string.settings_sync_sign_in_required))
        if (!state.isCloudSyncEnabled) return@withContext ""

        val docRef = firestore.collection(CLOUD_COLLECTION).document(uid)
        val remote = docRef.get().await()
        val remoteBackupJson = remote.getString("backup")
            ?.takeIf { it.isNotBlank() }
            ?.let(::JSONObject)
        val remoteUpdatedAt = remote.getLong("updatedAt") ?: 0L
        val mergeMode = state.syncStrategy == SyncConflictStrategy.MERGE
        val shouldApplyRemote = shouldApplyRemoteBeforePush(
            remoteExists = remote.exists(),
            remoteUpdatedAt = remoteUpdatedAt,
            lastSyncAt = state.lastSyncAt,
            strategy = state.syncStrategy,
            overwritePriority = state.syncOverwritePriority
        )
        if (shouldApplyRemote && remoteBackupJson != null) {
            backupPayloadService.applyBackupJson(remoteBackupJson, mergeMode, selection)
            backupPayloadService.consumeImportedSyncMetadata()?.let { metadata ->
                preferencesStore.updateSyncStrategy(metadata.strategy)
                preferencesStore.updateSyncOverwritePriority(metadata.overwritePriority)
                preferencesStore.updateImportStrategy(metadata.importStrategy)
            }
        }
        val localBackup = backupPayloadService.buildBackupJson(
            selection = selection,
            syncStrategy = state.syncStrategy,
            syncOverwritePriority = state.syncOverwritePriority,
            importStrategy = state.importStrategy
        )
        val localBackupJson = localBackup.toString()
        require(localBackupJson.isNotBlank()) { context.getString(R.string.settings_sync_json_build_failed) }
        val now = System.currentTimeMillis()
        docRef.set(mapOf("backup" to localBackupJson, "updatedAt" to now)).await()
        preferencesStore.updateLastSyncAt(now)
        context.getString(R.string.settings_sync_completed)
    }

    override suspend fun exportSettingsAndSources(uri: Uri, state: SettingsSyncState): String = withContext(dispatcherProvider.io) {
        val backupJson = backupPayloadService.buildBackupJson(
            selection = state.exportSelection,
            syncStrategy = state.syncStrategy,
            syncOverwritePriority = state.syncOverwritePriority,
            importStrategy = state.importStrategy
        ).toString()
        require(backupJson.isNotBlank()) { context.getString(R.string.settings_sync_json_build_failed) }
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
            writer.write(backupJson)
            writer.flush()
        } ?: error(context.getString(R.string.settings_export_open_stream_failed))
        context.getString(R.string.settings_export_completed)
    }

    override suspend fun importSettingsAndSources(
        uri: Uri,
        merge: Boolean,
        state: SettingsSyncState
    ): String = withContext(dispatcherProvider.io) {
        val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error(context.getString(R.string.settings_import_read_failed))
        require(content.isNotBlank()) { context.getString(R.string.settings_import_file_empty) }
        backupPayloadService.applyBackupJson(JSONObject(content), merge, state.importSelection)
        backupPayloadService.consumeImportedSyncMetadata()?.let { metadata ->
            preferencesStore.updateSyncStrategy(metadata.strategy)
            preferencesStore.updateSyncOverwritePriority(metadata.overwritePriority)
            preferencesStore.updateImportStrategy(metadata.importStrategy)
        }
        context.getString(R.string.settings_import_completed)
    }

    fun saveSyncPassphrase(passphrase: String): String {
        val normalized = passphrase.trim()
        require(normalized.length >= MIN_SYNC_PASSPHRASE_LENGTH) {
            context.getString(R.string.settings_sync_passphrase_too_short)
        }
        secretEncryptionManager.setSyncPassphrase(normalized)
        return context.getString(R.string.settings_sync_passphrase_saved)
    }

    fun clearSyncPassphrase(): String {
        secretEncryptionManager.clearSyncPassphrase()
        preferencesStore.persistSyncSelection(preferencesStore.loadState().syncSelection.copy(includeApiKeys = false))
        preferencesStore.persistExportSelection(preferencesStore.loadState().exportSelection.copy(includeApiKeys = false))
        preferencesStore.persistImportSelection(preferencesStore.loadState().importSelection.copy(includeApiKeys = false))
        return context.getString(R.string.settings_sync_passphrase_cleared)
    }

    fun isSyncPassphraseMatchingCurrent(passphrase: String): Boolean {
        val normalized = passphrase.trim()
        val currentPassphrase = secretEncryptionManager.getSyncPassphraseOrNull()?.trim().orEmpty()
        return normalized.isNotBlank() && normalized == currentPassphrase
    }

    fun syncErrorMessage(throwable: Throwable): String {
        return when ((throwable as? FirebaseFirestoreException)?.code) {
            FirebaseFirestoreException.Code.UNAVAILABLE -> {
                if (hasInternetConnection()) {
                    context.getString(R.string.settings_sync_error_firebase_unavailable_online)
                } else {
                    context.getString(R.string.settings_sync_error_no_internet)
                }
            }
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> context.getString(R.string.settings_sync_error_permission_denied)
            else -> throwable.localizedMessage ?: context.getString(R.string.settings_sync_error_generic)
        }
    }

    private fun hasInternetConnection(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        private const val CLOUD_COLLECTION = "user_sync_backups"
        private const val MIN_SYNC_PASSPHRASE_LENGTH = 8
    }
}

fun shouldApplyRemoteBeforePush(
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

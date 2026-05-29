package com.andrewwin.sumup.domain.entities.sync

data class UserDataSyncState(
    val isCloudSyncEnabled: Boolean = false,
    val syncIntervalHours: Int = DEFAULT_SYNC_INTERVAL_HOURS,
    val syncSelection: BackupSelection = BackupSelection(),
    val exportSelection: BackupSelection = BackupSelection(),
    val importSelection: BackupSelection = BackupSelection(),
    val syncStrategy: SyncConflictStrategy = SyncConflictStrategy.MERGE,
    val syncOverwritePriority: SyncOverwritePriority = SyncOverwritePriority.LOCAL,
    val importStrategy: SyncConflictStrategy = SyncConflictStrategy.MERGE,
    val lastSyncAt: Long = 0L,
    val hasSyncPassphrase: Boolean = false
) {
    companion object {
        const val DEFAULT_SYNC_INTERVAL_HOURS = 24
    }
}

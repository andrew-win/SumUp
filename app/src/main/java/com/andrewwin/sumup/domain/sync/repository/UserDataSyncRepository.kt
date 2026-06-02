package com.andrewwin.sumup.domain.sync.repository

import android.net.Uri
import com.andrewwin.sumup.domain.sync.model.BackupSelection
import com.andrewwin.sumup.domain.sync.model.UserDataSyncState

interface UserDataSyncRepository {
    suspend fun syncNow(selection: BackupSelection, state: UserDataSyncState): String
    suspend fun exportSettingsAndSources(uri: Uri, state: UserDataSyncState): String
    suspend fun importSettingsAndSources(uri: Uri, merge: Boolean, state: UserDataSyncState): String
}
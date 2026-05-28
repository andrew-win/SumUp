package com.andrewwin.sumup.domain.usecase.sync

import android.net.Uri
import com.andrewwin.sumup.domain.repository.UserDataSyncRepository
import com.andrewwin.sumup.domain.sync.UserDataSyncState
import javax.inject.Inject

class ExportBackupUseCase @Inject constructor(
    private val userDataSyncRepository: UserDataSyncRepository
) {
    suspend operator fun invoke(uri: Uri, state: UserDataSyncState): String =
        userDataSyncRepository.exportSettingsAndSources(uri, state)
}

package com.andrewwin.sumup.domain.sync.usecase

import android.net.Uri
import com.andrewwin.sumup.domain.sync.repository.UserDataSyncRepository
import com.andrewwin.sumup.domain.sync.model.UserDataSyncState
import javax.inject.Inject

class ExportBackupUseCase @Inject constructor(
    private val userDataSyncRepository: UserDataSyncRepository
) {
    suspend operator fun invoke(uri: Uri, state: UserDataSyncState): String =
        userDataSyncRepository.exportSettingsAndSources(uri, state)
}

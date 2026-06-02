package com.andrewwin.sumup.domain.sync.usecase

import android.net.Uri
import com.andrewwin.sumup.domain.sync.repository.UserDataSyncRepository
import com.andrewwin.sumup.domain.sync.model.UserDataSyncState
import javax.inject.Inject

class ImportBackupUseCase @Inject constructor(
    private val userDataSyncRepository: UserDataSyncRepository
) {
    suspend operator fun invoke(uri: Uri, merge: Boolean, state: UserDataSyncState): String =
        userDataSyncRepository.importSettingsAndSources(uri, merge, state)
}

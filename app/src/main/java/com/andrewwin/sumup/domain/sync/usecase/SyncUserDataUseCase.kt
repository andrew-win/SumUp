package com.andrewwin.sumup.domain.sync.usecase

import com.andrewwin.sumup.domain.sync.repository.UserDataSyncRepository
import com.andrewwin.sumup.domain.sync.model.BackupSelection
import com.andrewwin.sumup.domain.sync.model.UserDataSyncState
import javax.inject.Inject

class SyncUserDataUseCase @Inject constructor(
    private val userDataSyncRepository: UserDataSyncRepository
) {
    suspend operator fun invoke(selection: BackupSelection, state: UserDataSyncState): String =
        userDataSyncRepository.syncNow(selection, state)
}

package com.andrewwin.sumup.domain.usecase.sync

import com.andrewwin.sumup.domain.repository.UserDataSyncRepository
import com.andrewwin.sumup.domain.sync.BackupSelection
import com.andrewwin.sumup.domain.sync.UserDataSyncState
import javax.inject.Inject

class SyncUserDataUseCase @Inject constructor(
    private val userDataSyncRepository: UserDataSyncRepository
) {
    suspend operator fun invoke(selection: BackupSelection, state: UserDataSyncState): String =
        userDataSyncRepository.syncNow(selection, state)
}

package com.andrewwin.sumup.worker

import androidx.work.ListenableWorker
import com.andrewwin.sumup.data.repository.PublicSubscriptionsSyncManager
import com.andrewwin.sumup.domain.repository.SourceRepository
import javax.inject.Inject

class PublicSubscriptionsSyncWorkerHandler @Inject constructor(
    private val publicSubscriptionsSyncManager: PublicSubscriptionsSyncManager,
    private val sourceRepository: SourceRepository
) {
    suspend fun execute(): ListenableWorker.Result {
        return if (publicSubscriptionsSyncManager.sync(force = true)) {
            sourceRepository.syncSubscribedImportedGroups(publicSubscriptionsSyncManager.getCachedGroups())
            ListenableWorker.Result.success()
        } else {
            ListenableWorker.Result.retry()
        }
    }
}

package com.andrewwin.sumup.worker

import androidx.work.ListenableWorker
import com.andrewwin.sumup.data.repository.PublicSubscriptionsSyncManager
import javax.inject.Inject

class PublicSubscriptionsSyncWorkerHandler @Inject constructor(
    private val publicSubscriptionsSyncManager: PublicSubscriptionsSyncManager
) {
    suspend fun execute(): ListenableWorker.Result =
        if (publicSubscriptionsSyncManager.sync(force = true)) {
            ListenableWorker.Result.success()
        } else {
            ListenableWorker.Result.retry()
        }
}

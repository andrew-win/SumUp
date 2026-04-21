package com.andrewwin.sumup.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class PublicSubscriptionsSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val handler: PublicSubscriptionsSyncWorkerHandler
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = handler.execute()
}

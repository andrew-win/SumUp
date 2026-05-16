package com.andrewwin.sumup.worker

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object SummaryWorkRequestFactory {
    fun create(kind: ScheduledSummaryWorkKind, scheduledAt: Long): OneTimeWorkRequest {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        return OneTimeWorkRequestBuilder<SummaryWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    WorkerContracts.KEY_SCHEDULED_SUMMARY_WORK_KIND to kind.name,
                    WorkerContracts.KEY_SCHEDULED_SUMMARY_AT to scheduledAt
                )
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_DELAY_MINUTES, TimeUnit.MINUTES)
            .build()
    }

    private const val BACKOFF_DELAY_MINUTES = 10L
}

enum class ScheduledSummaryWorkKind {
    PREPARE,
    DELIVER
}

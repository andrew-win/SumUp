package com.andrewwin.sumup.worker.summary

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import com.andrewwin.sumup.worker.summary.model.ScheduledSummaryWorkKind
import java.util.concurrent.TimeUnit

object SummaryWorkRequestFactory {
    private const val SCHEDULED_SUMMARY_WORK_TAG = "scheduled_summary_work"

    fun create(kind: ScheduledSummaryWorkKind, scheduledAt: Long): OneTimeWorkRequest {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        return OneTimeWorkRequestBuilder<SummaryWorker>()
            .setConstraints(constraints)
            .addTag(SCHEDULED_SUMMARY_WORK_TAG)
            .setInputData(
                workDataOf(
                    SummaryWorkerHandler.KEY_SCHEDULED_SUMMARY_WORK_KIND to kind.name,
                    SummaryWorkerHandler.KEY_SCHEDULED_SUMMARY_AT to scheduledAt
                )
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_DELAY_MINUTES, TimeUnit.MINUTES)
            .build()
    }

    private const val BACKOFF_DELAY_MINUTES = 10L
}

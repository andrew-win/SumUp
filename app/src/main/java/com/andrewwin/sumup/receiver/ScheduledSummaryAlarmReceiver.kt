package com.andrewwin.sumup.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.andrewwin.sumup.worker.summary.model.ScheduledSummaryWorkKind
import com.andrewwin.sumup.worker.summary.SummaryWorkRequestFactory
import com.andrewwin.sumup.worker.summary.SummaryConstants
import com.andrewwin.sumup.worker.summary.SummaryWorkerHandler

class ScheduledSummaryAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = when (intent.action) {
            SummaryConstants.ACTION_PREPARE_SCHEDULED_SUMMARY -> ScheduledSummaryWorkKind.PREPARE
            SummaryConstants.ACTION_DELIVER_SCHEDULED_SUMMARY -> ScheduledSummaryWorkKind.DELIVER
            else -> return
        }
        val scheduledAt = intent.getLongExtra(SummaryWorkerHandler.KEY_SCHEDULED_SUMMARY_AT, 0L)
            .takeIf { it > 0L }
            ?: System.currentTimeMillis()
        val baseWorkName = when (kind) {
            ScheduledSummaryWorkKind.PREPARE -> SummaryConstants.PREPARE_SCHEDULED_SUMMARY_WORK_NAME
            ScheduledSummaryWorkKind.DELIVER -> SummaryConstants.DELIVER_SCHEDULED_SUMMARY_WORK_NAME
        }

        WorkManager.getInstance(context).enqueueUniqueWork(
            SummaryConstants.scheduledSummaryWorkName(baseWorkName, scheduledAt),
            ExistingWorkPolicy.REPLACE,
            SummaryWorkRequestFactory.create(kind, scheduledAt)
        )
    }
}

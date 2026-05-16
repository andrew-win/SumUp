package com.andrewwin.sumup.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.andrewwin.sumup.worker.ScheduledSummaryWorkKind
import com.andrewwin.sumup.worker.SummaryWorkRequestFactory
import com.andrewwin.sumup.worker.WorkerContracts

class ScheduledSummaryAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = when (intent.action) {
            WorkerContracts.ACTION_PREPARE_SCHEDULED_SUMMARY -> ScheduledSummaryWorkKind.PREPARE
            WorkerContracts.ACTION_DELIVER_SCHEDULED_SUMMARY -> ScheduledSummaryWorkKind.DELIVER
            else -> return
        }
        val scheduledAt = intent.getLongExtra(WorkerContracts.KEY_SCHEDULED_SUMMARY_AT, 0L)
            .takeIf { it > 0L }
            ?: System.currentTimeMillis()
        val baseWorkName = when (kind) {
            ScheduledSummaryWorkKind.PREPARE -> WorkerContracts.PREPARE_SCHEDULED_SUMMARY_WORK_NAME
            ScheduledSummaryWorkKind.DELIVER -> WorkerContracts.DELIVER_SCHEDULED_SUMMARY_WORK_NAME
        }

        WorkManager.getInstance(context).enqueueUniqueWork(
            WorkerContracts.scheduledSummaryWorkName(baseWorkName, scheduledAt),
            ExistingWorkPolicy.REPLACE,
            SummaryWorkRequestFactory.create(kind, scheduledAt)
        )
    }
}

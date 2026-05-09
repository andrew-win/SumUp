package com.andrewwin.sumup.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.andrewwin.sumup.worker.SummaryWorkRequestFactory
import com.andrewwin.sumup.worker.WorkerContracts

class ScheduledSummaryAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WorkerContracts.ACTION_RUN_SCHEDULED_SUMMARY) return

        WorkManager.getInstance(context).enqueueUniqueWork(
            WorkerContracts.SCHEDULED_SUMMARY_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            SummaryWorkRequestFactory.create()
        )
    }
}

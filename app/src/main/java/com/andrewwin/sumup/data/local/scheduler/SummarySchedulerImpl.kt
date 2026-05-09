package com.andrewwin.sumup.data.local.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.andrewwin.sumup.domain.repository.SummaryScheduler
import com.andrewwin.sumup.receiver.ScheduledSummaryAlarmReceiver
import com.andrewwin.sumup.worker.SummaryWorkRequestFactory
import com.andrewwin.sumup.worker.WorkerContracts
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class SummarySchedulerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val workManager: WorkManager,
    private val timeCalculator: ScheduledSummaryTimeCalculator
) : SummaryScheduler {

    override fun schedule(hour: Int, minute: Int) {
        val triggerAtMillis = timeCalculator.nextTriggerAtMillis(hour, minute)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = createPendingIntent()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    override fun runNow() {
        workManager.enqueueUniqueWork(
            WorkerContracts.SCHEDULED_SUMMARY_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            SummaryWorkRequestFactory.create()
        )
    }

    override fun cancel() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        findPendingIntent()?.let(alarmManager::cancel)
        workManager.cancelUniqueWork(WorkerContracts.SCHEDULED_SUMMARY_WORK_NAME)
    }

    private fun createPendingIntent(): PendingIntent {
        val intent = createAlarmIntent()
        return PendingIntent.getBroadcast(
            context,
            WorkerContracts.SCHEDULED_SUMMARY_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun findPendingIntent(): PendingIntent? {
        return PendingIntent.getBroadcast(
            context,
            WorkerContracts.SCHEDULED_SUMMARY_ALARM_REQUEST_CODE,
            createAlarmIntent(),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createAlarmIntent(): Intent {
        return Intent(context, ScheduledSummaryAlarmReceiver::class.java).apply {
            action = WorkerContracts.ACTION_RUN_SCHEDULED_SUMMARY
        }
    }
}







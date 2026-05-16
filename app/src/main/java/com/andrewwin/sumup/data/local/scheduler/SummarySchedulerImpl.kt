package com.andrewwin.sumup.data.local.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.andrewwin.sumup.data.local.entities.ScheduledSummaryTime
import com.andrewwin.sumup.data.local.entities.normalizedScheduledSummaryTimes
import com.andrewwin.sumup.domain.repository.SummaryScheduler
import com.andrewwin.sumup.receiver.ScheduledSummaryAlarmReceiver
import com.andrewwin.sumup.worker.ScheduledSummaryWorkKind
import com.andrewwin.sumup.worker.SummaryWorkRequestFactory
import com.andrewwin.sumup.worker.WorkerContracts
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class SummarySchedulerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val workManager: WorkManager,
    private val timeCalculator: ScheduledSummaryTimeCalculator
) : SummaryScheduler {

    override fun schedule(times: List<ScheduledSummaryTime>) {
        cancelAlarms()
        times.normalizedScheduledSummaryTimes().forEach { time ->
            val scheduledAtMillis = timeCalculator.nextTriggerAtMillis(time.hour, time.minute)
            scheduleAlarm(
                triggerAtMillis = timeCalculator.preparationTriggerAtMillis(scheduledAtMillis),
                pendingIntent = createPendingIntent(
                    action = WorkerContracts.ACTION_PREPARE_SCHEDULED_SUMMARY,
                    requestCode = alarmRequestCode(
                        baseRequestCode = WorkerContracts.SCHEDULED_SUMMARY_PREPARE_ALARM_REQUEST_CODE,
                        time = time
                    ),
                    scheduledAtMillis = scheduledAtMillis
                )
            )
            scheduleAlarm(
                triggerAtMillis = scheduledAtMillis,
                pendingIntent = createPendingIntent(
                    action = WorkerContracts.ACTION_DELIVER_SCHEDULED_SUMMARY,
                    requestCode = alarmRequestCode(
                        baseRequestCode = WorkerContracts.SCHEDULED_SUMMARY_DELIVER_ALARM_REQUEST_CODE,
                        time = time
                    ),
                    scheduledAtMillis = scheduledAtMillis
                )
            )
        }
    }

    private fun scheduleAlarm(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
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

    override fun prepareNow(scheduledAt: Long) {
        workManager.enqueueUniqueWork(
            WorkerContracts.scheduledSummaryWorkName(
                WorkerContracts.PREPARE_SCHEDULED_SUMMARY_WORK_NAME,
                scheduledAt
            ),
            ExistingWorkPolicy.KEEP,
            SummaryWorkRequestFactory.create(ScheduledSummaryWorkKind.PREPARE, scheduledAt)
        )
    }

    override fun deliverNow(scheduledAt: Long) {
        workManager.enqueueUniqueWork(
            WorkerContracts.scheduledSummaryWorkName(
                WorkerContracts.DELIVER_SCHEDULED_SUMMARY_WORK_NAME,
                scheduledAt
            ),
            ExistingWorkPolicy.REPLACE,
            SummaryWorkRequestFactory.create(ScheduledSummaryWorkKind.DELIVER, scheduledAt)
        )
    }

    override fun cancel() {
        cancelAlarms()
        workManager.cancelAllWorkByTag(WorkerContracts.SCHEDULED_SUMMARY_WORK_TAG)
        workManager.cancelUniqueWork(WorkerContracts.PREPARE_SCHEDULED_SUMMARY_WORK_NAME)
        workManager.cancelUniqueWork(WorkerContracts.DELIVER_SCHEDULED_SUMMARY_WORK_NAME)
    }

    private fun cancelAlarms() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (hour in 0..23) {
            for (minute in 0..59) {
                val time = ScheduledSummaryTime(hour, minute)
                findPendingIntent(
                    action = WorkerContracts.ACTION_PREPARE_SCHEDULED_SUMMARY,
                    requestCode = alarmRequestCode(WorkerContracts.SCHEDULED_SUMMARY_PREPARE_ALARM_REQUEST_CODE, time)
                )?.let(alarmManager::cancel)
                findPendingIntent(
                    action = WorkerContracts.ACTION_DELIVER_SCHEDULED_SUMMARY,
                    requestCode = alarmRequestCode(WorkerContracts.SCHEDULED_SUMMARY_DELIVER_ALARM_REQUEST_CODE, time)
                )?.let(alarmManager::cancel)
            }
        }
        findPendingIntent(
            action = WorkerContracts.ACTION_DELIVER_SCHEDULED_SUMMARY,
            requestCode = WorkerContracts.LEGACY_SCHEDULED_SUMMARY_DELIVER_ALARM_REQUEST_CODE
        )?.let(alarmManager::cancel)
    }

    private fun createPendingIntent(action: String, requestCode: Int, scheduledAtMillis: Long): PendingIntent {
        val intent = createAlarmIntent(action).putExtra(WorkerContracts.KEY_SCHEDULED_SUMMARY_AT, scheduledAtMillis)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun findPendingIntent(action: String, requestCode: Int): PendingIntent? {
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            createAlarmIntent(action),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createAlarmIntent(action: String): Intent {
        return Intent(context, ScheduledSummaryAlarmReceiver::class.java).apply {
            this.action = action
        }
    }

    private fun alarmRequestCode(baseRequestCode: Int, time: ScheduledSummaryTime): Int {
        return baseRequestCode + time.hour * MINUTES_PER_HOUR + time.minute
    }

    private companion object {
        const val MINUTES_PER_HOUR = 60
    }
}







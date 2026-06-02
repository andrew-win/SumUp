package com.andrewwin.sumup.data.local.scheduler

import com.andrewwin.sumup.worker.summary.SummaryConstants
import java.util.Calendar

class ScheduledSummaryTimeCalculator {

    fun nextTriggerAtMillis(hour: Int, minute: Int, nowMillis: Long = System.currentTimeMillis()): Long {
        val triggerTodayMillis = triggerTodayAtMillis(hour, minute, nowMillis)
        if (triggerTodayMillis > nowMillis) {
            return triggerTodayMillis
        }

        return Calendar.getInstance().apply {
            timeInMillis = triggerTodayMillis
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
    }

    fun wasTodayTriggerMissed(
        hour: Int,
        minute: Int,
        lastRunTimestamp: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val triggerToday = triggerTodayAtMillis(hour, minute, nowMillis)

        return nowMillis >= triggerToday && lastRunTimestamp < triggerToday
    }

    fun triggerTodayAtMillis(hour: Int, minute: Int, nowMillis: Long = System.currentTimeMillis()): Long =
        Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    fun preparationTriggerAtMillis(
        scheduledAtMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val preparationAtMillis = scheduledAtMillis -
            SummaryConstants.SCHEDULED_SUMMARY_PREPARATION_LEAD_TIME_MINUTES * 60L * 1000L
        return preparationAtMillis.coerceAtLeast(nowMillis)
    }
}

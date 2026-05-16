package com.andrewwin.sumup.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.andrewwin.sumup.data.local.scheduler.ScheduledSummaryTimeCalculator
import com.andrewwin.sumup.domain.repository.SummaryScheduler
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ScheduledSummaryRescheduleReceiver : BroadcastReceiver() {

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var summaryScheduler: SummaryScheduler

    @Inject
    lateinit var timeCalculator: ScheduledSummaryTimeCalculator

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                val prefs = userPreferencesRepository.preferences.first()
                if (prefs.isScheduledSummaryEnabled) {
                    val scheduledTimes = prefs.scheduledSummaryTimeList
                    scheduledTimes.forEach { time ->
                        if (
                            timeCalculator.wasTodayTriggerMissed(
                                time.hour,
                                time.minute,
                                prefs.lastWorkRunTimestamp
                            )
                        ) {
                            summaryScheduler.deliverNow(
                                timeCalculator.triggerTodayAtMillis(time.hour, time.minute)
                            )
                        }
                    }
                    summaryScheduler.schedule(scheduledTimes)
                } else {
                    summaryScheduler.cancel()
                }
            }
            pendingResult.finish()
        }
    }
}

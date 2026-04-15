package com.andrewwin.sumup.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.usecase.settings.ScheduleSummaryUseCase
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
    lateinit var scheduleSummaryUseCase: ScheduleSummaryUseCase

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                val prefs = userPreferencesRepository.preferences.first()
                scheduleSummaryUseCase(
                    prefs.isScheduledSummaryEnabled,
                    prefs.scheduledHour,
                    prefs.scheduledMinute
                )
            }
            pendingResult.finish()
        }
    }
}

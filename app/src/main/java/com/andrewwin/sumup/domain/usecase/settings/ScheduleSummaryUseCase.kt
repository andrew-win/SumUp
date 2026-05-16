package com.andrewwin.sumup.domain.usecase.settings

import com.andrewwin.sumup.data.local.entities.ScheduledSummaryTime
import com.andrewwin.sumup.data.local.entities.normalizedScheduledSummaryTimes
import com.andrewwin.sumup.data.local.entities.toScheduledSummaryTimesStorageValue
import com.andrewwin.sumup.domain.repository.SummaryScheduler
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ScheduleSummaryUseCase @Inject constructor(
    private val summaryScheduler: SummaryScheduler,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    suspend operator fun invoke(enabled: Boolean, times: List<ScheduledSummaryTime>) {
        val currentPrefs = userPreferencesRepository.preferences.first()
        val normalizedTimes = times.normalizedScheduledSummaryTimes().ifEmpty {
            listOf(ScheduledSummaryTime.DEFAULT)
        }
        val firstTime = normalizedTimes.first()
        userPreferencesRepository.updatePreferences(
            currentPrefs.copy(
                isScheduledSummaryEnabled = enabled,
                scheduledHour = firstTime.hour,
                scheduledMinute = firstTime.minute,
                scheduledSummaryTimes = normalizedTimes.toScheduledSummaryTimesStorageValue()
            )
        )
        if (enabled) {
            summaryScheduler.schedule(normalizedTimes)
        } else {
            summaryScheduler.cancel()
        }
    }
}










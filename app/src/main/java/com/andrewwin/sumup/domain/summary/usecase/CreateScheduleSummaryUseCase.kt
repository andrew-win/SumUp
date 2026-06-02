package com.andrewwin.sumup.domain.summary.usecase

import com.andrewwin.sumup.domain.summary.repository.SummaryScheduler
import com.andrewwin.sumup.domain.settings.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.settings.model.ScheduledSummaryTime
import com.andrewwin.sumup.domain.settings.model.normalizedScheduledSummaryTimes
import com.andrewwin.sumup.domain.settings.model.toScheduledSummaryTimesStorageValue
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class CreateScheduleSummaryUseCase @Inject constructor(
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










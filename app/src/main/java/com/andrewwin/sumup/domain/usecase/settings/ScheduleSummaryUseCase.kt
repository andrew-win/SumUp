package com.andrewwin.sumup.domain.usecase.settings

import com.andrewwin.sumup.domain.repository.SummaryScheduler
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ScheduleSummaryUseCase @Inject constructor(
    private val summaryScheduler: SummaryScheduler,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    suspend operator fun invoke(enabled: Boolean, hour: Int, minute: Int) {
        val currentPrefs = userPreferencesRepository.preferences.first()
        userPreferencesRepository.updatePreferences(
            currentPrefs.copy(
                isScheduledSummaryEnabled = enabled,
                scheduledHour = hour,
                scheduledMinute = minute
            )
        )
        if (enabled) {
            summaryScheduler.schedule(hour, minute)
        } else {
            summaryScheduler.cancel()
        }
    }
}










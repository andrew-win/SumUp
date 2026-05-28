package com.andrewwin.sumup.domain.settings.actions

import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class UpdateCustomSummaryPromptEnabledAction @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) {
    suspend operator fun invoke(enabled: Boolean) {
        val current = userPreferencesRepository.preferences.first()
        userPreferencesRepository.updatePreferences(current.copy(isCustomSummaryPromptEnabled = enabled))
    }
}










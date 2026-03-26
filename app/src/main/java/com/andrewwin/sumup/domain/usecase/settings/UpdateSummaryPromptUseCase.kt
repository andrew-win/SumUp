package com.andrewwin.sumup.domain.usecase.settings

import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class UpdateSummaryPromptUseCase @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) {
    suspend operator fun invoke(prompt: String) {
        val current = userPreferencesRepository.preferences.first()
        userPreferencesRepository.updatePreferences(current.copy(summaryPrompt = prompt))
    }
}










package com.andrewwin.sumup.domain.settings.repository

import com.andrewwin.sumup.domain.settings.model.UserSettings
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val preferences: Flow<UserSettings>
    suspend fun updatePreferences(preferences: UserSettings)
}







package com.andrewwin.sumup.domain.repository

import com.andrewwin.sumup.domain.settings.UserSettings
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val preferences: Flow<UserSettings>
    suspend fun updatePreferences(preferences: UserSettings)
}







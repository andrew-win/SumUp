package com.andrewwin.sumup.domain.repository

import com.andrewwin.sumup.data.local.entities.UserPreferences
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val preferences: Flow<UserPreferences>
    suspend fun updatePreferences(preferences: UserPreferences)
}

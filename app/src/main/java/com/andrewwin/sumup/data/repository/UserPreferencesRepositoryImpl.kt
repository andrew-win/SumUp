package com.andrewwin.sumup.data.repository

import com.andrewwin.sumup.data.mappers.toDomainModel
import com.andrewwin.sumup.data.mappers.toRoomEntity
import com.andrewwin.sumup.data.local.dao.UserPreferencesDao
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.entities.settings.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

class UserPreferencesRepositoryImpl @Inject constructor(
    private val userPreferencesDao: UserPreferencesDao
) : UserPreferencesRepository {

    override val preferences: Flow<UserSettings> = userPreferencesDao
        .getUserPreferences()
        .map { it?.toDomainModel() ?: UserSettings() }
        .onEach { com.andrewwin.sumup.domain.summary.SummaryLimits.currentPrefs = it }

    override suspend fun updatePreferences(preferences: UserSettings) {
        userPreferencesDao.insertUserPreferences(preferences.toRoomEntity())
    }
}





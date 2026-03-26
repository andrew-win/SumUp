package com.andrewwin.sumup.data.repository

import com.andrewwin.sumup.data.local.dao.UserPreferencesDao
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class UserPreferencesRepositoryImpl @Inject constructor(
    private val userPreferencesDao: UserPreferencesDao
) : UserPreferencesRepository {

    override val preferences: Flow<UserPreferences> = userPreferencesDao
        .getUserPreferences()
        .map { it ?: UserPreferences() }

    override suspend fun updatePreferences(preferences: UserPreferences) {
        userPreferencesDao.insertUserPreferences(preferences)
    }
}







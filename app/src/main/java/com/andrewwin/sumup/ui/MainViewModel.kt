package com.andrewwin.sumup.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andrewwin.sumup.domain.feed.usecase.FeedRefreshCoordinator
import com.andrewwin.sumup.domain.settings.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.settings.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext context: Context,
    userPreferencesRepository: UserPreferencesRepository,
    feedRefreshCoordinator: FeedRefreshCoordinator
) : ViewModel() {
    private val onboardingPrefs = context.getSharedPreferences(ONBOARDING_PREFS_NAME, Context.MODE_PRIVATE)
    private val _shouldShowOnboarding = MutableStateFlow(
        !onboardingPrefs.getBoolean(KEY_ONBOARDING_SEEN, false)
    )

    val userPreferences: StateFlow<UserSettings> = userPreferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())
    val shouldShowOnboarding: StateFlow<Boolean> = _shouldShowOnboarding.asStateFlow()

    init {
        viewModelScope.launch {
            feedRefreshCoordinator.refreshOnAppStart()
        }
    }

    fun markOnboardingSeen() {
        onboardingPrefs.edit()
            .putBoolean(KEY_ONBOARDING_SEEN, true)
            .apply()
        _shouldShowOnboarding.value = false
    }

    private companion object {
        private const val ONBOARDING_PREFS_NAME = "onboarding_preferences"
        private const val KEY_ONBOARDING_SEEN = "onboarding_seen"
    }
}







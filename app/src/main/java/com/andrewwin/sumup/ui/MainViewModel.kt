package com.andrewwin.sumup.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andrewwin.sumup.domain.feed.usecase.FeedRefreshCoordinator
import com.andrewwin.sumup.domain.settings.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.settings.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    userPreferencesRepository: UserPreferencesRepository,
    feedRefreshCoordinator: FeedRefreshCoordinator
) : ViewModel() {
    val userPreferences: StateFlow<UserSettings> = userPreferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    init {
        viewModelScope.launch {
            feedRefreshCoordinator.refreshOnAppStart()
        }
    }
}







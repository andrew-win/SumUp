package com.andrewwin.sumup.ui.screen.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andrewwin.sumup.data.local.dao.GroupWithSources
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.local.entities.SourceGroup
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.domain.repository.SourceRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.usecase.settings.ManageModelUseCase
import com.andrewwin.sumup.domain.usecase.sources.GetSuggestedThemesUseCase
import com.andrewwin.sumup.domain.usecase.sources.SuggestedTheme
import com.andrewwin.sumup.domain.usecase.sources.ThemeSuggestion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SourcesViewModel @Inject constructor(
    private val repository: SourceRepository,
    private val getSuggestedThemesUseCase: GetSuggestedThemesUseCase,
    private val manageModelUseCase: ManageModelUseCase,
    userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<List<GroupWithSources>> = repository.groupsWithSources
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _suggestedThemes = MutableStateFlow<List<ThemeSuggestion>>(emptyList())
    val suggestedThemes: StateFlow<List<ThemeSuggestion>> = _suggestedThemes.asStateFlow()

    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()
    val isRecommendationsEnabled: StateFlow<Boolean> = userPreferencesRepository.preferences
        .map { it.isRecommendationsEnabled }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )
    private var suggestedThemesJob: Job? = null

    init {
        checkModelStatus()
        viewModelScope.launch {
            isRecommendationsEnabled.collect { enabled ->
                if (!enabled) {
                    _suggestedThemes.value = emptyList()
                } else {
                    loadSuggestedThemes()
                }
            }
        }
    }

    private fun checkModelStatus() {
        _isModelLoaded.value = manageModelUseCase.isModelExists()
    }

    fun loadSuggestedThemes() {
        suggestedThemesJob?.cancel()
        suggestedThemesJob = viewModelScope.launch {
            getSuggestedThemesUseCase()
                .collect { themes ->
                    _suggestedThemes.value = themes
                }
        }
    }

    fun toggleThemeSubscription(suggestion: ThemeSuggestion, isSubscribed: Boolean) {
        _suggestedThemes.update { themes ->
            themes.map { current ->
                if (current.theme == suggestion.theme) current.copy(isSubscribed = isSubscribed) else current
            }
        }

        viewModelScope.launch {
            if (isSubscribed) {
                val groupsSnapshot = repository.groupsWithSources.first()
                val themeGroup = groupsSnapshot.firstOrNull {
                    it.group.name.equals(suggestion.theme.title, ignoreCase = true)
                }?.group
                val groupId = when {
                    themeGroup != null -> themeGroup.id
                    else -> {
                        repository.addGroup(suggestion.theme.title)
                        repository.groupsWithSources.first()
                            .firstOrNull { it.group.name.equals(suggestion.theme.title, ignoreCase = true) }
                            ?.group
                            ?.id
                    }
                }
                if (groupId != null) {
                    suggestion.theme.sources.forEachIndexed { index, source ->
                        repository.addSource(
                            groupId = groupId,
                            name = "${suggestion.theme.title} #${index + 1}",
                            url = source.url,
                            type = source.type
                        )
                    }
                }
            } else {
                val groupsWithSources = repository.groupsWithSources.first()
                val themeGroupWithSources = groupsWithSources.firstOrNull {
                    it.group.name.equals(suggestion.theme.title, ignoreCase = true)
                }
                if (themeGroupWithSources != null) {
                    themeGroupWithSources.sources.forEach { repository.deleteSource(it) }
                    repository.deleteGroup(themeGroupWithSources.group)
                } else {
                    suggestion.theme.sources.forEach { ts ->
                        groupsWithSources.flatMap { it.sources }
                            .firstOrNull { it.url == ts.url }
                            ?.let { repository.deleteSource(it) }
                    }
                }
            }
            loadSuggestedThemes()
        }
    }

    fun addGroup(name: String) {
        viewModelScope.launch {
            repository.addGroup(name)
        }
    }

    fun updateGroup(group: SourceGroup) {
        viewModelScope.launch {
            repository.updateGroup(group)
        }
    }

    fun deleteGroup(group: SourceGroup) {
        viewModelScope.launch {
            repository.deleteGroup(group)
            if (SuggestedTheme.entries.any { it.title.equals(group.name, ignoreCase = true) }) {
                _suggestedThemes.update { themes ->
                    themes.map { suggestion ->
                        if (suggestion.theme.title.equals(group.name, ignoreCase = true)) {
                            suggestion.copy(isSubscribed = false)
                        } else {
                            suggestion
                        }
                    }
                }
                loadSuggestedThemes()
            }
        }
    }

    fun addSource(groupId: Long, name: String, url: String, type: SourceType) {
        viewModelScope.launch {
            repository.addSource(groupId, name, url, type)
        }
    }

    fun addSource(
        groupId: Long,
        name: String,
        url: String,
        type: SourceType,
        titleSelector: String?,
        postLinkSelector: String?,
        descriptionSelector: String?,
        dateSelector: String?,
        useHeadlessBrowser: Boolean
    ) {
        viewModelScope.launch {
            repository.addSource(
                groupId = groupId,
                name = name,
                url = url,
                type = type,
                titleSelector = titleSelector,
                postLinkSelector = postLinkSelector,
                descriptionSelector = descriptionSelector,
                dateSelector = dateSelector,
                useHeadlessBrowser = useHeadlessBrowser
            )
        }
    }

    fun updateSource(source: Source) {
        viewModelScope.launch {
            repository.updateSource(source)
        }
    }

    fun deleteSource(source: Source) {
        viewModelScope.launch {
            repository.deleteSource(source)
        }
    }
    
    fun toggleGroup(group: SourceGroup, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.toggleGroup(group, isEnabled)
        }
    }
}








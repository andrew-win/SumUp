package com.andrewwin.sumup.ui.screens.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andrewwin.sumup.data.local.dao.GroupWithSources
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.local.entities.SourceGroup
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.domain.repository.SourceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import com.andrewwin.sumup.domain.usecase.sources.GetSuggestedThemesUseCase
import com.andrewwin.sumup.domain.usecase.sources.ThemeSuggestion
import com.andrewwin.sumup.domain.usecase.settings.ManageModelUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SourcesViewModel @Inject constructor(
    private val repository: SourceRepository,
    private val getSuggestedThemesUseCase: GetSuggestedThemesUseCase,
    private val manageModelUseCase: ManageModelUseCase
) : ViewModel() {

    val uiState: StateFlow<List<GroupWithSources>> = repository.groupsWithSources
        .map { groups ->
            val themeUrls = com.andrewwin.sumup.domain.usecase.sources.SuggestedTheme.entries.flatMap { it.sources.map { s -> s.url } }.toSet()
            groups.map { groupWithSources ->
                groupWithSources.copy(sources = groupWithSources.sources.filter { it.url !in themeUrls })
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _suggestedThemes = MutableStateFlow<List<ThemeSuggestion>>(emptyList())
    val suggestedThemes: StateFlow<List<ThemeSuggestion>> = _suggestedThemes.asStateFlow()

    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()

    private val _isLoadingThemes = MutableStateFlow(false)
    val isLoadingThemes: StateFlow<Boolean> = _isLoadingThemes.asStateFlow()

    init {
        _isModelLoaded.value = manageModelUseCase.isModelExists()
        loadSuggestedThemes()
    }

    fun loadSuggestedThemes() {
        viewModelScope.launch {
            _isLoadingThemes.value = true
            getSuggestedThemesUseCase().collect { themes ->
                _suggestedThemes.value = themes
            }
            _isLoadingThemes.value = false
        }
    }

    fun toggleThemeSubscription(suggestion: ThemeSuggestion, isSubscribed: Boolean) {
        viewModelScope.launch {
            if (isSubscribed) {
                // Add to first available group
                val firstGroup = repository.groupsWithSources.first().firstOrNull()?.group
                if (firstGroup != null) {
                    suggestion.theme.sources.forEach { source ->
                        repository.addSource(firstGroup.id, "${suggestion.theme.title} - ${source.url.takeLast(12)}", source.url, source.type)
                    }
                }
            } else {
                // Remove sources
                val groupsWithSources = repository.groupsWithSources.first()
                suggestion.theme.sources.forEach { ts ->
                    val sourceToRemove = groupsWithSources.flatMap { it.sources }.firstOrNull { it.url == ts.url }
                    if (sourceToRemove != null) {
                        repository.deleteSource(sourceToRemove)
                    }
                }
            }
            val updated = _suggestedThemes.value.map {
                if (it.theme == suggestion.theme) it.copy(isSubscribed = isSubscribed) else it
            }
            _suggestedThemes.value = updated
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
        }
    }

    fun addSource(groupId: Long, name: String, url: String, type: SourceType) {
        viewModelScope.launch {
            repository.addSource(groupId, name, url, type)
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

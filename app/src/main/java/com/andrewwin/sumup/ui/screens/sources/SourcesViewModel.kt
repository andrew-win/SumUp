package com.andrewwin.sumup.ui.screens.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andrewwin.sumup.data.local.dao.GroupWithSources
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.local.entities.SourceGroup
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.domain.repository.SourceRepository
import com.andrewwin.sumup.domain.usecase.settings.ManageModelUseCase
import com.andrewwin.sumup.domain.usecase.sources.GetSuggestedThemesUseCase
import com.andrewwin.sumup.domain.usecase.sources.SuggestedTheme
import com.andrewwin.sumup.domain.usecase.sources.ThemeSuggestion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
            val themeUrls = SuggestedTheme.entries.flatMap { it.sources.map { s -> s.url } }.toSet()
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

    init {
        checkModelStatus()
        loadSuggestedThemes()
    }

    private fun checkModelStatus() {
        _isModelLoaded.value = manageModelUseCase.isModelExists()
    }

    fun loadSuggestedThemes() {
        viewModelScope.launch {
            getSuggestedThemesUseCase()
                .collect { themes ->
                    _suggestedThemes.value = themes
                }
        }
    }

    fun toggleThemeSubscription(suggestion: ThemeSuggestion, isSubscribed: Boolean) {
        viewModelScope.launch {
            if (isSubscribed) {
                val firstGroup = repository.groupsWithSources.first().firstOrNull()?.group
                if (firstGroup != null) {
                    suggestion.theme.sources.forEach { source ->
                        repository.addSource(
                            groupId = firstGroup.id,
                            name = "${suggestion.theme.title} - ${source.url.takeLast(12)}",
                            url = source.url,
                            type = source.type
                        )
                    }
                }
            } else {
                val groupsWithSources = repository.groupsWithSources.first()
                suggestion.theme.sources.forEach { ts ->
                    groupsWithSources.flatMap { it.sources }
                        .firstOrNull { it.url == ts.url }
                        ?.let { repository.deleteSource(it) }
                }
            }
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

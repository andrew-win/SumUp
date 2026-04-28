package com.andrewwin.sumup.ui.screen.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andrewwin.sumup.data.local.dao.GroupWithSources
import com.andrewwin.sumup.data.local.entities.AppLanguage
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.local.entities.SourceGroup
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.domain.repository.ImportedSourceGroup
import com.andrewwin.sumup.domain.repository.SourceRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.usecase.feed.RefreshFeedUseCase
import com.andrewwin.sumup.domain.usecase.sources.GetSuggestedThemesUseCase
import com.andrewwin.sumup.domain.usecase.sources.ThemeSuggestion
import com.andrewwin.sumup.domain.usecase.settings.ManageModelUseCase
import com.andrewwin.sumup.data.repository.PublicSubscriptionsSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class FirebaseThemeSuggestion(
    val group: ImportedSourceGroup,
    val isSubscribed: Boolean,
    val isRecommended: Boolean
)

@HiltViewModel
class SourcesViewModel @Inject constructor(
    private val repository: SourceRepository,
    private val manageModelUseCase: ManageModelUseCase,
    private val publicSubscriptionsSyncManager: PublicSubscriptionsSyncManager,
    private val refreshFeedUseCase: RefreshFeedUseCase,
    private val getSuggestedThemesUseCase: GetSuggestedThemesUseCase,
    userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<List<GroupWithSources>> = repository.groupsWithSources
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _suggestedThemes = MutableStateFlow<List<FirebaseThemeSuggestion>>(emptyList())
    val suggestedThemes: StateFlow<List<FirebaseThemeSuggestion>> = _suggestedThemes.asStateFlow()
    private val pendingSubscriptionOverrides = mutableMapOf<String, Boolean>()

    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()
    private val _subscriptionsSyncFailed = MutableStateFlow(publicSubscriptionsSyncManager.hasSyncFailure())
    val subscriptionsSyncFailed: StateFlow<Boolean> = _subscriptionsSyncFailed.asStateFlow()
    private val _isRefreshingThemeRecommendations = MutableStateFlow(false)
    val isRefreshingThemeRecommendations: StateFlow<Boolean> = _isRefreshingThemeRecommendations.asStateFlow()
    val isRecommendationsEnabled: StateFlow<Boolean> = userPreferencesRepository.preferences
        .map { it.isRecommendationsEnabled }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )
    val appLanguage: StateFlow<AppLanguage> = userPreferencesRepository.preferences
        .map { it.appLanguage }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppLanguage.UK
        )
    private var suggestedThemesJob: Job? = null
    private val subscriptionMutex = Mutex()

    init {
        checkModelStatus()
        viewModelScope.launch {
            _subscriptionsSyncFailed.value = publicSubscriptionsSyncManager.hasSyncFailure()
            loadSuggestedThemes()
        }
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
            _subscriptionsSyncFailed.value = publicSubscriptionsSyncManager.hasSyncFailure()
            val suggestions = getSuggestedThemesUseCase(forceRefresh = false).first()
            _suggestedThemes.value = applyPendingOverrides(suggestions.toFirebaseThemeSuggestions())
        }
    }

    fun refreshSuggestedThemes(forceRefresh: Boolean) {
        suggestedThemesJob?.cancel()
        suggestedThemesJob = viewModelScope.launch {
            try {
                if (forceRefresh) {
                    publicSubscriptionsSyncManager.sync(force = true)
                } else {
                    publicSubscriptionsSyncManager.syncIfDue()
                }
                _subscriptionsSyncFailed.value = publicSubscriptionsSyncManager.hasSyncFailure()
                if (forceRefresh) {
                    _isRefreshingThemeRecommendations.value = true
                    try {
                        getSuggestedThemesUseCase(forceRefresh = true).collect { suggestions ->
                            _suggestedThemes.value = applyPendingOverrides(suggestions.toFirebaseThemeSuggestions())
                        }
                    } finally {
                        _isRefreshingThemeRecommendations.value = false
                    }
                } else {
                    val suggestions = getSuggestedThemesUseCase(forceRefresh = false).first()
                    _suggestedThemes.value = applyPendingOverrides(suggestions.toFirebaseThemeSuggestions())
                }
            } finally {
                if (!forceRefresh) {
                    _isRefreshingThemeRecommendations.value = false
                }
            }
        }
    }

    private fun applyPendingOverrides(themes: List<FirebaseThemeSuggestion>): List<FirebaseThemeSuggestion> {
        if (pendingSubscriptionOverrides.isEmpty()) return themes

        val matchedOverrides = mutableListOf<String>()
        val mapped = themes.map { suggestion ->
            val key = suggestion.group.id.trim().lowercase()
            val pending = pendingSubscriptionOverrides[key] ?: return@map suggestion
            if (suggestion.isSubscribed == pending) {
                matchedOverrides += key
                suggestion
            } else {
                suggestion.copy(isSubscribed = pending)
            }
        }
        matchedOverrides.forEach { pendingSubscriptionOverrides.remove(it) }
        return mapped
    }

    private fun List<ThemeSuggestion>.toFirebaseThemeSuggestions(): List<FirebaseThemeSuggestion> {
        return filter { it.theme.name.isNotBlank() }
            .map { suggestion ->
                FirebaseThemeSuggestion(
                    group = suggestion.theme,
                    isSubscribed = suggestion.isSubscribed,
                    isRecommended = suggestion.isRecommended
                )
            }
    }

    private fun setPendingOverride(themeTitle: String, isSubscribed: Boolean) {
        pendingSubscriptionOverrides[themeTitle.trim().lowercase()] = isSubscribed
    }

    private fun clearPendingOverride(themeTitle: String) {
        pendingSubscriptionOverrides.remove(themeTitle.trim().lowercase())
    }

    fun toggleThemeSubscription(suggestion: FirebaseThemeSuggestion, isSubscribed: Boolean) {
        setPendingOverride(suggestion.group.id, isSubscribed)
        _suggestedThemes.update { themes ->
            themes.map { current ->
                if (current.group.id.equals(suggestion.group.id, ignoreCase = true)) current.copy(isSubscribed = isSubscribed) else current
            }
        }

        viewModelScope.launch {
            val applied = runCatching {
                subscriptionMutex.withLock {
                    if (isSubscribed) {
                        val currentLanguage = appLanguage.value
                        val targetGroupName = suggestion.group.displayName(currentLanguage)
                        val groupsSnapshot = repository.groupsWithSources.first()
                        val themeGroup = groupsSnapshot.firstOrNull {
                            suggestion.group.sources.all { source ->
                                it.sources.any { existing -> existing.url.equals(source.url, ignoreCase = true) }
                            }
                        }?.group
                        val groupId = when {
                            themeGroup != null -> themeGroup.id
                            else -> {
                                repository.addGroup(targetGroupName)
                                repository.groupsWithSources.first()
                                    .firstOrNull { it.group.name.equals(targetGroupName, ignoreCase = true) }
                                    ?.group
                                    ?.id
                            }
                        }
                        if (groupId != null) {
                            suggestion.group.sources.forEachIndexed { index, source ->
                                repository.addSource(
                                    groupId = groupId,
                                    name = source.name.ifBlank { "${suggestion.group.displayName(currentLanguage)} #${index + 1}" },
                                    url = source.url,
                                    type = source.type,
                                    titleSelector = source.titleSelector,
                                    postLinkSelector = source.postLinkSelector,
                                    descriptionSelector = source.descriptionSelector,
                                    dateSelector = source.dateSelector,
                                    useHeadlessBrowser = source.useHeadlessBrowser,
                                    detectFooterPattern = false
                                )
                            }
                            refreshFeedUseCase()
                        }
                    } else {
                        val groupsWithSources = repository.groupsWithSources.first()
                        val themeGroupWithSources = groupsWithSources.firstOrNull {
                            suggestion.group.sources.all { source ->
                                it.sources.any { existing -> existing.url.equals(source.url, ignoreCase = true) }
                            }
                        }
                        if (themeGroupWithSources != null) {
                            themeGroupWithSources.sources.forEach { repository.deleteSource(it) }
                            repository.deleteGroup(themeGroupWithSources.group)
                        } else {
                            suggestion.group.sources.forEach { ts ->
                                groupsWithSources.flatMap { it.sources }
                                    .firstOrNull { it.url == ts.url }
                                    ?.let { repository.deleteSource(it) }
                            }
                        }
                    }
                }
            }.isSuccess

            if (!applied) {
                clearPendingOverride(suggestion.group.id)
                // Rollback optimistic UI state on failure to avoid stuck toggles.
                _suggestedThemes.update { themes ->
                    themes.map { current ->
                        if (current.group.id.equals(suggestion.group.id, ignoreCase = true)) {
                            current.copy(isSubscribed = !isSubscribed)
                        } else {
                            current
                        }
                    }
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
        deleteGroups(listOf(group))
    }

    fun deleteGroups(groups: List<SourceGroup>) {
        if (groups.isEmpty()) return
        viewModelScope.launch {
            val deletedGroupIds = groups.map { it.id }.toSet()
            val deletedGroupUrls = uiState.value
                .filter { it.group.id in deletedGroupIds }
                .flatMap { groupWithSources -> groupWithSources.sources }
                .map { it.url.trim().lowercase() }
                .toSet()

            groups.forEach { group ->
                repository.deleteGroup(group)
            }
            updateSuggestedThemesAfterDeletedUrls(deletedGroupUrls)
        }
    }

    private fun updateSuggestedThemesAfterDeletedUrls(deletedGroupUrls: Set<String>) {
        if (deletedGroupUrls.isEmpty()) return
        _suggestedThemes.update { themes ->
            themes.map { suggestion ->
                val suggestionUrls = suggestion.group.sources
                    .map { it.url.trim().lowercase() }
                    .toSet()
                if (suggestionUrls.isNotEmpty() && suggestionUrls.all { it in deletedGroupUrls }) {
                    clearPendingOverride(suggestion.group.id)
                    suggestion.copy(isSubscribed = false)
                } else {
                    suggestion
                }
            }
        }
    }

    fun addSource(groupId: Long, name: String, url: String, type: SourceType) {
        viewModelScope.launch {
            repository.addSource(groupId, name, url, type)
            refreshFeedUseCase()
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
            refreshFeedUseCase()
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








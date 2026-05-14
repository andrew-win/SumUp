package com.andrewwin.sumup.ui.screen.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andrewwin.sumup.data.local.dao.GroupWithSources
import com.andrewwin.sumup.data.local.entities.AppLanguage
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.local.entities.SourceGroup
import com.andrewwin.sumup.data.local.entities.SourceGroupOrigin
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.domain.repository.ImportedSourceGroup
import com.andrewwin.sumup.domain.repository.SourceRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.source.SourceUrlNormalizer
import com.andrewwin.sumup.domain.usecase.feed.RefreshFeedUseCase
import com.andrewwin.sumup.domain.usecase.sources.GetSuggestedThemesUseCase
import com.andrewwin.sumup.domain.usecase.sources.ThemeSuggestion
import com.andrewwin.sumup.domain.ai.LocalModelManager
import com.andrewwin.sumup.data.repository.PublicSubscriptionsSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class FirebaseThemeSuggestion(
    val group: ImportedSourceGroup,
    val isSubscribed: Boolean,
    val isRecommended: Boolean
)

enum class SourceSortOrder {
    BY_NAME,
    BY_DATE
}

sealed interface SourcesUiState {
    data object Loading : SourcesUiState
    data class Content(
        val groups: List<GroupWithSources>
    ) : SourcesUiState
}

@HiltViewModel
@OptIn(FlowPreview::class)
class SourcesViewModel @Inject constructor(
    private val repository: SourceRepository,
    private val manageModelUseCase: LocalModelManager,
    private val publicSubscriptionsSyncManager: PublicSubscriptionsSyncManager,
    private val refreshFeedUseCase: RefreshFeedUseCase,
    private val getSuggestedThemesUseCase: GetSuggestedThemesUseCase,
    userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SourceSortOrder.BY_NAME)
    val sortOrder: StateFlow<SourceSortOrder> = _sortOrder.asStateFlow()

    private val debouncedSearchQuery = _searchQuery
        .debounce { query -> if (query.isBlank()) 0L else SEARCH_QUERY_DEBOUNCE_MS }

    val uiState: StateFlow<SourcesUiState> = combine(
        repository.groupsWithSources,
        debouncedSearchQuery,
        _sortOrder
    ) { groups, query, sort ->
        val filteredByQuery = if (query.isBlank()) {
            groups
        } else {
            val tokens = tokenizeQuery(query)
            groups.mapNotNull { groupWithSources ->
                val matchesGroup = matchesQuery(groupWithSources.group.name, tokens)
                val filteredSources = groupWithSources.sources.filter { source ->
                    matchesQuery(source.name, tokens)
                }

                if (matchesGroup || filteredSources.isNotEmpty()) {
                    groupWithSources.copy(sources = if (matchesGroup) groupWithSources.sources else filteredSources)
                } else {
                    null
                }
            }
        }

        when (sort) {
            SourceSortOrder.BY_NAME -> filteredByQuery.sortedBy { it.group.name }
            SourceSortOrder.BY_DATE -> filteredByQuery.sortedByDescending { it.group.id }
        }.let(SourcesUiState::Content)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SourcesUiState.Loading
        )

    private val _suggestedThemes = MutableStateFlow<List<FirebaseThemeSuggestion>>(emptyList())
    val suggestedThemes: StateFlow<List<FirebaseThemeSuggestion>> = _suggestedThemes.asStateFlow()
    private val _reservedGroupNames = MutableStateFlow(buildReservedGroupNames(publicSubscriptionsSyncManager.getCachedGroups()))
    val reservedGroupNames: StateFlow<List<String>> = _reservedGroupNames.asStateFlow()
    private val pendingSubscriptionOverrides = mutableMapOf<String, Boolean>()

    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()
    private val _subscriptionsSyncFailed = MutableStateFlow(publicSubscriptionsSyncManager.hasSyncFailure())
    val subscriptionsSyncFailed: StateFlow<Boolean> = _subscriptionsSyncFailed.asStateFlow()
    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val messages: SharedFlow<Int> = _messages.asSharedFlow()
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
            isRecommendationsEnabled.drop(1).collect { loadSuggestedThemes() }
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
            updateSuggestedThemes(suggestions.toFirebaseThemeSuggestions())
            refreshReservedGroupNames()
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
                if (!isRecommendationsEnabled.value) {
                    val suggestions = getSuggestedThemesUseCase(forceRefresh = false).first()
                    updateSuggestedThemes(suggestions.toFirebaseThemeSuggestions())
                    refreshReservedGroupNames()
                    return@launch
                }
                if (forceRefresh) {
                    _isRefreshingThemeRecommendations.value = true
                    try {
                        getSuggestedThemesUseCase(forceRefresh = true).collect { suggestions ->
                            updateSuggestedThemes(suggestions.toFirebaseThemeSuggestions())
                        }
                    } finally {
                        _isRefreshingThemeRecommendations.value = false
                    }
                } else {
                    val suggestions = getSuggestedThemesUseCase(forceRefresh = false).first()
                    updateSuggestedThemes(suggestions.toFirebaseThemeSuggestions())
                }
                refreshReservedGroupNames()
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

    private suspend fun updateSuggestedThemes(themes: List<FirebaseThemeSuggestion>) {
        val updatedThemes = applyPendingOverrides(themes)
        _suggestedThemes.value = updatedThemes
        repository.syncSubscribedImportedGroups(updatedThemes.map { it.group })
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
        updateSuggestedThemeSubscriptionState(suggestion.group.id, isSubscribed)

        viewModelScope.launch {
            val applied = runCatching {
                subscriptionMutex.withLock {
                    applyThemeSubscriptionChange(suggestion, isSubscribed)
                }
            }.isSuccess

            if (!applied) {
                clearPendingOverride(suggestion.group.id)
                updateSuggestedThemeSubscriptionState(suggestion.group.id, !isSubscribed)
            }
        }
    }

    fun addGroup(name: String) {
        viewModelScope.launch {
            if (isReservedGroupName(name)) return@launch
            repository.addGroup(name)
        }
    }

    fun updateGroup(group: SourceGroup) {
        viewModelScope.launch {
            if (isReservedGroupName(group.name)) return@launch
            repository.updateGroup(group)
        }
    }

    fun deleteGroup(group: SourceGroup) {
        deleteGroups(listOf(group))
    }

    fun deleteGroups(groups: List<SourceGroup>) {
        if (groups.isEmpty()) return
        val subscriptionSuggestionByGroupId = groups.mapNotNull { group ->
            findSubscriptionSuggestionForGroup(group)?.let { suggestion -> group.id to suggestion }
        }.toMap()
        val subscriptionSuggestions = subscriptionSuggestionByGroupId.values
            .distinctBy { it.group.id.trim().lowercase() }
        subscriptionSuggestions.forEach { suggestion ->
            setPendingOverride(suggestion.group.id, false)
            updateSuggestedThemeSubscriptionState(suggestion.group.id, false)
        }

        viewModelScope.launch {
            val deletedGroupIds = groups.map { it.id }.toSet()
            val currentGroups = (uiState.value as? SourcesUiState.Content)?.groups.orEmpty()
            val deletedGroupUrls = currentGroups
                .filter { it.group.id in deletedGroupIds }
                .flatMap { groupWithSources -> groupWithSources.sources }
                .map { it.url.trim().lowercase() }
                .toSet()

            val regularGroups = groups.filterNot { group ->
                group.id in subscriptionSuggestionByGroupId.keys
            }

            val unsubscribed = runCatching {
                subscriptionMutex.withLock {
                    subscriptionSuggestions.forEach { suggestion ->
                        applyThemeSubscriptionChange(suggestion, isSubscribed = false)
                    }
                }
            }.isSuccess

            regularGroups.forEach { group ->
                repository.deleteGroup(group)
            }
            updateSuggestedThemesAfterDeletedUrls(deletedGroupUrls)
            if (!unsubscribed) {
                subscriptionSuggestions.forEach { suggestion ->
                    clearPendingOverride(suggestion.group.id)
                    updateSuggestedThemeSubscriptionState(suggestion.group.id, true)
                }
            }
        }
    }

    private suspend fun applyThemeSubscriptionChange(
        suggestion: FirebaseThemeSuggestion,
        isSubscribed: Boolean
    ) {
        if (isSubscribed) {
            val currentLanguage = appLanguage.value
            val targetGroupName = suggestion.group.displayName(currentLanguage)
            repository.subscribeToImportedGroup(
                group = suggestion.group,
                displayName = targetGroupName
            )
            refreshFeedInBackground()
        } else {
            repository.unsubscribeFromImportedGroup(suggestion.group)
        }
    }

    private fun updateSuggestedThemeSubscriptionState(themeId: String, isSubscribed: Boolean) {
        _suggestedThemes.update { themes ->
            themes.map { current ->
                if (current.group.id.equals(themeId, ignoreCase = true)) {
                    current.copy(isSubscribed = isSubscribed)
                } else {
                    current
                }
            }
        }
    }

    private fun findSubscriptionSuggestionForGroup(group: SourceGroup): FirebaseThemeSuggestion? {
        group.subscriptionId
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { subscriptionId ->
                _suggestedThemes.value.firstOrNull { suggestion ->
                    suggestion.group.id.equals(subscriptionId, ignoreCase = true)
                }
            }
            ?.let { return it }

        if (group.origin != SourceGroupOrigin.PUBLIC_SUBSCRIPTION) return null
        val groupName = group.name.trim().lowercase()
        return _suggestedThemes.value.firstOrNull { suggestion ->
            listOf(suggestion.group.name, suggestion.group.nameUk, suggestion.group.nameEn)
                .any { it.trim().lowercase() == groupName }
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
            if (isSourceInActiveSubscriptions(url, type)) {
                _messages.tryEmit(com.andrewwin.sumup.R.string.validation_source_in_active_subscriptions)
                return@launch
            }
            repository.addSource(
                groupId = groupId,
                name = name,
                url = url,
                type = type,
                detectFooterPattern = false
            )
            refreshFeedInBackground()
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
            if (isSourceInActiveSubscriptions(url, type)) {
                _messages.tryEmit(com.andrewwin.sumup.R.string.validation_source_in_active_subscriptions)
                return@launch
            }
            repository.addSource(
                groupId = groupId,
                name = name,
                url = url,
                type = type,
                titleSelector = titleSelector,
                postLinkSelector = postLinkSelector,
                descriptionSelector = descriptionSelector,
                dateSelector = dateSelector,
                useHeadlessBrowser = useHeadlessBrowser,
                detectFooterPattern = false
            )
            refreshFeedInBackground()
        }
    }

    fun fetchGeneratedSourceName(
        url: String,
        type: SourceType,
        onResult: (String?) -> Unit
    ) {
        viewModelScope.launch {
            val name = runCatching {
                repository.fetchGeneratedSourceName(url, type)
            }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
            onResult(name)
        }
    }

    private fun isSourceInActiveSubscriptions(url: String, type: SourceType): Boolean {
        val normalizedUrl = SourceUrlNormalizer.normalize(url, type)
        if (normalizedUrl.isBlank()) return false
        return _suggestedThemes.value
            .asSequence()
            .filter { it.isSubscribed }
            .flatMap { it.group.sources.asSequence() }
            .any { source ->
                source.type == type &&
                    SourceUrlNormalizer.normalize(source.url, source.type).equals(normalizedUrl, ignoreCase = true)
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

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(order: SourceSortOrder) {
        _sortOrder.value = order
    }

    private fun refreshFeedInBackground() {
        viewModelScope.launch {
            refreshFeedUseCase()
        }
    }

    private fun matchesQuery(text: String, queryTokens: List<String>): Boolean {
        if (queryTokens.isEmpty()) return true
        val normalizedText = normalizeForSearch(text)
        if (normalizedText.isBlank()) return false

        val matchedCount = queryTokens.count { token -> normalizedText.contains(token) }
        val requiredMatches = kotlin.math.ceil(queryTokens.size * 0.6f).toInt().coerceAtLeast(1)
        return matchedCount >= requiredMatches
    }

    private fun tokenizeQuery(query: String): List<String> =
        normalizeForSearch(query)
            .split(" ")
            .map { it.trim() }
            .filter { it.length >= 2 }
            .distinct()

    private fun refreshReservedGroupNames() {
        val cachedGroups = publicSubscriptionsSyncManager.getCachedGroups()
        val suggestedGroups = _suggestedThemes.value.map { it.group }
        _reservedGroupNames.value = buildReservedGroupNames(cachedGroups + suggestedGroups)
    }

    private fun buildReservedGroupNames(groups: List<ImportedSourceGroup>): List<String> {
        return groups
            .flatMap { it.allNamesForFolderValidation() }
            .distinctBy(::normalizeGroupNameForComparison)
    }

    private fun isReservedGroupName(name: String): Boolean {
        val normalizedName = normalizeGroupNameForComparison(name)
        if (normalizedName.isBlank()) return false
        return _reservedGroupNames.value.any {
            normalizeGroupNameForComparison(it) == normalizedName
        }
    }

    private fun ImportedSourceGroup.allNamesForFolderValidation(): List<String> {
        return listOf(name, nameUk, nameEn)
            .map(String::trim)
            .filter(String::isNotBlank)
    }

    private fun normalizeForSearch(value: String): String =
        value
            .lowercase()
            .replace(Regex("[\\p{Punct}\\p{S}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun normalizeGroupNameForComparison(name: String): String = name.trim().lowercase()

    private companion object {
        private const val SEARCH_QUERY_DEBOUNCE_MS = 300L
    }
}

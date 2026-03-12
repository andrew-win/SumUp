package com.andrewwin.sumup.ui.screens.feed

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.data.local.dao.GroupWithSources
import com.andrewwin.sumup.data.local.entities.SourceGroup
import com.andrewwin.sumup.domain.exception.NoActiveModelException
import com.andrewwin.sumup.domain.exception.UnsupportedStrategyException
import com.andrewwin.sumup.domain.repository.SourceRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.ArticleCluster
import com.andrewwin.sumup.domain.usecase.RefreshArticlesUseCase
import com.andrewwin.sumup.domain.usecase.ai.AskQuestionUseCase
import com.andrewwin.sumup.domain.usecase.ai.SummarizeContentUseCase
import com.andrewwin.sumup.domain.usecase.feed.GetFeedArticlesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DateFilter(@StringRes val labelRes: Int, val hours: Int?) {
    ALL(R.string.filter_date_all, null),
    HOUR_1(R.string.filter_date_1h, 1),
    HOUR_3(R.string.filter_date_3h, 3),
    HOUR_6(R.string.filter_date_6h, 6),
    HOUR_12(R.string.filter_date_12h, 12),
    HOUR_24(R.string.filter_date_24h, 24)
}

@HiltViewModel
class FeedViewModel @Inject constructor(
    application: Application,
    private val refreshArticlesUseCase: RefreshArticlesUseCase,
    private val getFeedArticlesUseCase: GetFeedArticlesUseCase,
    private val summarizeContentUseCase: SummarizeContentUseCase,
    private val askQuestionUseCase: AskQuestionUseCase,
    private val sourceRepository: SourceRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : AndroidViewModel(application) {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedGroupId = MutableStateFlow<Long?>(null)
    val selectedGroupId: StateFlow<Long?> = _selectedGroupId.asStateFlow()

    private val _dateFilter = MutableStateFlow(DateFilter.ALL)
    val dateFilter: StateFlow<DateFilter> = _dateFilter.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _aiResult = MutableStateFlow<String?>(null)
    val aiResult: StateFlow<String?> = _aiResult.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    val userPreferences: StateFlow<UserPreferences> = userPreferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    private val groupsWithSources: StateFlow<List<GroupWithSources>> = sourceRepository.groupsWithSources
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groups: StateFlow<List<SourceGroup>> = groupsWithSources
        .map { list -> list.map { it.group }.filter { it.isEnabled } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sources: StateFlow<List<com.andrewwin.sumup.data.local.entities.Source>> = groupsWithSources
        .map { list -> list.flatMap { it.sources } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val articleClusters: StateFlow<List<ArticleCluster>> = getFeedArticlesUseCase(
        _searchQuery,
        _selectedGroupId,
        _dateFilter.map { it.hours },
        userPreferences
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = emptyList()
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            refreshArticlesUseCase()
            _isRefreshing.value = false
        }
    }

    fun onSearchQueryChange(query: String) { _searchQuery.value = query }
    fun selectGroup(groupId: Long?) { _selectedGroupId.value = groupId }
    fun setDateFilter(filter: DateFilter) { _dateFilter.value = filter }
    fun clearAiResult() { _aiResult.value = null }

    fun summarizeContent(content: String) {
        viewModelScope.launch {
            _isAiLoading.value = true
            _aiResult.value = null
            _aiResult.value = summarizeContentUseCase(content).getOrElse { e -> localizeError(e) }
            _isAiLoading.value = false
        }
    }

    fun askQuestion(content: String, question: String) {
        viewModelScope.launch {
            _isAiLoading.value = true
            _aiResult.value = null
            _aiResult.value = askQuestionUseCase(content, question).getOrElse { e -> localizeError(e) }
            _isAiLoading.value = false
        }
    }

    fun summarizeFeed() {
        val content = articleClusters.value.joinToString("\n\n") { "${it.representative.title}: ${it.representative.content}" }
        if (content.isNotBlank()) summarizeContent(content)
    }

    fun askFeed(question: String) {
        val content = articleClusters.value.joinToString("\n\n") { "${it.representative.title}: ${it.representative.content}" }
        if (content.isNotBlank()) askQuestion(content, question)
    }

    private fun localizeError(e: Throwable): String {
        val context = getApplication<Application>()
        return when (e) {
            is NoActiveModelException -> context.getString(R.string.error_no_active_model)
            is UnsupportedStrategyException -> context.getString(R.string.error_unsupported_strategy)
            else -> "${context.getString(R.string.ai_error_prefix)} ${e.localizedMessage.orEmpty()}"
        }
    }
}

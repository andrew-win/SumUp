package com.andrewwin.sumup.ui.screens.feed

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.AppDatabase
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.SourceGroup
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.data.repository.AiRepository
import com.andrewwin.sumup.data.repository.ArticleRepository
import com.andrewwin.sumup.data.repository.SourceRepository
import com.andrewwin.sumup.domain.ArticleCluster
import com.andrewwin.sumup.domain.DeduplicationService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class DateFilter(@StringRes val labelRes: Int, val hours: Int?) {
    ALL(R.string.filter_date_all, null),
    HOUR_1(R.string.filter_date_1h, 1),
    HOUR_3(R.string.filter_date_3h, 3),
    HOUR_6(R.string.filter_date_6h, 6),
    HOUR_12(R.string.filter_date_12h, 12),
    HOUR_24(R.string.filter_date_24h, 24)
}

class FeedViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val articleRepository = ArticleRepository(db.articleDao(), db.sourceDao())
    private val sourceRepository = SourceRepository(db.sourceDao())
    private val aiRepository = AiRepository(db.aiModelDao(), db.userPreferencesDao())
    private val deduplicationService = DeduplicationService(application, db.articleDao())
    
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

    val userPreferences: StateFlow<UserPreferences> = db.userPreferencesDao().getUserPreferences()
        .map { it ?: UserPreferences() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    init {
        refresh()
    }

    val groups: StateFlow<List<SourceGroup>> = sourceRepository.groupsWithSources
        .map { list -> list.map { it.group }.filter { it.isEnabled } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val articleClusters: StateFlow<List<ArticleCluster>> = combine(
        articleRepository.enabledArticles,
        _searchQuery,
        _selectedGroupId,
        _dateFilter,
        userPreferences
    ) { articles, query, groupId, dateFilter, prefs ->
        var filteredArticles = articles
        
        if (groupId != null) {
            val sourceIds = db.sourceDao().getSourcesByGroupId(groupId).first().map { it.id }
            filteredArticles = filteredArticles.filter { it.sourceId in sourceIds }
        }

        dateFilter.hours?.let { hours ->
            val threshold = System.currentTimeMillis() - (hours * 60 * 60 * 1000L)
            filteredArticles = filteredArticles.filter { it.publishedAt >= threshold }
        }

        if (query.isNotBlank()) {
            filteredArticles = filteredArticles.filter { 
                it.title.contains(query, ignoreCase = true) || 
                it.content.contains(query, ignoreCase = true) 
            }
        }

        if (prefs.isDeduplicationEnabled && prefs.modelPath != null && filteredArticles.isNotEmpty()) {
            if (deduplicationService.initialize(prefs.modelPath)) {
                val clusters = deduplicationService.clusterArticles(filteredArticles, prefs.deduplicationThreshold)
                clusters.filter { it.duplicates.size + 1 >= prefs.minMentions }
            } else {
                filteredArticles.map { ArticleCluster(it, emptyList()) }
            }
        } else {
            filteredArticles.map { ArticleCluster(it, emptyList()) }
        }
    }.stateIn(
        scope = viewModelScope, 
        started = SharingStarted.Lazily,
        initialValue = emptyList()
    )

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            articleRepository.refreshArticles()
            _isRefreshing.value = false
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun selectGroup(groupId: Long?) {
        _selectedGroupId.value = groupId
    }

    fun setDateFilter(filter: DateFilter) {
        _dateFilter.value = filter
    }

    fun summarizeContent(content: String) {
        viewModelScope.launch {
            _isAiLoading.value = true
            _aiResult.value = null
            try {
                _aiResult.value = aiRepository.summarize(content)
            } catch (e: Exception) {
                _aiResult.value = getApplication<Application>().getString(R.string.ai_error_prefix, e.message ?: "")
            } finally {
                _isAiLoading.value = false
            }
        }
    }

    fun askQuestion(content: String, question: String) {
        viewModelScope.launch {
            _isAiLoading.value = true
            _aiResult.value = null
            try {
                _aiResult.value = aiRepository.askQuestion(content, question)
            } catch (e: Exception) {
                _aiResult.value = getApplication<Application>().getString(R.string.ai_error_prefix, e.message ?: "")
            } finally {
                _isAiLoading.value = false
            }
        }
    }

    fun summarizeFeed() {
        val combinedContent = articleClusters.value.joinToString("\n\n") { "${it.representative.title}: ${it.representative.content}" }
        if (combinedContent.isNotBlank()) {
            summarizeContent(combinedContent)
        }
    }

    fun askFeed(question: String) {
        val combinedContent = articleClusters.value.joinToString("\n\n") { "${it.representative.title}: ${it.representative.content}" }
        if (combinedContent.isNotBlank()) {
            askQuestion(combinedContent, question)
        }
    }

    fun clearAiResult() {
        _aiResult.value = null
    }

    override fun onCleared() {
        super.onCleared()
        deduplicationService.close()
    }
}

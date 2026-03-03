package com.andrewwin.sumup.ui.screens.feed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andrewwin.sumup.data.local.AppDatabase
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.SourceGroup
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.data.repository.AiRepository
import com.andrewwin.sumup.data.repository.ArticleRepository
import com.andrewwin.sumup.data.repository.SourceRepository
import com.andrewwin.sumup.domain.ArticleCluster
import com.andrewwin.sumup.domain.DeduplicationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class DateFilter(val label: String, val hours: Int?) {
    ALL("Всі", null),
    HOUR_1("1 год", 1),
    HOUR_3("3 год", 3),
    HOUR_6("6 год", 6),
    HOUR_12("12 год", 12),
    HOUR_24("24 год", 24)
}

class FeedViewModel(application: Application) : AndroidViewModel(application) {
    private val articleRepository: ArticleRepository
    private val sourceRepository: SourceRepository
    private val aiRepository: AiRepository
    private val deduplicationService = DeduplicationService(application)
    private val db = AppDatabase.getDatabase(application)
    
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
        articleRepository = ArticleRepository(db.articleDao(), db.sourceDao())
        sourceRepository = SourceRepository(db.sourceDao())
        aiRepository = AiRepository(db.aiModelDao())
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

        if (prefs.isDeduplicationEnabled && prefs.modelPath != null) {
            if (deduplicationService.initialize(prefs.modelPath)) {
                val clusters = deduplicationService.clusterArticles(filteredArticles, prefs.deduplicationThreshold)
                clusters.filter { it.duplicates.isNotEmpty() }
            } else {
                emptyList()
            }
        } else {
            filteredArticles.map { ArticleCluster(it, emptyList()) }
        }
    }.stateIn(
        scope = viewModelScope, 
        started = SharingStarted.WhileSubscribed(5000), 
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
                _aiResult.value = "Помилка ШІ: ${e.message}"
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
                _aiResult.value = "Помилка ШІ: ${e.message}"
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

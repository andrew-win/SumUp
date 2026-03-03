package com.andrewwin.sumup.ui.screens.feed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andrewwin.sumup.data.local.AppDatabase
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.SourceGroup
import com.andrewwin.sumup.data.repository.AiRepository
import com.andrewwin.sumup.data.repository.ArticleRepository
import com.andrewwin.sumup.data.repository.SourceRepository
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

    init {
        val db = AppDatabase.getDatabase(application)
        articleRepository = ArticleRepository(db.articleDao(), db.sourceDao())
        sourceRepository = SourceRepository(db.sourceDao())
        aiRepository = AiRepository(db.aiModelDao())
        refresh()
    }

    val groups: StateFlow<List<SourceGroup>> = sourceRepository.groupsWithSources
        .map { list -> list.map { it.group }.filter { it.isEnabled } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val articles: StateFlow<List<Article>> = combine(
        articleRepository.enabledArticles,
        _searchQuery,
        _selectedGroupId,
        _dateFilter
    ) { articles, query, groupId, dateFilter ->
        var result = articles
        
        if (groupId != null) {
            val db = AppDatabase.getDatabase(getApplication())
            val sourceIds = db.sourceDao().getSourcesByGroupId(groupId).first().map { it.id }
            result = result.filter { it.sourceId in sourceIds }
        }

        dateFilter.hours?.let { hours ->
            val threshold = System.currentTimeMillis() - (hours * 60 * 60 * 1000L)
            result = result.filter { it.publishedAt >= threshold }
        }

        if (query.isNotBlank()) {
            result = result.filter { 
                it.title.contains(query, ignoreCase = true) || 
                it.content.contains(query, ignoreCase = true) 
            }
        }

        result
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
        val combinedContent = articles.value.joinToString("\n\n") { "${it.title}: ${it.content}" }
        if (combinedContent.isNotBlank()) {
            summarizeContent(combinedContent)
        }
    }

    fun askFeed(question: String) {
        val combinedContent = articles.value.joinToString("\n\n") { "${it.title}: ${it.content}" }
        if (combinedContent.isNotBlank()) {
            askQuestion(combinedContent, question)
        }
    }

    fun clearAiResult() {
        _aiResult.value = null
    }
}

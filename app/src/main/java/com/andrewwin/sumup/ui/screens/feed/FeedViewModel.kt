package com.andrewwin.sumup.ui.screens.feed

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.dao.GroupWithSources
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.domain.exception.AllAiModelsFailedException
import com.andrewwin.sumup.domain.exception.NoActiveModelException
import com.andrewwin.sumup.domain.exception.UnsupportedStrategyException
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.SourceRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.usecase.ai.AskQuestionUseCase
import com.andrewwin.sumup.domain.usecase.ai.SummarizeContentUseCase
import com.andrewwin.sumup.domain.usecase.feed.GetFeedArticlesUseCase
import com.andrewwin.sumup.domain.usecase.feed.RefreshFeedUseCase
import com.andrewwin.sumup.ui.screens.feed.model.ArticleClusterUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
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

enum class SavedFilter(@StringRes val labelRes: Int, val savedOnly: Boolean) {
    ALL(R.string.filter_saved_all, false),
    SAVED(R.string.filter_saved_only, true)
}

@HiltViewModel
class FeedViewModel @Inject constructor(
    application: Application,
    private val articleRepository: ArticleRepository,
    private val refreshFeedUseCase: RefreshFeedUseCase,
    private val getFeedArticlesUseCase: GetFeedArticlesUseCase,
    private val summarizeContentUseCase: SummarizeContentUseCase,
    private val askQuestionUseCase: AskQuestionUseCase,
    private val sourceRepository: SourceRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val feedUiModelMapper: FeedUiModelMapper
) : AndroidViewModel(application) {
    private val aiSessionCache = mutableMapOf<String, String>()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedGroupId = MutableStateFlow<Long?>(null)
    val selectedGroupId: StateFlow<Long?> = _selectedGroupId.asStateFlow()

    private val _dateFilter = MutableStateFlow(DateFilter.ALL)
    val dateFilter: StateFlow<DateFilter> = _dateFilter.asStateFlow()

    private val _savedFilter = MutableStateFlow(SavedFilter.ALL)
    val savedFilter: StateFlow<SavedFilter> = _savedFilter.asStateFlow()

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

    val groups = groupsWithSources
        .map { list -> list.map { it.group }.filter { it.isEnabled } }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val feedResultFlow = getFeedArticlesUseCase(
        _searchQuery,
        _selectedGroupId,
        _dateFilter.map { it.hours },
        _savedFilter.map { it.savedOnly },
        userPreferences
    )
        .distinctUntilChanged { old, new ->
            old.isDedupInProgress == new.isDedupInProgress &&
                old.clusters.size == new.clusters.size &&
                old.clusters.map { it.representative.id } == new.clusters.map { it.representative.id } &&
                old.clusters.map { it.duplicates.size } == new.clusters.map { it.duplicates.size }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GetFeedArticlesUseCase.FeedResult(emptyList(), false))

    private val feedUiState: StateFlow<FeedUiState> = combine(
        feedResultFlow,
        groupsWithSources
    ) { feed, groupsList ->
        val clusters = feedUiModelMapper.map(
            clusters = feed.clusters,
            groupsWithSources = groupsList,
            ellipsis = getApplication<Application>().getString(R.string.ellipsis)
        )

        FeedUiState(
            clusters = clusters,
            isDedupInProgress = feed.isDedupInProgress
        )
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FeedUiState(emptyList(), false))

    val isDedupInProgress: StateFlow<Boolean> = feedUiState
        .map { it.isDedupInProgress }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val articleClusters: StateFlow<List<ArticleClusterUiModel>> = feedUiState
        .map { state -> state.clusters to state.isDedupInProgress }
        .scan(emptyList<ArticleClusterUiModel>()) { previous, (clusters, inProgress) ->
            when {
                clusters.isNotEmpty() -> clusters
                inProgress -> previous
                else -> emptyList()
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            refreshFeedUseCase()
            _isRefreshing.value = false
        }
    }

    fun onSearchQueryChange(query: String) { _searchQuery.value = query }
    fun selectGroup(groupId: Long?) { _selectedGroupId.value = groupId }
    fun setDateFilter(filter: DateFilter) { _dateFilter.value = filter }
    fun setSavedFilter(filter: SavedFilter) { _savedFilter.value = filter }
    fun clearAiResult() { _aiResult.value = null }

    fun toggleSaved(article: Article) {
        viewModelScope.launch {
            articleRepository.updateArticle(article.copy(isFavorite = !article.isFavorite))
        }
    }

    private fun launchAi(block: suspend () -> Result<String>) {
        viewModelScope.launch {
            _isAiLoading.value = true
            _aiResult.value = null
            _aiResult.value = block().getOrElse { localizeError(it) }
            _isAiLoading.value = false
        }
    }

    fun summarizeContent(content: String) = launchAi { summarizeContentUseCase(content) }

    fun askQuestion(article: Article, question: String) = launchAi { askQuestionUseCase(article, question) }

    fun askQuestion(content: String, question: String) = launchAi { askQuestionUseCase(content, question) }

    fun openCachedArticleSummary(article: Article) {
        _aiResult.value = aiSessionCache["article:${article.id}"]
    }

    fun openCachedFeedSummary() {
        _aiResult.value = aiSessionCache[buildFeedCacheKey()]
    }

    fun openCachedClusterSummary(cluster: ArticleClusterUiModel) {
        _aiResult.value = aiSessionCache[buildCompareCacheKey(cluster)]
    }

    fun summarizeArticle(article: Article, forceRefresh: Boolean = false) {
        val cacheKey = "article:${article.id}"
        if (!forceRefresh) {
            aiSessionCache[cacheKey]?.let {
                _aiResult.value = it
                return
            }
        }
        launchAi {
            summarizeContentUseCase(article).onSuccess { aiSessionCache[cacheKey] = it }
        }
    }

    fun summarizeCluster(cluster: ArticleClusterUiModel, forceRefresh: Boolean = false) {
        val representative = cluster.representative.article
        val duplicates = cluster.duplicates.map { it.first.article }
        val cacheKey = buildCompareCacheKey(cluster)
        if (!forceRefresh) {
            aiSessionCache[cacheKey]?.let {
                _aiResult.value = it
                return
            }
        }
        launchAi {
            summarizeContentUseCase(representative, duplicates).onSuccess { aiSessionCache[cacheKey] = it }
        }
    }

    fun summarizeFeed(forceRefresh: Boolean = false) {
        val articles = articleClusters.value.map { it.representative.article }
        if (articles.isEmpty()) return
        val cacheKey = buildFeedCacheKey()
        if (!forceRefresh) {
            aiSessionCache[cacheKey]?.let {
                _aiResult.value = it
                return
            }
        }
        launchAi {
            summarizeContentUseCase(articles).onSuccess { aiSessionCache[cacheKey] = it }
        }
    }

    fun askFeed(question: String) {
        val perArticleLimit = userPreferences.value.aiMaxCharsPerFeedArticle.coerceAtLeast(200)
        val content = articleClusters.value.joinToString("\n\n") {
            "${it.representative.displayTitle}: ${it.representative.article.content.take(perArticleLimit)}"
        }
        if (content.isNotBlank()) askQuestion(content, question)
    }

    private fun localizeError(e: Throwable): String {
        val context = getApplication<Application>()
        return when (e) {
            is NoActiveModelException -> context.getString(R.string.error_no_active_model)
            is AllAiModelsFailedException -> context.getString(R.string.error_all_ai_models_failed)
            is UnsupportedStrategyException -> context.getString(R.string.error_unsupported_strategy)
            else -> "${context.getString(R.string.ai_error_prefix)} ${e.localizedMessage.orEmpty()}"
        }
    }

    private fun buildCompareCacheKey(cluster: ArticleClusterUiModel): String {
        val duplicateIds = cluster.duplicates.map { it.first.article.id }.sorted()
        return "compare:${cluster.representative.article.id}:${duplicateIds.joinToString(",")}"
    }

    private fun buildFeedCacheKey(): String {
        val articleIds = articleClusters.value.map { it.representative.article.id }
        return "feed:${articleIds.joinToString(",")}"
    }

    private data class FeedUiState(
        val clusters: List<ArticleClusterUiModel>,
        val isDedupInProgress: Boolean
    )
}

package com.andrewwin.sumup.ui.screen.feed

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.dao.GroupWithSources
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.AiModelType
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.domain.support.AllAiModelsFailedException
import com.andrewwin.sumup.domain.support.NoActiveModelException
import com.andrewwin.sumup.domain.support.UnsupportedStrategyException
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.usecase.common.FormatSummaryResultUseCase
import com.andrewwin.sumup.domain.usecase.ai.SummarizeSingleArticleUseCase
import com.andrewwin.sumup.domain.usecase.ai.SummarizeFeedUseCase
import com.andrewwin.sumup.domain.usecase.ai.CompareNewsUseCase
import com.andrewwin.sumup.domain.usecase.ai.AskQuestionAboutNewsUseCase
import com.andrewwin.sumup.domain.repository.AiModelConfigRepository
import com.andrewwin.sumup.domain.repository.SourceRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.usecase.feed.GetFeedArticlesUseCase
import com.andrewwin.sumup.domain.usecase.feed.RefreshFeedUseCase
import com.andrewwin.sumup.domain.usecase.ai.SummaryResult
import com.andrewwin.sumup.ui.screen.feed.model.ArticleClusterUiModel
import com.andrewwin.sumup.ui.screen.feed.model.ArticleUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
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
    private val summarizeSingleArticleUseCase: SummarizeSingleArticleUseCase,
    private val summarizeFeedUseCase: SummarizeFeedUseCase,
    private val compareNewsUseCase: CompareNewsUseCase,
    private val askQuestionAboutNewsUseCase: AskQuestionAboutNewsUseCase,
    private val formatSummaryResultUseCase: FormatSummaryResultUseCase,
    private val aiModelConfigRepository: AiModelConfigRepository,
    private val sourceRepository: SourceRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val feedUiModelMapper: FeedUiModelMapper
) : AndroidViewModel(application) {
    private val aiSessionCache = FeedAiSessionCache()
    private val favoriteOverrides = MutableStateFlow<Map<Long, Boolean>>(emptyMap())

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

    private val _aiResult = MutableStateFlow<AiPresentationResult?>(null)
    val aiResult: StateFlow<AiPresentationResult?> = _aiResult.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    val userPreferences: StateFlow<UserPreferences> = userPreferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    val activeSummaryModelName: StateFlow<String?> = aiModelConfigRepository.getConfigsByType(AiModelType.SUMMARY)
        .combine(aiModelConfigRepository.lastUsedSummaryModelName) { configs, lastUsed ->
            lastUsed?.takeIf { it.isNotBlank() }
                ?: configs.firstOrNull { it.isEnabled }?.modelName?.takeIf { it.isNotBlank() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val groupsWithSources: StateFlow<List<GroupWithSources>> = sourceRepository.groupsWithSources
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groups = groupsWithSources
        .map { list -> list.map { it.group }.filter { it.isEnabled } }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val feedResultFlow = getFeedArticlesUseCase(
        _searchQuery
            .debounce(300.milliseconds)
            .distinctUntilChanged(),
        _selectedGroupId,
        _dateFilter.map { it.hours },
        _savedFilter.map { it.savedOnly },
        userPreferences
    )
        .distinctUntilChanged { old, new ->
            old.isDedupInProgress == new.isDedupInProgress &&
                old.clusters.size == new.clusters.size &&
                old.clusters.map { it.representative.id } == new.clusters.map { it.representative.id } &&
                old.clusters.map { it.duplicates.size } == new.clusters.map { it.duplicates.size } &&
                old.clusters.map { it.representative.isFavorite } == new.clusters.map { it.representative.isFavorite } &&
                old.clusters.map { cluster -> cluster.duplicates.map { duplicate -> duplicate.first.isFavorite } } ==
                new.clusters.map { cluster -> cluster.duplicates.map { duplicate -> duplicate.first.isFavorite } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GetFeedArticlesUseCase.FeedResult(emptyList(), false))

    private val favoriteSavedAtFlow: StateFlow<Map<Long, Long>> = feedResultFlow
        .mapLatest { feed ->
            val ids = buildList {
                feed.clusters.forEach { cluster ->
                    add(cluster.representative.id)
                    addAll(cluster.duplicates.map { it.first.id })
                }
            }.distinct()
            articleRepository.getFavoriteSavedAt(ids)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val feedUiState: StateFlow<FeedUiState> = combine(
        feedResultFlow,
        groupsWithSources,
        favoriteOverrides,
        favoriteSavedAtFlow
    ) { feed, groupsList, overrides, favoriteSavedAt ->
        val mappedClusters = feedUiModelMapper.map(
            clusters = feed.clusters,
            groupsWithSources = groupsList,
            favoriteSavedAt = favoriteSavedAt,
            ellipsis = getApplication<Application>().getString(R.string.ellipsis)
        )
        val clusters = applyFavoriteOverrides(mappedClusters, overrides)

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

    fun toggleSaved(cluster: ArticleClusterUiModel) {
        val clusterArticles = buildList {
            add(cluster.representative.article)
            addAll(cluster.duplicates.map { it.first.article })
        }
        if (clusterArticles.isEmpty()) return
        val clusterIds = clusterArticles.map { it.id }.distinct()
        val newFavorite = clusterArticles.any { !it.isFavorite }
        val previousOverrides = favoriteOverrides.value

        favoriteOverrides.update { current ->
            val updated = current.toMutableMap()
            clusterArticles.forEach { article ->
                updated[article.id] = newFavorite
            }
            updated
        }
        viewModelScope.launch {
            runCatching {
                val updated = articleRepository.setFavoriteByIds(clusterIds, newFavorite)
                if (newFavorite) {
                    val clusterKey = if (clusterIds.size > 1) {
                        "cluster:${cluster.representative.article.id}"
                    } else {
                        ""
                    }
                    val scoreByArticleId = buildMap {
                        put(cluster.representative.article.id, 0f)
                        cluster.duplicates.forEach { (duplicate, score) ->
                            put(duplicate.article.id, score)
                        }
                    }
                    articleRepository.saveFavoriteClusterMapping(clusterIds, clusterKey)
                    articleRepository.saveFavoriteClusterScores(scoreByArticleId)
                    articleRepository.saveFavoriteSavedAt(clusterIds)
                } else {
                    articleRepository.clearFavoriteClusterMapping(clusterIds)
                    articleRepository.clearFavoriteSavedAt(clusterIds)
                }
            }.onFailure {
                favoriteOverrides.value = previousOverrides
            }
        }
    }

    private fun applyFavoriteOverrides(
        clusters: List<ArticleClusterUiModel>,
        overrides: Map<Long, Boolean>
    ): List<ArticleClusterUiModel> {
        if (overrides.isEmpty()) return clusters
        return clusters.map { cluster ->
            val representative = applyFavoriteOverride(cluster.representative, overrides)
            val duplicates = cluster.duplicates.map { (articleUi, score) ->
                applyFavoriteOverride(articleUi, overrides) to score
            }
            cluster.copy(representative = representative, duplicates = duplicates)
        }
    }

    private fun applyFavoriteOverride(
        uiModel: ArticleUiModel,
        overrides: Map<Long, Boolean>
    ): ArticleUiModel {
        val override = overrides[uiModel.article.id] ?: return uiModel
        if (uiModel.article.isFavorite == override) return uiModel
        return uiModel.copy(article = uiModel.article.copy(isFavorite = override))
    }

    private fun launchAi(block: suspend () -> Result<SummaryResult>) {
        viewModelScope.launch {
            _isAiLoading.value = true
            _aiResult.value = null
            val result = block()
                .map { summary ->
                    AiPresentationResult(
                        result = summary,
                        rawText = formatSummaryResultUseCase(summary)
                    )
                }
                .getOrElse { error ->
                    val localized = localizeError(error)
                    AiPresentationResult(
                        result = SummaryResult.Error(localized),
                        rawText = localized
                    )
                }
            _aiResult.value = result
            _isAiLoading.value = false
        }
    }

    fun summarizeContent(content: String) = launchAi {
        summarizeSingleArticleUseCase(title = "Текст", content = content)
    }

    fun askQuestion(article: Article, question: String) = launchAi { askQuestionAboutNewsUseCase(listOf(article), question) }



    fun askClusterQuestion(cluster: ArticleClusterUiModel, question: String) = launchAi {
        val clusterArticles = buildList {
            add(cluster.representative.article)
            addAll(cluster.duplicates.map { it.first.article })
        }
        askQuestionAboutNewsUseCase(clusterArticles, question)
    }

    fun openCachedArticleSummary(article: Article) {
        _aiResult.value = aiSessionCache.getArticleSummary(article.id)
    }

    fun openCachedFeedSummary() {
        _aiResult.value = aiSessionCache.getFeedSummary(currentFeedArticleIds())
    }

    fun openCachedClusterSummary(cluster: ArticleClusterUiModel) {
        _aiResult.value = aiSessionCache.getClusterSummary(cluster)
    }

    fun summarizeArticle(article: Article, forceRefresh: Boolean = false) {
        if (!forceRefresh) {
            aiSessionCache.getArticleSummary(article.id)?.let {
                _aiResult.value = it
                return
            }
        }
        launchAi {
            summarizeSingleArticleUseCase(article).onSuccess { result ->
                val presentation = AiPresentationResult(result = result, rawText = formatSummaryResultUseCase(result))
                aiSessionCache.putArticleSummary(article.id, presentation)
            }
        }
    }

    fun summarizeCluster(cluster: ArticleClusterUiModel, forceRefresh: Boolean = false) {
        val representative = cluster.representative.article
        val duplicates = cluster.duplicates.map { it.first.article }
        if (!forceRefresh) {
            aiSessionCache.getClusterSummary(cluster)?.let {
                _aiResult.value = it
                return
            }
        }
        launchAi {
            compareNewsUseCase(listOf(representative) + duplicates)
                .onSuccess {
                    aiSessionCache.putClusterSummary(
                        cluster,
                        AiPresentationResult(result = it, rawText = formatSummaryResultUseCase(it))
                    )
                }
        }
    }

    fun summarizeFeed(forceRefresh: Boolean = false) {
        val articles = articleClusters.value.map { it.representative.article }
        if (articles.isEmpty()) return
        val articleIds = currentFeedArticleIds()
        if (!forceRefresh) {
            aiSessionCache.getFeedSummary(articleIds)?.let {
                _aiResult.value = it
                return
            }
        }
        launchAi {
            summarizeFeedUseCase(articles).onSuccess { result ->
                val presentation = AiPresentationResult(result = result, rawText = formatSummaryResultUseCase(result))
                aiSessionCache.putFeedSummary(articleIds, presentation)
            }
        }
    }

    fun askFeed(question: String) {
        val representatives = articleClusters.value.map { it.representative }
        if (representatives.isEmpty()) return
        launchAi { askQuestionAboutNewsUseCase(representatives.map { it.article }, question) }
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

    private fun currentFeedArticleIds(): List<Long> = articleClusters.value.map { it.representative.article.id }

    private data class FeedUiState(
        val clusters: List<ArticleClusterUiModel>,
        val isDedupInProgress: Boolean
    )
}








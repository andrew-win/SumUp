package com.andrewwin.sumup.ui.screen.feed

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.dao.GroupWithSources
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.SourceRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.usecase.feed.FeedArticlesBuilder
import com.andrewwin.sumup.domain.usecase.feed.FeedRefreshStage
import com.andrewwin.sumup.domain.usecase.feed.RefreshFeedUseCase
import com.andrewwin.sumup.ui.screen.feed.model.ArticleClusterUiModel
import com.andrewwin.sumup.ui.screen.feed.model.ArticleUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

enum class FeedLoadingStage(@StringRes val messageRes: Int) {
    LOADING_FROM_DATABASE(R.string.feed_loading_from_database),
    PARSING_NEWS(R.string.feed_loading_news),
    DEDUPLICATING_NEWS(R.string.feed_deduplicating)
}

@HiltViewModel
class FeedViewModel @Inject constructor(
    application: Application,
    private val articleRepository: ArticleRepository,
    private val refreshFeedUseCase: RefreshFeedUseCase,
    private val feedArticlesBuilder: FeedArticlesBuilder,
    private val sourceRepository: SourceRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val feedUiModelMapper: FeedUiModelMapper
) : AndroidViewModel(application) {
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

    private val _feedLoadingStage = MutableStateFlow<FeedLoadingStage?>(FeedLoadingStage.LOADING_FROM_DATABASE)
    private val frozenClustersWhileProcessing = MutableStateFlow<List<ArticleClusterUiModel>?>(null)
    private var lastRefreshFinishedAt = 0L

    val userPreferences: StateFlow<UserPreferences> = userPreferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    private val groupsWithSources: StateFlow<List<GroupWithSources>> = sourceRepository.groupsWithSources
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groups = groupsWithSources
        .map { list -> list.map { it.group }.filter { it.isEnabled } }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val feedResultState: StateFlow<FeedResultState> = feedArticlesBuilder(
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
                old.processedArticlesCount == new.processedArticlesCount &&
                old.totalArticlesCount == new.totalArticlesCount &&
                old.clusters.size == new.clusters.size &&
                old.clusters.map { it.representative.id } == new.clusters.map { it.representative.id } &&
                old.clusters.map { it.duplicates.size } == new.clusters.map { it.duplicates.size } &&
                old.clusters.map { it.representative.isFavorite } == new.clusters.map { it.representative.isFavorite } &&
                old.clusters.map { cluster -> cluster.duplicates.map { duplicate -> duplicate.first.isFavorite } } ==
                new.clusters.map { cluster -> cluster.duplicates.map { duplicate -> duplicate.first.isFavorite } }
        }
        .map<FeedArticlesBuilder.FeedResult, FeedResultState> { FeedResultState.Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FeedResultState.Initial)

    private val favoriteSavedAtFlow: StateFlow<Map<Long, Long>> = feedResultState
        .mapLatest { feedState ->
            val feed = (feedState as? FeedResultState.Loaded)?.feedResult ?: return@mapLatest emptyMap()
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
        feedResultState,
        groupsWithSources,
        favoriteOverrides,
        favoriteSavedAtFlow
    ) { feedState, groupsList, overrides, favoriteSavedAt ->
        val feed = (feedState as? FeedResultState.Loaded)?.feedResult
            ?: return@combine FeedUiState(
                clusters = emptyList(),
                isDedupInProgress = false,
                isInitial = true
            )
        val mappedClusters = feedUiModelMapper.map(
            clusters = feed.clusters,
            groupsWithSources = groupsList,
            favoriteSavedAt = favoriteSavedAt,
            ellipsis = getApplication<Application>().getString(R.string.ellipsis)
        )
        val clusters = applyFavoriteOverrides(mappedClusters, overrides)

        FeedUiState(
            clusters = clusters,
            isDedupInProgress = feed.isDedupInProgress,
            isInitial = false
        )
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FeedUiState(emptyList(), false, true))

    val isDedupInProgress: StateFlow<Boolean> = feedUiState
        .map { it.isDedupInProgress }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val feedLoadingStage: StateFlow<FeedLoadingStage?> = _feedLoadingStage
        .withStableVisibleStageDuration()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FeedLoadingStage.LOADING_FROM_DATABASE)

    val isAnyLoading: StateFlow<Boolean> = _feedLoadingStage
        .map { it != null }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val isFeedProcessing: StateFlow<Boolean> = combine(
        isAnyLoading,
        isDedupInProgress
    ) { loadingStageVisible, deduping -> loadingStageVisible || deduping }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val articleClusters: StateFlow<List<ArticleClusterUiModel>> = feedUiState
        .combine(frozenClustersWhileProcessing) { state, frozenClusters -> state to frozenClusters }
        .scan(emptyList<ArticleClusterUiModel>()) { previous, (state, frozenClusters) ->
            when {
                frozenClusters != null -> frozenClusters
                state.isDedupInProgress && previous.isNotEmpty() -> previous
                state.clusters.isNotEmpty() -> state.clusters
                else -> emptyList()
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val firstLocalState = feedUiState.first { !it.isInitial }
            val initialFrozenClusters = if (firstLocalState.isDedupInProgress) {
                isDedupInProgress.first { !it }
                articleClusters.value.ifEmpty { feedUiState.value.clusters }
            } else {
                firstLocalState.clusters
            }
            setFeedLoadingStage(null, "database_loaded_before_initial_refresh")
            refresh(frozenClusters = initialFrozenClusters)
        }

        viewModelScope.launch {
            isFeedProcessing
                .collect { isProcessing ->
                    if (!isProcessing) {
                        frozenClustersWhileProcessing.value = null
                    }
                }
        }

        viewModelScope.launch {
            articleRepository.dataInvalidationSignal
                .drop(1) // Пропускаємо початкове значення 0
                .collect {
                    if (_isRefreshing.value) return@collect
                    val now = System.currentTimeMillis()
                    if (now - lastRefreshFinishedAt >= AUTO_REFRESH_AFTER_INVALIDATION_SUPPRESSION_MS) {
                        refresh()
                    }
                }
        }
    }

    fun refresh() {
        refresh(frozenClusters = articleClusters.value)
    }

    private fun refresh(frozenClusters: List<ArticleClusterUiModel>) {
        viewModelScope.launch {
            if (_isRefreshing.value || isDedupInProgress.value) {
                Log.d(
                    FEED_STATES_LOG_TAG,
                    "refresh_rejected refreshing=${_isRefreshing.value} dedupActive=${isDedupInProgress.value} " +
                        "stage=${_feedLoadingStage.value}"
                )
                return@launch
            }
            Log.d(
                FEED_STATES_LOG_TAG,
                "refresh_start frozenClusters=${frozenClusters.size} dedupActive=${isDedupInProgress.value} " +
                    "currentStage=${_feedLoadingStage.value}"
            )
            if (frozenClustersWhileProcessing.value == null) {
                frozenClustersWhileProcessing.value = frozenClusters
            }
            _isRefreshing.value = true
            setFeedLoadingStage(FeedLoadingStage.PARSING_NEWS, "refresh_started")
            try {
                val result = refreshFeedUseCase { stage ->
                    setFeedLoadingStage(stage.toFeedLoadingStage(), "refresh_use_case_stage")
                }
                Log.d(FEED_STATES_LOG_TAG, "refresh_use_case_complete success=${result.isSuccess}")
                if (result.isSuccess) {
                    Log.d(FEED_STATES_LOG_TAG, "post_refresh_invalidation_triggered")
                    articleRepository.triggerDataInvalidation()
                }
            } finally {
                setFeedLoadingStage(null, "refresh_finally")
                _isRefreshing.value = false
                lastRefreshFinishedAt = System.currentTimeMillis()
                Log.d(FEED_STATES_LOG_TAG, "refresh_finish lastRefreshFinishedAt=$lastRefreshFinishedAt")
            }
        }
    }

    private fun setFeedLoadingStage(stage: FeedLoadingStage?, reason: String) {
        val previousStage = _feedLoadingStage.value
        if (previousStage == stage) {
            Log.d(FEED_STATES_LOG_TAG, "stage_unchanged stage=$stage reason=$reason")
            return
        }
        Log.d(FEED_STATES_LOG_TAG, "stage_change from=$previousStage to=$stage reason=$reason")
        _feedLoadingStage.value = stage
    }

    fun onSearchQueryChange(query: String) { _searchQuery.value = query }
    fun selectGroup(groupId: Long?) { _selectedGroupId.value = groupId }
    fun setDateFilter(filter: DateFilter) { _dateFilter.value = filter }
    fun setSavedFilter(filter: SavedFilter) { _savedFilter.value = filter }
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

    private data class FeedUiState(
        val clusters: List<ArticleClusterUiModel>,
        val isDedupInProgress: Boolean,
        val isInitial: Boolean
    )

    private sealed interface FeedResultState {
        data object Initial : FeedResultState
        data class Loaded(val feedResult: FeedArticlesBuilder.FeedResult) : FeedResultState
    }

    private fun FeedRefreshStage.toFeedLoadingStage(): FeedLoadingStage {
        return when (this) {
            FeedRefreshStage.PARSING_NEWS -> FeedLoadingStage.PARSING_NEWS
            FeedRefreshStage.DEDUPLICATING_NEWS -> FeedLoadingStage.DEDUPLICATING_NEWS
        }
    }

    private fun StateFlow<FeedLoadingStage?>.withStableVisibleStageDuration(): Flow<FeedLoadingStage?> = flow {
        var visibleStage: FeedLoadingStage? = null
        var visibleStageStartedAt = 0L

        collect { requestedStage ->
            if (requestedStage == visibleStage) return@collect
            if (requestedStage == null) {
                delay(LOADING_STAGE_FINISH_SETTLE_MS)
            }

            val now = System.currentTimeMillis()
            val visibleDuration = now - visibleStageStartedAt
            if (visibleStageStartedAt > 0L && visibleDuration < MIN_LOADING_STAGE_VISIBLE_MS) {
                delay(MIN_LOADING_STAGE_VISIBLE_MS - visibleDuration)
            }

            val latestStage = value
            if (latestStage != visibleStage) {
                visibleStage = latestStage
                visibleStageStartedAt = System.currentTimeMillis()
                Log.d(FEED_STATES_LOG_TAG, "visible_stage_emit stage=$latestStage")
                emit(latestStage)
            }
        }
    }

    private companion object {
        private const val MIN_LOADING_STAGE_VISIBLE_MS = 500L
        private const val LOADING_STAGE_FINISH_SETTLE_MS = 500L
        private const val AUTO_REFRESH_AFTER_INVALIDATION_SUPPRESSION_MS = 6_000L
        private const val FEED_STATES_LOG_TAG = "FeedStatesDebug"
    }
}








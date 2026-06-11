package com.andrewwin.sumup.ui.screen.feed

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andrewwin.sumup.R
import com.andrewwin.sumup.domain.export.model.ExportDestination
import com.andrewwin.sumup.domain.export.model.FeedExportArticle
import com.andrewwin.sumup.domain.feed.pipeline.FeedArticlesBuilder
import com.andrewwin.sumup.domain.article.repository.ArticleRepository
import com.andrewwin.sumup.domain.source.repository.SourceRepository
import com.andrewwin.sumup.domain.settings.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.settings.model.UserSettings
import com.andrewwin.sumup.domain.source.model.SourceGroupWithSources
import com.andrewwin.sumup.domain.export.service.ExportFeedUseCase
import com.andrewwin.sumup.domain.feed.usecase.ArticleBookmarkToggleRequest
import com.andrewwin.sumup.domain.feed.usecase.FeedRefreshCoordinator
import com.andrewwin.sumup.domain.feed.usecase.FeedRefreshCoordinatorState
import com.andrewwin.sumup.domain.feed.usecase.FeedRefreshStatus
import com.andrewwin.sumup.domain.feed.usecase.ToggleArticleBookmarkUseCase
import com.andrewwin.sumup.ui.screen.feed.model.ArticleClusterUiModel
import com.andrewwin.sumup.ui.screen.feed.model.ArticleUiModel
import com.andrewwin.sumup.ui.screen.feed.model.SourceGroupUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

import com.andrewwin.sumup.ui.screen.feed.model.DateFilter
import com.andrewwin.sumup.ui.screen.feed.model.FeedLoadingStage
import com.andrewwin.sumup.ui.screen.feed.model.SavedFilter

@HiltViewModel
class FeedViewModel @Inject constructor(
    application: Application,
    private val articleRepository: ArticleRepository,
    private val feedRefreshCoordinator: FeedRefreshCoordinator,
    private val feedArticlesBuilder: FeedArticlesBuilder,
    private val exportFeedUseCase: ExportFeedUseCase,
    private val toggleArticleBookmarkUseCase: ToggleArticleBookmarkUseCase,
    private val sourceRepository: SourceRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val feedUiModelMapper: FeedUiModelMapper
) : AndroidViewModel(application) {
    private val favoriteOverrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedGroupId = MutableStateFlow<Long?>(null)
    val selectedGroupId: StateFlow<Long?> = _selectedGroupId.asStateFlow()

    private val _dateFilter = MutableStateFlow(DateFilter.ALL)
    val dateFilter: StateFlow<DateFilter> = _dateFilter.asStateFlow()

    private val _savedFilter = MutableStateFlow(SavedFilter.ALL)
    val savedFilter: StateFlow<SavedFilter> = _savedFilter.asStateFlow()

    private val _toastMessageResId = MutableStateFlow<Int?>(null)
    val toastMessageResId: StateFlow<Int?> = _toastMessageResId.asStateFlow()

    val isRefreshing: StateFlow<Boolean> = feedRefreshCoordinator.state
        .map { it.isRefreshing }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _feedLoadingStage = MutableStateFlow<FeedLoadingStage?>(FeedLoadingStage.LOADING_FROM_DATABASE)
    private val frozenClustersWhileProcessing = MutableStateFlow<List<ArticleClusterUiModel>?>(null)
    private val activeFeedBuildSignal = MutableStateFlow<Long?>(null)
    private val activeFeedBuildStartedAt = MutableStateFlow<Long?>(null)
    private val _isBuildingFeed = MutableStateFlow(false)
    val isBuildingFeed: StateFlow<Boolean> = _isBuildingFeed.asStateFlow()
    private var lastRefreshFinishedAt = 0L

    val userPreferences: StateFlow<UserSettings> = userPreferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    private val groupsWithSources: StateFlow<List<SourceGroupWithSources>> = sourceRepository.groupsWithSources
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groups = groupsWithSources
        .map { list ->
            val uncategorizedName = getApplication<Application>().getString(R.string.group_uncategorized)
            list.map { it.group }
                .filter { it.isEnabled }
                .map { group ->
                    SourceGroupUiModel(
                        id = group.id,
                        displayName = if (group.id == UNCATEGORIZED_GROUP_ID && !group.isDeletable) {
                            uncategorizedName
                        } else {
                            group.name
                        }
                    )
                }
        }
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
            old.fingerprint == new.fingerprint &&
                old.invalidationSignal == new.invalidationSignal
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
                isInitial = true
            )
        val mappingStartedAt = System.currentTimeMillis()
        val mappedClusters = feedUiModelMapper.map(
            clusters = feed.clusters,
            groupsWithSources = groupsList,
            favoriteSavedAt = favoriteSavedAt,
            ellipsis = getApplication<Application>().getString(R.string.ellipsis)
        )
        val clusters = applyFavoriteOverrides(mappedClusters, overrides)
        Log.d(
            FEED_BUILD_PROFILE_LOG_TAG,
            "ui_mapping durationMs=${System.currentTimeMillis() - mappingStartedAt} " +
                "domainClusters=${feed.clusters.size} uiClusters=${clusters.size} " +
                "favoriteSavedAt=${favoriteSavedAt.size} signal=${feed.invalidationSignal}"
        )

        FeedUiState(
            clusters = clusters,
            isInitial = false,
            invalidationSignal = feed.invalidationSignal,
            fingerprint = buildUiFingerprint(
                clusters = clusters,
                feedFingerprint = feed.fingerprint,
                favoriteSavedAt = favoriteSavedAt
            )
        )
    }
        .distinctUntilChanged { old, new ->
            old.isInitial == new.isInitial &&
                old.invalidationSignal == new.invalidationSignal &&
                old.fingerprint == new.fingerprint
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FeedUiState(emptyList(), true))

    val isDedupInProgress: StateFlow<Boolean> = _feedLoadingStage
        .map { it == FeedLoadingStage.DEDUPLICATING_NEWS }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val feedLoadingStage: StateFlow<FeedLoadingStage?> = _feedLoadingStage
        .withStableVisibleStageDuration()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FeedLoadingStage.LOADING_FROM_DATABASE)

    val isAnyLoading: StateFlow<Boolean> = _feedLoadingStage
        .map { it != null }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val isFeedProcessing: StateFlow<Boolean> = isAnyLoading

    val articleClusters: StateFlow<List<ArticleClusterUiModel>> = feedUiState
        .combine(frozenClustersWhileProcessing) { state, frozenClusters -> state to frozenClusters }
        .combine(activeFeedBuildSignal) { (state, frozenClusters), buildSignal ->
            Triple(state, frozenClusters, buildSignal)
        }
        .scan(emptyList<ArticleClusterUiModel>()) { previous, (state, frozenClusters, buildSignal) ->
            when {
                buildSignal != null && state.invalidationSignal < buildSignal -> frozenClusters ?: previous
                frozenClusters != null -> frozenClusters
                state.clusters.isNotEmpty() -> state.clusters
                else -> emptyList()
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            feedRefreshCoordinator.state
                .collect { state ->
                    handleRefreshCoordinatorState(state)
                }
        }

        viewModelScope.launch {
            feedUiState
                .first { state -> !state.isInitial }
                .let {
                    if (_feedLoadingStage.value == FeedLoadingStage.LOADING_FROM_DATABASE &&
                        activeFeedBuildSignal.value == null
                    ) {
                        setFeedLoadingStage(null, "initial_feed_state_loaded")
                    }
                }
        }

        viewModelScope.launch {
            isFeedProcessing
                .collect { isProcessing ->
                    if (!isProcessing && activeFeedBuildSignal.value == null) {
                        frozenClustersWhileProcessing.value = null
                    }
                }
        }

        viewModelScope.launch {
            feedUiState
                .combine(activeFeedBuildSignal) { state, buildSignal -> state to buildSignal }
                .collect { (state, buildSignal) ->
                    if (buildSignal == null || state.isInitial || state.invalidationSignal < buildSignal) {
                        return@collect
                    }
                    Log.d(
                        FEED_STATES_LOG_TAG,
                        "post_refresh_build_ready signal=$buildSignal clusters=${state.clusters.size}"
                    )
                    val durationMs = activeFeedBuildStartedAt.value?.let { System.currentTimeMillis() - it } ?: -1L
                    Log.d(
                        FEED_BUILD_PROFILE_LOG_TAG,
                        "ui_state_ready signal=$buildSignal durationMs=$durationMs clusters=${state.clusters.size}"
                    )
                    activeFeedBuildSignal.value = null
                    activeFeedBuildStartedAt.value = null
                    frozenClustersWhileProcessing.value = null
                    _isBuildingFeed.value = false
                    feedRefreshCoordinator.markFeedBuildFinished(buildSignal)
                    setFeedLoadingStage(null, "post_refresh_feed_state_ready signal=$buildSignal")
                }
        }

        viewModelScope.launch {
            articleRepository.feedRefreshRequests
                .filter { requestTimestamp -> requestTimestamp > 0L }
                .collect { requestTimestamp ->
                    val coordinatorState = feedRefreshCoordinator.state.value
                    if (coordinatorState.isRefreshing || coordinatorState.buildSignal == requestTimestamp) {
                        return@collect
                    }
                    val now = System.currentTimeMillis()
                    if (now - lastRefreshFinishedAt >= AUTO_REFRESH_AFTER_INVALIDATION_SUPPRESSION_MS) {
                        refreshNow(frozenClusters = articleClusters.value)
                    }
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            refreshNow(frozenClusters = articleClusters.value)
        }
    }

    private suspend fun refreshNow(frozenClusters: List<ArticleClusterUiModel>) {
        val coordinatorState = feedRefreshCoordinator.state.value
        val isDedupActive = coordinatorState.status == FeedRefreshStatus.DEDUPLICATING_NEWS
        if (coordinatorState.isRefreshing || isDedupActive) {
            Log.d(
                FEED_STATES_LOG_TAG,
                "refresh_rejected refreshing=${coordinatorState.isRefreshing} dedupActive=$isDedupActive " +
                    "stage=${_feedLoadingStage.value}"
            )
            return
        }
        Log.d(
            FEED_STATES_LOG_TAG,
            "refresh_start frozenClusters=${frozenClusters.size} dedupActive=$isDedupActive " +
                "currentStage=${_feedLoadingStage.value}"
        )
        if (frozenClustersWhileProcessing.value == null) {
            frozenClustersWhileProcessing.value = frozenClusters
        }
        val result = feedRefreshCoordinator.refreshManually()
        if (result.getOrNull()?.cloudEmbeddingsIncomplete == true) {
            _toastMessageResId.value = R.string.cloud_embeddings_incomplete_toast
        }
        if (result.isFailure && activeFeedBuildSignal.value == null) {
            _isBuildingFeed.value = false
            setFeedLoadingStage(null, "refresh_failed")
        }
        lastRefreshFinishedAt = System.currentTimeMillis()
        Log.d(FEED_STATES_LOG_TAG, "refresh_finish lastRefreshFinishedAt=$lastRefreshFinishedAt")
    }

    private suspend fun setFeedLoadingStage(stage: FeedLoadingStage?, reason: String) {
        withContext(Dispatchers.Main.immediate) {
            val previousStage = _feedLoadingStage.value
            if (previousStage == stage) {
                Log.d(FEED_STATES_LOG_TAG, "stage_unchanged stage=$stage reason=$reason")
                return@withContext
            }
            Log.d(FEED_STATES_LOG_TAG, "stage_change from=$previousStage to=$stage reason=$reason")
            _feedLoadingStage.value = stage
        }
    }

    fun onSearchQueryChange(query: String) { _searchQuery.value = query }
    fun clearToastMessage() { _toastMessageResId.value = null }
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
                updated[article.url] = newFavorite
            }
            updated
        }
        viewModelScope.launch {
            runCatching {
                toggleArticleBookmarkUseCase(
                    ArticleBookmarkToggleRequest(
                        articles = clusterArticles,
                        scoreByArticleId = buildMap {
                            put(cluster.representative.article.id, 0f)
                            cluster.duplicates.forEach { (duplicate, score) ->
                                put(duplicate.article.id, score)
                            }
                        },
                        clusterRepresentativeId = cluster.representative.article.id
                    )
                )
            }.onFailure {
                favoriteOverrides.value = previousOverrides
            }
        }
    }

    private fun applyFavoriteOverrides(
        clusters: List<ArticleClusterUiModel>,
        overrides: Map<String, Boolean>
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
        overrides: Map<String, Boolean>
    ): ArticleUiModel {
        val override = overrides[uiModel.article.url] ?: return uiModel
        if (uiModel.article.isFavorite == override) return uiModel
        return uiModel.copy(article = uiModel.article.copy(isFavorite = override))
    }

    private data class FeedUiState(
        val clusters: List<ArticleClusterUiModel>,
        val isInitial: Boolean,
        val invalidationSignal: Long = 0L,
        val fingerprint: Long = 0L
    )

    private sealed interface FeedResultState {
        data object Initial : FeedResultState
        data class Loaded(val feedResult: FeedArticlesBuilder.FeedResult) : FeedResultState
    }

    private suspend fun handleRefreshCoordinatorState(state: FeedRefreshCoordinatorState) {
        when (state.status) {
            FeedRefreshStatus.IDLE -> {
                if (
                    activeFeedBuildSignal.value == null &&
                    _feedLoadingStage.value != FeedLoadingStage.LOADING_FROM_DATABASE
                ) {
                    _isBuildingFeed.value = false
                    setFeedLoadingStage(null, "refresh_coordinator_idle")
                }
            }
            FeedRefreshStatus.PARSING_NEWS -> {
                freezeClustersForRefresh()
                setFeedLoadingStage(FeedLoadingStage.PARSING_NEWS, "refresh_coordinator_parsing")
            }
            FeedRefreshStatus.DEDUPLICATING_NEWS -> {
                freezeClustersForRefresh()
                setFeedLoadingStage(FeedLoadingStage.DEDUPLICATING_NEWS, "refresh_coordinator_deduplicating")
            }
            FeedRefreshStatus.BUILDING_FEED -> {
                val signal = state.buildSignal ?: return
                freezeClustersForRefresh()
                if (activeFeedBuildSignal.value != signal) {
                    Log.d(
                        FEED_BUILD_PROFILE_LOG_TAG,
                        "post_refresh_build_start signal=$signal frozenClusters=${articleClusters.value.size}"
                    )
                    activeFeedBuildSignal.value = signal
                    activeFeedBuildStartedAt.value = System.currentTimeMillis()
                    _isBuildingFeed.value = true
                }
                setFeedLoadingStage(
                    stage = FeedLoadingStage.BUILDING_UPDATED_FEED,
                    reason = "refresh_coordinator_building signal=$signal"
                )
            }
        }
    }

    private fun freezeClustersForRefresh() {
        if (frozenClustersWhileProcessing.value == null) {
            frozenClustersWhileProcessing.value = articleClusters.value
        }
    }

    suspend fun exportFeed(
        articles: List<ArticleUiModel>,
        uri: Uri,
        includeMedia: Boolean
    ): Result<Unit> {
        return exportFeedUseCase(
            articles = articles.map { article ->
                FeedExportArticle(
                    title = article.displayTitle,
                    content = article.displayContent,
                    sourceName = article.sourceName,
                    publishedAt = article.article.publishedAt,
                    articleUrl = article.article.url,
                    mediaUrl = article.article.mediaUrl
                )
            },
            destination = ExportDestination {
                getApplication<Application>().contentResolver.openOutputStream(uri)
            },
            includeMedia = includeMedia
        )
    }

    private fun StateFlow<FeedLoadingStage?>.withStableVisibleStageDuration(): Flow<FeedLoadingStage?> {
        var visibleStage: FeedLoadingStage? = null
        var visibleStageStartedAt = 0L

        return transformLatest { requestedStage ->
            if (requestedStage == visibleStage) return@transformLatest

            if (requestedStage != null) {
                visibleStage = requestedStage
                visibleStageStartedAt = System.currentTimeMillis()
                Log.d(FEED_STATES_LOG_TAG, "visible_stage_emit stage=$requestedStage")
                emit(requestedStage)
                return@transformLatest
            }

            delay(LOADING_STAGE_FINISH_SETTLE_MS)

            val visibleDuration = System.currentTimeMillis() - visibleStageStartedAt
            if (visibleStageStartedAt > 0L && visibleDuration < MIN_LOADING_STAGE_VISIBLE_MS) {
                delay(MIN_LOADING_STAGE_VISIBLE_MS - visibleDuration)
            }

            if (value != null || visibleStage == null) return@transformLatest

            visibleStage = null
            Log.d(FEED_STATES_LOG_TAG, "visible_stage_emit stage=null")
            emit(null)
        }
    }

    private companion object {
        private const val UNCATEGORIZED_GROUP_ID = 1L
        private const val MIN_LOADING_STAGE_VISIBLE_MS = 250L
        private const val LOADING_STAGE_FINISH_SETTLE_MS = 250L
        private const val AUTO_REFRESH_AFTER_INVALIDATION_SUPPRESSION_MS = 6_000L
        private const val FEED_STATES_LOG_TAG = "FeedStatesDebug"
        private const val FEED_BUILD_PROFILE_LOG_TAG = "FeedBuildProfile"
    }

    private fun buildUiFingerprint(
        clusters: List<ArticleClusterUiModel>,
        feedFingerprint: Long,
        favoriteSavedAt: Map<Long, Long>
    ): Long {
        var fingerprint = feedFingerprint
        clusters.forEach { cluster ->
            fingerprint = fingerprint * 31 + cluster.representative.article.id
            fingerprint = fingerprint * 31 + cluster.representative.article.isFavorite.hashCode().toLong()
            fingerprint = fingerprint * 31 + (cluster.representative.savedAt ?: 0L)
            fingerprint = fingerprint * 31 + cluster.duplicates.size
            cluster.duplicates.forEach { (articleUi, score) ->
                fingerprint = fingerprint * 31 + articleUi.article.id
                fingerprint = fingerprint * 31 + articleUi.article.isFavorite.hashCode().toLong()
                fingerprint = fingerprint * 31 + (articleUi.savedAt ?: 0L)
                fingerprint = fingerprint * 31 + score.toRawBits()
            }
        }
        fingerprint = fingerprint * 31 + favoriteSavedAt.size
        return fingerprint
    }
}






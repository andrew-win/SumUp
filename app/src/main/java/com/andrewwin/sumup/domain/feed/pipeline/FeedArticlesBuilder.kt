package com.andrewwin.sumup.domain.feed.pipeline

import android.util.Log
import com.andrewwin.sumup.domain.article.Article
import com.andrewwin.sumup.domain.feed.FeedSearchMatcher
import com.andrewwin.sumup.domain.feed.clustering.ArticlePairKey
import com.andrewwin.sumup.domain.feed.clustering.FeedClusterCalculator
import com.andrewwin.sumup.domain.news.ArticleCluster
import com.andrewwin.sumup.domain.news.ArticleImportanceScorer
import com.andrewwin.sumup.domain.news.SimilarityScorer
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.FeedClusterSnapshotRepository
import com.andrewwin.sumup.domain.repository.SourceRepository
import com.andrewwin.sumup.domain.settings.DeduplicationStrategy
import com.andrewwin.sumup.domain.settings.UserSettings
import com.andrewwin.sumup.domain.source.SourceGroupWithSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject

class FeedArticlesBuilder @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val sourceRepository: SourceRepository,
    private val feedSearchMatcher: FeedSearchMatcher,
    private val similarityScorer: SimilarityScorer,
    private val feedClusterSnapshotStore: FeedClusterSnapshotRepository
) {
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    operator fun invoke(
        searchQueryFlow: Flow<String>,
        selectedGroupIdFlow: Flow<Long?>,
        dateFilterHoursFlow: Flow<Int?>,
        savedOnlyFlow: Flow<Boolean>,
        userPreferencesFlow: Flow<UserSettings>
    ): Flow<FeedResult> {
        val collectorId = feedCollectorId++

        val feedDataFlow = combine(
            articleRepository.enabledArticles,
            articleRepository.favoriteArticles,
            sourceRepository.groupsWithSources,
            searchQueryFlow
        ) { enabledArticles, favoriteArticles, groups, query ->
            FeedData(enabledArticles, favoriteArticles, groups, query)
        }.distinctUntilChanged { old, new ->
            old.enabledArticles.size == new.enabledArticles.size &&
                old.enabledArticles.zip(new.enabledArticles).all { (oldArticle, newArticle) ->
                    oldArticle.id == newArticle.id &&
                        oldArticle.title == newArticle.title &&
                        oldArticle.content == newArticle.content &&
                        oldArticle.publishedAt == newArticle.publishedAt &&
                        oldArticle.sourceId == newArticle.sourceId &&
                        oldArticle.url == newArticle.url &&
                        oldArticle.isFavorite == newArticle.isFavorite &&
                        oldArticle.isRead == newArticle.isRead
                } &&
                old.query == new.query &&
                old.favoriteArticles.size == new.favoriteArticles.size &&
                old.groupsWithSources == new.groupsWithSources
        }

        val filterParamsFlow = combine(
            selectedGroupIdFlow,
            dateFilterHoursFlow,
            savedOnlyFlow,
            userPreferencesFlow,
            articleRepository.feedRefreshRequests
        ) { groupId, dateFilterHours, savedOnly, prefs, signal ->
            FeedFilterParams(groupId, dateFilterHours, savedOnly, prefs, signal)
        }

        return combine(feedDataFlow, filterParamsFlow) { data, params ->
            val startedAt = System.currentTimeMillis()
            buildPipelineState(data, params).also { state ->
                Log.d(
                    FEED_BUILD_PROFILE_LOG_TAG,
                    "pipeline_state collectorId=$collectorId durationMs=${System.currentTimeMillis() - startedAt} " +
                        "articles=${state.articles.size} savedOnly=${state.savedOnly} signal=${state.invalidationSignal}"
                )
            }
        }
            .flowOn(Dispatchers.Default)
            .debounce(FEED_SNAPSHOT_DEBOUNCE_MS)
            .flatMapLatest { state ->
                flow {
                    val emitStartedAt = System.currentTimeMillis()
                    val buildClustersStartedAt = System.currentTimeMillis()
                    val clusters = buildClusters(state)
                    val filteredClusters = applyMinMentionsFilter(clusters, state.prefs, state.savedOnly)
                    emit(FeedResult(filteredClusters, state.invalidationSignal))
                }
            }
    }

    private fun buildPipelineState(data: FeedData, params: FeedFilterParams): FeedPipelineState {
        var processedArticles = if (params.savedOnly) data.favoriteArticles else data.enabledArticles

        if (!params.savedOnly) {
            params.groupId?.let { groupId ->
                val sourceIds = data.groupsWithSources
                    .firstOrNull { it.group.id == groupId }
                    ?.sources
                    ?.map { it.id }
                    .orEmpty()
                processedArticles = processedArticles.filter { it.sourceId in sourceIds }
            }

            params.dateFilterHours?.let { hours ->
                val threshold = System.currentTimeMillis() - (hours * 60 * 60 * 1000L)
                processedArticles = processedArticles.filter { it.publishedAt >= threshold }
            }
        }

        if (params.savedOnly) {
            processedArticles = processedArticles.filter { it.isFavorite }
        }

        if (!params.savedOnly && data.query.isNotBlank()) {
            val tokens = feedSearchMatcher.tokenizeQuery(data.query)
            processedArticles = processedArticles.filter { article ->
                feedSearchMatcher.matchesQueryWithTokenThreshold(
                    title = article.title,
                    content = article.content,
                    queryTokens = tokens
                )
            }
        }

        if (!params.savedOnly && params.prefs.isImportanceFilterEnabled) {
            processedArticles = processedArticles.filter { article ->
                article.importanceScore >= ArticleImportanceScorer.IMPORTANCE_THRESHOLD
            }
        }

        return FeedPipelineState(
            articles = processedArticles,
            prefs = params.prefs,
            savedOnly = params.savedOnly,
            invalidationSignal = params.invalidationSignal
        )
    }

    private suspend fun buildClusters(state: FeedPipelineState): List<ArticleCluster> {
        val startedAt = System.currentTimeMillis()
        if (state.articles.isEmpty()) return emptyList()
        if (state.savedOnly) return buildSavedFavoriteClusters(state.articles, state.prefs)
        if (!state.prefs.isDeduplicationEnabled) {
            return state.articles.map { ArticleCluster(it, emptyList()) }
        }

        return withContext(Dispatchers.IO) {
            buildClustersFromDb(state.articles, state.prefs).ifEmpty {
                state.articles.map { ArticleCluster(it, emptyList()) }
            }.also { clusters ->
                Log.d(
                    FEED_BUILD_PROFILE_LOG_TAG,
                    "build_clusters durationMs=${System.currentTimeMillis() - startedAt} " +
                        "articles=${state.articles.size} clusters=${clusters.size} savedOnly=${state.savedOnly}"
                )
            }
        }
    }

    private fun applyMinMentionsFilter(
        clusters: List<ArticleCluster>,
        prefs: UserSettings,
        savedOnly: Boolean
    ): List<ArticleCluster> {
        if (savedOnly) return clusters
        if (!prefs.isDeduplicationEnabled) return clusters
        if (clusters.isEmpty()) return clusters

        return clusters.filter { cluster ->
            val mentions = cluster.duplicates.size + 1
            val isSingle = cluster.duplicates.isEmpty()
            val requiredMentions = prefs.minMentions.coerceAtLeast(2)

            if (isSingle) {
                !prefs.isHideSingleNewsEnabled
            } else {
                mentions >= requiredMentions
            }
        }
    }

    private suspend fun buildClustersFromDb(
        currentArticles: List<Article>,
        prefs: UserSettings
    ): List<ArticleCluster> {
        val startedAt = System.currentTimeMillis()
        val ids = currentArticles.map { it.id }
        val strategyKey = similarityScorer.similarityCacheKeyForStrategy(prefs.deduplicationStrategy)
        val threshold = prefs.deduplicationThreshold()
        val clusteringSettingsSignature = feedClusterSnapshotStore.buildClusteringSettingsSignature(
            strategyKey = strategyKey,
            threshold = threshold
        )
        feedClusterSnapshotStore.loadClusters(
            articles = currentArticles,
            clusteringSettingsSignature = clusteringSettingsSignature
        )?.let { clusters ->
            Log.d(
                FEED_BUILD_PROFILE_LOG_TAG,
                "clusters_from_db snapshotHit=true durationMs=${System.currentTimeMillis() - startedAt} " +
                    "articles=${currentArticles.size} clusters=${clusters.size}"
            )
            return clusters
        }

        val similaritiesStartedAt = System.currentTimeMillis()
        val similarities = articleRepository.getSimilaritiesInsideArticleSetAboveThreshold(
            articleIds = ids,
            strategyKey = strategyKey,
            threshold = threshold
        )
        val similaritiesDurationMs = System.currentTimeMillis() - similaritiesStartedAt
        if (similarities.isEmpty()) return emptyList()

        val clusterCalculationStartedAt = System.currentTimeMillis()
        val currentArticleIds = currentArticles.mapTo(mutableSetOf()) { it.id }
        val pairScores = similarities
            .asSequence()
            .filter { it.leftArticleId in currentArticleIds && it.rightArticleId in currentArticleIds }
            .associate { similarity ->
                ArticlePairKey.of(similarity.leftArticleId, similarity.rightArticleId) to similarity.score
            }

        val clusters = FeedClusterCalculator.buildFinalClusters(currentArticles, pairScores)
        val clusterCalculationDurationMs = System.currentTimeMillis() - clusterCalculationStartedAt
        feedClusterSnapshotStore.saveClusters(
            articles = currentArticles,
            clusteringSettingsSignature = clusteringSettingsSignature,
            clusters = clusters
        )
        return clusters
    }

    private suspend fun buildSavedFavoriteClusters(
        favoriteArticles: List<Article>,
        prefs: UserSettings
    ): List<ArticleCluster> {
        if (favoriteArticles.isEmpty()) return emptyList()
        val byId = favoriteArticles.associateBy { it.id }
        val mappings = articleRepository.getFavoriteClusterMappings(favoriteArticles.map { it.id })
        val savedAtById = articleRepository.getFavoriteSavedAt(favoriteArticles.map { it.id })
        val strategyKey = similarityScorer.similarityCacheKeyForStrategy(prefs.deduplicationStrategy)
        val threshold = prefs.deduplicationThreshold()
        val similarities = articleRepository.getFavoriteSimilarities(favoriteArticles.map { it.id }, strategyKey)
        val savedClusterScores = articleRepository.getFavoriteClusterScores(favoriteArticles.map { it.id })
        val scoreMap = buildMap<Pair<Long, Long>, Float> {
            similarities
                .filter { it.score >= threshold }
                .forEach { similarity ->
                    put(similarity.leftArticleId to similarity.rightArticleId, similarity.score)
                    put(similarity.rightArticleId to similarity.leftArticleId, similarity.score)
            }
        }

        val groupedByKey = mappings.entries
            .groupBy(
                keySelector = { it.value },
                valueTransform = { it.key }
            )
            .mapValues { (_, ids) -> ids.mapNotNull { byId[it] } }
            .filterValues { it.size >= 2 }

        val clusters = mutableListOf<ArticleCluster>()
        val clusteredIds = mutableSetOf<Long>()
        val clusterSavedAt = mutableMapOf<Long, Long>()

        groupedByKey.values.forEach { articles ->
            val representative = FeedClusterCalculator.selectRepresentativeArticleForCluster(articles)
            val duplicates = articles
                .asSequence()
                .filterNot { it.id == representative.id }
                .mapNotNull { article ->
                    val score = scoreMap[representative.id to article.id]
                        ?: scoreMap[article.id to representative.id]
                        ?: savedClusterScores[article.id]
                        ?: return@mapNotNull null
                    article to score
                }
                .toList()
            clusters.add(ArticleCluster(representative, duplicates))
            val savedAt = articles.maxOfOrNull { savedAtById[it.id] ?: 0L } ?: 0L
            clusterSavedAt[representative.id] = savedAt
            articles.forEach { clusteredIds.add(it.id) }
        }

        favoriteArticles
            .asSequence()
            .filterNot { it.id in clusteredIds }
            .forEach { article ->
                clusters.add(ArticleCluster(article, emptyList()))
                clusterSavedAt[article.id] = savedAtById[article.id] ?: 0L
            }

        return clusters.sortedWith(
            compareByDescending<ArticleCluster> { clusterSavedAt[it.representative.id] ?: 0L }
                .thenByDescending { it.representative.publishedAt }
        )
    }

    data class FeedResult(
        val clusters: List<ArticleCluster>,
        val invalidationSignal: Long
    )

    private data class FeedPipelineState(
        val articles: List<Article>,
        val prefs: UserSettings,
        val savedOnly: Boolean,
        val invalidationSignal: Long
    )

    private data class FeedFilterParams(
        val groupId: Long?,
        val dateFilterHours: Int?,
        val savedOnly: Boolean,
        val prefs: UserSettings,
        val invalidationSignal: Long
    )

    private data class FeedData(
        val enabledArticles: List<Article>,
        val favoriteArticles: List<Article>,
        val groupsWithSources: List<SourceGroupWithSources>,
        val query: String
    )

    companion object {
        private const val FEED_BUILD_PROFILE_LOG_TAG = "FeedBuildProfile"
        private const val FEED_SNAPSHOT_DEBOUNCE_MS = 200L
        private var feedCollectorId = 0L
    }
}

private fun UserSettings.deduplicationThreshold(): Float =
    when (deduplicationStrategy) {
        DeduplicationStrategy.LOCAL -> localDeduplicationThreshold
        DeduplicationStrategy.CLOUD -> cloudDeduplicationThreshold
    }

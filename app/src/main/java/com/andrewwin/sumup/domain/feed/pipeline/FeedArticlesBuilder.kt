package com.andrewwin.sumup.domain.feed.pipeline

import android.util.Log
import com.andrewwin.sumup.domain.article.model.Article
import com.andrewwin.sumup.domain.feed.clustering.FeedClusterCalculator
import com.andrewwin.sumup.domain.feed.model.ArticleCluster
import com.andrewwin.sumup.domain.article.processing.ArticleImportanceScorer
import com.andrewwin.sumup.domain.article.deduplication.SimilarityScorer
import com.andrewwin.sumup.domain.article.repository.ArticleRepository
import com.andrewwin.sumup.domain.feed.dedup.ThresholdSimilarityResolver
import com.andrewwin.sumup.domain.source.repository.SourceRepository
import com.andrewwin.sumup.domain.settings.model.DeduplicationStrategy
import com.andrewwin.sumup.domain.settings.model.UserSettings
import com.andrewwin.sumup.domain.source.model.SourceGroupWithSources
import com.andrewwin.sumup.domain.feed.model.toPairScoreMap
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
    private val thresholdSimilarityResolver: ThresholdSimilarityResolver
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

        val baseFeedDataFlow = combine(
            articleRepository.enabledArticles,
            sourceRepository.groupsWithSources,
            searchQueryFlow
        ) { enabledArticles, groups, query ->
            BaseFeedData(
                enabledArticles = enabledArticles,
                groupsWithSources = groups,
                query = query,
                fingerprint = buildBaseFeedDataFingerprint(
                    enabledArticles = enabledArticles,
                    groupsWithSources = groups,
                    query = query
                )
            )
        }.distinctUntilChanged { old, new ->
            old.fingerprint == new.fingerprint
        }

        val filterParamsFlow = combine(
            selectedGroupIdFlow,
            dateFilterHoursFlow,
            savedOnlyFlow,
            userPreferencesFlow,
            articleRepository.feedRebuildRequests
        ) { groupId, dateFilterHours, savedOnly, prefs, signal ->
            FeedFilterParams(groupId, dateFilterHours, savedOnly, prefs, signal)
        }

        return combine(baseFeedDataFlow, articleRepository.favoriteArticles, filterParamsFlow) { data, favoriteArticles, params ->
            val startedAt = System.currentTimeMillis()
            val feedData = FeedData(
                enabledArticles = data.enabledArticles,
                favoriteArticles = favoriteArticles,
                groupsWithSources = data.groupsWithSources,
                query = data.query
            )
            buildPipelineState(feedData, params).also { state ->
                Log.d(
                    FEED_BUILD_PROFILE_LOG_TAG,
                    "pipeline_state collectorId=$collectorId durationMs=${System.currentTimeMillis() - startedAt} " +
                        "articles=${state.articles.size} savedOnly=${state.savedOnly} signal=${state.invalidationSignal}"
                )
            }
        }.distinctUntilChanged { old, new ->
            old.fingerprint == new.fingerprint
        }
            .flowOn(Dispatchers.Default)
            .debounce(FEED_BUILD_DEBOUNCE_MS)
            .flatMapLatest { state ->
                flow {
                    val emitStartedAt = System.currentTimeMillis()
                    val buildClustersStartedAt = System.currentTimeMillis()
                    val clusters = buildClusters(state)
                    val filteredClusters = applyMinMentionsFilter(clusters, state.prefs, state.savedOnly)
                    emit(
                        FeedResult(
                            clusters = filteredClusters,
                            invalidationSignal = state.invalidationSignal,
                            fingerprint = buildClusterFingerprint(filteredClusters)
                        )
                    )
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

        if (!params.savedOnly) {
            params.prefs.feedTitleExcludeRegexOrNull()?.let { excludeRegex ->
                processedArticles = processedArticles.filterNot { article ->
                    excludeRegex.containsMatchIn(article.title)
                }
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
            invalidationSignal = params.invalidationSignal,
            fingerprint = buildPipelineStateFingerprint(
                articles = processedArticles,
                groupsWithSources = data.groupsWithSources,
                query = data.query,
                params = params
            )
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
            val isSingle = cluster.duplicates.isEmpty()

            if (isSingle) {
                !prefs.isHideSingleNewsEnabled
            } else {
                cluster.duplicates.size >= prefs.minMentions
            }
        }
    }

    private suspend fun buildClustersFromDb(
        currentArticles: List<Article>,
        prefs: UserSettings
    ): List<ArticleCluster> {
        val startedAt = System.currentTimeMillis()
        val threshold = prefs.deduplicationThreshold()
        val strategyKey = similarityScorer.thresholdSimilarityCacheKey(
            prefs.deduplicationStrategy,
            threshold
        )
        val similaritiesStartedAt = System.currentTimeMillis()
            val orderedPairScores = thresholdSimilarityResolver.resolveOrderedPairScores(
                articles = currentArticles,
                prefs = prefs,
                persistComputed = true,
                allowOnDemandComputation = true
            )
        val similaritiesDurationMs = System.currentTimeMillis() - similaritiesStartedAt
        if (orderedPairScores.isEmpty()) return emptyList()
        val pairScoreByKey = orderedPairScores.toPairScoreMap()

        val clusterCalculationStartedAt = System.currentTimeMillis()
        val clusters = FeedClusterCalculator.buildFinalClusters(
            articles = currentArticles,
            orderedPairScores = orderedPairScores,
            pairScoreByKey = pairScoreByKey,
            threshold = threshold
        )
        val clusterCalculationDurationMs = System.currentTimeMillis() - clusterCalculationStartedAt
        Log.d(
            FEED_BUILD_PROFILE_LOG_TAG,
            "clusters_from_db threshold_similarity_resolve_ms=$similaritiesDurationMs " +
                "clusterCalculationMs=$clusterCalculationDurationMs pairScores=${orderedPairScores.size} strategyKey=$strategyKey"
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
        val threshold = prefs.deduplicationThreshold()
        val strategyKey = similarityScorer.thresholdSimilarityCacheKey(
            prefs.deduplicationStrategy,
            threshold
        )
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
        val invalidationSignal: Long,
        val fingerprint: Long
    )

    private data class FeedPipelineState(
        val articles: List<Article>,
        val prefs: UserSettings,
        val savedOnly: Boolean,
        val invalidationSignal: Long,
        val fingerprint: Long
    )

    private data class FeedFilterParams(
        val groupId: Long?,
        val dateFilterHours: Int?,
        val savedOnly: Boolean,
        val prefs: UserSettings,
        val invalidationSignal: Long
    )

    private data class BaseFeedData(
        val enabledArticles: List<Article>,
        val groupsWithSources: List<SourceGroupWithSources>,
        val query: String,
        val fingerprint: Long
    )

    private data class FeedData(
        val enabledArticles: List<Article>,
        val favoriteArticles: List<Article>,
        val groupsWithSources: List<SourceGroupWithSources>,
        val query: String
    )

    companion object {
        private const val FEED_BUILD_PROFILE_LOG_TAG = "FeedBuildProfile"
        private const val FEED_BUILD_DEBOUNCE_MS = 200L
        private var feedCollectorId = 0L
    }

    private fun buildBaseFeedDataFingerprint(
        enabledArticles: List<Article>,
        groupsWithSources: List<SourceGroupWithSources>,
        query: String
    ): Long {
        var fingerprint = query.hashCode().toLong()
        fingerprint = fingerprint * 31 + buildArticleListFingerprint(enabledArticles)
        fingerprint = fingerprint * 31 + buildGroupsFingerprint(groupsWithSources)
        return fingerprint
    }

    private fun buildPipelineStateFingerprint(
        articles: List<Article>,
        groupsWithSources: List<SourceGroupWithSources>,
        query: String,
        params: FeedFilterParams
    ): Long {
        var fingerprint = query.hashCode().toLong()
        fingerprint = fingerprint * 31 + buildArticleListFingerprint(articles)
        fingerprint = fingerprint * 31 + buildGroupsFingerprint(groupsWithSources)
        fingerprint = fingerprint * 31 + (params.groupId ?: 0L)
        fingerprint = fingerprint * 31 + (params.dateFilterHours ?: 0)
        fingerprint = fingerprint * 31 + params.savedOnly.hashCode().toLong()
        fingerprint = fingerprint * 31 + buildFeedSettingsFingerprint(params.prefs)
        fingerprint = fingerprint * 31 + params.invalidationSignal
        return fingerprint
    }

    private fun buildFeedSettingsFingerprint(prefs: UserSettings): Long {
        var fingerprint = prefs.isDeduplicationEnabled.hashCode().toLong()
        fingerprint = fingerprint * 31 + prefs.deduplicationStrategy.hashCode().toLong()
        fingerprint = fingerprint * 31 + prefs.localDeduplicationThreshold.toRawBits()
        fingerprint = fingerprint * 31 + prefs.cloudDeduplicationThreshold.toRawBits()
        fingerprint = fingerprint * 31 + prefs.minMentions
        fingerprint = fingerprint * 31 + prefs.isHideSingleNewsEnabled.hashCode().toLong()
        fingerprint = fingerprint * 31 + prefs.isImportanceFilterEnabled.hashCode().toLong()
        fingerprint = fingerprint * 31 + prefs.isFeedTitleExcludeRegexEnabled.hashCode().toLong()
        fingerprint = fingerprint * 31 + prefs.feedTitleExcludeRegex.hashCode().toLong()
        return fingerprint
    }

    private fun buildArticleListFingerprint(articles: List<Article>): Long {
        var fingerprint = articles.size.toLong()
        articles.forEach { article ->
            fingerprint = fingerprint * 31 + article.id
            fingerprint = fingerprint * 31 + article.sourceId
            fingerprint = fingerprint * 31 + article.publishedAt
            fingerprint = fingerprint * 31 + article.title.hashCode().toLong()
            fingerprint = fingerprint * 31 + article.content.hashCode().toLong()
            fingerprint = fingerprint * 31 + article.url.hashCode().toLong()
            fingerprint = fingerprint * 31 + article.isRead.hashCode().toLong()
        }
        return fingerprint
    }

    private fun buildGroupsFingerprint(groupsWithSources: List<SourceGroupWithSources>): Long {
        var fingerprint = groupsWithSources.size.toLong()
        groupsWithSources.forEach { groupWithSources ->
            fingerprint = fingerprint * 31 + groupWithSources.group.id
            fingerprint = fingerprint * 31 + groupWithSources.group.isEnabled.hashCode().toLong()
            fingerprint = fingerprint * 31 + groupWithSources.group.name.hashCode().toLong()
            groupWithSources.sources.forEach { source ->
                fingerprint = fingerprint * 31 + source.id
                fingerprint = fingerprint * 31 + source.groupId
                fingerprint = fingerprint * 31 + source.isEnabled.hashCode().toLong()
                fingerprint = fingerprint * 31 + source.name.hashCode().toLong()
            }
        }
        return fingerprint
    }

    private fun buildClusterFingerprint(clusters: List<ArticleCluster>): Long {
        var fingerprint = clusters.size.toLong()
        clusters.forEach { cluster ->
            fingerprint = fingerprint * 31 + cluster.representative.id
            fingerprint = fingerprint * 31 + cluster.duplicates.size
            cluster.duplicates.forEach { (article, score) ->
                fingerprint = fingerprint * 31 + article.id
                fingerprint = fingerprint * 31 + score.toRawBits()
            }
        }
        return fingerprint
    }
}

private fun UserSettings.deduplicationThreshold(): Float =
    when (deduplicationStrategy) {
        DeduplicationStrategy.LOCAL -> localDeduplicationThreshold
        DeduplicationStrategy.CLOUD -> cloudDeduplicationThreshold
    }

private fun UserSettings.feedTitleExcludeRegexOrNull(): Regex? {
    if (!isFeedTitleExcludeRegexEnabled) return null
    val pattern = feedTitleExcludeRegex.trim()
    if (pattern.isBlank()) return null
    return runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull()
}

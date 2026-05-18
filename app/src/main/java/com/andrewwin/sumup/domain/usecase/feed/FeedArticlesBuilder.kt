package com.andrewwin.sumup.domain.usecase.feed

import android.util.Log
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.domain.feed.FeedSearchMatcher
import com.andrewwin.sumup.domain.news.ArticleCluster
import com.andrewwin.sumup.domain.news.ArticleImportanceScorer
import com.andrewwin.sumup.domain.news.SimilarityScorer
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.SourceRepository
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
    private val importanceScorer: ArticleImportanceScorer,
    private val feedSearchMatcher: FeedSearchMatcher,
    private val similarityScorer: SimilarityScorer
) {
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    operator fun invoke(
        searchQueryFlow: Flow<String>,
        selectedGroupIdFlow: Flow<Long?>,
        dateFilterHoursFlow: Flow<Int?>,
        savedOnlyFlow: Flow<Boolean>,
        userPreferencesFlow: Flow<UserPreferences>
    ): Flow<FeedResult> {
        val collectorId = feedCollectorId++
        Log.d(FEED_STATES_LOG_TAG, "builder_created collectorId=$collectorId")

        val feedDataFlow = combine(
            articleRepository.enabledArticles,
            articleRepository.allArticles,
            articleRepository.favoriteArticles,
            sourceRepository.groupsWithSources,
            searchQueryFlow
        ) { enabledArticles, allArticles, favoriteArticles, groups, query ->
            FeedData(enabledArticles, allArticles, favoriteArticles, groups, query)
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
                old.allArticles.size == new.allArticles.size &&
                old.favoriteArticles.size == new.favoriteArticles.size &&
                old.groupsWithSources == new.groupsWithSources
        }

        val filterParamsFlow = combine(
            selectedGroupIdFlow,
            dateFilterHoursFlow,
            savedOnlyFlow,
            userPreferencesFlow,
            articleRepository.dataInvalidationSignal
        ) { groupId, dateFilterHours, savedOnly, prefs, signal ->
            FeedFilterParams(groupId, dateFilterHours, savedOnly, prefs, signal)
        }

        return combine(feedDataFlow, filterParamsFlow) { data, params ->
            buildPipelineState(data, params)
        }
            .flowOn(Dispatchers.Default)
            .debounce(FEED_SNAPSHOT_DEBOUNCE_MS)
            .flatMapLatest { state ->
                flow {
                    val clusters = buildClusters(state)
                    val filteredClusters = applyMinMentionsFilter(clusters, state.prefs, state.savedOnly)
                    Log.d(
                        FEED_STATES_LOG_TAG,
                        "builder_result collectorId=$collectorId articles=${state.articles.size} " +
                            "clusters=${filteredClusters.size} savedOnly=${state.savedOnly}"
                    )
                    emit(FeedResult(filteredClusters))
                }
            }
    }

    private fun buildPipelineState(data: FeedData, params: FeedFilterParams): FeedPipelineState {
        val sourceTypeMap = data.groupsWithSources
            .flatMap { it.sources }
            .associate { it.id to it.type }

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

        val averageViews = processedArticles
            .asSequence()
            .map { it.viewCount }
            .filter { it > 0L }
            .average()
            .takeIf { !it.isNaN() }
            ?.toLong()
            ?: 0L

        processedArticles = processedArticles.map { article ->
            val sourceType = sourceTypeMap[article.sourceId] ?: SourceType.RSS
            val score = importanceScorer.score(article, averageViews, sourceType)
            article.copy(importanceScore = score)
        }

        if (!params.savedOnly && params.prefs.isImportanceFilterEnabled) {
            processedArticles = processedArticles.filter { article ->
                article.importanceScore >= ArticleImportanceScorer.IMPORTANCE_THRESHOLD
            }
        }

        return FeedPipelineState(processedArticles, params.prefs, params.savedOnly)
    }

    private suspend fun buildClusters(state: FeedPipelineState): List<ArticleCluster> {
        if (state.articles.isEmpty()) return emptyList()
        if (state.savedOnly) return buildSavedFavoriteClusters(state.articles, state.prefs)
        if (!state.prefs.isDeduplicationEnabled) {
            return state.articles.map { ArticleCluster(it, emptyList()) }
        }

        return withContext(Dispatchers.IO) {
            buildClustersFromDb(state.articles, state.prefs).ifEmpty {
                state.articles.map { ArticleCluster(it, emptyList()) }
            }
        }
    }

    private fun applyMinMentionsFilter(
        clusters: List<ArticleCluster>,
        prefs: UserPreferences,
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
        prefs: UserPreferences
    ): List<ArticleCluster> {
        val ids = currentArticles.map { it.id }
        val strategyKey = similarityScorer.similarityCacheKeyForStrategy(prefs.deduplicationStrategy)
        val threshold = prefs.deduplicationThreshold()
        val similarities = articleRepository.getSimilaritiesForArticles(ids, strategyKey)
        if (similarities.isEmpty()) return emptyList()

        val currentArticleIds = currentArticles.mapTo(mutableSetOf()) { it.id }
        val pairScores = similarities
            .asSequence()
            .filter { it.leftArticleId in currentArticleIds && it.rightArticleId in currentArticleIds }
            .filter { it.score >= threshold }
            .associate { similarity ->
                ArticlePairKey.of(similarity.leftArticleId, similarity.rightArticleId) to similarity.score
            }

        return FeedClusterCalculator.buildFinalClusters(currentArticles, pairScores)
    }

    private suspend fun buildSavedFavoriteClusters(
        favoriteArticles: List<Article>,
        prefs: UserPreferences
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
        val isDedupInProgress: Boolean = false,
        val processedArticlesCount: Int = 0,
        val totalArticlesCount: Int = 0
    )

    private data class FeedPipelineState(
        val articles: List<Article>,
        val prefs: UserPreferences,
        val savedOnly: Boolean
    )

    private data class FeedFilterParams(
        val groupId: Long?,
        val dateFilterHours: Int?,
        val savedOnly: Boolean,
        val prefs: UserPreferences,
        val invalidationSignal: Long
    )

    private data class FeedData(
        val enabledArticles: List<Article>,
        val allArticles: List<Article>,
        val favoriteArticles: List<Article>,
        val groupsWithSources: List<com.andrewwin.sumup.data.local.dao.GroupWithSources>,
        val query: String
    )

    companion object {
        private const val FEED_STATES_LOG_TAG = "FeedStatesDebug"
        private const val FEED_SNAPSHOT_DEBOUNCE_MS = 600L
        private var feedCollectorId = 0L
    }
}

private fun UserPreferences.deduplicationThreshold(): Float =
    when (deduplicationStrategy) {
        com.andrewwin.sumup.data.local.entities.DeduplicationStrategy.LOCAL -> localDeduplicationThreshold
        com.andrewwin.sumup.data.local.entities.DeduplicationStrategy.CLOUD -> cloudDeduplicationThreshold
    }

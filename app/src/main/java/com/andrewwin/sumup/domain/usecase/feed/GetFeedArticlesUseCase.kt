package com.andrewwin.sumup.domain.usecase.feed

import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.domain.service.ArticleImportanceScorer
import com.andrewwin.sumup.domain.service.ArticleCluster
import com.andrewwin.sumup.domain.service.DeduplicationService
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.SourceRepository
import com.andrewwin.sumup.domain.usecase.settings.ManageModelUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetFeedArticlesUseCase @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val sourceRepository: SourceRepository,
    private val deduplicationService: DeduplicationService,
    private val importanceScorer: ArticleImportanceScorer,
    private val aiRepository: AiRepository,
    private val manageModelUseCase: ManageModelUseCase
) {
    private val tag = "GetFeedArticles"

    operator fun invoke(
        searchQueryFlow: Flow<String>,
        selectedGroupIdFlow: Flow<Long?>,
        dateFilterHoursFlow: Flow<Int?>,
        savedOnlyFlow: Flow<Boolean>,
        userPreferencesFlow: Flow<UserPreferences>
    ): Flow<FeedResult> {
        val flow1 = combine(
            articleRepository.enabledArticles,
            sourceRepository.groupsWithSources,
            searchQueryFlow
        ) { articles, groups, query -> Triple(articles, groups, query) }

        val flow2 = combine(
            selectedGroupIdFlow,
            dateFilterHoursFlow,
            savedOnlyFlow,
            userPreferencesFlow
        ) { groupId, dateFilterHours, savedOnly, prefs ->
            FeedFilterParams(groupId, dateFilterHours, savedOnly, prefs)
        }

        return combine(flow1, flow2) { triple1, triple2 ->
            val (articles, groupsWithSources, query) = triple1
            val groupId = triple2.groupId
            val dateFilterHours = triple2.dateFilterHours
            val savedOnly = triple2.savedOnly
            val prefs = triple2.prefs
            
            val sourceTypeMap = groupsWithSources
                .flatMap { it.sources }
                .associate { it.id to it.type }

            var processedArticles = articles

            if (groupId != null) {
                val sourceIds = groupsWithSources
                    .firstOrNull { it.group.id == groupId }
                    ?.sources
                    ?.map { it.id }
                    .orEmpty()
                processedArticles = processedArticles.filter { it.sourceId in sourceIds }
            }

            dateFilterHours?.let { hours ->
                val threshold = System.currentTimeMillis() - (hours * 60 * 60 * 1000L)
                processedArticles = processedArticles.filter { it.publishedAt >= threshold }
            }

            if (savedOnly) {
                processedArticles = processedArticles.filter { it.isFavorite }
            }

            if (query.isNotBlank()) {
                val tokens = tokenizeQuery(query)
                processedArticles = processedArticles.filter { article ->
                    matchesQueryWithTokenThreshold(
                        title = article.title,
                        content = article.content,
                        queryTokens = tokens
                    )
                }
            }

            if (prefs.isImportanceFilterEnabled) {
                processedArticles = processedArticles.filter { article ->
                    val sourceType = sourceTypeMap[article.sourceId] ?: SourceType.RSS
                    importanceScorer.score(article, sourceType) >= ArticleImportanceScorer.IMPORTANCE_THRESHOLD
                }
            }

            FeedPipelineState(processedArticles, prefs)
        }.flowOn(Dispatchers.Default)
            .flatMapLatest { state ->
                flow {
                    coroutineScope {
                        val baseClusters = if (state.articles.isNotEmpty()) {
                            if (!state.prefs.isDeduplicationEnabled) {
                                state.articles.map { ArticleCluster(it, emptyList()) }
                            } else {
                                withContext(Dispatchers.IO) {
                                    buildClustersFromDb(state.articles)
                                }
                            }
                        } else {
                            emptyList()
                        }

                        val initialClusters = if (baseClusters.isEmpty() && state.articles.isNotEmpty() && !state.shouldDedup) {
                            state.articles.map { ArticleCluster(it, emptyList()) }
                        } else {
                            baseClusters
                        }

                        val initial = applyMinMentionsFilter(initialClusters, state.prefs)
                        val shouldRunDedup = state.shouldDedup && state.articles.size >= 2

                        emit(FeedResult(initial, shouldRunDedup))

                        if (!shouldRunDedup) return@coroutineScope

                        val hasCloudEmbedding = withContext(Dispatchers.IO) {
                            aiRepository.hasEnabledEmbeddingConfig()
                        }
                        val resolvedModelPath = resolveModelPath(state.prefs)
                        val hasLocalEmbedding = resolvedModelPath
                            ?.let { path -> deduplicationService.initialize(path) }
                            ?: false

                        val canDeduplicate = when (state.prefs.aiStrategy) {
                            AiStrategy.LOCAL -> hasLocalEmbedding
                            AiStrategy.CLOUD, AiStrategy.ADAPTIVE -> hasCloudEmbedding || hasLocalEmbedding
                        }
                        if (!canDeduplicate) {
                            emit(FeedResult(initial, false))
                            return@coroutineScope
                        }

                        var lastClusters: List<ArticleCluster>? = null
                        val deduplicationThreshold = when (state.prefs.aiStrategy) {
                            AiStrategy.LOCAL -> state.prefs.localDeduplicationThreshold
                            AiStrategy.CLOUD, AiStrategy.ADAPTIVE -> if (hasCloudEmbedding) {
                                state.prefs.cloudDeduplicationThreshold
                            } else {
                                state.prefs.localDeduplicationThreshold
                            }
                        }

                        deduplicationService
                            .clusterArticlesIncremental(
                                articles = state.articles,
                                threshold = deduplicationThreshold,
                                emitEvery = DEDUP_EMIT_EVERY
                            )
                            .collect { clusters ->
                                val filtered = applyMinMentionsFilter(clusters, state.prefs)
                                lastClusters = filtered
                                emit(FeedResult(filtered, true))
                            }

                        lastClusters?.let { final ->
                            val filteredFinal = applyMinMentionsFilter(final, state.prefs)
                            emit(FeedResult(filteredFinal, false))
                        }
                    }
                }
            }
    }

    private fun applyMinMentionsFilter(
        clusters: List<ArticleCluster>,
        prefs: UserPreferences
    ): List<ArticleCluster> {
        if (!prefs.isDeduplicationEnabled) return clusters
        if (clusters.isEmpty()) return clusters

        return clusters.filter { cluster ->
            val mentions = cluster.duplicates.size + 1
            val isSingle = cluster.duplicates.isEmpty()

            if (isSingle) {
                !prefs.isHideSingleNewsEnabled
            } else {
                mentions >= prefs.minMentions
            }
        }
    }

    private suspend fun buildClustersFromDb(
        currentArticles: List<Article>
    ): List<ArticleCluster> {
        val currentById = currentArticles.associateBy { it.id }
        val ids = currentArticles.map { it.id }
        val similarities = articleRepository.getSimilaritiesForArticles(ids)
        if (similarities.isEmpty()) return emptyList()

        val adjacency = mutableMapOf<Long, MutableSet<Long>>()
        val scoreMap = mutableMapOf<Pair<Long, Long>, Float>()

        for (sim in similarities) {
            adjacency.getOrPut(sim.representativeId) { mutableSetOf() }.add(sim.articleId)
            adjacency.getOrPut(sim.articleId) { mutableSetOf() }.add(sim.representativeId)
            scoreMap[sim.representativeId to sim.articleId] = sim.score
            scoreMap[sim.articleId to sim.representativeId] = sim.score
        }

        val visited = mutableSetOf<Long>()
        val clusters = mutableListOf<ArticleCluster>()

        for (id in adjacency.keys) {
            if (visited.contains(id)) continue
            val stack = ArrayDeque<Long>()
            val component = mutableListOf<Long>()
            stack.add(id)
            visited.add(id)
            while (stack.isNotEmpty()) {
                val cur = stack.removeFirst()
                component.add(cur)
                adjacency[cur]?.forEach { neighbor ->
                    if (!visited.contains(neighbor)) {
                        visited.add(neighbor)
                        stack.add(neighbor)
                    }
                }
            }

            val articlesInCluster = component.mapNotNull { currentById[it] }
            if (articlesInCluster.size < 2) continue

            val representative = articlesInCluster.maxBy { it.publishedAt }
            val componentIds = component.toSet()
            val duplicates = articlesInCluster
                .filterNot { it.id == representative.id }
                .map { article ->
                    val score = componentIds
                        .asSequence()
                        .filterNot { it == article.id }
                        .mapNotNull { otherId -> scoreMap[article.id to otherId] }
                        .maxOrNull() ?: 0f
                    article to score
                }
            clusters.add(ArticleCluster(representative, duplicates))
        }

        val clusteredIds = clusters.flatMap { c -> listOf(c.representative.id) + c.duplicates.map { it.first.id } }.toSet()
        val remaining = currentArticles.filterNot { clusteredIds.contains(it.id) }
        remaining.forEach { clusters.add(ArticleCluster(it, emptyList())) }

        return clusters.sortedByDescending { it.representative.publishedAt }
    }

    data class FeedPipelineState(
        val articles: List<Article>,
        val prefs: UserPreferences
    ) {
        val shouldDedup = prefs.isDeduplicationEnabled
    }

    data class FeedFilterParams(
        val groupId: Long?,
        val dateFilterHours: Int?,
        val savedOnly: Boolean,
        val prefs: UserPreferences
    )

    private fun resolveModelPath(prefs: UserPreferences): String? {
        if (!prefs.modelPath.isNullOrBlank()) return prefs.modelPath
        return if (manageModelUseCase.isModelExists()) {
            manageModelUseCase.getModelPath()
        } else {
            null
        }
    }

    data class FeedResult(
        val clusters: List<ArticleCluster>,
        val isDedupInProgress: Boolean
    )

    companion object {
        private const val DEDUP_EMIT_EVERY = 32
        private const val SEARCH_TOKEN_MATCH_RATIO = 0.6f
    }

    private fun matchesQueryWithTokenThreshold(
        title: String,
        content: String,
        queryTokens: List<String>
    ): Boolean {
        if (queryTokens.isEmpty()) return true
        val normalizedText = normalizeForSearch("$title $content")
        if (normalizedText.isBlank()) return false

        val matchedCount = queryTokens.count { token -> normalizedText.contains(token) }
        val requiredMatches = kotlin.math.ceil(queryTokens.size * SEARCH_TOKEN_MATCH_RATIO).toInt().coerceAtLeast(1)
        return matchedCount >= requiredMatches
    }

    private fun tokenizeQuery(query: String): List<String> =
        normalizeForSearch(query)
            .split(" ")
            .map { it.trim() }
            .filter { it.length >= 2 }
            .distinct()

    private fun normalizeForSearch(value: String): String =
        value
            .lowercase()
            .replace(Regex("[\\p{Punct}\\p{S}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}










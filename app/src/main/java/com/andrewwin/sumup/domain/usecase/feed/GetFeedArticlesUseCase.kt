package com.andrewwin.sumup.domain.usecase.feed

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.DeduplicationStrategy
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.domain.service.ArticleImportanceScorer
import com.andrewwin.sumup.domain.service.ArticleCluster
import com.andrewwin.sumup.domain.service.DeduplicationService
import com.andrewwin.sumup.domain.repository.AiModelConfigRepository
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
    private val aiModelConfigRepository: AiModelConfigRepository,
    private val manageModelUseCase: ManageModelUseCase
) {
    private val tag = "GetFeedArticles"
    private var lastDedupInputKey: String? = null
    private var lastDedupClusters: List<ArticleCluster>? = null

    operator fun invoke(
        searchQueryFlow: Flow<String>,
        selectedGroupIdFlow: Flow<Long?>,
        dateFilterHoursFlow: Flow<Int?>,
        savedOnlyFlow: Flow<Boolean>,
        userPreferencesFlow: Flow<UserPreferences>
    ): Flow<FeedResult> {
        val flow1 = combine(
            articleRepository.enabledArticles,
            articleRepository.allArticles,
            articleRepository.favoriteArticles,
            sourceRepository.groupsWithSources,
            searchQueryFlow
        ) { enabledArticles, allArticles, favoriteArticles, groups, query ->
            FeedData(enabledArticles, allArticles, favoriteArticles, groups, query)
        }

        val flow2 = combine(
            selectedGroupIdFlow,
            dateFilterHoursFlow,
            savedOnlyFlow,
            userPreferencesFlow
        ) { groupId, dateFilterHours, savedOnly, prefs ->
            FeedFilterParams(groupId, dateFilterHours, savedOnly, prefs)
        }

        return combine(flow1, flow2) { data, triple2 ->
            val enabledArticles = data.enabledArticles
            val allArticles = data.allArticles
            val favoriteArticles = data.favoriteArticles
            val groupsWithSources = data.groupsWithSources
            val query = data.query
            val groupId = triple2.groupId
            val dateFilterHours = triple2.dateFilterHours
            val savedOnly = triple2.savedOnly
            val prefs = triple2.prefs
            
            val sourceTypeMap = groupsWithSources
                .flatMap { it.sources }
                .associate { it.id to it.type }

            var processedArticles = if (savedOnly) favoriteArticles else enabledArticles

            if (!savedOnly) {
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
            }

            if (savedOnly) {
                processedArticles = processedArticles.filter { it.isFavorite }
            }

            if (!savedOnly && query.isNotBlank()) {
                val tokens = tokenizeQuery(query)
                processedArticles = processedArticles.filter { article ->
                    matchesQueryWithTokenThreshold(
                        title = article.title,
                        content = article.content,
                        queryTokens = tokens
                    )
                }
            }

            val averageViews = if (processedArticles.isNotEmpty()) {
                processedArticles
                    .asSequence()
                    .map { it.viewCount }
                    .filter { it > 0L }
                    .average()
                    .toLong()
            } else 0L

            // Calculate and bind importance scores using .copy()
            processedArticles = processedArticles.map { article ->
                val sourceType = sourceTypeMap[article.sourceId] ?: SourceType.RSS
                val score = importanceScorer.score(article, averageViews, sourceType)
                article.copy(importanceScore = score)
            }

            if (!savedOnly && prefs.isImportanceFilterEnabled) {
                processedArticles = processedArticles.filter { article ->
                    article.importanceScore >= ArticleImportanceScorer.IMPORTANCE_THRESHOLD
                }
            }

            FeedPipelineState(processedArticles, prefs, savedOnly)
        }.flowOn(Dispatchers.Default)
            .flatMapLatest { state ->
                flow {
                    coroutineScope {
                        val baseClusters = if (state.articles.isNotEmpty()) {
                            if (state.savedOnly) {
                                buildSavedFavoriteClusters(state.articles)
                            } else if (!state.prefs.isDeduplicationEnabled) {
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

                        val initial = applyMinMentionsFilter(initialClusters, state.prefs, state.savedOnly)
                        val shouldRunDedup = state.shouldDedup && state.articles.size >= 2

                        val dedupInputKey = buildDedupInputKey(state.articles, state.prefs)
                        val cachedClusters = lastDedupClusters
                        if (shouldRunDedup && dedupInputKey == lastDedupInputKey && cachedClusters != null) {
                            val rebound = rebindClustersWithLatestArticles(cachedClusters, state.articles)
                            emit(FeedResult(applyMinMentionsFilter(rebound, state.prefs, state.savedOnly), false))
                            return@coroutineScope
                        }

                        emit(FeedResult(initial, shouldRunDedup))

                        if (!shouldRunDedup) {
                            return@coroutineScope
                        }

                        var lastClusters: List<ArticleCluster>? = null

                        val deduplicationThreshold = when (state.prefs.deduplicationStrategy) {
                            DeduplicationStrategy.LOCAL -> state.prefs.localDeduplicationThreshold
                            DeduplicationStrategy.CLOUD -> state.prefs.cloudDeduplicationThreshold
                        }

                        val modelPath = resolveModelPath(state.prefs)

                        val isModelInitialized = if (!modelPath.isNullOrBlank()) {
                            deduplicationService.initialize(modelPath)
                        } else {
                            false
                        }

                        if (!isModelInitialized) {
                            emit(FeedResult(initial, false))
                            return@coroutineScope
                        }

                        deduplicationService
                            .clusterArticlesIncremental(
                                articles = state.articles,
                                threshold = deduplicationThreshold,
                                emitEvery = DEDUP_EMIT_EVERY
                            )
                            .collect { clusters ->
                                val filtered = applyMinMentionsFilter(clusters, state.prefs, state.savedOnly)
                                lastClusters = filtered
                                emit(FeedResult(filtered, true))
                            }

                        lastClusters?.let { final ->
                            lastDedupInputKey = dedupInputKey
                            lastDedupClusters = final
                            val filteredFinal = applyMinMentionsFilter(final, state.prefs, state.savedOnly)
                            emit(FeedResult(filteredFinal, false))
                        }
                    }
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

    private suspend fun buildSavedFavoriteClusters(
        favoriteArticles: List<Article>
    ): List<ArticleCluster> {
        if (favoriteArticles.isEmpty()) return emptyList()
        val byId = favoriteArticles.associateBy { it.id }
        val mappings = articleRepository.getFavoriteClusterMappings(favoriteArticles.map { it.id })
        val savedAtById = articleRepository.getFavoriteSavedAt(favoriteArticles.map { it.id })
        val similarities = articleRepository.getFavoriteSimilarities(favoriteArticles.map { it.id })
        val savedClusterScores = articleRepository.getFavoriteClusterScores(favoriteArticles.map { it.id })
        val scoreMap = buildMap<Pair<Long, Long>, Float> {
            similarities.forEach { sim ->
                put(sim.representativeId to sim.articleId, sim.score)
                put(sim.articleId to sim.representativeId, sim.score)
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
            val representative = articles.maxBy { it.publishedAt }
            val duplicates = articles
                .asSequence()
                .filterNot { it.id == representative.id }
                .map { article ->
                    val score = articles
                        .asSequence()
                        .map { it.id }
                        .filter { it != article.id }
                        .mapNotNull { otherId -> scoreMap[article.id to otherId] }
                        .maxOrNull()
                        ?: savedClusterScores[article.id]
                        ?: 0f
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

    private fun rebindClustersWithLatestArticles(
        clusters: List<ArticleCluster>,
        latestArticles: List<Article>
    ): List<ArticleCluster> {
        val articleById = latestArticles.associateBy { it.id }
        return clusters.map { cluster ->
            val representative = articleById[cluster.representative.id] ?: cluster.representative
            val duplicates = cluster.duplicates.mapNotNull { (article, score) ->
                val updated = articleById[article.id] ?: article
                updated to score
            }
            ArticleCluster(representative, duplicates)
        }
    }

    private fun buildDedupInputKey(
        articles: List<Article>,
        prefs: UserPreferences
    ): String {
        val articlePart = articles
            .sortedBy { it.id }
            .joinToString("|") { article ->
                listOf(
                    article.id.toString(),
                    article.sourceId.toString(),
                    article.publishedAt.toString(),
                    article.url,
                    article.title,
                    article.content
                ).joinToString("::")
            }
        val prefsPart = listOf(
            prefs.isDeduplicationEnabled.toString(),
            prefs.deduplicationStrategy.name,
            prefs.localDeduplicationThreshold.toString(),
            prefs.cloudDeduplicationThreshold.toString(),
            prefs.minMentions.toString(),
            prefs.isHideSingleNewsEnabled.toString()
        ).joinToString("::")
        return "$prefsPart##$articlePart"
    }

    data class FeedPipelineState(
        val articles: List<Article>,
        val prefs: UserPreferences,
        val savedOnly: Boolean
    ) {
        val shouldDedup = prefs.isDeduplicationEnabled && !savedOnly
    }

    data class FeedFilterParams(
        val groupId: Long?,
        val dateFilterHours: Int?,
        val savedOnly: Boolean,
        val prefs: UserPreferences
    )

    data class FeedData(
        val enabledArticles: List<Article>,
        val allArticles: List<Article>,
        val favoriteArticles: List<Article>,
        val groupsWithSources: List<com.andrewwin.sumup.data.local.dao.GroupWithSources>,
        val query: String
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










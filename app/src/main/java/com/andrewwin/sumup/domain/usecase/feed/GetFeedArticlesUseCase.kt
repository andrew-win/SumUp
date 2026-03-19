package com.andrewwin.sumup.domain.usecase.feed

import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.domain.ArticleCluster
import com.andrewwin.sumup.domain.ArticleImportanceScorer
import com.andrewwin.sumup.domain.DeduplicationService
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.SourceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.util.Log
import javax.inject.Inject

class GetFeedArticlesUseCase @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val sourceRepository: SourceRepository,
    private val deduplicationService: DeduplicationService,
    private val importanceScorer: ArticleImportanceScorer
) {
    private val tag = "GetFeedArticles"
    operator fun invoke(
        searchQueryFlow: Flow<String>,
        selectedGroupIdFlow: Flow<Long?>,
        dateFilterHoursFlow: Flow<Int?>,
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
            userPreferencesFlow
        ) { groupId, dateFilterHours, prefs -> Triple(groupId, dateFilterHours, prefs) }

        return combine(flow1, flow2) { triple1, triple2 ->
            val (articles, groupsWithSources, query) = triple1
            val (groupId, dateFilterHours, prefs) = triple2
            Log.d(tag, "pipeline input articles=${articles.size} groupId=$groupId dateFilterHours=$dateFilterHours query='${query}' prefs=dedup=${prefs.isDeduplicationEnabled} modelPath=${prefs.modelPath.orEmpty()} minMentions=${prefs.minMentions} importance=${prefs.isImportanceFilterEnabled}")

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

            if (query.isNotBlank()) {
                processedArticles = processedArticles.filter {
                    it.title.contains(query, ignoreCase = true) ||
                            it.content.contains(query, ignoreCase = true)
                }
            }

            // 1. Filter by importance if enabled
            if (prefs.isImportanceFilterEnabled) {
                processedArticles = processedArticles.filter { article ->
                    val sourceType = sourceTypeMap[article.sourceId] ?: SourceType.RSS
                    importanceScorer.score(article, sourceType) >= ArticleImportanceScorer.IMPORTANCE_THRESHOLD
                }
            }

            Log.d(tag, "after filters articles=${processedArticles.size}")

            FeedPipelineState(processedArticles, prefs)
        }.flowOn(Dispatchers.Default)
            .debounce(DEDUP_START_DEBOUNCE_MS)
            .flatMapLatest { state ->
                flow {
                    coroutineScope {
                        val baseClusters = if (state.articles.isNotEmpty()) {
                            if (!state.prefs.isDeduplicationEnabled || state.prefs.modelPath == null) {
                                state.articles.map { ArticleCluster(it, emptyList()) }
                            } else {
                                withContext(Dispatchers.IO) {
                                    buildClustersFromDb(state.articles)
                                }
                            }
                        } else {
                            emptyList()
                        }

                        val initialClusters = when {
                            // If dedup is enabled and we don't have any clusters yet,
                            // avoid showing non-deduped singletons to prevent flicker.
                            state.shouldDedup && baseClusters.isEmpty() -> emptyList()
                            baseClusters.isEmpty() && state.articles.isNotEmpty() ->
                                state.articles.map { ArticleCluster(it, emptyList()) }
                            else -> baseClusters
                        }

                        val initial = applyMinMentionsFilter(initialClusters, state.prefs)
                        Log.d(tag, "baseClusters=${baseClusters.size} initial=${initial.size} shouldDedup=${state.shouldDedup}")
                        val usedIds = baseClusters
                            .flatMap { cluster -> listOf(cluster.representative.id) + cluster.duplicates.map { it.first.id } }
                            .toSet()
                        val newArticles = state.articles.filterNot { usedIds.contains(it.id) }
                        val shouldRunDedup = state.shouldDedup && newArticles.isNotEmpty()
                        Log.d(tag, "newArticles=${newArticles.size} shouldRunDedup=$shouldRunDedup")

                        emit(FeedResult(initial, shouldRunDedup))
                        var lastEmitted = initial

                        val warmupJob = launch {
                            val modelPath = state.prefs.modelPath ?: return@launch
                            if (state.articles.isEmpty()) return@launch
                            delay(EMBED_WARMUP_DELAY_MS)
                            if (deduplicationService.initialize(modelPath)) {
                                deduplicationService.warmUpEmbeddings(
                                    articles = state.articles,
                                    throttleMs = EMBED_WARMUP_THROTTLE_MS
                                )
                            }
                        }

                        if (!shouldRunDedup) return@coroutineScope

                        delay(DEDUP_DELAY_MS)
                        warmupJob.cancel()
                        val initialized = deduplicationService.initialize(state.prefs.modelPath!!)
                        if (!initialized) {
                            val fallback = state.articles.map { ArticleCluster(it, emptyList()) }
                            emit(FeedResult(fallback, false))
                            return@coroutineScope
                        }

                        var lastClusters: List<ArticleCluster>? = null
                        deduplicationService
                            .attachNewArticlesIncremental(
                                existingClusters = baseClusters,
                                newArticles = newArticles,
                                threshold = state.prefs.deduplicationThreshold,
                                emitEvery = DEDUP_EMIT_EVERY,
                                throttleMs = DEDUP_THROTTLE_MS
                            )
                            .collect { clusters ->
                                val filtered = applyMinMentionsFilter(clusters, state.prefs)
                                lastClusters = filtered
                                lastEmitted = filtered
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
        if (!prefs.isDeduplicationEnabled || prefs.modelPath == null) return clusters
        if (clusters.isEmpty()) return clusters
        // If dedup hasn't produced any matches yet, don't hide singletons.
        if (clusters.all { it.duplicates.isEmpty() }) return clusters
        return clusters.filter { it.duplicates.size + 1 >= prefs.minMentions }
    }

    private fun mergeCachedWithCurrent(
        cached: List<ArticleCluster>,
        currentArticles: List<com.andrewwin.sumup.data.local.entities.Article>
    ): List<ArticleCluster> {
        val currentById = currentArticles.associateBy { it.id }
        val usedIds = mutableSetOf<Long>()
        val merged = mutableListOf<ArticleCluster>()

        for (cluster in cached) {
            val rep = currentById[cluster.representative.id]
            val keptDuplicates = cluster.duplicates
                .mapNotNull { (article, score) ->
                    val current = currentById[article.id] ?: return@mapNotNull null
                    current to score
                }

            val finalRep = rep ?: keptDuplicates.firstOrNull()?.first
            if (finalRep != null) {
                usedIds.add(finalRep.id)
                keptDuplicates.forEach { usedIds.add(it.first.id) }

                val finalDuplicates = keptDuplicates
                    .filterNot { it.first.id == finalRep.id }

                merged.add(ArticleCluster(finalRep, finalDuplicates))
            }
        }

        val missing = currentArticles.filterNot { usedIds.contains(it.id) }
        missing.forEach { merged.add(ArticleCluster(it, emptyList())) }

        return merged
    }

    private suspend fun buildClustersFromDb(
        currentArticles: List<com.andrewwin.sumup.data.local.entities.Article>
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

            val articles = component.mapNotNull { currentById[it] }
            if (articles.size < 2) continue

            val representative = articles.maxBy { it.publishedAt }
            val componentIds = component.toSet()
            val duplicates = articles
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

        return clusters
    }

    private data class FeedPipelineState(
        val articles: List<com.andrewwin.sumup.data.local.entities.Article>,
        val prefs: UserPreferences
    ) {
        val shouldDedup: Boolean =
            prefs.isDeduplicationEnabled && prefs.modelPath != null && articles.isNotEmpty()
    }

    data class FeedResult(
        val clusters: List<ArticleCluster>,
        val isDedupInProgress: Boolean
    )

    private companion object {
        private const val DEDUP_DELAY_MS = 1_000L
        private const val DEDUP_EMIT_EVERY = 1
        private const val DEDUP_THROTTLE_MS = 25L
        private const val EMBED_WARMUP_DELAY_MS = 500L
        private const val EMBED_WARMUP_THROTTLE_MS = 20L
        private const val DEDUP_START_DEBOUNCE_MS = 1_200L
    }
}

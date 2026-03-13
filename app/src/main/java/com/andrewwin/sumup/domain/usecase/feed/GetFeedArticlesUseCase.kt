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
import javax.inject.Inject

class GetFeedArticlesUseCase @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val sourceRepository: SourceRepository,
    private val deduplicationService: DeduplicationService,
    private val importanceScorer: ArticleImportanceScorer
) {
    operator fun invoke(
        searchQueryFlow: Flow<String>,
        selectedGroupIdFlow: Flow<Long?>,
        dateFilterHoursFlow: Flow<Int?>,
        userPreferencesFlow: Flow<UserPreferences>
    ): Flow<List<ArticleCluster>> {
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

            // Optimized feed logic
            if (prefs.isImportanceFilterEnabled) {
                // 1. Filter by importance
                processedArticles = processedArticles.filter { article ->
                    val sourceType = sourceTypeMap[article.sourceId] ?: SourceType.RSS
                    importanceScorer.score(article, sourceType) >= ArticleImportanceScorer.IMPORTANCE_THRESHOLD
                }

                // 2. Deduplicate filtered articles
                if (prefs.isDeduplicationEnabled && prefs.modelPath != null && processedArticles.isNotEmpty()) {
                    if (deduplicationService.initialize(prefs.modelPath)) {
                        return@combine deduplicationService.clusterArticles(
                            processedArticles,
                            prefs.deduplicationThreshold
                        ).filter { it.duplicates.size + 1 >= prefs.minMentions }
                    }
                }
            }

            // Return "naked" feed or if deduplication failed/disabled
            processedArticles.map { ArticleCluster(it, emptyList()) }
        }
    }
}

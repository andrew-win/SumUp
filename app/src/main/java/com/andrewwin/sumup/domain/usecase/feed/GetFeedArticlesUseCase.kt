package com.andrewwin.sumup.domain.usecase.feed

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.domain.ArticleCluster
import com.andrewwin.sumup.domain.DeduplicationService
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.SourceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class GetFeedArticlesUseCase @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val sourceRepository: SourceRepository,
    private val deduplicationService: DeduplicationService
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

            var filteredArticles = articles

            if (groupId != null) {
                val sourceIds = groupsWithSources
                    .firstOrNull { it.group.id == groupId }
                    ?.sources
                    ?.map { it.id }
                    .orEmpty()
                filteredArticles = filteredArticles.filter { it.sourceId in sourceIds }
            }

            dateFilterHours?.let { hours ->
                val threshold = System.currentTimeMillis() - (hours * 60 * 60 * 1000L)
                filteredArticles = filteredArticles.filter { it.publishedAt >= threshold }
            }

            if (query.isNotBlank()) {
                filteredArticles = filteredArticles.filter {
                    it.title.contains(query, ignoreCase = true) ||
                            it.content.contains(query, ignoreCase = true)
                }
            }

            if (prefs.isDeduplicationEnabled && prefs.modelPath != null && filteredArticles.isNotEmpty()) {
                if (deduplicationService.initialize(prefs.modelPath)) {
                    val clusters = deduplicationService.clusterArticles(
                        filteredArticles,
                        prefs.deduplicationThreshold
                    )
                    clusters.filter { it.duplicates.size + 1 >= prefs.minMentions }
                } else {
                    filteredArticles.map { ArticleCluster(it, emptyList()) }
                }
            } else {
                filteredArticles.map { ArticleCluster(it, emptyList()) }
            }
        }
    }
}

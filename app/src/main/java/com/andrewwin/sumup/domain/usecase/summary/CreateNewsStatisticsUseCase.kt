package com.andrewwin.sumup.domain.usecase.summary

import com.andrewwin.sumup.domain.feed.pipeline.FeedArticlesBuilder
import com.andrewwin.sumup.domain.news.ArticleImportanceScorer
import com.andrewwin.sumup.domain.repository.SourceRepository
import com.andrewwin.sumup.domain.settings.UserSettings
import com.andrewwin.sumup.domain.source.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

enum class NewsStatisticsType {
    VIEWS,
    MENTIONS,
    FACTUALITY
}

sealed interface NewsStatisticsMetric {
    data class Views(val totalViews: Long, val hasKnownViews: Boolean) : NewsStatisticsMetric
    data class Mentions(val count: Int) : NewsStatisticsMetric
    data class Factuality(val score: Float) : NewsStatisticsMetric
}

data class NewsStatisticsItem(
    val headline: String,
    val value: Float,
    val metric: NewsStatisticsMetric,
    val sourceName: String? = null,
    val sourceUrl: String? = null
)

class CreateNewsStatisticsUseCase @Inject constructor(
    private val feedArticlesBuilder: FeedArticlesBuilder,
    private val importanceScorer: ArticleImportanceScorer,
    private val sourceRepository: SourceRepository
) {
    operator fun invoke(
        chartTypeFlow: Flow<NewsStatisticsType>,
        userPreferencesFlow: Flow<UserSettings>
    ): Flow<List<NewsStatisticsItem>> {
        return combine(
            feedArticlesBuilder(
                searchQueryFlow = flowOf(""),
                selectedGroupIdFlow = flowOf(null),
                dateFilterHoursFlow = flowOf(24),
                savedOnlyFlow = flowOf(false),
                userPreferencesFlow = userPreferencesFlow
            ),
            chartTypeFlow,
            userPreferencesFlow,
            sourceRepository.groupsWithSources
        ) { feedResult, type, prefs, groups ->
            val sourceById = groups.flatMap { it.sources }.associateBy { it.id }
            val sourceTypeMap = sourceById.mapValues { it.value.type }
            val limit = prefs.showInfographicNewsCount.coerceAtLeast(1)

            when (type) {
                NewsStatisticsType.VIEWS -> {
                    feedResult.clusters.map { cluster ->
                        val clusterArticles = listOf(cluster.representative) + cluster.duplicates.map { it.first }
                        val hasKnownViews = clusterArticles.any {
                            (sourceTypeMap[it.sourceId] ?: SourceType.RSS) != SourceType.RSS && it.viewCount > 0
                        }
                        val totalViews = clusterArticles.sumOf { article ->
                            val sourceType = sourceTypeMap[article.sourceId] ?: SourceType.RSS
                            if (sourceType == SourceType.RSS) 0L else article.viewCount.coerceAtLeast(0L)
                        }
                        val source = sourceById[cluster.representative.sourceId]
                        NewsStatisticsItem(
                            headline = cluster.representative.title,
                            value = totalViews.toFloat(),
                            metric = NewsStatisticsMetric.Views(
                                totalViews = totalViews,
                                hasKnownViews = hasKnownViews
                            ),
                            sourceName = source?.name,
                            sourceUrl = cluster.representative.url.takeIf { it.isNotBlank() } ?: source?.url
                        )
                    }.sortedByDescending { it.value }.take(limit)
                }

                NewsStatisticsType.MENTIONS -> {
                    feedResult.clusters.map { cluster ->
                        val count = cluster.duplicates.size + 1
                        val source = sourceById[cluster.representative.sourceId]
                        NewsStatisticsItem(
                            headline = cluster.representative.title,
                            value = count.toFloat(),
                            metric = NewsStatisticsMetric.Mentions(count),
                            sourceName = source?.name,
                            sourceUrl = cluster.representative.url.takeIf { it.isNotBlank() } ?: source?.url
                        )
                    }.sortedByDescending { it.value }.take(limit)
                }

                NewsStatisticsType.FACTUALITY -> {
                    val articles = feedResult.clusters.map { it.representative }
                    val averageViews = articles
                        .asSequence()
                        .map { it.viewCount }
                        .filter { it > 0L }
                        .average()
                        .toLong()

                    feedResult.clusters.map { cluster ->
                        val article = cluster.representative
                        val score = importanceScorer.score(
                            article = article,
                            averageViews = averageViews,
                            sourceType = sourceTypeMap[article.sourceId] ?: SourceType.RSS
                        )
                        val source = sourceById[article.sourceId]
                        NewsStatisticsItem(
                            headline = article.title,
                            value = score,
                            metric = NewsStatisticsMetric.Factuality(score),
                            sourceName = source?.name,
                            sourceUrl = article.url.takeIf { it.isNotBlank() } ?: source?.url
                        )
                    }.sortedByDescending { it.value }.take(limit)
                }
            }
        }.flowOn(Dispatchers.Default)
    }
}

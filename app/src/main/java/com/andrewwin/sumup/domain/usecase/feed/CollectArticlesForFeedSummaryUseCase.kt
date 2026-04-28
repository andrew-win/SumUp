package com.andrewwin.sumup.domain.usecase.feed

import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class CollectArticlesForFeedSummaryUseCase @Inject constructor(
    private val getFeedArticlesUseCase: GetFeedArticlesUseCase,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    suspend operator fun invoke(): List<FeedSummaryArticle> {
        val feedResult = getFeedArticlesUseCase(
            searchQueryFlow = flowOf(""),
            selectedGroupIdFlow = flowOf(null),
            dateFilterHoursFlow = flowOf(null),
            savedOnlyFlow = flowOf(false),
            userPreferencesFlow = userPreferencesRepository.preferences
        ).first { !it.isDedupInProgress }

        return feedResult.clusters.map { cluster ->
            FeedSummaryArticle(
                article = cluster.representative,
                similarArticlesCount = cluster.duplicates.size,
                baseImportanceScore = cluster.representative.importanceScore
            )
        }
    }
}

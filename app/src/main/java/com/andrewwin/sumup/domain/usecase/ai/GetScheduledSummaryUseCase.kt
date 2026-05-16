package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.domain.feed.ScheduledSummaryArticleCollector
import com.andrewwin.sumup.domain.summary.SummaryResult
import javax.inject.Inject

class GetScheduledSummaryUseCase @Inject constructor(
    private val collectArticlesForScheduledSummaryUseCase: ScheduledSummaryArticleCollector,
    private val getFeedSummaryUseCase: GetFeedSummaryUseCase
) {
    suspend operator fun invoke(refresh: Boolean = false): Result<SummaryResult> {
        val articles = collectArticlesForScheduledSummaryUseCase(refresh)
        return getFeedSummaryUseCase(articles)
    }
}

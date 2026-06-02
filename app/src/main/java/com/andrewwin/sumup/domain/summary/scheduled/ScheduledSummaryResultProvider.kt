package com.andrewwin.sumup.domain.summary.scheduled

import com.andrewwin.sumup.domain.feed.pipeline.ScheduledSummaryArticleCollector
import com.andrewwin.sumup.domain.summary.model.SummaryResult
import com.andrewwin.sumup.domain.summary.usecase.SummarizeFeedUseCase
import javax.inject.Inject

class ScheduledSummaryResultProvider @Inject constructor(
    private val collectArticlesForScheduledSummaryUseCase: ScheduledSummaryArticleCollector,
    private val summarizeFeedUseCase: SummarizeFeedUseCase
) {
    suspend operator fun invoke(refresh: Boolean = false): Result<SummaryResult> {
        val articles = collectArticlesForScheduledSummaryUseCase(refresh)
        return summarizeFeedUseCase(articles)
    }
}

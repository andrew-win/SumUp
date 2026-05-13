package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.domain.feed.FeedSummaryArticleCollector
import com.andrewwin.sumup.domain.summary.SummaryResult
import com.andrewwin.sumup.domain.usecase.common.RefreshArticlesUseCase
import javax.inject.Inject

class GetScheduledSummaryUseCase @Inject constructor(
    private val refreshArticlesUseCase: RefreshArticlesUseCase,
    private val collectArticlesForFeedSummaryUseCase: FeedSummaryArticleCollector,
    private val getFeedSummaryUseCase: GetFeedSummaryUseCase
) {
    suspend operator fun invoke(refresh: Boolean = false): Result<SummaryResult> {
        if (refresh) {
            refreshArticlesUseCase()
        }

        val articles = collectArticlesForFeedSummaryUseCase()
        return getFeedSummaryUseCase(articles)
    }
}

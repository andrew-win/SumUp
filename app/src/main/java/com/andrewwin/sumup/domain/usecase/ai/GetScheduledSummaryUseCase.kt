package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.domain.usecase.common.RefreshArticlesUseCase
import com.andrewwin.sumup.domain.usecase.feed.CollectArticlesForFeedSummaryUseCase
import javax.inject.Inject

class GetScheduledSummaryUseCase @Inject constructor(
    private val refreshArticlesUseCase: RefreshArticlesUseCase,
    private val collectArticlesForFeedSummaryUseCase: CollectArticlesForFeedSummaryUseCase,
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

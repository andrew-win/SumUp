package com.andrewwin.sumup.domain.usecase.common

import com.andrewwin.sumup.domain.summary.SummaryResultFormatter
import com.andrewwin.sumup.domain.summary.SummaryResult
import com.andrewwin.sumup.domain.usecase.ai.GetScheduledSummaryUseCase
import javax.inject.Inject

interface GenerateSummaryUseCase {
    suspend operator fun invoke(refresh: Boolean = false): String
}

class GenerateSummaryUseCaseImpl @Inject constructor(
    private val getScheduledSummaryUseCase: GetScheduledSummaryUseCase,
    private val formatSummaryResultUseCase: SummaryResultFormatter
) : GenerateSummaryUseCase {

    override suspend fun invoke(refresh: Boolean): String {
        val result = getScheduledSummaryUseCase(refresh).getOrThrow()
        if (result is SummaryResult.Digest && result.themes.isEmpty()) {
            throw NoArticlesException()
        }
        return formatSummaryResultUseCase(result)
    }
}

class NoArticlesException : Exception()

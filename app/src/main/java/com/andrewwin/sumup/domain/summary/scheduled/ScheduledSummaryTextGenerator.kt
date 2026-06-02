package com.andrewwin.sumup.domain.summary.scheduled

import com.andrewwin.sumup.domain.summary.model.SummaryResult
import com.andrewwin.sumup.domain.summary.formatter.SummaryResultFormatter
import javax.inject.Inject

interface ScheduledSummaryTextGenerator {
    suspend operator fun invoke(refresh: Boolean = false): String
}

class DefaultScheduledSummaryTextGenerator @Inject constructor(
    private val scheduledSummaryResultProvider: ScheduledSummaryResultProvider,
    private val formatSummaryResultUseCase: SummaryResultFormatter
) : ScheduledSummaryTextGenerator {

    override suspend fun invoke(refresh: Boolean): String {
        val result = scheduledSummaryResultProvider(refresh).getOrThrow()
        if (result is SummaryResult.Digest && result.themes.isEmpty()) {
            throw NoArticlesException()
        }
        return formatSummaryResultUseCase(result)
    }
}

class NoArticlesException : Exception()

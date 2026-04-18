package com.andrewwin.sumup.domain.usecase.common

import com.andrewwin.sumup.domain.usecase.ai.SummaryContext
import com.andrewwin.sumup.domain.usecase.ai.SummarizationEngineUseCase
import kotlinx.coroutines.flow.first
import javax.inject.Inject

interface GenerateSummaryUseCase {
    suspend operator fun invoke(): String
}

class GenerateSummaryUseCaseImpl @Inject constructor(
    private val collectScheduledSummaryArticlesUseCase: CollectScheduledSummaryArticlesUseCase,
    private val userPreferencesRepository: com.andrewwin.sumup.domain.repository.UserPreferencesRepository,
    private val summarizationEngineUseCase: SummarizationEngineUseCase
) : GenerateSummaryUseCase {

    override suspend fun invoke(): String {
        val articles = collectScheduledSummaryArticlesUseCase()

        if (articles.isEmpty()) {
            throw NoArticlesException()
        }

        val prefs = userPreferencesRepository.preferences.first()
        return summarizationEngineUseCase
            .summarizeArticles(
                articles = articles,
                context = SummaryContext.ScheduledSummary(articleCount = articles.size)
            )
            .getOrThrow()
    }
}

class NoArticlesException : Exception()










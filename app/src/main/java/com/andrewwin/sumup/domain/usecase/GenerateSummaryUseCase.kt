package com.andrewwin.sumup.domain.usecase

import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.usecase.ai.SummaryContext
import com.andrewwin.sumup.domain.usecase.ai.SummarizationEngineUseCase
import kotlinx.coroutines.flow.first
import javax.inject.Inject

interface GenerateSummaryUseCase {
    suspend operator fun invoke(): String
}

class GenerateSummaryUseCaseImpl @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val userPreferencesRepository: com.andrewwin.sumup.domain.repository.UserPreferencesRepository,
    private val summarizationEngineUseCase: SummarizationEngineUseCase
) : GenerateSummaryUseCase {

    override suspend fun invoke(): String {
        val articles = articleRepository.getEnabledArticlesOnce()

        if (articles.isEmpty()) {
            throw NoArticlesException()
        }

        val prefs = userPreferencesRepository.preferences.first()
        val limit = when (prefs.aiStrategy) {
            AiStrategy.LOCAL -> prefs.summaryNewsInScheduledExtractive.coerceAtLeast(1)
            AiStrategy.CLOUD, AiStrategy.ADAPTIVE -> prefs.summaryNewsInScheduledCloud.coerceAtLeast(1)
        }
        val articlesToSummarize = articles.take(limit)
        return summarizationEngineUseCase
            .summarizeArticles(
                articles = articlesToSummarize,
                context = SummaryContext.ScheduledSummary(articleCount = articlesToSummarize.size)
            )
            .getOrThrow()
    }
}

class NoArticlesException : Exception()

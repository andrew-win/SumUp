package com.andrewwin.sumup.domain.usecase.common

import com.andrewwin.sumup.domain.usecase.ai.SummarizeFeedUseCase
import com.andrewwin.sumup.domain.repository.ArticleRepository
import javax.inject.Inject

interface GenerateSummaryUseCase {
    suspend operator fun invoke(refresh: Boolean = false): String
}

class GenerateSummaryUseCaseImpl @Inject constructor(
    private val refreshArticlesUseCase: RefreshArticlesUseCase,
    private val summarizeFeedUseCase: SummarizeFeedUseCase,
    private val articleRepository: ArticleRepository,
    private val formatSummaryResultUseCase: FormatSummaryResultUseCase
) : GenerateSummaryUseCase {

    override suspend fun invoke(refresh: Boolean): String {
        if (refresh) {
            refreshArticlesUseCase()
        }

        val articles = articleRepository.getEnabledArticlesOnce()
        if (articles.isEmpty()) {
            throw NoArticlesException()
        }

        val result = summarizeFeedUseCase(articles).getOrThrow()
        return formatSummaryResultUseCase(result)
    }
}

class NoArticlesException : Exception()

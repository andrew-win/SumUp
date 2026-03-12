package com.andrewwin.sumup.domain.usecase

import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import javax.inject.Inject

interface GenerateSummaryUseCase {
    suspend operator fun invoke(): String
}

class GenerateSummaryUseCaseImpl @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val aiRepository: AiRepository
) : GenerateSummaryUseCase {

    override suspend fun invoke(): String {
        val articles = articleRepository.getEnabledArticlesOnce()

        if (articles.isEmpty()) throw NoArticlesException()

        val content = articles.take(MAX_ARTICLES_FOR_SUMMARY)
            .joinToString(separator = "\n\n") { "${it.title}: ${it.content}" }

        return aiRepository.summarize(content)
    }

    companion object {
        private const val MAX_ARTICLES_FOR_SUMMARY = 10
    }
}

class NoArticlesException : Exception()

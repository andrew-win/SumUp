package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.exception.NoActiveModelException
import com.andrewwin.sumup.domain.ExtractiveSummarizer
import com.andrewwin.sumup.domain.usecase.ai.FormatExtractiveSummaryUseCase
import javax.inject.Inject

class SummarizeContentUseCase @Inject constructor(
    private val aiRepository: AiRepository,
    private val articleRepository: ArticleRepository,
    private val formatExtractiveSummaryUseCase: FormatExtractiveSummaryUseCase
) {
    suspend operator fun invoke(article: Article): Result<String> {
        return try {
            val fullContent = articleRepository.fetchFullContent(article)
            try {
                Result.success(aiRepository.summarize(fullContent))
            } catch (e: NoActiveModelException) {
                // Adaptive Fallback for single article
                val sentences = ExtractiveSummarizer.summarize(fullContent, 5)
                val formatted = formatExtractiveSummaryUseCase.formatItem(
                    title = article.title,
                    sentences = sentences,
                    isScheduledReport = false
                )
                Result.success(formatted)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Для сумаризації довільного тексту (напр. всієї стрічки)
    suspend operator fun invoke(content: String): Result<String> {
        return try {
            try {
                Result.success(aiRepository.summarize(content))
            } catch (e: NoActiveModelException) {
                // Adaptive Fallback for custom content (e.g. feed summary)
                val sentences = ExtractiveSummarizer.summarize(content, 15)
                if (sentences.isEmpty()) return Result.success("")
                
                val result = sentences.joinToString("\n") { "- $it" }
                Result.success(result)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

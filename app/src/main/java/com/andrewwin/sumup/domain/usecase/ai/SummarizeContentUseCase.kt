package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import javax.inject.Inject

class SummarizeContentUseCase @Inject constructor(
    private val aiRepository: AiRepository,
    private val articleRepository: ArticleRepository
) {
    suspend operator fun invoke(article: Article): Result<String> {
        return try {
            val fullContent = articleRepository.fetchFullContent(article)
            Result.success(aiRepository.summarize(fullContent))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Для сумаризації довільного тексту (напр. всієї стрічки)
    suspend operator fun invoke(content: String): Result<String> {
        return try {
            Result.success(aiRepository.summarize(content))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import javax.inject.Inject

class AskQuestionUseCase @Inject constructor(
    private val aiRepository: AiRepository,
    private val articleRepository: ArticleRepository
) {
    suspend operator fun invoke(article: Article, question: String): Result<String> {
        return try {
            val fullContent = articleRepository.fetchFullContent(article)
            Result.success(aiRepository.askQuestion(fullContent, question))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend operator fun invoke(content: String, question: String): Result<String> {
        return try {
            Result.success(aiRepository.askQuestion(content, question))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

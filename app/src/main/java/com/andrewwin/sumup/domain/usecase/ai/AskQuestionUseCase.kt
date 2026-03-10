package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.domain.repository.AiRepository
import javax.inject.Inject

class AskQuestionUseCase @Inject constructor(
    private val aiRepository: AiRepository
) {
    suspend operator fun invoke(content: String, question: String): Result<String> {
        return try {
            Result.success(aiRepository.askQuestion(content, question))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

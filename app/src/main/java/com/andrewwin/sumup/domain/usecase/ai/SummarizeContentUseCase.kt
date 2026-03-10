package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.domain.repository.AiRepository
import javax.inject.Inject

class SummarizeContentUseCase @Inject constructor(
    private val aiRepository: AiRepository
) {
    suspend operator fun invoke(content: String): Result<String> {
        return try {
            Result.success(aiRepository.summarize(content))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

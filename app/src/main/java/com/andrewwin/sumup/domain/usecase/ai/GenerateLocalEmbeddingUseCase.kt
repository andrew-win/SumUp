package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.domain.repository.EmbeddingService
import com.andrewwin.sumup.domain.repository.ModelRepository
import javax.inject.Inject

class GenerateLocalEmbeddingUseCase @Inject constructor(
    private val modelRepository: ModelRepository,
    private val embeddingService: EmbeddingService
) {
    suspend operator fun invoke(text: String): FloatArray? {
        val hasLocalModel = modelRepository.isModelExists() && 
            embeddingService.initialize(modelRepository.getModelPath())
            
        if (hasLocalModel) {
            return try {
                embeddingService.getEmbedding(text)
            } catch (e: Exception) {
                null
            }
        }
        return null
    }
}

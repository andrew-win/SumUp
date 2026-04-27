package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.AiModelType
import com.andrewwin.sumup.data.remote.AiService
import com.andrewwin.sumup.domain.repository.AiModelConfigRepository
import javax.inject.Inject

class GenerateCloudEmbeddingUseCase @Inject constructor(
    private val aiModelConfigRepository: AiModelConfigRepository,
    private val aiService: AiService
) {
    suspend operator fun invoke(text: String): FloatArray? {
        val enabledConfigs = aiModelConfigRepository.getEnabledConfigsByType(AiModelType.EMBEDDING)
        if (enabledConfigs.isEmpty()) return null

        for (config in enabledConfigs) {
            try {
                return aiService.generateEmbedding(config, text)
            } catch (e: Exception) {
                continue
            }
        }
        return null
    }
}

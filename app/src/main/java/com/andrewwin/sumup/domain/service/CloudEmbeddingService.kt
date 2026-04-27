package com.andrewwin.sumup.domain.service

import com.andrewwin.sumup.domain.usecase.ai.GenerateCloudEmbeddingUseCase

class CloudEmbeddingService(
    private val generateCloudEmbeddingUseCase: GenerateCloudEmbeddingUseCase
) {
    suspend fun getCloudEmbedding(text: String): FloatArray? {
        val cloudEmbedding = generateCloudEmbeddingUseCase(text) ?: return null
        return EmbeddingUtils.normalize(cloudEmbedding)
    }
}

package com.andrewwin.sumup.domain.ai.service

import com.andrewwin.sumup.domain.ai.model.AiModelConfig
import com.andrewwin.sumup.domain.ai.model.AiModelType

interface AiProviderHandler {
    suspend fun fetchModels(apiKey: String, type: AiModelType): List<String>
    
    suspend fun generateResponse(
        config: AiModelConfig,
        prompt: String,
        content: String,
        expectJson: Boolean
    ): String

    suspend fun generateEmbeddings(
        config: AiModelConfig,
        texts: List<String>
    ): List<FloatArray?>
}

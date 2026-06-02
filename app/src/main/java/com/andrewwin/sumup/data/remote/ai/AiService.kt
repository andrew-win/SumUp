package com.andrewwin.sumup.data.remote.ai

import com.andrewwin.sumup.domain.ai.service.AiProviderHandler
import com.andrewwin.sumup.domain.ai.model.AiModelConfig
import com.andrewwin.sumup.domain.ai.model.AiModelType
import com.andrewwin.sumup.domain.ai.model.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiService(private val handlers: Map<AiProvider, AiProviderHandler>) {

    suspend fun fetchModels(provider: AiProvider, apiKey: String, type: AiModelType): List<String> =
        withContext(Dispatchers.IO) {
            getHandler(provider).fetchModels(apiKey, type)
        }

    suspend fun generateResponse(
        config: AiModelConfig,
        prompt: String,
        content: String,
        expectJson: Boolean = false
    ): String =
        withContext(Dispatchers.IO) {
            getHandler(config.provider).generateResponse(config, prompt, content, expectJson)
        }

    suspend fun generateEmbedding(config: AiModelConfig, text: String, debugRunId: Long? = null): FloatArray =
        withContext(Dispatchers.IO) {
            generateEmbeddings(config, listOf(text), debugRunId).firstOrNull()
                ?: throw Exception("Порожня відповідь від сервера")
        }

    suspend fun generateEmbeddings(config: AiModelConfig, texts: List<String>, debugRunId: Long? = null): List<FloatArray?> =
        withContext(Dispatchers.IO) {
            if (texts.isEmpty()) return@withContext emptyList()
            getHandler(config.provider).generateEmbeddings(config, texts)
        }

    private fun getHandler(provider: AiProvider): AiProviderHandler {
        return handlers[provider] ?: throw Exception("Provider $provider is not supported")
    }
}
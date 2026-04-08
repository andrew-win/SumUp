package com.andrewwin.sumup.domain.repository

import com.andrewwin.sumup.data.local.entities.AiModelConfig
import com.andrewwin.sumup.data.local.entities.AiModelType
import com.andrewwin.sumup.data.local.entities.AiProvider
import kotlinx.coroutines.flow.Flow

interface AiRepository {
    val allConfigs: Flow<List<AiModelConfig>>
    fun getConfigsByType(type: AiModelType): Flow<List<AiModelConfig>>
    suspend fun fetchAvailableModels(provider: AiProvider, apiKey: String, type: AiModelType): List<String>
    suspend fun addConfig(config: AiModelConfig)
    suspend fun updateConfig(config: AiModelConfig)
    suspend fun deleteConfig(config: AiModelConfig)
    suspend fun summarize(content: String, pointsPerNews: Int? = null): String
    suspend fun askQuestion(content: String, question: String): String
    suspend fun askWithPrompt(content: String, prompt: String): String
    suspend fun embed(text: String): FloatArray?
    suspend fun hasEnabledEmbeddingConfig(): Boolean
}







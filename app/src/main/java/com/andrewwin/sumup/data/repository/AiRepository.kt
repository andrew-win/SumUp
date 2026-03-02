package com.andrewwin.sumup.data.repository

import com.andrewwin.sumup.data.local.dao.AiModelDao
import com.andrewwin.sumup.data.local.entities.AiModelConfig
import com.andrewwin.sumup.data.local.entities.AiProvider
import com.andrewwin.sumup.data.remote.AiService
import kotlinx.coroutines.flow.Flow

class AiRepository(
    private val aiModelDao: AiModelDao,
    private val aiService: AiService = AiService()
) {
    val allConfigs: Flow<List<AiModelConfig>> = aiModelDao.getAllConfigs()

    suspend fun fetchAvailableModels(provider: AiProvider, apiKey: String): List<String> {
        return aiService.fetchModels(provider, apiKey)
    }

    suspend fun addConfig(config: AiModelConfig) {
        aiModelDao.insertConfig(config)
    }

    suspend fun updateConfig(config: AiModelConfig) {
        aiModelDao.updateConfig(config)
    }

    suspend fun deleteConfig(config: AiModelConfig) {
        aiModelDao.deleteConfig(config)
    }

    suspend fun summarize(content: String): String {
        val config = aiModelDao.getActiveConfig() ?: throw Exception("Немає активної ШІ моделі")
        val prompt = "Зроби стислий підсумок цієї новини українською мовою:"
        return aiService.generateResponse(config, prompt, content)
    }

    suspend fun askQuestion(content: String, question: String): String {
        val config = aiModelDao.getActiveConfig() ?: throw Exception("Немає активної ШІ моделі")
        val prompt = "Дай відповідь на питання: $question\n\nБазуючись на цьому тексті:"
        return aiService.generateResponse(config, prompt, content)
    }
}

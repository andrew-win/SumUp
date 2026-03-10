package com.andrewwin.sumup.data.repository

import com.andrewwin.sumup.data.local.dao.AiModelDao
import com.andrewwin.sumup.data.local.dao.UserPreferencesDao
import com.andrewwin.sumup.data.local.entities.AiModelConfig
import com.andrewwin.sumup.data.local.entities.AiProvider
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.remote.AiService
import com.andrewwin.sumup.domain.ExtractiveSummarizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class AiRepository(
    private val aiModelDao: AiModelDao,
    private val prefsDao: UserPreferencesDao,
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
        val prefs = prefsDao.getUserPreferences().first()
        val strategy = prefs?.aiStrategy ?: AiStrategy.ADAPTIVE
        val activeConfig = aiModelDao.getActiveConfig()

        if (strategy == AiStrategy.EXTRACTIVE || (strategy == AiStrategy.ADAPTIVE && activeConfig == null)) {
            return ExtractiveSummarizer.summarize(content).joinToString("\n") { "- $it" }
        }

        return when (strategy) {
            AiStrategy.CLOUD -> {
                callCloudSummarize(content)
            }
            AiStrategy.ADAPTIVE -> {
                val processedContent = if (content.length > 1000) {
                    ExtractiveSummarizer.summarize(content, 10).joinToString(" ")
                } else {
                    content
                }
                aiService.generateResponse(activeConfig!!, "Зроби стислий підсумок цієї новини українською мовою:", processedContent)
            }
            AiStrategy.EXTRACTIVE -> {
                ExtractiveSummarizer.summarize(content).joinToString("\n") { "- $it" }
            }
        }
    }

    private suspend fun callCloudSummarize(content: String): String {
        val config = aiModelDao.getActiveConfig() ?: throw Exception("Немає активної ШІ моделі")
        val prompt = "Зроби стислий підсумок цієї новини українською мовою:"
        return aiService.generateResponse(config, prompt, content)
    }

    suspend fun askQuestion(content: String, question: String): String {
        val prefs = prefsDao.getUserPreferences().first()
        val activeConfig = aiModelDao.getActiveConfig()
        
        if (prefs?.aiStrategy == AiStrategy.EXTRACTIVE || (prefs?.aiStrategy == AiStrategy.ADAPTIVE && activeConfig == null)) {
            throw Exception("Екстрактивна сумаризація не підтримує запитання")
        }

        val config = activeConfig ?: throw Exception("Немає активної ШІ моделі")
        val prompt = "Дай відповідь на питання: $question\n\nБазуючись на цьому тексті:"
        return aiService.generateResponse(config, prompt, content)
    }
}

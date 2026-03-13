package com.andrewwin.sumup.data.repository

import com.andrewwin.sumup.data.local.dao.AiModelDao
import com.andrewwin.sumup.data.local.dao.UserPreferencesDao
import com.andrewwin.sumup.data.local.entities.AiModelConfig
import com.andrewwin.sumup.data.local.entities.AiProvider
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.remote.AiService
import com.andrewwin.sumup.domain.ExtractiveSummarizer
import com.andrewwin.sumup.domain.exception.NoActiveModelException
import com.andrewwin.sumup.domain.exception.UnsupportedStrategyException
import com.andrewwin.sumup.domain.repository.AiRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class AiRepositoryImpl @Inject constructor(
    private val aiModelDao: AiModelDao,
    private val prefsDao: UserPreferencesDao,
    private val aiService: AiService
) : AiRepository {

    override val allConfigs: Flow<List<AiModelConfig>> = aiModelDao.getAllConfigs()

    override suspend fun fetchAvailableModels(provider: AiProvider, apiKey: String): List<String> =
        aiService.fetchModels(provider, apiKey)

    override suspend fun addConfig(config: AiModelConfig) = aiModelDao.insertConfig(config)
    override suspend fun updateConfig(config: AiModelConfig) = aiModelDao.updateConfig(config)
    override suspend fun deleteConfig(config: AiModelConfig) = aiModelDao.deleteConfig(config)

    override suspend fun summarize(content: String): String {
        val prefs = prefsDao.getUserPreferences().first()
        val strategy = prefs?.aiStrategy ?: AiStrategy.ADAPTIVE
        val activeConfig = aiModelDao.getActiveConfig()

        if (strategy == AiStrategy.EXTRACTIVE || (strategy == AiStrategy.ADAPTIVE && activeConfig == null)) {
            return ExtractiveSummarizer.summarize(content, 5).joinToString("\n") { "- $it" }
        }

        return when (strategy) {
            AiStrategy.CLOUD -> {
                val config = activeConfig ?: throw NoActiveModelException()
                aiService.generateResponse(config, SUMMARY_PROMPT, content.take(MAX_AI_CONTENT_LENGTH))
            }
            AiStrategy.ADAPTIVE -> {
                val processedContent = if (content.length > MAX_AI_CONTENT_LENGTH) {
                    ExtractiveSummarizer.summarize(content, 15).joinToString(" ")
                } else {
                    content
                }
                aiService.generateResponse(activeConfig!!, SUMMARY_PROMPT, processedContent)
            }
            AiStrategy.EXTRACTIVE -> {
                ExtractiveSummarizer.summarize(content, 5).joinToString("\n") { "- $it" }
            }
        }
    }

    override suspend fun askQuestion(content: String, question: String): String {
        val prefs = prefsDao.getUserPreferences().first()
        val activeConfig = aiModelDao.getActiveConfig()

        if (prefs?.aiStrategy == AiStrategy.EXTRACTIVE || (prefs?.aiStrategy == AiStrategy.ADAPTIVE && activeConfig == null)) {
            throw UnsupportedStrategyException()
        }

        val config = activeConfig ?: throw NoActiveModelException()
        val prompt = "$QUESTION_PROMPT_PREFIX $question\n\n$QUESTION_PROMPT_SUFFIX"
        return aiService.generateResponse(config, prompt, content.take(MAX_AI_CONTENT_LENGTH))
    }

    companion object {
        private const val MAX_AI_CONTENT_LENGTH = 12000
        private const val SUMMARY_PROMPT = "Зроби стислий підсумок цієї новини українською мовою:"
        private const val QUESTION_PROMPT_PREFIX = "Дай відповідь на питання:"
        private const val QUESTION_PROMPT_SUFFIX = "Базуючись на цьому тексті:"
    }
}

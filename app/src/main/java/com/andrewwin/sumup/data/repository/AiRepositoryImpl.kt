package com.andrewwin.sumup.data.repository

import com.andrewwin.sumup.data.local.dao.AiModelDao
import com.andrewwin.sumup.data.local.dao.UserPreferencesDao
import com.andrewwin.sumup.data.local.entities.AiModelConfig
import com.andrewwin.sumup.data.local.entities.AiModelType
import com.andrewwin.sumup.data.local.entities.AiProvider
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.remote.AiService
import com.andrewwin.sumup.domain.ExtractiveSummarizer
import com.andrewwin.sumup.domain.exception.NoActiveModelException
import com.andrewwin.sumup.domain.exception.UnsupportedStrategyException
import com.andrewwin.sumup.domain.provider.AiPromptProvider
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.usecase.ai.FormatExtractiveSummaryUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class AiRepositoryImpl @Inject constructor(
    private val aiModelDao: AiModelDao,
    private val prefsDao: UserPreferencesDao,
    private val aiService: AiService,
    private val formatExtractiveSummaryUseCase: FormatExtractiveSummaryUseCase,
    private val aiPromptProvider: AiPromptProvider
) : AiRepository {

    override val allConfigs: Flow<List<AiModelConfig>> = aiModelDao.getAllConfigs()

    override fun getConfigsByType(type: AiModelType): Flow<List<AiModelConfig>> =
        aiModelDao.getConfigsByType(type)

    override suspend fun fetchAvailableModels(provider: AiProvider, apiKey: String, type: AiModelType): List<String> =
        aiService.fetchModels(provider, apiKey, type)

    override suspend fun addConfig(config: AiModelConfig) = aiModelDao.insertConfig(config)
    override suspend fun updateConfig(config: AiModelConfig) = aiModelDao.updateConfig(config)
    override suspend fun deleteConfig(config: AiModelConfig) = aiModelDao.deleteConfig(config)

    override suspend fun summarize(content: String, extractiveSentenceCount: Int?): String {
        val prefs = prefsDao.getUserPreferences().first()
        val strategy = prefs?.aiStrategy ?: AiStrategy.ADAPTIVE
        val maxTotalChars = (prefs?.aiMaxCharsTotal ?: DEFAULT_MAX_AI_CONTENT_LENGTH).coerceAtLeast(1000)
        val sentenceCount = extractiveSentenceCount ?: (prefs?.extractiveSentencesInScheduled ?: 5)

        if (strategy == AiStrategy.LOCAL) {
            return ExtractiveSummarizer.summarize(content, sentenceCount).joinToString(" ")
        }

        val enabledConfigs = aiModelDao.getEnabledConfigsByType(AiModelType.SUMMARY)
        if (enabledConfigs.isEmpty()) {
            return ExtractiveSummarizer.summarize(content, sentenceCount).joinToString(" ")
        }

        var lastException: Exception? = null
        for (config in enabledConfigs) {
            try {
                val processedContent = if (strategy == AiStrategy.ADAPTIVE && prefs?.isAdaptiveExtractivePreprocessingEnabled == true && content.length > maxTotalChars) {
                    ExtractiveSummarizer.summarize(content, sentenceCount).joinToString(" ")
                } else {
                    content.take(maxTotalChars)
                }

                val prompt = if (prefs?.isCustomSummaryPromptEnabled == true) {
                    prefs.summaryPrompt.ifBlank { aiPromptProvider.defaultSummaryPrompt() }
                } else {
                    aiPromptProvider.defaultSummaryPrompt()
                }
                
                return aiService.generateResponse(config, prompt, processedContent)
            } catch (e: com.andrewwin.sumup.domain.exception.AiServiceException) {
                lastException = e
                continue
            } catch (e: Exception) {
                throw e
            }
        }

        // If all cloud fails, fallback to extractive as a last resort for CLOUD/ADAPTIVE
        return ExtractiveSummarizer.summarize(content, sentenceCount).joinToString(" ")
    }

    override suspend fun askQuestion(content: String, question: String): String {
        val prefs = prefsDao.getUserPreferences().first()
        val maxTotalChars = (prefs?.aiMaxCharsTotal ?: DEFAULT_MAX_AI_CONTENT_LENGTH).coerceAtLeast(1000)
        
        if (prefs?.aiStrategy == AiStrategy.LOCAL) {
            throw UnsupportedStrategyException()
        }

        val enabledConfigs = aiModelDao.getEnabledConfigsByType(AiModelType.SUMMARY)
        if (enabledConfigs.isEmpty()) throw NoActiveModelException()

        for (config in enabledConfigs) {
            try {
                val prompt = "${aiPromptProvider.questionPromptPrefix()} $question\n\n${aiPromptProvider.questionPromptSuffix()}"
                return aiService.generateResponse(config, prompt, content.take(maxTotalChars))
            } catch (e: com.andrewwin.sumup.domain.exception.AiServiceException) {
                continue
            }
        }

        throw com.andrewwin.sumup.domain.exception.AllAiModelsFailedException()
    }

    override suspend fun embed(text: String): FloatArray? {
        val enabledConfigs = aiModelDao.getEnabledConfigsByType(AiModelType.EMBEDDING)
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

    override suspend fun hasEnabledEmbeddingConfig(): Boolean {
        return aiModelDao.getEnabledConfigsByType(AiModelType.EMBEDDING).isNotEmpty()
    }

    companion object {
        private const val DEFAULT_MAX_AI_CONTENT_LENGTH = 12000
    }
}

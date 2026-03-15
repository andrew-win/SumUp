package com.andrewwin.sumup.data.repository

import android.util.Log
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
import com.andrewwin.sumup.domain.usecase.ai.FormatExtractiveSummaryUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class AiRepositoryImpl @Inject constructor(
    private val aiModelDao: AiModelDao,
    private val prefsDao: UserPreferencesDao,
    private val aiService: AiService,
    private val formatExtractiveSummaryUseCase: FormatExtractiveSummaryUseCase
) : AiRepository {

    override val allConfigs: Flow<List<AiModelConfig>> = aiModelDao.getAllConfigs()

    override suspend fun fetchAvailableModels(provider: AiProvider, apiKey: String): List<String> =
        aiService.fetchModels(provider, apiKey)

    override suspend fun addConfig(config: AiModelConfig) = aiModelDao.insertConfig(config)
    override suspend fun updateConfig(config: AiModelConfig) = aiModelDao.updateConfig(config)
    override suspend fun deleteConfig(config: AiModelConfig) = aiModelDao.deleteConfig(config)

    override suspend fun summarize(content: String): String {
        Log.d("AiRepo", "Summarize started, content length: ${content.length}")
        val prefs = prefsDao.getUserPreferences().first()
        val strategy = prefs?.aiStrategy ?: AiStrategy.ADAPTIVE

        val enabledConfigs = aiModelDao.getEnabledConfigs()
        if (enabledConfigs.isEmpty()) {
            Log.w("AiRepo", "No enabled configs for cloud/adaptive strategy")
            throw NoActiveModelException()
        }

        var lastException: Exception? = null
        for (config in enabledConfigs) {
            try {
                Log.d("AiRepo", "Attempting summary with config: ${config.name}")
                val response = when (strategy) {
                    AiStrategy.CLOUD -> {
                        aiService.generateResponse(config, SUMMARY_PROMPT, content.take(MAX_AI_CONTENT_LENGTH))
                    }
                    AiStrategy.ADAPTIVE -> {
                        val processedContent = if (content.length > MAX_AI_CONTENT_LENGTH) {
                            Log.d("AiRepo", "Content exceeds limit, shrinking via Extractive")
                            ExtractiveSummarizer.summarize(content, 15).joinToString(" ")
                        } else {
                            content
                        }
                        aiService.generateResponse(config, SUMMARY_PROMPT, processedContent)
                    }
                    else -> "" // Should not happen
                }
                Log.d("AiRepo", "AI response received from ${config.name}. Length: ${response.length}")
                return response
            } catch (e: com.andrewwin.sumup.domain.exception.AiServiceException) {
                Log.w("AiRepo", "Failover: Config ${config.name} failed with ${e.javaClass.simpleName}. Trying next...", e)
                lastException = e
                continue
            } catch (e: Exception) {
                Log.e("AiRepo", "Non-failover error with config ${config.name}", e)
                throw e
            }
        }

        Log.e("AiRepo", "All AI models failed")
        throw com.andrewwin.sumup.domain.exception.AllAiModelsFailedException()
    }

    override suspend fun askQuestion(content: String, question: String): String {
        val prefs = prefsDao.getUserPreferences().first()
        val enabledConfigs = aiModelDao.getEnabledConfigs()

        if (prefs?.aiStrategy == AiStrategy.EXTRACTIVE || (prefs?.aiStrategy == AiStrategy.ADAPTIVE && enabledConfigs.isEmpty())) {
            throw UnsupportedStrategyException()
        }

        if (enabledConfigs.isEmpty()) throw NoActiveModelException()

        var lastException: Exception? = null
        for (config in enabledConfigs) {
            try {
                Log.d("AiRepo", "Attempting question with config: ${config.name}")
                val prompt = "$QUESTION_PROMPT_PREFIX $question\n\n$QUESTION_PROMPT_SUFFIX"
                return aiService.generateResponse(config, prompt, content.take(MAX_AI_CONTENT_LENGTH))
            } catch (e: com.andrewwin.sumup.domain.exception.AiServiceException) {
                Log.w("AiRepo", "Failover: Config ${config.name} failed with ${e.javaClass.simpleName}. Trying next...", e)
                lastException = e
                continue
            } catch (e: Exception) {
                Log.e("AiRepo", "Non-failover error with config ${config.name}", e)
                throw e
            }
        }

        Log.e("AiRepo", "All AI models failed for question")
        throw com.andrewwin.sumup.domain.exception.AllAiModelsFailedException()
    }

    companion object {
        private const val MAX_AI_CONTENT_LENGTH = 12000
        private const val SUMMARY_PROMPT = "Зроби стислий підсумок цієї новини українською мовою:"
        private const val QUESTION_PROMPT_PREFIX = "Дай відповідь на питання:"
        private const val QUESTION_PROMPT_SUFFIX = "Базуючись на цьому тексті:"
    }
}

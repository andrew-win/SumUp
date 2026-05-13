package com.andrewwin.sumup.data.ai

import com.andrewwin.sumup.data.local.entities.AiModelType
import com.andrewwin.sumup.data.remote.AiService
import com.andrewwin.sumup.domain.repository.AiModelConfigRepository
import com.andrewwin.sumup.domain.ai.AiRequestSender
import com.andrewwin.sumup.domain.support.AiServiceException
import com.andrewwin.sumup.domain.support.NoActiveModelException
import com.andrewwin.sumup.domain.support.AllAiModelsFailedException
import javax.inject.Inject

class CloudAiRequestSender @Inject constructor(
    private val aiModelConfigRepository: AiModelConfigRepository,
    private val aiService: AiService
) : AiRequestSender {
    override suspend fun sendSummaryRequest(prompt: String, content: String): String {
        val enabledConfigs = aiModelConfigRepository.getEnabledConfigsByType(AiModelType.SUMMARY)
        if (enabledConfigs.isEmpty()) {
            throw NoActiveModelException()
        }

        var lastFailure: AiServiceException? = null
        for (config in enabledConfigs) {
            try {
                val response = aiService.generateResponse(
                    config = config,
                    prompt = prompt,
                    content = content,
                    expectJson = true
                )
                
                aiModelConfigRepository.setLastUsedSummaryModelName(config.modelName.takeIf { it.isNotBlank() })
                return response
            } catch (e: AiServiceException) {
                lastFailure = e
                // If this model fails, try the next one
                continue
            }
        }

        throw AllAiModelsFailedException(lastFailure)
    }
}

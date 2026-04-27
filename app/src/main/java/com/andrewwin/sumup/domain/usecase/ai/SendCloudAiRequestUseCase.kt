package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.AiModelType
import com.andrewwin.sumup.data.remote.AiService
import com.andrewwin.sumup.domain.repository.AiModelConfigRepository
import com.andrewwin.sumup.domain.support.AiServiceException
import com.andrewwin.sumup.domain.support.NoActiveModelException
import com.andrewwin.sumup.domain.support.AllAiModelsFailedException
import javax.inject.Inject

class SendCloudAiRequestUseCase @Inject constructor(
    private val aiModelConfigRepository: AiModelConfigRepository,
    private val aiService: AiService
) {
    suspend operator fun invoke(prompt: String, content: String): String {
        val enabledConfigs = aiModelConfigRepository.getEnabledConfigsByType(AiModelType.SUMMARY)
        if (enabledConfigs.isEmpty()) {
            throw NoActiveModelException()
        }

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
                // If this model fails, try the next one
                continue
            }
        }

        throw AllAiModelsFailedException()
    }
}

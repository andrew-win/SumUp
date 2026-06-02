package com.andrewwin.sumup.data.remote.ai

import android.content.Context
import com.andrewwin.sumup.R
import com.andrewwin.sumup.domain.ai.model.AiModelConfig
import com.andrewwin.sumup.domain.ai.model.AiModelType
import com.andrewwin.sumup.domain.ai.service.AiModelFailure
import com.andrewwin.sumup.domain.ai.service.AiRequestSender
import com.andrewwin.sumup.domain.ai.service.CloudAiResponse
import com.andrewwin.sumup.domain.ai.repository.AiModelConfigRepository
import com.andrewwin.sumup.domain.support.AiServiceException
import com.andrewwin.sumup.domain.support.AllAiModelsFailedException
import com.andrewwin.sumup.domain.support.NoActiveModelException
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class CloudAiRequestSender @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiModelConfigRepository: AiModelConfigRepository,
    private val aiService: AiService
) : AiRequestSender {
    override suspend fun sendSummaryRequest(prompt: String, content: String): CloudAiResponse {
        val enabledConfigs = aiModelConfigRepository.getEnabledConfigsByType(AiModelType.SUMMARY)
        if (enabledConfigs.isEmpty()) {
            throw NoActiveModelException()
        }

        var lastFailure: Throwable? = null
        val failures = mutableListOf<AiModelFailure>()
        for (config in enabledConfigs) {
            try {
                val response = aiService.generateResponse(
                    config = config,
                    prompt = prompt,
                    content = content,
                    expectJson = true
                )
                
                aiModelConfigRepository.setLastUsedSummaryModelName(config.modelName.takeIf { it.isNotBlank() })
                return CloudAiResponse(
                    content = response,
                    modelName = config.modelName.takeIf { it.isNotBlank() },
                    failedAttempts = failures.toList()
                )
            } catch (e: AiServiceException) {
                lastFailure = e
                failures += AiModelFailure(
                    configName = config.displayName(),
                    message = e.toUserMessage(),
                    modelName = config.modelName.takeIf { it.isNotBlank() },
                    code = e.code
                )
                // If this model fails, try the next one
                continue
            } catch (e: Exception) {
                lastFailure = e
                failures += AiModelFailure(
                    configName = config.displayName(),
                    message = e.localizedMessage.orEmpty().ifBlank {
                        context.getString(R.string.error_all_ai_models_failed)
                    },
                    modelName = config.modelName.takeIf { it.isNotBlank() }
                )
                continue
            }
        }

        throw AllAiModelsFailedException(failures = failures, cause = lastFailure)
    }

    private fun AiModelConfig.displayName(): String =
        name.takeIf { it.isNotBlank() }
            ?: modelName.substringAfter('/').takeIf { it.isNotBlank() }
            ?: provider.name

    private fun AiServiceException.toUserMessage(): String =
        when (code) {
            PAYMENT_REQUIRED_CODE -> context.getString(R.string.ai_error_payment_required)
            PAYLOAD_TOO_LARGE_CODE -> context.getString(R.string.ai_error_payload_too_large)
            RATE_LIMIT_CODE -> context.getString(R.string.ai_error_rate_limit)
            else -> localizedMessage.orEmpty().ifBlank {
                context.getString(R.string.error_all_ai_models_failed)
            }
        }

    private companion object {
        private const val PAYMENT_REQUIRED_CODE = 402
        private const val PAYLOAD_TOO_LARGE_CODE = 413
        private const val RATE_LIMIT_CODE = 429
    }
}

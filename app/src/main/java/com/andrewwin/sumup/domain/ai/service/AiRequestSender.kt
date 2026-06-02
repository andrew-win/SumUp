package com.andrewwin.sumup.domain.ai.service

interface AiRequestSender {
    suspend fun sendSummaryRequest(prompt: String, content: String): CloudAiResponse
}

data class CloudAiResponse(
    val content: String,
    val modelName: String?,
    val failedAttempts: List<AiModelFailure> = emptyList()
)

data class AiModelFailure(
    val configName: String,
    val message: String,
    val modelName: String? = null,
    val code: Int? = null
)

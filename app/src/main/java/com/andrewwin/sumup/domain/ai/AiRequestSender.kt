package com.andrewwin.sumup.domain.ai

interface AiRequestSender {
    suspend fun sendSummaryRequest(prompt: String, content: String): String
}

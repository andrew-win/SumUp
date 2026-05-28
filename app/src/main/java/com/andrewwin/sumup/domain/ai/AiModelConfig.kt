package com.andrewwin.sumup.domain.ai

typealias AiModelConfig = com.andrewwin.sumup.domain.entities.ai.AiModelConfig
typealias AiModelType = com.andrewwin.sumup.domain.entities.ai.AiModelType
typealias AiProvider = com.andrewwin.sumup.domain.entities.ai.AiProvider

fun AiModelConfig.normalizedStableKey(): String = "${type.name}:${apiKey.trim()}"

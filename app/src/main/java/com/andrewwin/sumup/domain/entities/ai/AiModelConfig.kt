package com.andrewwin.sumup.domain.entities.ai

data class AiModelConfig(
    val id: Long = 0,
    val name: String,
    val provider: AiProvider,
    val apiKey: String,
    val modelName: String,
    val isEnabled: Boolean = true,
    val type: AiModelType = AiModelType.SUMMARY,
    val sortOrder: Int = Int.MAX_VALUE
) {
    companion object
}

fun AiModelConfig.normalizedStableKey(): String = "${type.name}:${apiKey.trim()}"

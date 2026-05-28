package com.andrewwin.sumup.data.mappers

import com.andrewwin.sumup.data.local.entities.AiModelConfig as RoomAiModelConfig
import com.andrewwin.sumup.data.local.entities.AiModelType as RoomAiModelType
import com.andrewwin.sumup.data.local.entities.AiProvider as RoomAiProvider
import com.andrewwin.sumup.domain.ai.AiModelConfig
import com.andrewwin.sumup.domain.ai.AiModelType
import com.andrewwin.sumup.domain.ai.AiProvider

fun RoomAiModelConfig.toDomainModel(): AiModelConfig = AiModelConfig(
    id = id,
    name = name,
    provider = provider.toDomainModel(),
    apiKey = apiKey,
    modelName = modelName,
    isEnabled = isEnabled,
    type = type.toDomainModel(),
    sortOrder = sortOrder
)

fun AiModelConfig.toRoomEntity(): RoomAiModelConfig = RoomAiModelConfig(
    id = id,
    name = name,
    provider = provider.toRoomEntity(),
    apiKey = apiKey,
    modelName = modelName,
    isEnabled = isEnabled,
    type = type.toRoomEntity(),
    sortOrder = sortOrder
)

fun RoomAiModelType.toDomainModel(): AiModelType = when (this) {
    RoomAiModelType.SUMMARY -> AiModelType.SUMMARY
    RoomAiModelType.EMBEDDING -> AiModelType.EMBEDDING
}

fun AiModelType.toRoomEntity(): RoomAiModelType = when (this) {
    AiModelType.SUMMARY -> RoomAiModelType.SUMMARY
    AiModelType.EMBEDDING -> RoomAiModelType.EMBEDDING
}

fun RoomAiProvider.toDomainModel(): AiProvider = when (this) {
    RoomAiProvider.GEMINI -> AiProvider.GEMINI
    RoomAiProvider.GROQ -> AiProvider.GROQ
    RoomAiProvider.OPENROUTER -> AiProvider.OPENROUTER
    RoomAiProvider.COHERE -> AiProvider.COHERE
    RoomAiProvider.CHATGPT -> AiProvider.CHATGPT
    RoomAiProvider.CLAUDE -> AiProvider.CLAUDE
}

fun AiProvider.toRoomEntity(): RoomAiProvider = when (this) {
    AiProvider.GEMINI -> RoomAiProvider.GEMINI
    AiProvider.GROQ -> RoomAiProvider.GROQ
    AiProvider.OPENROUTER -> RoomAiProvider.OPENROUTER
    AiProvider.COHERE -> RoomAiProvider.COHERE
    AiProvider.CHATGPT -> RoomAiProvider.CHATGPT
    AiProvider.CLAUDE -> RoomAiProvider.CLAUDE
}

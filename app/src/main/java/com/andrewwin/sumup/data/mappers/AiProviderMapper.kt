package com.andrewwin.sumup.data.mappers

import com.andrewwin.sumup.data.local.entities.AiProvider as RoomAiProvider
import com.andrewwin.sumup.domain.entities.ai.AiProvider

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

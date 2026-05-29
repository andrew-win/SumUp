package com.andrewwin.sumup.data.mappers

import com.andrewwin.sumup.data.local.entities.AiModelType as RoomAiModelType
import com.andrewwin.sumup.domain.entities.ai.AiModelType

fun RoomAiModelType.toDomainModel(): AiModelType = when (this) {
    RoomAiModelType.SUMMARY -> AiModelType.SUMMARY
    RoomAiModelType.EMBEDDING -> AiModelType.EMBEDDING
}

fun AiModelType.toRoomEntity(): RoomAiModelType = when (this) {
    AiModelType.SUMMARY -> RoomAiModelType.SUMMARY
    AiModelType.EMBEDDING -> RoomAiModelType.EMBEDDING
}

package com.andrewwin.sumup.data.mappers

import com.andrewwin.sumup.data.local.entities.AiStrategy as RoomAiStrategy
import com.andrewwin.sumup.domain.entities.settings.AiStrategy

fun RoomAiStrategy.toDomainModel(): AiStrategy = when (this) {
    RoomAiStrategy.CLOUD -> AiStrategy.CLOUD
    RoomAiStrategy.LOCAL -> AiStrategy.LOCAL
    RoomAiStrategy.ADAPTIVE -> AiStrategy.ADAPTIVE
}

fun AiStrategy.toRoomEntity(): RoomAiStrategy = when (this) {
    AiStrategy.CLOUD -> RoomAiStrategy.CLOUD
    AiStrategy.LOCAL -> RoomAiStrategy.LOCAL
    AiStrategy.ADAPTIVE -> RoomAiStrategy.ADAPTIVE
}

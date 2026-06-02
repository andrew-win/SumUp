package com.andrewwin.sumup.data.mappers

import com.andrewwin.sumup.data.local.entities.AiModelConfig as RoomAiModelConfig
import com.andrewwin.sumup.domain.ai.model.AiModelConfig

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

package com.andrewwin.sumup.data.mappers

import com.andrewwin.sumup.data.local.entities.Summary
import com.andrewwin.sumup.domain.summary.model.SummaryRecord

fun Summary.toDomainModel(): SummaryRecord = SummaryRecord(
    id = id,
    content = content,
    strategy = strategy.toDomainModel(),
    createdAt = createdAt,
    isError = isError,
    isFavorite = isFavorite,
    executionLabel = executionLabel,
    executionNote = executionNote
)

fun SummaryRecord.toRoomEntity(): Summary = Summary(
    id = id,
    content = content,
    strategy = strategy.toRoomEntity(),
    createdAt = createdAt,
    isError = isError,
    isFavorite = isFavorite,
    executionLabel = executionLabel,
    executionNote = executionNote
)

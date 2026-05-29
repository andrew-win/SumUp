package com.andrewwin.sumup.data.mappers

import com.andrewwin.sumup.data.local.entities.PreparedScheduledSummary
import com.andrewwin.sumup.domain.entities.summary.ScheduledSummaryDraft

fun PreparedScheduledSummary.toDomainModel(): ScheduledSummaryDraft = ScheduledSummaryDraft(
    scheduledAt = scheduledAt,
    content = content,
    strategy = strategy.toDomainModel(),
    createdAt = createdAt,
    isError = isError,
    executionLabel = executionLabel,
    executionNote = executionNote
)

fun ScheduledSummaryDraft.toRoomEntity(): PreparedScheduledSummary = PreparedScheduledSummary(
    scheduledAt = scheduledAt,
    content = content,
    strategy = strategy.toRoomEntity(),
    createdAt = createdAt,
    isError = isError,
    executionLabel = executionLabel,
    executionNote = executionNote
)

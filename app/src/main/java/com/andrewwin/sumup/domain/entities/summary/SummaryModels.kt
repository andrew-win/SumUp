package com.andrewwin.sumup.domain.entities.summary

import com.andrewwin.sumup.domain.settings.AiStrategy

data class SummaryRecord(
    val id: Long = 0,
    val content: String,
    val strategy: AiStrategy = AiStrategy.ADAPTIVE,
    val createdAt: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val isFavorite: Boolean = false,
    val executionLabel: String? = null,
    val executionNote: String? = null
)

data class ScheduledSummaryDraft(
    val scheduledAt: Long,
    val content: String,
    val strategy: AiStrategy = AiStrategy.ADAPTIVE,
    val createdAt: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val executionLabel: String? = null,
    val executionNote: String? = null
)

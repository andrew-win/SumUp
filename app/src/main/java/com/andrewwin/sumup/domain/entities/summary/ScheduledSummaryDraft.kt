package com.andrewwin.sumup.domain.entities.summary

import com.andrewwin.sumup.domain.entities.settings.AiStrategy

data class ScheduledSummaryDraft(
    val scheduledAt: Long,
    val content: String,
    val strategy: AiStrategy = AiStrategy.ADAPTIVE,
    val createdAt: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val executionLabel: String? = null,
    val executionNote: String? = null
)

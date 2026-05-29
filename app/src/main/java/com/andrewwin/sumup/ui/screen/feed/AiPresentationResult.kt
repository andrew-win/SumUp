package com.andrewwin.sumup.ui.screen.feed

import com.andrewwin.sumup.domain.entities.summary.SummaryResult

data class AiPresentationResult(
    val result: SummaryResult,
    val rawText: String,
    val executionLabel: String? = null,
    val executionNote: String? = null
)

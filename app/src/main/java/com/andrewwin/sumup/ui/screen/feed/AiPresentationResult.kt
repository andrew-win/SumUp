package com.andrewwin.sumup.ui.screen.feed

import com.andrewwin.sumup.domain.summary.SummaryResult

data class AiPresentationResult(
    val result: SummaryResult,
    val rawText: String,
    val executionLabel: String? = null
)

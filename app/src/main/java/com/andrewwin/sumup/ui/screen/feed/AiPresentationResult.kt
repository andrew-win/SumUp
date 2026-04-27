package com.andrewwin.sumup.ui.screen.feed

import com.andrewwin.sumup.domain.usecase.ai.SummaryResult

data class AiPresentationResult(
    val result: SummaryResult,
    val rawText: String
)

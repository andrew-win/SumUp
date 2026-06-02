package com.andrewwin.sumup.domain.summary.model

data class SummaryItem(
    val text: String,
    val sources: List<SummarySourceRef> = emptyList()
)

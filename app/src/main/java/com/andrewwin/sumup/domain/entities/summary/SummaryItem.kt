package com.andrewwin.sumup.domain.entities.summary

data class SummaryItem(
    val text: String,
    val sources: List<SummarySourceRef> = emptyList()
)

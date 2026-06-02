package com.andrewwin.sumup.domain.summary.model

data class DigestTheme(
    val title: String,
    val summary: String? = null,
    val items: List<SummaryItem> = emptyList()
)

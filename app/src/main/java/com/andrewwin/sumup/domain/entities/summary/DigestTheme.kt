package com.andrewwin.sumup.domain.entities.summary

data class DigestTheme(
    val title: String,
    val summary: String? = null,
    val items: List<SummaryItem> = emptyList()
)

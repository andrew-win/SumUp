package com.andrewwin.sumup.ui.screen.summary.model

data class SummaryChartItem(
    val headline: String,
    val value: Float,
    val displayValue: String,
    val sourceName: String? = null,
    val sourceUrl: String? = null,
    val isValueUnavailable: Boolean = false
)

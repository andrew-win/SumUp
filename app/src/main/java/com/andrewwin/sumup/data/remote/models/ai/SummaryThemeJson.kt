package com.andrewwin.sumup.data.remote.models.ai

import kotlinx.serialization.Serializable

@Serializable
data class SummaryThemeJson(
    val title: String? = null,
    val summary: String? = null,
    val items: List<SummaryThemeItemJson> = emptyList()
)

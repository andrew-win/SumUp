package com.andrewwin.sumup.data.remote.ai.models

import kotlinx.serialization.Serializable

@Serializable
data class SummaryThemeJson(
    val title: String? = null,
    val summary: String? = null,
    val items: List<SummaryThemeItemJson> = emptyList()
)

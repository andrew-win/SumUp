package com.andrewwin.sumup.data.remote.ai.models

import kotlinx.serialization.Serializable

@Serializable
data class SummaryResponseJson(
    val main: String? = null,
    val details: List<SummaryDetailJson> = emptyList(),
    val items: List<SummaryItemJson> = emptyList(),
    val themes: List<SummaryThemeJson> = emptyList()
)

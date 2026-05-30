package com.andrewwin.sumup.data.remote.ai.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SummaryDetailJson(
    val text: String,
    @SerialName("source_ids") val sourceIds: List<String> = emptyList()
)

package com.andrewwin.sumup.data.remote.models.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SummaryItemJson(
    val title: String? = null,
    val bullets: List<String> = emptyList(),
    val source: String? = null,
    @SerialName("source_id") val sourceId: String? = null,
    @SerialName("source_ids") val sourceIds: List<String> = emptyList()
)

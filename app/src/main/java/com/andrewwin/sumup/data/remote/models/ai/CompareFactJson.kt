package com.andrewwin.sumup.data.remote.models.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CompareFactJson(
    val text: String,
    @SerialName("source_ids") val sourceIds: List<String> = emptyList()
)

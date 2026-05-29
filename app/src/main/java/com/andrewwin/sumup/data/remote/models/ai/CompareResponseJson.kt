package com.andrewwin.sumup.data.remote.models.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CompareResponseJson(
    val main: String? = null,
    val details: List<CompareFactJson> = emptyList(),
    val items: List<CompareFactJson> = emptyList(),
    val fallback: String? = null,
    @SerialName("common_facts") val commonFacts: List<CompareFactJson> = emptyList(),
    @SerialName("unique_facts") val uniqueFacts: List<CompareFactJson> = emptyList(),
    @SerialName("common_fallback") val commonFallback: String? = null,
    @SerialName("unique_fallback") val uniqueFallback: String? = null
)

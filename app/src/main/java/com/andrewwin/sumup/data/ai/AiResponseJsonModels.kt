package com.andrewwin.sumup.data.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class SummaryResponseJson(
    val main: String? = null,
    val details: List<SummaryDetailJson> = emptyList(),
    val items: List<SummaryItemJson> = emptyList(),
    val themes: List<SummaryThemeJson> = emptyList()
)

@Serializable
data class SummaryDetailJson(
    val text: String,
    @SerialName("source_ids") val sourceIds: List<String> = emptyList()
)

@Serializable
data class SummaryItemJson(
    val title: String? = null,
    val bullets: List<String> = emptyList(),
    val source: String? = null,
    @SerialName("source_id") val sourceId: String? = null,
    @SerialName("source_ids") val sourceIds: List<String> = emptyList()
)

@Serializable
data class SummaryThemeJson(
    val title: String? = null,
    val summary: String? = null,
    val items: List<SummaryThemeItemJson> = emptyList()
)

@Serializable
data class SummaryThemeItemJson(
    val title: String? = null,
    @SerialName("source_id") val sourceId: String? = null,
    @SerialName("source_ids") val sourceIds: List<String> = emptyList()
)

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

@Serializable
data class CompareFactJson(
    val text: String,
    @SerialName("source_ids") val sourceIds: List<String> = emptyList()
)

@Serializable
data class QaResponseJson(
    @SerialName("short_answer") val shortAnswer: String? = null,
    val details: List<QaStatementJson> = emptyList()
)

@Serializable
data class QaStatementJson(
    val text: String,
    @SerialName("source_ids") val sourceIds: List<String> = emptyList()
)







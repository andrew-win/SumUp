package com.andrewwin.sumup.domain.usecase.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class SummaryResponseJson(
    val items: List<SummaryItemJson> = emptyList(),
    val themes: List<SummaryThemeJson> = emptyList()
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
    val emojis: List<String> = emptyList(),
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
    @SerialName("common_facts") val commonFacts: List<CompareCommonFactJson> = emptyList(),
    val items: List<CompareItemJson> = emptyList(),
    @SerialName("common_topic") val commonTopic: String? = null,
    @SerialName("fallback_message") val fallbackMessage: String? = null
)

@Serializable
data class CompareCommonFactJson(
    val text: String,
    @SerialName("source_ids") val sourceIds: List<String> = emptyList()
)

@Serializable
data class CompareItemJson(
    @SerialName("source_id") val sourceId: String? = null,
    @SerialName("unique_details") val uniqueDetails: List<String> = emptyList()
)

@Serializable
data class QaResponseJson(
    val question: String? = null,
    @SerialName("short_answer") val shortAnswer: String? = null,
    val answer: String? = null,
    val sources: List<String> = emptyList(),
    val details: List<QaStatementJson> = emptyList(),
    val statements: List<QaStatementJson> = emptyList()
)

@Serializable
data class QaStatementJson(
    val text: String,
    val sources: List<String> = emptyList()
)











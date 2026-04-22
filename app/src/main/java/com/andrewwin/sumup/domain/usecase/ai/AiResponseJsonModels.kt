package com.andrewwin.sumup.domain.usecase.ai

data class SummaryResponseJson(
    val headline: String? = null,
    val items: List<SummaryItemJson> = emptyList(),
    val themes: List<SummaryThemeJson> = emptyList()
)

data class SummaryItemJson(
    val title: String? = null,
    val bullets: List<String> = emptyList(),
    val source: String? = null,
    val sourceId: String? = null
)

data class SummaryThemeJson(
    val title: String? = null,
    val summary: String? = null,
    val emojis: List<String> = emptyList(),
    val items: List<SummaryThemeItemJson> = emptyList()
)

data class SummaryThemeItemJson(
    val title: String? = null,
    val sourceId: String? = null
)

data class CompareResponseJson(
    val commonFacts: List<CompareCommonFactJson> = emptyList(),
    val items: List<CompareItemJson> = emptyList(),
    val commonTopic: String? = null,
    val fallbackMessage: String? = null
)

data class CompareCommonFactJson(
    val text: String,
    val sourceIds: List<String> = emptyList()
)

data class CompareItemJson(
    val sourceId: String? = null,
    val uniqueDetails: List<String> = emptyList()
)

data class QaResponseJson(
    val answer: String? = null,
    val sources: List<String> = emptyList(),
    val statements: List<QaStatementJson> = emptyList()
)

data class QaStatementJson(
    val text: String,
    val sources: List<String> = emptyList()
)










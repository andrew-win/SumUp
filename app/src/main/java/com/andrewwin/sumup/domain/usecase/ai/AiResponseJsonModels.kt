package com.andrewwin.sumup.domain.usecase.ai

data class SummaryResponseJson(
    val headline: String? = null,
    val items: List<SummaryItemJson> = emptyList()
)

data class SummaryItemJson(
    val title: String? = null,
    val bullets: List<String> = emptyList(),
    val source: String? = null
)

data class CompareResponseJson(
    val headline: String? = null,
    val items: List<CompareItemJson> = emptyList()
)

data class CompareItemJson(
    val sourceId: String? = null,
    val common: List<String> = emptyList(),
    val different: List<String> = emptyList()
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










package com.andrewwin.sumup.domain.usecase.ai

data class SummarySourceRef(
    val name: String,
    val url: String
)

data class SummaryItem(
    val text: String,
    val sources: List<SummarySourceRef> = emptyList()
)

data class DigestTheme(
    val title: String,
    val summary: String? = null,
    val items: List<SummaryItem> = emptyList()
)

sealed class SummaryResult {
    data class Single(
        val title: String? = null,
        val points: List<SummaryItem>,
        val sources: List<SummarySourceRef> = emptyList()
    ) : SummaryResult()

    data class Compare(
        val common: List<SummaryItem>,
        val unique: List<SummaryItem>
    ) : SummaryResult()

    data class Digest(
        val themes: List<DigestTheme>
    ) : SummaryResult()

    data class QA(
        val question: String? = null,
        val shortAnswer: String,
        val details: List<SummaryItem>,
        val sources: List<SummarySourceRef> = emptyList()
    ) : SummaryResult()

    data class Error(
        val message: String
    ) : SummaryResult()
}

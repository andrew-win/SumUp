package com.andrewwin.sumup.domain.entities.summary

sealed class SummaryResult {
    data class Single(
        val title: String? = null,
        val main: String? = null,
        val points: List<SummaryItem>,
        val sources: List<SummarySourceRef> = emptyList()
    ) : SummaryResult()

    data class Compare(
        val main: String? = null,
        val points: List<SummaryItem>
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

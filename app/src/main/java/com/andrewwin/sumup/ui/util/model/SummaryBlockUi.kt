package com.andrewwin.sumup.ui.util.model

sealed interface SummaryBlockUi {
    data class Section(
        val body: String,
        val sources: List<SummarySourceLinkUi>
    ) : SummaryBlockUi

    data class Theme(
        val heading: String,
        val summary: String?,
        val items: List<ThemeItem>
    ) : SummaryBlockUi

    data class PlainList(
        val items: List<ThemeItem>
    ) : SummaryBlockUi
}

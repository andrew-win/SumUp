package com.andrewwin.sumup.domain.settings.model

data class SummaryPromptSettings(
    val prompt: String,
    val isCustomPromptEnabled: Boolean,
    val language: SummaryLanguage
)

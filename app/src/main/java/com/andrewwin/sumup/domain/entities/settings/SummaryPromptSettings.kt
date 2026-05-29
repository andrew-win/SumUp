package com.andrewwin.sumup.domain.entities.settings

data class SummaryPromptSettings(
    val prompt: String,
    val isCustomPromptEnabled: Boolean,
    val language: SummaryLanguage
)

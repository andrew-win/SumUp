package com.andrewwin.sumup.domain.support

interface AiPromptProvider {
    fun defaultSummaryPrompt(): String
    fun questionPromptPrefix(): String
    fun questionPromptSuffix(): String
}









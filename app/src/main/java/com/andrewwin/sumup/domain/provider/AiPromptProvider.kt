package com.andrewwin.sumup.domain.provider

interface AiPromptProvider {
    fun defaultSummaryPrompt(): String
    fun questionPromptPrefix(): String
    fun questionPromptSuffix(): String
    fun strictJsonInstruction(): String
}

package com.andrewwin.sumup.domain.ai.prompt

interface PromptTemplateRepository {
    fun getTemplate(path: String): String
}

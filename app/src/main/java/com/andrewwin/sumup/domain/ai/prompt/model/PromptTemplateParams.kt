package com.andrewwin.sumup.domain.ai.prompt.model

interface PromptTemplateParams {
    fun toTemplateValues(): Map<String, String>
}

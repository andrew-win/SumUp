package com.andrewwin.sumup.domain.ai.prompt.model

data class PromptLayoutParams(
    val role: String,
    val goal: String,
    val rules: String,
    val optionalSections: String,
    val schema: String
) : PromptTemplateParams {

    override fun toTemplateValues(): Map<String, String> = mapOf(
        ROLE to role,
        GOAL to goal,
        RULES to rules,
        OPTIONAL_SECTIONS to optionalSections,
        SCHEMA to schema
    )

    private companion object {
        const val ROLE = "role"
        const val GOAL = "goal"
        const val RULES = "rules"
        const val OPTIONAL_SECTIONS = "optional_sections"
        const val SCHEMA = "schema"
    }
}

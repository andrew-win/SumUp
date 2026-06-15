package com.andrewwin.sumup.domain.ai.prompt.model

data class ComparePromptParams(
    val mainSentences: Int,
    val maxBullets: Int,
    val maxWordsPerPoint: Int,
    val fallback: String,
    val analyticChainRule: String,
    val example: String,
    val languageRule: String
) : PromptTemplateParams {

    override fun toTemplateValues(): Map<String, String> = mapOf(
        COMPARE_MAIN_SENTENCES to mainSentences.toString(),
        COMPARE_MAX_BULLETS to maxBullets.toString(),
        COMPARE_MAX_WORDS_PER_POINT to maxWordsPerPoint.toString(),
        FALLBACK to fallback,
        ANALYTIC_CHAIN_RULE to analyticChainRule,
        COMPARE_EXAMPLE to example,
        LANGUAGE_RULE to languageRule
    )

    private companion object {
        const val COMPARE_MAIN_SENTENCES = "compare_main_sentences"
        const val COMPARE_MAX_BULLETS = "compare_max_bullets"
        const val COMPARE_MAX_WORDS_PER_POINT = "compare_max_words_per_point"
        const val FALLBACK = "fallback"
        const val ANALYTIC_CHAIN_RULE = "analytic_chain_rule"
        const val COMPARE_EXAMPLE = "compare_example"
        const val LANGUAGE_RULE = "language_rule"
    }
}

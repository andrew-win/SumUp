package com.andrewwin.sumup.domain.ai.prompt.model

data class SingleArticlePromptParams(
    val mainSentences: Int,
    val maxPoints: Int,
    val maxWordsPerPoint: Int,
    val analyticChainRule: String,
    val example: String,
    val languageRule: String
) : PromptTemplateParams {

    override fun toTemplateValues(): Map<String, String> = mapOf(
        SINGLE_MAIN_SENTENCES to mainSentences.toString(),
        SINGLE_MAX_POINTS to maxPoints.toString(),
        SINGLE_MAX_WORDS_PER_POINT to maxWordsPerPoint.toString(),
        ANALYTIC_CHAIN_RULE to analyticChainRule,
        SINGLE_ARTICLE_EXAMPLE to example,
        LANGUAGE_RULE to languageRule
    )

    private companion object {
        const val SINGLE_MAIN_SENTENCES = "single_main_sentences"
        const val SINGLE_MAX_POINTS = "single_max_points"
        const val SINGLE_MAX_WORDS_PER_POINT = "single_max_words_per_point"
        const val ANALYTIC_CHAIN_RULE = "analytic_chain_rule"
        const val SINGLE_ARTICLE_EXAMPLE = "single_article_example"
        const val LANGUAGE_RULE = "language_rule"
    }
}

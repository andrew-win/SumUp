package com.andrewwin.sumup.domain.ai.prompt.model

data class QuestionPromptParams(
    val maxWordsShortAnswer: Int,
    val maxDetailPoints: Int,
    val maxWordsPerDetailedBullet: Float,
    val noDirectAnswer: String,
    val fallback: String,
    val analyticChainRule: String,
    val example: String,
    val languageRule: String
) : PromptTemplateParams {

    override fun toTemplateValues(): Map<String, String> = mapOf(
        QA_MAX_WORDS_SHORT_ANSWER to maxWordsShortAnswer.toString(),
        QA_MAX_DETAIL_POINTS to maxDetailPoints.toString(),
        QA_MAX_WORDS_PER_DETAILED_BULLET to maxWordsPerDetailedBullet.toString(),
        NO_DIRECT_ANSWER to noDirectAnswer,
        FALLBACK to fallback,
        ANALYTIC_CHAIN_RULE to analyticChainRule,
        QUESTION_EXAMPLE to example,
        LANGUAGE_RULE to languageRule
    )

    private companion object {
        const val QA_MAX_WORDS_SHORT_ANSWER = "qa_max_words_short_answer"
        const val QA_MAX_DETAIL_POINTS = "qa_max_detail_points"
        const val QA_MAX_WORDS_PER_DETAILED_BULLET = "qa_max_words_per_detailed_bullet"
        const val NO_DIRECT_ANSWER = "no_direct_answer"
        const val FALLBACK = "fallback"
        const val ANALYTIC_CHAIN_RULE = "analytic_chain_rule"
        const val QUESTION_EXAMPLE = "question_example"
        const val LANGUAGE_RULE = "language_rule"
    }
}

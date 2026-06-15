package com.andrewwin.sumup.domain.ai.prompt.model

data class QuestionExampleParams(
    val maxWordsShortAnswer: Int,
    val maxWordsPerDetailedBullet: Float
) : PromptTemplateParams {

    override fun toTemplateValues(): Map<String, String> = mapOf(
        QA_MAX_WORDS_SHORT_ANSWER to maxWordsShortAnswer.toString(),
        QA_MAX_WORDS_PER_DETAILED_BULLET to maxWordsPerDetailedBullet.toString()
    )

    private companion object {
        const val QA_MAX_WORDS_SHORT_ANSWER = "qa_max_words_short_answer"
        const val QA_MAX_WORDS_PER_DETAILED_BULLET = "qa_max_words_per_detailed_bullet"
    }
}

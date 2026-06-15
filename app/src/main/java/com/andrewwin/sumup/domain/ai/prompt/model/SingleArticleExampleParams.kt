package com.andrewwin.sumup.domain.ai.prompt.model

data class SingleArticleExampleParams(
    val maxWordsPerPoint: Int
) : PromptTemplateParams {

    override fun toTemplateValues(): Map<String, String> = mapOf(
        SINGLE_MAX_WORDS_PER_POINT to maxWordsPerPoint.toString()
    )

    private companion object {
        const val SINGLE_MAX_WORDS_PER_POINT = "single_max_words_per_point"
    }
}

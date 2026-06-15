package com.andrewwin.sumup.domain.ai.prompt.model

data class CompareExampleParams(
    val maxWordsPerPoint: Int
) : PromptTemplateParams {

    override fun toTemplateValues(): Map<String, String> = mapOf(
        COMPARE_MAX_WORDS_PER_POINT to maxWordsPerPoint.toString()
    )

    private companion object {
        const val COMPARE_MAX_WORDS_PER_POINT = "compare_max_words_per_point"
    }
}

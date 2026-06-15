package com.andrewwin.sumup.domain.ai.prompt.model

data class FeedDigestExampleParams(
    val emojisCount: Int,
    val maxWordsPerTitle: Int
) : PromptTemplateParams {

    override fun toTemplateValues(): Map<String, String> = mapOf(
        DIGEST_EMOJIS_COUNT to emojisCount.toString(),
        DIGEST_MAX_WORDS_PER_TITLE to maxWordsPerTitle.toString()
    )

    private companion object {
        const val DIGEST_EMOJIS_COUNT = "digest_emojis_count"
        const val DIGEST_MAX_WORDS_PER_TITLE = "digest_max_words_per_title"
    }
}

package com.andrewwin.sumup.domain.ai.prompt.model

data class FeedDigestPromptParams(
    val minThemes: Int,
    val maxThemes: Int,
    val emojisCount: Int,
    val minItemsPerTheme: Int,
    val maxItemsPerTheme: Int,
    val maxWordsPerTitle: Int,
    val example: String,
    val languageRule: String
) : PromptTemplateParams {

    override fun toTemplateValues(): Map<String, String> = mapOf(
        DIGEST_MIN_THEMES to minThemes.toString(),
        DIGEST_MAX_THEMES to maxThemes.toString(),
        DIGEST_EMOJIS_COUNT to emojisCount.toString(),
        DIGEST_MIN_ITEMS_PER_THEME to minItemsPerTheme.toString(),
        DIGEST_MAX_ITEMS_PER_THEME to maxItemsPerTheme.toString(),
        DIGEST_MAX_WORDS_PER_TITLE to maxWordsPerTitle.toString(),
        FEED_DIGEST_EXAMPLE to example,
        LANGUAGE_RULE to languageRule
    )

    private companion object {
        const val DIGEST_MIN_THEMES = "digest_min_themes"
        const val DIGEST_MAX_THEMES = "digest_max_themes"
        const val DIGEST_EMOJIS_COUNT = "digest_emojis_count"
        const val DIGEST_MIN_ITEMS_PER_THEME = "digest_min_items_per_theme"
        const val DIGEST_MAX_ITEMS_PER_THEME = "digest_max_items_per_theme"
        const val DIGEST_MAX_WORDS_PER_TITLE = "digest_max_words_per_title"
        const val FEED_DIGEST_EXAMPLE = "feed_digest_example"
        const val LANGUAGE_RULE = "language_rule"
    }
}

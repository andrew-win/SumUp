package com.andrewwin.sumup.domain.news

object ArticleTitleCleaner {
    private val markdownUrlRegex = Regex("\\((?:https?://|www\\.)[^\\s)]+\\)", RegexOption.IGNORE_CASE)
    private val urlRegex = Regex("(?:https?://|www\\.)\\S+", RegexOption.IGNORE_CASE)
    private val hashtagRegex = Regex("(^|\\s)#[\\p{L}\\p{N}_-]+")
    private val mentionRegex = Regex("(^|\\s)@[A-Za-z0-9_]{3,}")
    private val emojiRegex = Regex(
        "[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE0F}\\x{200D}]"
    )

    fun clean(value: String): String {
        return value
            .replace(markdownUrlRegex, " ")
            .replace(urlRegex, " ")
            .replace(hashtagRegex, " ")
            .replace(mentionRegex, " ")
            .replace(emojiRegex, " ")
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\s+([,.!?;:])"), "$1")
            .trim()
            .trimEnd('.')
            .trim()
    }
}

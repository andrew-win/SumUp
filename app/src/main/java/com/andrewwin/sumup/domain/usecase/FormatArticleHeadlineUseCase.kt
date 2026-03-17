package com.andrewwin.sumup.domain.usecase

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.SourceType
import javax.inject.Inject

/**
 * Use case to centralize headline (title) and content display logic.
 * Ensures consistent article representation across the feed and summaries.
 */
class FormatArticleHeadlineUseCase @Inject constructor() {

    data class FormattedArticle(
        val displayTitle: String,
        val displayContent: String
    )

    private val ellipsis = "…" // Standard ellipsis

    operator fun invoke(article: Article, sourceType: SourceType): FormattedArticle {
        val displayTitle: String
        val rawDescription: String
        val contentForDisplay = article.content
            .replace(Regex("(?m)^\\[ad\\]\\s*"), "")
            .trim()

        if (sourceType == SourceType.TELEGRAM) {
            val fullText = contentForDisplay.trim()
            
            // Look for first sentence boundary or newline
            val breakMatch = Regex("[.!?](\\s|\n|$)|\n").find(fullText)
            val breakIndex = breakMatch?.range?.first ?: -1

            if (breakIndex != -1 && breakIndex < MAX_TELEGRAM_TITLE_LENGTH) {
                displayTitle = fullText.substring(0, breakIndex).trim()
                rawDescription = fullText.substring(breakMatch!!.range.last + 1).trim()
            } else if (fullText.length > MAX_TELEGRAM_TITLE_LENGTH) {
                val slice = fullText.take(MAX_TELEGRAM_TITLE_LENGTH)
                val lastSpace = slice.lastIndexOf(' ')
                val cutPos = if (lastSpace > 50) lastSpace else MAX_TELEGRAM_TITLE_LENGTH
                
                displayTitle = fullText.substring(0, cutPos).trim().removeSuffix(".") + ellipsis
                rawDescription = fullText.substring(cutPos).trim()
            } else {
                displayTitle = fullText.removeSuffix(".")
                rawDescription = ""
            }
        } else {
            displayTitle = article.title.trim().removeSuffix(".")
            val content = contentForDisplay.trim()
            
            // If content starts with the title, strip it
            rawDescription = if (content.startsWith(article.title.trim(), ignoreCase = true)) {
                content.substring(article.title.trim().length).trim()
                    .removePrefix(":")
                    .removePrefix("-")
                    .trim()
            } else {
                content
            }
        }

        return FormattedArticle(
            displayTitle = displayTitle,
            displayContent = rawDescription
        )
    }

    companion object {
        private const val MAX_TELEGRAM_TITLE_LENGTH = 225
    }
}

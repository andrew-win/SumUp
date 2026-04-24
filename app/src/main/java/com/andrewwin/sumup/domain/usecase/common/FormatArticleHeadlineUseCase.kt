package com.andrewwin.sumup.domain.usecase.common

import android.net.Uri
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
            displayTitle = article.title.trim().removeSuffix(".").ifBlank { fallbackTitle(article) }
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
        val enrichedTitle = enrichShortTitle(displayTitle, rawDescription)

        return FormattedArticle(
            displayTitle = enrichedTitle,
            displayContent = rawDescription
        )
    }

    private fun enrichShortTitle(title: String, description: String): String {
        val normalizedTitle = title.replace(WHITESPACE_REGEX, " ").trim()
        if (normalizedTitle.isBlank()) return normalizedTitle
        if (normalizedTitle.length >= MIN_TITLE_CHARS) {
            return truncateTitle(normalizedTitle)
        }

        val normalizedDescription = description.trim()
        if (normalizedDescription.isBlank()) return truncateTitle(normalizedTitle)
        val addition = extractDescriptionSnippet(normalizedDescription)
        if (addition.isBlank()) return truncateTitle(normalizedTitle)
        return truncateTitle("$normalizedTitle $addition".replace(WHITESPACE_REGEX, " ").trim())
    }

    private fun truncateTitle(value: String): String {
        if (value.length <= MAX_ENRICHED_TITLE_CHARS) return value
        return value.take(MAX_ENRICHED_TITLE_CHARS).trimEnd().removeSuffix(".") + "..."
    }

    private fun extractDescriptionSnippet(description: String): String {
        val newlineIndex = description.indexOf('\n').takeIf { it >= 0 } ?: Int.MAX_VALUE
        val dotIndex = description.indexOf('.').takeIf { it >= 0 } ?: Int.MAX_VALUE
        val boundary = minOf(newlineIndex, dotIndex)
        return if (boundary != Int.MAX_VALUE) {
            description.take(boundary).trim()
        } else {
            description.take(FALLBACK_DESCRIPTION_CHARS).trim().removeSuffix(".") + "..."
        }
    }

    private fun fallbackTitle(article: Article): String {
        val contentLine = article.content
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith("http") }
            ?.take(120)
            ?.removeSuffix(".")
            .orEmpty()
        if (contentLine.isNotBlank()) return contentLine

        return runCatching {
            val uri = Uri.parse(article.url)
            val host = uri.host?.removePrefix("www.")?.ifBlank { null }
            val path = uri.path?.trim('/')?.substringBefore('/')?.ifBlank { null }
            listOfNotNull(host, path).joinToString(" • ").ifBlank { article.url }
        }.getOrDefault(article.url)
    }

    companion object {
        private const val MAX_TELEGRAM_TITLE_LENGTH = 225
        private const val MIN_TITLE_CHARS = 15
        private const val MAX_ENRICHED_TITLE_CHARS = 180
        private const val FALLBACK_DESCRIPTION_CHARS = 120
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}










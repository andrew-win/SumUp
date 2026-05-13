package com.andrewwin.sumup.domain.feed

import javax.inject.Inject
import kotlin.math.ceil

class FeedSearchMatcher @Inject constructor() {
    fun tokenizeQuery(query: String): List<String> =
        normalizeForSearch(query)
            .split(" ")
            .map { it.trim() }
            .filter { it.length >= MIN_SEARCH_TOKEN_LENGTH }
            .distinct()

    fun matchesQueryWithTokenThreshold(
        title: String,
        content: String,
        queryTokens: List<String>
    ): Boolean {
        if (queryTokens.isEmpty()) return true
        val normalizedText = normalizeForSearch("$title $content")
        if (normalizedText.isBlank()) return false

        val matchedCount = queryTokens.count { token -> normalizedText.contains(token) }
        val requiredMatches = ceil(queryTokens.size * SEARCH_TOKEN_MATCH_RATIO).toInt().coerceAtLeast(1)
        return matchedCount >= requiredMatches
    }

    private fun normalizeForSearch(value: String): String =
        value
            .lowercase()
            .replace(SEARCH_SYMBOLS_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()

    private companion object {
        private const val SEARCH_TOKEN_MATCH_RATIO = 0.6f
        private const val MIN_SEARCH_TOKEN_LENGTH = 2
        private val SEARCH_SYMBOLS_REGEX = Regex("[\\p{Punct}\\p{S}]")
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}

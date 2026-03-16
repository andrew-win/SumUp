package com.andrewwin.sumup.data.repository

import com.andrewwin.sumup.domain.repository.FooterCleaner
import java.util.regex.Pattern

class FooterCleanerImpl : FooterCleaner {

    companion object {
        private const val MIN_POSTS_FOR_ANALYSIS = 3
        private const val MIN_FOOTER_OCCURRENCE_RATIO = 0.5
        private const val MAX_LINES_TO_SCAN = 14
        private const val MIN_PATTERN_LENGTH = 3
        private val NUMBER_PATTERN = Pattern.compile("\\d+")
        private val URL_PATTERN = Pattern.compile("https?://\\S+|t\\.me/\\S+")
    }

    override fun findCommonFooter(texts: List<String>): String? {
        if (texts.size < MIN_POSTS_FOR_ANALYSIS) return null

        val normalizedPosts = texts.map { text ->
            text.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .takeLast(MAX_LINES_TO_SCAN)
                .map { normalize(it) }
                .filter { it.isNotBlank() }
        }

        if (normalizedPosts.all { it.isEmpty() }) return null

        val maxLines = normalizedPosts.maxOf { it.size }
        val footerLines = mutableListOf<String>()

        for (i in 1..maxLines) {
            val linesAtPosition = mutableMapOf<String, Int>()
            var availablePosts = 0

            normalizedPosts.forEach { lines ->
                if (lines.size >= i) {
                    availablePosts++
                    val line = lines[lines.size - i]
                    linesAtPosition[line] = linesAtPosition.getOrDefault(line, 0) + 1
                }
            }

            if (availablePosts < MIN_POSTS_FOR_ANALYSIS) break

            val mostFrequentEntry = linesAtPosition.maxByOrNull { it.value } ?: break
            val frequency = mostFrequentEntry.value.toDouble() / availablePosts

            if (frequency >= MIN_FOOTER_OCCURRENCE_RATIO) {
                footerLines.add(0, mostFrequentEntry.key)
            } else {
                break
            }
        }

        return if (footerLines.isNotEmpty()) footerLines.joinToString("\n") else null
    }

    override fun removeFooter(text: String, footerPattern: String?): String {
        if (footerPattern.isNullOrBlank()) return text

        val footerLines = footerPattern.lines()
            .map { normalize(it) }
            .filter { it.isNotBlank() }
        if (footerLines.isEmpty()) return text

        val originalLines = text.lines().toMutableList()
        val contentWithIndexes = originalLines.mapIndexed { index, s -> index to normalize(s) }
            .filter { it.second.isNotBlank() }

        if (contentWithIndexes.size < footerLines.size) return text

        var matches = true
        for (i in 1..footerLines.size) {
            val textLine = contentWithIndexes[contentWithIndexes.size - i].second
            val footerLine = footerLines[footerLines.size - i]
            if (textLine != footerLine) {
                matches = false
                break
            }
        }

        return if (matches) {
            val firstLineIndexInOriginal = contentWithIndexes[contentWithIndexes.size - footerLines.size].first
            originalLines.subList(0, firstLineIndexInOriginal).joinToString("\n").trim()
        } else {
            text
        }
    }

    private fun normalize(line: String): String {
        if (line.isBlank()) return ""
        val lowercase = line.lowercase()
        val masked = URL_PATTERN.matcher(lowercase).replaceAll("url")
            .let { NUMBER_PATTERN.matcher(it).replaceAll("num") }
        val result = masked.filter { it.isLetter() || it.isDigit() }

        return if (result.length >= MIN_PATTERN_LENGTH) result else ""
    }
}

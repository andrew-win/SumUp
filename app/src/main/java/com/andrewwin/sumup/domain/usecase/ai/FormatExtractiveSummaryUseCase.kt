package com.andrewwin.sumup.domain.usecase.ai

import javax.inject.Inject

class FormatExtractiveSummaryUseCase @Inject constructor(
) {
    private val bulletSymbol = "•"

    /**
     * @param title The article title
     * @param sentences Extracted sentences
     * @param isScheduledReport Kept for backward compatibility
     * @param index Optional index for numbering (e.g., "1. Title")
     */
    fun formatItem(
        title: String,
        sentences: List<String>,
        isScheduledReport: Boolean,
        index: Int? = null
    ): String {
        if (sentences.isEmpty()) return ""

        val normalizedTitle = normalizeForCompare(title)
        val filteredSentences = sentences
            .map { it.trim() }
            .flatMap { splitToSingleSentences(it) }
            .filter { it.isNotBlank() }
            .filterNot { isTitleDuplicate(it, normalizedTitle) }
            .ifEmpty { listOf(sentences.maxBy { it.length }.trim()) }

        if (filteredSentences.size == 1) {
            val onlySentence = filteredSentences.first()
            if (isTitleDuplicate(onlySentence, normalizedTitle)) {
                return title.removeSuffix(":").trim()
            }
            val safeTitle = title.removeSuffix(":").trim()
            val header = if (index != null) "$index. $safeTitle:" else "$safeTitle:"
            return "$header\n$bulletSymbol ${onlySentence.removeSuffix(":").trim()}"
        }

        val safeTitle = title.removeSuffix(":").trim()
        val header = if (index != null) "$index. $safeTitle:" else "$safeTitle:"
        val body = filteredSentences
            .map { it.trim().removeSuffix(":").trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n") { "$bulletSymbol $it" }
        if (body.isBlank()) return title.removeSuffix(":").trim()
        return "$header\n$body"
    }

    private fun isTitleDuplicate(sentence: String, normalizedTitle: String): Boolean {
        val normalizedSentence = normalizeForCompare(sentence)
        if (normalizedSentence.isBlank() || normalizedTitle.isBlank()) return false
        return normalizedSentence == normalizedTitle ||
            (normalizedSentence.contains(normalizedTitle) && normalizedTitle.length > 20) ||
            (normalizedTitle.contains(normalizedSentence) && normalizedSentence.length > 20)
    }

    private fun normalizeForCompare(value: String): String {
        return value.lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
            .removeSuffix(".")
            .removeSuffix(":")
    }

    private fun splitToSingleSentences(value: String): List<String> {
        return value
            .split(SENTENCE_SPLIT_REGEX)
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    companion object {
        private val SENTENCE_SPLIT_REGEX = Regex("(?<=[.!?…])\\s+|(?<=[.!?…])(?=[A-ZА-ЯІЇЄҐ])|\\n+")
    }
}










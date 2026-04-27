package com.andrewwin.sumup.domain.usecase.common

import com.andrewwin.sumup.domain.support.DispatcherProvider
import com.andrewwin.sumup.domain.usecase.ai.SummaryLimits
import kotlinx.coroutines.withContext
import javax.inject.Inject

class BuildExtractiveSummaryUseCase @Inject constructor(
    private val getExtractiveSummaryUseCase: GetExtractiveSummaryUseCase,
    private val dispatcherProvider: DispatcherProvider
) {
    private val bulletSymbol = "—"

    suspend operator fun invoke(
        headlines: List<String>,
        contentMap: Map<String, String>,
        topCount: Int = SummaryLimits.Extractive.defaultTopCount,
        sentencesPerArticle: Int = SummaryLimits.Extractive.defaultSentencesPerArticle
    ): String = withContext(dispatcherProvider.default) {
        val orderedHeadlines = headlines.take(topCount)
        val summaryBody = orderedHeadlines.map { title ->
            val content = contentMap[title].orEmpty()
            val sentences = getExtractiveSummaryUseCase(content, sentencesPerArticle)

            formatItem(
                title = title,
                sentences = sentences,
                maxBullets = sentencesPerArticle
            )
        }.joinToString("\n\n")

        summaryBody
    }

    fun formatItem(
        title: String,
        sentences: List<String>,
        index: Int? = null,
        maxBullets: Int? = null
    ): String {
        if (sentences.isEmpty()) return ""

        val normalizedTitle = normalizeForCompare(title)
        val filteredSentences = sentences
            .map { it.trim() }
            .flatMap { splitToSingleSentences(it) }
            .filter { it.isNotBlank() }
            .filterNot { isTitleDuplicate(it, normalizedTitle) }
            .ifEmpty { listOf(sentences.maxBy { it.length }.trim()) }
            .let { items ->
                val limit = maxBullets?.coerceAtLeast(1)
                if (limit == null) items else items.take(limit)
            }

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
        private val SENTENCE_SPLIT_REGEX = Regex(
            "(?<=[.!?…])[\"'»”)]*\\s+|(?<=[.!?…])[\"'»”)]*(?=[A-ZА-ЯІЇЄҐ])|\\n+"
        )
    }
}

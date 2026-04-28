package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.domain.service.EmbeddingUtils
import com.andrewwin.sumup.domain.service.TextOptimizationFeatures
import javax.inject.Inject
import kotlin.math.exp

class GetExtractiveSummaryUseCase @Inject constructor() {

    operator fun invoke(text: String, n: Int): List<String> {
        if (text.isBlank()) return emptyList()
        val targetCount = n.coerceAtLeast(1)

        val candidateSentences = extractSentences(text)
        if (candidateSentences.isEmpty()) return emptyList()
        val candidateFeatures = candidateSentences.map(::buildSentenceFeatures)

        val topIndices = candidateSentences.indices
            .map { i ->
                i to scoreSentence(
                    sentence = candidateSentences[i],
                    position = i,
                    sentenceFeatures = candidateFeatures[i],
                    allSentenceFeatures = candidateFeatures
                )
            }
            .sortedByDescending { it.second }
            .take(targetCount)
            .map { it.first }
            .toSortedSet()

        return candidateSentences.filterIndexed { i, _ -> i in topIndices }
    }

    private fun extractSentences(text: String): List<String> {
        return text
            .split(PRIMARY_SENTENCE_SPLIT_REGEX)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { cleanSentenceStart(it) }
            .filter { isValidSummarySentence(it) }
            .toList()
            .distinct()
    }

    private fun scoreSentence(
        sentence: String,
        position: Int,
        sentenceFeatures: TextOptimizationFeatures,
        allSentenceFeatures: List<TextOptimizationFeatures>
    ): Double {
        val positionScore = when (position) {
            0 -> FIRST_SENTENCE_POSITION_SCORE
            1 -> SECOND_SENTENCE_POSITION_SCORE
            2 -> THIRD_SENTENCE_POSITION_SCORE
            else -> DEFAULT_POSITION_SCORE
        }
        val wordCount = sentence.split(WHITESPACE_REGEX).count { it.isNotBlank() }
        val lengthScore = 1.0 / (1.0 + exp(-LENGTH_SCORE_SIGMOID_STEEPNESS * (wordCount - PREFERRED_WORDS_COUNT)))
        val jaccardScore = calculateAverageSentenceJaccard(sentenceFeatures, allSentenceFeatures)
        val entityBonus = if (hasEntityMatch(sentenceFeatures, allSentenceFeatures)) ENTITY_MATCH_BONUS else 0.0
        val digitsBonus = if (DIGIT_REGEX.containsMatchIn(sentence)) DIGIT_BONUS else 0.0
        return positionScore * lengthScore + jaccardScore + entityBonus + digitsBonus
    }

    private fun cleanSentenceStart(sentence: String): String {
        val trimmed = sentence.dropWhile { !it.isLetterOrDigit() }
        if (trimmed.isBlank()) return ""
        return trimmed.replaceFirstChar { it.uppercase() }
    }

    private fun isValidSummarySentence(sentence: String): Boolean {
        val normalized = sentence.trim()
        if (normalized.length < MIN_SUMMARY_SENTENCE_LENGTH_CHARS) return false
        return true
    }

    private fun buildSentenceFeatures(sentence: String): TextOptimizationFeatures {
        return EmbeddingUtils.extractTextFeatures(sentence)
    }

    private fun calculateAverageSentenceJaccard(
        sentenceFeatures: TextOptimizationFeatures,
        allSentenceFeatures: List<TextOptimizationFeatures>
    ): Double {
        if (sentenceFeatures.jaccardTokens.isEmpty() || allSentenceFeatures.size < 2) return 0.0
        val comparisons = allSentenceFeatures
            .asSequence()
            .filter { it !== sentenceFeatures }
            .map { otherFeatures -> jaccardSim(sentenceFeatures.jaccardTokens, otherFeatures.jaccardTokens) }
            .filter { it > 0.0 }
            .toList()
        if (comparisons.isEmpty()) return 0.0
        return comparisons.average()
    }

    private fun hasEntityMatch(
        sentenceFeatures: TextOptimizationFeatures,
        allSentenceFeatures: List<TextOptimizationFeatures>
    ): Boolean {
        if (sentenceFeatures.entities.isEmpty()) return false
        return allSentenceFeatures.any { otherFeatures ->
            otherFeatures !== sentenceFeatures && sentenceFeatures.entities.intersect(otherFeatures.entities).isNotEmpty()
        }
    }

    private fun jaccardSim(a: Set<String>, b: Set<String>): Double {
        val intersection = a.intersect(b).size.toDouble()
        val union = a.union(b).size.toDouble()
        return if (union == 0.0) 0.0 else intersection / union
    }

    private companion object {
        val PRIMARY_SENTENCE_SPLIT_REGEX = Regex("(?<=[.!?…])\\s+|(?<=[.!?…])(?=[A-ZА-ЯІЇЄҐ])|\\n+")
        val WHITESPACE_REGEX = Regex("\\s+")
        val DIGIT_REGEX = Regex("\\d")
        const val MIN_SUMMARY_SENTENCE_LENGTH_CHARS = 20
        const val PREFERRED_WORDS_COUNT = 12
        const val LENGTH_SCORE_SIGMOID_STEEPNESS = 0.2
        const val FIRST_SENTENCE_POSITION_SCORE = 1.25
        const val SECOND_SENTENCE_POSITION_SCORE = 1.15
        const val THIRD_SENTENCE_POSITION_SCORE = 1.05
        const val DEFAULT_POSITION_SCORE = 1.0
        const val ENTITY_MATCH_BONUS = 0.25
        const val DIGIT_BONUS = 0.25
    }
}

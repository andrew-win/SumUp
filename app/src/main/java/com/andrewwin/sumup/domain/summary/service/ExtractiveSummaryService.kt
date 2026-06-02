package com.andrewwin.sumup.domain.summary.service

import android.util.Log
import com.andrewwin.sumup.domain.article.deduplication.EmbeddingUtils
import com.andrewwin.sumup.domain.article.deduplication.TextOptimizationFeatures
import com.andrewwin.sumup.domain.summary.model.ExtractiveSentenceCandidate
import java.util.Locale
import javax.inject.Inject
import kotlin.math.exp

class ExtractiveSummaryService @Inject constructor() {

    operator fun invoke(text: String, n: Int): List<String> {
        return getTopCandidates(text, n).sortedBy { it.originalIndex }.map { it.text }
    }

    fun getTopCandidates(text: String, n: Int): List<ExtractiveSentenceCandidate> {
        if (text.isBlank()) return emptyList()
        val targetCount = n.coerceAtLeast(1)

        val candidateSentences = extractSentences(text)
        if (candidateSentences.isEmpty()) return emptyList()
        val candidateFeatures = candidateSentences.map(::buildSentenceFeatures)

        val scoredSentences = candidateSentences.indices
            .map { i ->
                scoreSentence(
                    sentence = candidateSentences[i],
                    position = i,
                    sentenceFeatures = candidateFeatures[i],
                    allSentenceFeatures = candidateFeatures
                )
            }
        logSentenceScores(candidateSentences, scoredSentences, targetCount)

        return scoredSentences
            .sortedByDescending { it.second.finalScore }
            .take(targetCount)
            .map { (index, score) ->
                ExtractiveSentenceCandidate(
                    text = candidateSentences[index],
                    originalIndex = index,
                    score = score.finalScore
                )
            }
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
    ): Pair<Int, SentenceScoreBreakdown> {
        val positionScore = when (position) {
            0 -> FIRST_SENTENCE_POSITION_SCORE
            1 -> SECOND_SENTENCE_POSITION_SCORE
            2 -> THIRD_SENTENCE_POSITION_SCORE
            else -> DEFAULT_POSITION_SCORE
        }
        val wordCount = sentence.split(WHITESPACE_REGEX).count { it.isNotBlank() }
        val lengthScore = 1.0 / (1.0 + exp(-LENGTH_SCORE_SIGMOID_STEEPNESS * (wordCount - PREFERRED_WORDS_COUNT)))
        val jaccardPenalty = calculateAverageSentenceJaccard(sentenceFeatures, allSentenceFeatures) *
            JACCARD_REDUNDANCY_PENALTY_WEIGHT
        val entityBonus = if (hasEntityMatch(sentenceFeatures, allSentenceFeatures)) ENTITY_MATCH_BONUS else 0.0
        val digitsBonus = if (DIGIT_REGEX.containsMatchIn(sentence)) DIGIT_BONUS else 0.0
        val finalScore = positionScore * lengthScore - jaccardPenalty + entityBonus + digitsBonus
        return position to SentenceScoreBreakdown(
            finalScore = finalScore,
            positionScore = positionScore,
            lengthScore = lengthScore,
            jaccardPenalty = jaccardPenalty,
            entityBonus = entityBonus,
            digitsBonus = digitsBonus,
            wordCount = wordCount
        )
    }

    private fun logSentenceScores(
        candidateSentences: List<String>,
        scoredSentences: List<Pair<Int, SentenceScoreBreakdown>>,
        targetCount: Int
    ) {
        val sorted = scoredSentences.sortedByDescending { it.second.finalScore }
        Log.d(DEBUG_TAG, "score_run candidates=${candidateSentences.size} targetCount=$targetCount")
        sorted.forEachIndexed { rank, (index, score) ->
            Log.d(
                DEBUG_TAG,
                "score rank=${rank + 1} index=$index final=${score.finalScore.format3()} " +
                    "pos=${score.positionScore.format3()} len=${score.lengthScore.format3()} " +
                    "jaccardPenalty=${score.jaccardPenalty.format3()} entity=${score.entityBonus.format3()} " +
                    "digits=${score.digitsBonus.format3()} words=${score.wordCount} " +
                    "text=\"${candidateSentences[index].safeSnippet()}\""
            )
        }
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

    private fun Double.format3(): String = String.format(Locale.US, "%.3f", this)

    private fun String.safeSnippet(): String =
        replace('\n', ' ').replace(WHITESPACE_REGEX, " ").trim().take(DEBUG_TEXT_LIMIT)

    private companion object {
        const val DEBUG_TAG = "ExtractiveScoreDebug"
        val PRIMARY_SENTENCE_SPLIT_REGEX = Regex("(?<=[.!?…])\\s+|(?<=[.!?…])(?=[A-ZА-ЯІЇЄҐ])|\\n+")
        val WHITESPACE_REGEX = Regex("\\s+")
        val DIGIT_REGEX = Regex("\\d")
        const val MIN_SUMMARY_SENTENCE_LENGTH_CHARS = 20
        const val PREFERRED_WORDS_COUNT = 9
        const val LENGTH_SCORE_SIGMOID_STEEPNESS = 0.09
        const val FIRST_SENTENCE_POSITION_SCORE = 1.25
        const val SECOND_SENTENCE_POSITION_SCORE = 1.15
        const val THIRD_SENTENCE_POSITION_SCORE = 1.05
        const val DEFAULT_POSITION_SCORE = 1.0
        const val JACCARD_REDUNDANCY_PENALTY_WEIGHT = 0.12
        const val ENTITY_MATCH_BONUS = 0.065
        const val DIGIT_BONUS = 0.065
        const val DEBUG_TEXT_LIMIT = 180
    }

    private data class SentenceScoreBreakdown(
        val finalScore: Double,
        val positionScore: Double,
        val lengthScore: Double,
        val jaccardPenalty: Double,
        val entityBonus: Double,
        val digitsBonus: Double,
        val wordCount: Int
    )
}

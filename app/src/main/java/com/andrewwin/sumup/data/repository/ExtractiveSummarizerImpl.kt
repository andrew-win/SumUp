package com.andrewwin.sumup.data.repository

import com.andrewwin.sumup.domain.repository.ExtractiveSummarizer
import kotlin.math.sqrt

class ExtractiveSummarizerImpl : ExtractiveSummarizer {

    companion object {
        private val SENTENCE_SPLIT_REGEX = Regex("(?<=[.!?])(\\s|\n)+|\n+")
        private val WHITESPACE_REGEX = Regex("\\s+")
        private const val MIN_SENTENCE_LENGTH = 10
        private const val FIRST_SENTENCE_SCORE_BOOST = 100.0
    }

    override fun summarize(text: String, sentenceCount: Int): List<String> {
        if (text.isBlank()) return emptyList()

        val sentences = text
            .split(SENTENCE_SPLIT_REGEX)
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length > MIN_SENTENCE_LENGTH }
            .map { cleanSentenceStart(it) }

        if (sentences.isEmpty()) return emptyList()

        val scored = sentences.indices.map { i ->
            val sentence = sentences[i]
            val score = sentence.length.toDouble() + (if (i == 0) FIRST_SENTENCE_SCORE_BOOST else 0.0)
            sentence to score
        }

        return scored
            .sortedByDescending { it.second }
            .take(sentenceCount)
            .map { it.first }
    }

    override fun getCentralHeadlines(headlines: List<String>, count: Int, alpha: Double): List<String> {
        if (headlines.isEmpty()) return emptyList()
        if (headlines.size <= count) return headlines

        val allWords = headlines.flatMap { it.lowercase().split(WHITESPACE_REGEX) }.toSet()
        val vectors = headlines.map { h ->
            val words = h.lowercase().split(WHITESPACE_REGEX)
            allWords.associateWith { word -> if (word in words) 1.0 else 0.0 }
        }

        val scores = DoubleArray(headlines.size)
        for (i in headlines.indices) {
            for (j in headlines.indices) {
                if (i != j) scores[i] += calculateCosineSimilarity(vectors[i], vectors[j])
            }
        }

        val selectedIndices = mutableListOf<Int>()
        val used = BooleanArray(headlines.size) { false }

        while (selectedIndices.size < count && selectedIndices.size < headlines.size) {
            val nextIndex = scores.indices
                .filter { !used[it] }
                .maxByOrNull { scores[it] } ?: break

            selectedIndices.add(nextIndex)
            used[nextIndex] = true

            for (i in scores.indices) {
                if (!used[i]) {
                    val maxSimilarity = selectedIndices.maxOf { calculateCosineSimilarity(vectors[i], vectors[it]) }
                    scores[i] *= (1.0 - maxSimilarity * alpha)
                }
            }
        }

        return selectedIndices.map { headlines[it] }
    }

    private fun cleanSentenceStart(sentence: String): String {
        var startIndex = 0
        while (startIndex < sentence.length && !sentence[startIndex].isLetterOrDigit()) {
            startIndex++
        }
        return if (startIndex < sentence.length) {
            sentence.substring(startIndex).replaceFirstChar { it.uppercase() }
        } else {
            sentence
        }
    }

    private fun calculateCosineSimilarity(a: Map<String, Double>, b: Map<String, Double>): Double {
        var dotProduct = 0.0
        var magnitudeA = 0.0
        var magnitudeB = 0.0

        a.forEach { (key, value) ->
            val valueB = b[key] ?: 0.0
            dotProduct += value * valueB
            magnitudeA += value * value
        }
        b.values.forEach { value -> magnitudeB += value * value }

        return if (magnitudeA == 0.0 || magnitudeB == 0.0) 0.0 else dotProduct / (sqrt(magnitudeA) * sqrt(magnitudeB))
    }
}

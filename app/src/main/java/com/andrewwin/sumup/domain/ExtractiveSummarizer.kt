package com.andrewwin.sumup.domain

import kotlin.math.sqrt

object ExtractiveSummarizer {

    fun summarize(text: String, n: Int = 3): List<String> {
        if (text.isBlank()) return emptyList()

        val sentences = text
            .split(Regex("(?<=[.!?])(\\s|\n)+|\n+"))
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length > 10 }
            .map { cleanSentenceStart(it) }

        if (sentences.isEmpty()) return emptyList()

        val scored = sentences.indices.map { i ->
            val sentence = sentences[i]
            val score = sentence.length.toDouble() + (if (i == 0) 100.0 else 0.0)
            sentence to score
        }

        return scored
            .sortedByDescending { it.second }
            .take(n)
            .map { it.first }
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

    fun getCentralHeadlines(headlines: List<String>, count: Int = 3, alpha: Double = 0.7): List<String> {
        if (headlines.isEmpty()) return emptyList()
        if (headlines.size <= count) return headlines

        val allWords = headlines.flatMap { it.lowercase().split(Regex("\\s+")) }.toSet()
        val vectors = headlines.map { h ->
            val words = h.lowercase().split(Regex("\\s+"))
            allWords.associateWith { word -> if (word in words) 1.0 else 0.0 }
        }

        val scores = DoubleArray(headlines.size)
        for (i in headlines.indices) {
            for (j in headlines.indices) {
                if (i != j) scores[i] += cosineSim(vectors[i], vectors[j])
            }
        }

        val selected = mutableListOf<Int>()
        val used = BooleanArray(headlines.size) { false }

        while (selected.size < count && selected.size < headlines.size) {
            val nextIndex = scores.indices
                .filter { !used[it] }
                .maxByOrNull { scores[it] } ?: break

            selected.add(nextIndex)
            used[nextIndex] = true

            for (i in scores.indices) {
                if (!used[i]) {
                    val maxSim = selected.maxOf { cosineSim(vectors[i], vectors[it]) }
                    scores[i] *= (1.0 - maxSim * alpha) // penalize схожі
                }
            }
        }

        return selected.map { headlines[it] }
    }

    private fun cosineSim(a: Map<String, Double>, b: Map<String, Double>): Double {
        var dot = 0.0
        var magA = 0.0
        var magB = 0.0

        a.forEach { (k, v) ->
            val vb = b[k] ?: 0.0
            dot += v * vb
            magA += v * v
        }
        b.values.forEach { v -> magB += v * v }

        return if (magA == 0.0 || magB == 0.0) 0.0 else dot / (sqrt(magA) * sqrt(magB))
    }
}
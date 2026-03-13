package com.andrewwin.sumup.domain

import kotlin.math.sqrt

object ExtractiveSummarizer {

    fun summarize(text: String, n: Int = 3): List<String> {
        val cleaned = text
            .replace(Regex("Play Video|\\(.*?\\)|\".*?\""), "")
            .replace("–", ".")
            .replace(Regex("[ \t]+"), " ")

        val sentences = cleaned
            .split(Regex("(?<=[.!?])(\\s|\n)+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (sentences.isEmpty()) return emptyList()

        val scored = sentences.indices.map { i ->
            val sentence = sentences[i]
            val score = sentence.length.toDouble() + (if (i == 0) 50.0 else 0.0)
            sentence to score
        }

        return scored
            .sortedByDescending { it.second }
            .take(n)
            .map { it.first }
    }

    fun getCentralHeadlines(headlines: List<String>, count: Int = 3): List<String> {
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

        return scores.indices
            .sortedByDescending { scores[it] }
            .take(count)
            .map { headlines[it] }
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

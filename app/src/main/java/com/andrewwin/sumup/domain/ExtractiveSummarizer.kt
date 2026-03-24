package com.andrewwin.sumup.domain

private const val MMR_LAMBDA = 0.7

object ExtractiveSummarizer {

    fun summarize(text: String, n: Int = 3): List<String> {
        if (text.isBlank()) return emptyList()
        val targetCount = n.coerceAtLeast(1)

        val sentences = extractSentences(text, targetCount + 1)
        val candidateSentences = if (sentences.size > 1) {
            sentences.drop(1)
        } else {
            sentences
        }

        if (candidateSentences.isEmpty()) return emptyList()

        val topIndices = candidateSentences.indices
            .map { i -> i to scoreSentence(candidateSentences[i], i) }
            .sortedByDescending { it.second }
            .take(targetCount)
            .map { it.first }
            .toSortedSet()

        return candidateSentences.filterIndexed { i, _ -> i in topIndices }
    }

    private fun extractSentences(text: String, targetCount: Int): List<String> {
        val primary = text
            .split(PRIMARY_SENTENCE_SPLIT_REGEX)
            .asSequence()
            .map { it.trim() }
            .map { cleanSentenceStart(it) }
            .filter { isValidSummarySentence(it) }
            .toList()
            .takeIf { it.size >= targetCount }

        if (primary != null) return primary

        val fallback = text
            .split(FALLBACK_SENTENCE_SPLIT_REGEX)
            .asSequence()
            .map { it.trim() }
            .map { cleanSentenceStart(it) }
            .filter { isValidSummarySentence(it) }
            .distinct()
            .toList()

        return if (fallback.isNotEmpty()) fallback else emptyList()
    }

    private fun scoreSentence(sentence: String, position: Int): Double {
        val positionScore = when (position) {
            0 -> 2.0
            1 -> 1.5
            else -> 1.0
        }
        val lengthScore = sentence.split(Regex("\\s+")).size.toDouble()
        return positionScore * lengthScore
    }

    private fun cleanSentenceStart(sentence: String): String {
        val trimmed = sentence.dropWhile { !it.isLetterOrDigit() }
        if (trimmed.isBlank()) return ""
        return trimmed.replaceFirstChar { it.uppercase() }
    }

    private fun isValidSummarySentence(sentence: String): Boolean {
        val normalized = sentence.trim()
        if (normalized.length < MIN_SUMMARY_SENTENCE_LENGTH_CHARS) return false
        val compact = normalized.lowercase().replace(Regex("\\s+"), " ")
        if (FOOTER_PATTERNS.any { it.containsMatchIn(compact) }) return false
        return true
    }

    fun getCentralHeadlines(headlines: List<String>, count: Int = 3): List<String> {
        if (headlines.isEmpty()) return emptyList()
        if (headlines.size <= count) return headlines

        val wordSets = headlines.map { h -> h.lowercase().split(Regex("\\s+")).toSet() }

        val scores = DoubleArray(headlines.size) { i ->
            headlines.indices.sumOf { j -> if (i != j) jaccardSim(wordSets[i], wordSets[j]) else 0.0 }
        }

        val selected = mutableListOf<Int>()
        val used = BooleanArray(headlines.size)

        while (selected.size < count) {
            val next = scores.indices.filter { !used[it] }.maxByOrNull { scores[it] } ?: break
            selected.add(next)
            used[next] = true
            for (i in scores.indices) {
                if (!used[i]) {
                    val maxSim = selected.maxOf { jaccardSim(wordSets[i], wordSets[it]) }
                    scores[i] *= (1.0 - maxSim * MMR_LAMBDA)
                }
            }
        }

        return selected.map { headlines[it] }
    }

    private fun jaccardSim(a: Set<String>, b: Set<String>): Double {
        val intersection = a.intersect(b).size.toDouble()
        val union = a.union(b).size.toDouble()
        return if (union == 0.0) 0.0 else intersection / union
    }

    private val PRIMARY_SENTENCE_SPLIT_REGEX = Regex("(?<=[.!?…])\\s+|\\n+")
    private val FALLBACK_SENTENCE_SPLIT_REGEX = Regex("(?<=[;:])\\s+|\\n+")
    private const val MIN_SUMMARY_SENTENCE_LENGTH_CHARS = 45
    private val FOOTER_PATTERNS = listOf(
        Regex("підписатися\\s+на"),
        Regex("подписат(ь|и)ся\\s+на"),
        Regex("subscribe"),
        Regex("t\\.me/"),
        Regex("telegram"),
        Regex("times\\s+of\\s+ukraine")
    )
}

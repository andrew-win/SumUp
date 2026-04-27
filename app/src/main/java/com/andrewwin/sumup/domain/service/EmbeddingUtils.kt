package com.andrewwin.sumup.domain.service

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

data class TextOptimizationFeatures(
    val jaccardTokens: Set<String>,
    val entities: Set<String>
)

object EmbeddingUtils {
    const val EMBEDDING_DIM = 768
    const val MIN_TOKEN_LENGTH = 5
    const val ENTITY_PREFIX_LENGTH = 4
    const val ENTITY_FIRST_PREFIX_LENGTH = 3
    const val MIN_ENTITY_WORD_LENGTH = 3
    const val MIN_ABBREVIATION_LENGTH = 2

    private val WHITESPACE_REGEX = Regex("\\s+")
    private val ENTITY_WORD_REGEX = Regex("\\p{L}[\\p{L}\\p{N}ʼ'’\\-]*")
    private val TITLE_STOPWORDS = setOf<String>()

    fun toByteArray(floats: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        floats.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    fun toFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / 4) { buffer.float }
    }

    fun normalize(vector: FloatArray): FloatArray {
        val mag = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        return if (mag > 0f) {
            FloatArray(vector.size) { vector[it] / mag }
        } else {
            vector
        }
    }

    fun dotProduct(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) {
            sum += a[i] * b[i]
        }
        return sum
    }

    fun resizeEmbedding(embedding: FloatArray): FloatArray {
        return when {
            embedding.size == EMBEDDING_DIM -> embedding
            embedding.size > EMBEDDING_DIM -> embedding.copyOfRange(0, EMBEDDING_DIM)
            else -> FloatArray(EMBEDDING_DIM).also { embedding.copyInto(it) }
        }
    }

    fun isZeroVector(vector: FloatArray): Boolean {
        for (value in vector) {
            if (value != 0f) return false
        }
        return true
    }

    fun normalizeTitle(title: String): String {
        return title.lowercase().replace(WHITESPACE_REGEX, " ").trim()
    }

    fun extractTextFeatures(title: String): TextOptimizationFeatures {
        return TextOptimizationFeatures(
            jaccardTokens = importantTitleTokens(title),
            entities = titleEntityPrefixes(title)
        )
    }

    fun titleJaccard(leftTitle: String, rightTitle: String): Float {
        val leftTokens = importantTitleTokens(leftTitle)
        val rightTokens = importantTitleTokens(rightTitle)
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) return 0f
        val intersectionSize = leftTokens.intersect(rightTokens).size
        val unionSize = leftTokens.union(rightTokens).size
        return if (unionSize == 0) 0f else intersectionSize.toFloat() / unionSize.toFloat()
    }

    fun titleEntityPrefixes(title: String): Set<String> {
        val words = allTitleWords(title)
        if (words.isEmpty()) return emptySet()
        val result = mutableSetOf<String>()
        var index = 0
        while (index < words.size) {
            val word = words[index]
            if (index == 0 && !isAbbreviation(word)) {
                index++
                continue
            }
            if (isAbbreviation(word)) {
                normalizeEntityWords(listOf(word))?.let { result.add(it) }
                index++
                continue
            }
            if (!isCapitalizedWord(word)) {
                index++
                continue
            }
            val entityWords = mutableListOf(word)
            var nextIndex = index + 1
            while (nextIndex < words.size) {
                val nextWord = words[nextIndex]
                if (isAbbreviation(nextWord) || !isCapitalizedWord(nextWord)) break
                entityWords.add(nextWord)
                nextIndex++
            }
            normalizeEntityWords(entityWords)?.let { result.add(it) }
            index = nextIndex
        }
        return result
    }

    fun allTitleWords(title: String): List<String> {
        return ENTITY_WORD_REGEX.findAll(title).map { it.value }.toList()
    }

    private fun importantTitleTokens(title: String): Set<String> {
        val words = allTitleWords(title)
        if (words.isEmpty()) return emptySet()
        return words.asSequence()
            .mapIndexedNotNull { index, word ->
                if (isAbbreviation(word)) null
                else if (index > 0 && isCapitalizedWord(word)) null
                else normalizeJaccardToken(word.lowercase())
            }
            .toSet()
    }

    private fun normalizeJaccardToken(token: String): String? {
        if (token.length < MIN_TOKEN_LENGTH || token in TITLE_STOPWORDS) return null
        val normalized = token.dropLast(1)
        if (normalized.isBlank() || normalized in TITLE_STOPWORDS) return null
        return normalized
    }

    private fun normalizeEntityWords(words: List<String>): String? {
        val normalizedParts = words.mapIndexedNotNull { index, word ->
            val cleaned = cleanEntityWord(word)
            val normalized = cleaned.lowercase()
            if (isAbbreviation(cleaned)) return@mapIndexedNotNull normalized
            if (normalized.length < MIN_ENTITY_WORD_LENGTH) return@mapIndexedNotNull null
            val prefixLength = if (words.size > 1 && index == 0) ENTITY_FIRST_PREFIX_LENGTH else ENTITY_PREFIX_LENGTH
            normalized.take(prefixLength)
        }
        return if (normalizedParts.isEmpty()) null else normalizedParts.joinToString("_")
    }

    private fun cleanEntityWord(word: String): String {
        return word.trim().replace(Regex("[ʼ’'-]"), "")
    }

    private fun isCapitalizedWord(word: String): Boolean {
        return word.firstOrNull()?.isUpperCase() == true
    }

    fun isAbbreviation(word: String): Boolean {
        val cleaned = cleanEntityWord(word)
        if (cleaned.length < MIN_ABBREVIATION_LENGTH) return false
        var hasLetter = false
        for (char in cleaned) {
            if (char.isLetter()) {
                hasLetter = true
                if (!char.isUpperCase()) return false
            }
        }
        return hasLetter
    }
}

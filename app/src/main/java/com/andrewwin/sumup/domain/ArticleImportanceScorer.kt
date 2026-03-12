package com.andrewwin.sumup.domain

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.SourceType
import kotlin.math.ln
import kotlin.math.min

class ArticleImportanceScorer {

    fun score(
        article: Article,
        sourceType: SourceType,
        minContentLength: Int = DEFAULT_MIN_CONTENT_LENGTH,
        weightLength: Float = DEFAULT_WEIGHT_LENGTH,
        weightViews: Float = DEFAULT_WEIGHT_VIEWS,
        weightFacts: Float = DEFAULT_WEIGHT_FACTS
    ): Float {
        if (article.content.length < minContentLength) return 0f

        val lengthScore = computeLengthScore(article.content.length)
        val viewScore = computeViewScore(article.viewCount, sourceType)
        val factScore = computeFactScore(article.title + " " + article.content)

        val totalWeight = weightLength + weightViews + weightFacts
        return if (totalWeight > 0f) {
            (weightLength * lengthScore + weightViews * viewScore + weightFacts * factScore) / totalWeight
        } else {
            0f
        }
    }

    private fun computeLengthScore(length: Int): Float {
        return min(length.toFloat() / MAX_LENGTH_FOR_FULL_SCORE, 1f)
    }

    private fun computeViewScore(viewCount: Long, sourceType: SourceType): Float {
        return when (sourceType) {
            SourceType.RSS -> STATIC_RSS_VIEW_SCORE
            SourceType.TELEGRAM, SourceType.YOUTUBE -> {
                if (viewCount <= 0) return 0f
                val logView = ln(viewCount.toDouble() + 1).toFloat()
                min(logView / LOG_VIEW_SCALE, 1f)
            }
        }
    }

    private fun computeFactScore(text: String): Float {
        val words = text.split(Regex("\\s+"))
        var factCount = 0
        words.forEachIndexed { index, word ->
            if (word.isBlank()) return@forEachIndexed
            val isNumber = word.contains(Regex("\\d"))
            val isMidSentenceCapitalized = index > 0 &&
                word.first().isUpperCase() &&
                !words[index - 1].endsWith(".")
            if (isNumber || isMidSentenceCapitalized) factCount++
        }
        return min(factCount.toFloat() / MAX_FACTS_FOR_FULL_SCORE, 1f)
    }

    companion object {
        const val DEFAULT_MIN_CONTENT_LENGTH = 150
        const val DEFAULT_WEIGHT_LENGTH = 0.3f
        const val DEFAULT_WEIGHT_VIEWS = 0.4f
        const val DEFAULT_WEIGHT_FACTS = 0.3f
        private const val MAX_LENGTH_FOR_FULL_SCORE = 500
        private const val MAX_FACTS_FOR_FULL_SCORE = 10f
        private const val LOG_VIEW_SCALE = 14f
        private const val STATIC_RSS_VIEW_SCORE = 0.5f
    }
}

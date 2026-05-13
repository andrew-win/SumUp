package com.andrewwin.sumup.domain.ai

import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.domain.summary.SummaryLimits
import com.andrewwin.sumup.domain.summary.ExtractiveSummaryService
import javax.inject.Inject

class AdaptiveTextShrinker @Inject constructor(
    private val getExtractiveSummaryUseCase: ExtractiveSummaryService
) {
    fun shrinkDigestArticle(text: String): String {
        if (text.isBlank()) return ""
        return getExtractiveSummaryUseCase(
            text,
            SummaryLimits.Adaptive.digestExtractiveSentences
        ).joinToString(" ")
    }

    operator fun invoke(text: String, prefs: UserPreferences): String {
        return shrinkByAdaptiveRange(
            text = text,
            prefs = prefs,
            rangeLength = text.length
        )
    }

    fun shrinkByAdaptiveRange(text: String, prefs: UserPreferences, rangeLength: Int): String {
        if (text.isBlank()) return ""

        return when {
            rangeLength < prefs.adaptiveExtractiveOnlyBelowChars -> {
                shrinkFirstRange(text, prefs)
            }
            rangeLength in prefs.adaptiveExtractiveOnlyBelowChars..prefs.adaptiveExtractiveHighCompressionAboveChars -> {
                shrinkToPercent(text, prefs.adaptiveExtractiveCompressionPercentMedium / PERCENT_DIVISOR)
            }
            else -> {
                shrinkToPercent(text, prefs.adaptiveExtractiveCompressionPercentHigh / PERCENT_DIVISOR)
            }
        }
    }

    private fun shrinkFirstRange(text: String, prefs: UserPreferences): String {
        val compressionPercent = prefs.adaptiveExtractiveCompressionPercentFirst
        return if (compressionPercent <= 0) {
            getExtractiveSummaryUseCase(
                text,
                SummaryLimits.Extractive.defaultSentencesPerArticle
            ).joinToString(" ")
        } else {
            shrinkToPercent(text, compressionPercent / PERCENT_DIVISOR)
        }
    }

    private fun shrinkToPercent(text: String, percent: Float): String {
        val targetChars = (text.length * percent).toInt().coerceAtLeast(1)
        val sentenceCountEstimate = estimateSentenceCountByTargetLength(text, targetChars)
        return getExtractiveSummaryUseCase(text, sentenceCountEstimate).joinToString(" ")
    }

    private fun estimateSentenceCountByTargetLength(content: String, targetChars: Int): Int {
        if (content.isBlank()) return 1
        val sentenceCount = content
            .split(Regex("(?<=[.!?…])\\s+"))
            .map { it.trim() }
            .count { it.isNotBlank() }
            .coerceAtLeast(1)
        val ratio = (targetChars.toFloat() / content.length.coerceAtLeast(1)).coerceIn(0.05f, 1f)
        return (sentenceCount * ratio).toInt().coerceAtLeast(1)
    }

    private companion object {
        const val PERCENT_DIVISOR = 100f
    }
}

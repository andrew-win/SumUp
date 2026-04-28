package com.andrewwin.sumup.domain.usecase.ai

import javax.inject.Inject

import com.andrewwin.sumup.data.local.entities.UserPreferences

class ShrinkTextForAdaptiveStrategyUseCase @Inject constructor(
    private val getExtractiveSummaryUseCase: GetExtractiveSummaryUseCase
) {
    operator fun invoke(text: String, prefs: UserPreferences): String {
        if (text.isBlank()) return ""
        
        val length = text.length
        return when {
            length < SummaryLimits.Adaptive.shortTextThresholdChars -> {
                getExtractiveSummaryUseCase(text, 5).joinToString(" ")
            }
            length in SummaryLimits.Adaptive.shortTextThresholdChars..SummaryLimits.Adaptive.mediumTextThresholdChars -> {
                shrinkToPercent(text, SummaryLimits.Adaptive.mediumCompressionPercent / 100f)
            }
            else -> {
                shrinkToPercent(text, SummaryLimits.Adaptive.highCompressionPercent / 100f)
            }
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
}

package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.domain.service.ExtractiveSummarizer
import javax.inject.Inject

class ArticlePreprocessorUseCase @Inject constructor() {

    data class Output(
        val textForCloud: String,
        val finalExtractiveText: String? = null
    )

    fun preprocess(
        rawText: String,
        prefs: UserPreferences,
        context: SummaryContext
    ): Output {
        val maxChars = prefs.aiMaxCharsPerArticle.coerceAtLeast(200)
        val trimmed = rawText.take(maxChars)

        return when (prefs.aiStrategy) {
            AiStrategy.LOCAL, AiStrategy.CLOUD -> Output(textForCloud = trimmed)
            AiStrategy.ADAPTIVE -> preprocessAdaptive(trimmed, prefs, context)
        }
    }

    private fun preprocessAdaptive(
        text: String,
        prefs: UserPreferences,
        context: SummaryContext
    ): Output {
        if (text.isBlank()) return Output(textForCloud = "")

        val sentenceLimit = context.extractiveSentencesLimit(prefs)
        val baseExtractive = ExtractiveSummarizer.summarize(text, sentenceLimit)
        if (baseExtractive.size <= sentenceLimit) {
            return Output(
                textForCloud = baseExtractive.joinToString(" "),
                finalExtractiveText = baseExtractive.joinToString(" ")
            )
        }

        val targetChars = (text.length * (prefs.adaptiveExtractiveCompressionPercent.coerceIn(1, 100) / 100f))
            .toInt()
            .coerceAtLeast(1)
        val estimateSentences = estimateSentenceCountByTargetLength(text, targetChars)
        val compressed = ExtractiveSummarizer.summarize(text, estimateSentences).joinToString(" ")

        return Output(textForCloud = compressed.take(prefs.aiMaxCharsPerArticle.coerceAtLeast(200)))
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










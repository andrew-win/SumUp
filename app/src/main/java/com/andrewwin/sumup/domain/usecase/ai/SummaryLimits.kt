package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.UserPreferences

object SummaryLimits {
    var currentPrefs: UserPreferences? = null

    object Single {
        const val mainSentences = 1
        const val maxPoints = 5
        const val localExtractiveSentences = mainSentences + maxPoints
        const val uiMaxDetails = maxPoints
        const val maxWordsPerPoint = 25
    }

    object Compare {
        const val mainSentences = 1
        const val maxBullets = 5
        const val uiMaxDetails = maxBullets
        const val maxWordsPerPoint = 25
    }

    object LocalClusterSummary {
        const val maxSummarySentences = 5
        const val minSentencesPerSource = 1
        const val candidateSentencesPerSource = 3
        const val nearDuplicateThreshold: Float = 0.87f
    }

    object Digest {
        const val minThemes = 2
        const val maxThemes = 4
        const val minItemsPerTheme = 2
        const val maxItemsPerTheme = 5
        const val maxWordsPerTitle = 25
        const val maxLocalArticles = 8
        const val emojiesCount = 3
    }

    object QA {
        const val maxDetailPoints = 5
        const val maxWordsShortAnswer = 15
        const val maxWordsPerDetailedBullet = 25
    }

    object Extractive {
        const val defaultTopCount = 5
        const val defaultSentencesPerArticle = 5
    }

    object Adaptive {
        const val digestExtractiveSentences = 2

        val shortTextThresholdChars: Int
            get() = currentPrefs?.adaptiveExtractiveOnlyBelowChars ?: 1000
        val mediumTextThresholdChars: Int
            get() = currentPrefs?.adaptiveExtractiveHighCompressionAboveChars ?: 2200
        val firstCompressionPercent: Int
            get() = currentPrefs?.adaptiveExtractiveCompressionPercentFirst ?: 0
        val mediumCompressionPercent: Int
            get() = currentPrefs?.adaptiveExtractiveCompressionPercentMedium ?: 22
        val highCompressionPercent: Int
            get() = currentPrefs?.adaptiveExtractiveCompressionPercentHigh ?: 15
    }
}

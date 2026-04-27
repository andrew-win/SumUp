package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.UserPreferences

object SummaryLimits {
    var currentPrefs: UserPreferences? = null

    object Single {
        const val maxPoints = 5
        const val maxWordsPerPoint = 20
    }

    object Compare {
        const val maxCommon = 5
        const val maxUnique = 5
        const val maxWordsPerPoint = 20

        val jaccardThreshold: Float = 0.2f
        val localSimilarityThreshold: Float
            get() = currentPrefs?.localDeduplicationThreshold ?: 0.55f
    }

    object Digest {
        const val maxThemes = 4
        const val minItemsPerTheme = 2
        const val maxItemsPerTheme = 5
        const val maxWordsPerTitle = 15
        const val topLocalArticles = 7
        const val emojiesCount = 3
    }

    object QA {
        const val maxDetailPoints = 5
        const val maxWordsShortAnswer = 15
        const val maxWordsPerDetailedBullet = 20
    }

    object Extractive {
        const val defaultTopCount = 5
        const val defaultSentencesPerArticle = 5
    }

    object Adaptive {
        val shortTextThresholdChars: Int
            get() = currentPrefs?.adaptiveExtractiveOnlyBelowChars ?: 1000
        val mediumTextThresholdChars: Int
            get() = currentPrefs?.adaptiveExtractiveHighCompressionAboveChars ?: 3000
        val mediumCompressionPercent: Int
            get() = currentPrefs?.adaptiveExtractiveCompressionPercentMedium ?: 50
        val highCompressionPercent: Int
            get() = currentPrefs?.adaptiveExtractiveCompressionPercentHigh ?: 25
    }
}

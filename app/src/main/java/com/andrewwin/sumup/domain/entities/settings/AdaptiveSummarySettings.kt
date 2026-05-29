package com.andrewwin.sumup.domain.entities.settings

data class AdaptiveSummarySettings(
    val isPreprocessingEnabled: Boolean,
    val extractiveOnlyBelowChars: Int,
    val highCompressionAboveChars: Int,
    val compressionPercentFirst: Int,
    val compressionPercentMedium: Int,
    val compressionPercentHigh: Int
)

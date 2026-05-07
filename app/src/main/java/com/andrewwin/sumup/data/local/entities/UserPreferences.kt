package com.andrewwin.sumup.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AiStrategy {
    CLOUD, LOCAL, ADAPTIVE
}

enum class DeduplicationStrategy {
    CLOUD, LOCAL
}

enum class AppThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class AppLanguage {
    UK, EN
}

enum class SummaryLanguage {
    UK, EN
}

@Entity(tableName = "user_preferences")
data class UserPreferences(
    @PrimaryKey val id: Int = 0,
    val aiStrategy: AiStrategy = AiStrategy.ADAPTIVE,
    val isScheduledSummaryEnabled: Boolean = false,
    val isScheduledSummaryPushEnabled: Boolean = false,
    val scheduledHour: Int = 8,
    val scheduledMinute: Int = 0,
    val lastWorkRunTimestamp: Long = 0,
    val isDeduplicationEnabled: Boolean = false,
    val deduplicationStrategy: DeduplicationStrategy = DeduplicationStrategy.CLOUD,
    val localDeduplicationThreshold: Float = 0.85f,
    val cloudDeduplicationThreshold: Float = 0.84f,
    val minMentions: Int = 2,
    val isHideSingleNewsEnabled: Boolean = false,
    val modelPath: String? = null,
    val isImportanceFilterEnabled: Boolean = true,
    val isAdaptiveExtractivePreprocessingEnabled: Boolean = true,
    val adaptiveExtractiveOnlyBelowChars: Int = 1000,
    val adaptiveExtractiveHighCompressionAboveChars: Int = 3000,
    val adaptiveExtractiveCompressionPercentMedium: Int = 50,
    val adaptiveExtractiveCompressionPercentHigh: Int = 25,
    val summaryItemsPerNewsInFeed: Int = 3,
    val summaryItemsPerNewsInScheduled: Int = 3,
    val summaryNewsInFeedExtractive: Int = 4,
    val summaryNewsInFeedCloud: Int = 4,
    val summaryNewsInScheduledExtractive: Int = 4,
    val summaryNewsInScheduledCloud: Int = 4,
    val extractiveNewsInFeed: Int = 4,
    val extractiveSentencesInScheduled: Int = 3,
    val extractiveNewsInScheduled: Int = 4,
    val showLastSummariesCount: Int = 5,
    val showInfographicNewsCount: Int = 5,
    val aiMaxCharsPerArticle: Int = 1000,
    val aiMaxCharsPerFeedArticle: Int = 1000,
    val aiMaxCharsTotal: Int = 12000,
    val summaryPrompt: String = "",
    val isCustomSummaryPromptEnabled: Boolean = false,
    val isFeedMediaEnabled: Boolean = true,
    val isFeedDescriptionEnabled: Boolean = false,
    val isFeedSummaryUseFullTextEnabled: Boolean = false,
    val isRecommendationsEnabled: Boolean = false,
    val articleAutoCleanupDays: Int = 3,
    val appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val appLanguage: AppLanguage = AppLanguage.UK,
    val summaryLanguage: SummaryLanguage = SummaryLanguage.UK
)







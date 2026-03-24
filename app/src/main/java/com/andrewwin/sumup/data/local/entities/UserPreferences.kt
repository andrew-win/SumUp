package com.andrewwin.sumup.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AiStrategy {
    CLOUD, LOCAL, ADAPTIVE
}

enum class AppThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class AppLanguage {
    UK, EN
}

@Entity(tableName = "user_preferences")
data class UserPreferences(
    @PrimaryKey val id: Int = 0,
    val aiStrategy: AiStrategy = AiStrategy.ADAPTIVE,
    val isScheduledSummaryEnabled: Boolean = false,
    val scheduledHour: Int = 8,
    val scheduledMinute: Int = 0,
    val lastWorkRunTimestamp: Long = 0,
    val isDeduplicationEnabled: Boolean = false,
    val localDeduplicationThreshold: Float = 0.55f,
    val cloudDeduplicationThreshold: Float = 0.75f,
    val minMentions: Int = 2,
    val isHideSingleNewsEnabled: Boolean = false,
    val modelPath: String? = null,
    val isImportanceFilterEnabled: Boolean = true,
    val isAdaptiveExtractivePreprocessingEnabled: Boolean = true,
    val extractiveSentencesInFeed: Int = 3,
    val extractiveSentencesInScheduled: Int = 5,
    val extractiveNewsInScheduled: Int = 10,
    val showLastSummariesCount: Int = 5,
    val showInfographicNewsCount: Int = 4,
    val aiMaxCharsPerArticle: Int = 1000,
    val aiMaxCharsPerFeedArticle: Int = 1000,
    val aiMaxCharsTotal: Int = 12000,
    val summaryPrompt: String = "",
    val isCustomSummaryPromptEnabled: Boolean = false,
    val isFeedMediaEnabled: Boolean = false,
    val isFeedDescriptionEnabled: Boolean = true,
    val appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val appLanguage: AppLanguage = AppLanguage.UK
)

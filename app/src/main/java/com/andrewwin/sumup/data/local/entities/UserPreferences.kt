package com.andrewwin.sumup.data.local.entities

import androidx.room.ColumnInfo
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

data class ScheduledSummaryTime(
    val hour: Int,
    val minute: Int
) {
    fun toStorageValue(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

    companion object {
        val DEFAULT = ScheduledSummaryTime(8, 0)

        fun fromStorageValue(value: String): ScheduledSummaryTime? {
            val parts = value.trim().split(":")
            if (parts.size != 2) return null
            val hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null
            return ScheduledSummaryTime(hour, minute).takeIf { it.isValid() }
        }
    }
}

fun ScheduledSummaryTime.isValid(): Boolean = hour in 0..23 && minute in 0..59

fun List<ScheduledSummaryTime>.normalizedScheduledSummaryTimes(): List<ScheduledSummaryTime> =
    asSequence()
        .filter { it.isValid() }
        .distinctBy { it.hour * 60 + it.minute }
        .sortedWith(compareBy<ScheduledSummaryTime> { it.hour }.thenBy { it.minute })
        .toList()

fun List<ScheduledSummaryTime>.toScheduledSummaryTimesStorageValue(): String =
    normalizedScheduledSummaryTimes().joinToString(",") { it.toStorageValue() }

fun String.toScheduledSummaryTimes(fallback: ScheduledSummaryTime = ScheduledSummaryTime.DEFAULT): List<ScheduledSummaryTime> {
    val times = split(",")
        .mapNotNull(ScheduledSummaryTime::fromStorageValue)
        .normalizedScheduledSummaryTimes()
    return times.ifEmpty { listOf(fallback) }
}

@Entity(tableName = "user_preferences")
data class UserPreferences(
    @PrimaryKey val id: Int = 0,
    val aiStrategy: AiStrategy = AiStrategy.ADAPTIVE,
    val isScheduledSummaryEnabled: Boolean = false,
    val isScheduledSummaryPushEnabled: Boolean = false,
    val scheduledHour: Int = 8,
    val scheduledMinute: Int = 0,
    val scheduledSummaryTimes: String = ScheduledSummaryTime.DEFAULT.toStorageValue(),
    val lastWorkRunTimestamp: Long = 0,
    val isDeduplicationEnabled: Boolean = true,
    val deduplicationStrategy: DeduplicationStrategy = DeduplicationStrategy.LOCAL,
    val localDeduplicationThreshold: Float = 0.860f,
    val cloudDeduplicationThreshold: Float = 0.84f,
    val minMentions: Int = 2,
    val isHideSingleNewsEnabled: Boolean = false,
    val modelPath: String? = null,
    val isImportanceFilterEnabled: Boolean = true,
    val isAdaptiveExtractivePreprocessingEnabled: Boolean = true,
    val adaptiveExtractiveOnlyBelowChars: Int = 1000,
    val adaptiveExtractiveHighCompressionAboveChars: Int = 3000,
    val adaptiveExtractiveCompressionPercentFirst: Int = 0,
    val adaptiveExtractiveCompressionPercentMedium: Int = 30,
    val adaptiveExtractiveCompressionPercentHigh: Int = 15,
    val summaryItemsPerNewsInFeed: Int = 3,
    val summaryItemsPerNewsInScheduled: Int = 3,
    val summaryNewsInFeedCloud: Int = 4,
    val summaryNewsInScheduledCloud: Int = 4,
    val extractiveNewsInFeed: Int = 4,
    val extractiveSentencesInScheduled: Int = 3,
    val extractiveNewsInScheduled: Int = 4,
    val showLastSummariesCount: Int = 5,
    val showInfographicNewsCount: Int = 6,
    val aiMaxCharsSingleArticle: Int = 1000,
    val aiMaxCharsNewsCluster: Int = 1000,
    val aiMaxCharsSingleFeedArticle: Int = 1000,
    val aiMaxCharsFeedCluster: Int = 1000,
    val aiMaxCharsTotal: Int = 30000,
    val summaryPrompt: String = "",
    val isCustomSummaryPromptEnabled: Boolean = false,
    val isFeedMediaEnabled: Boolean = true,
    val isFeedDescriptionEnabled: Boolean = false,
    val isFeedSummaryUseFullTextEnabled: Boolean = false,
    val isRecommendationsEnabled: Boolean = false,
    @ColumnInfo(name = "articleAutoCleanupDays")
    val articleAutoCleanupHours: Int = DEFAULT_ARTICLE_AUTO_CLEANUP_HOURS,
    val appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val appLanguage: AppLanguage = AppLanguage.UK,
    val summaryLanguage: SummaryLanguage = SummaryLanguage.UK
) {
    val scheduledSummaryTimeList: List<ScheduledSummaryTime>
        get() = scheduledSummaryTimes.toScheduledSummaryTimes(
            fallback = ScheduledSummaryTime(scheduledHour, scheduledMinute).takeIf { it.isValid() }
                ?: ScheduledSummaryTime.DEFAULT
        )

    companion object {
        const val MIN_ARTICLE_AUTO_CLEANUP_HOURS = 6
        const val MAX_ARTICLE_AUTO_CLEANUP_HOURS = 24
        const val DEFAULT_ARTICLE_AUTO_CLEANUP_HOURS = 16
    }
}







package com.andrewwin.sumup.domain.settings

enum class AiStrategy {
    CLOUD,
    LOCAL,
    ADAPTIVE
}

enum class DeduplicationStrategy {
    CLOUD,
    LOCAL
}

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class AppLanguage {
    UK,
    EN
}

enum class SummaryLanguage {
    UK,
    EN
}

data class ScheduledSummaryTime(
    val hour: Int,
    val minute: Int
) {
    fun toStorageValue(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

    fun isValid(): Boolean = hour in 0..23 && minute in 0..59

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

fun List<ScheduledSummaryTime>.normalizedScheduledSummaryTimes(): List<ScheduledSummaryTime> =
    asSequence()
        .filter { it.isValid() }
        .distinctBy { it.hour * 60 + it.minute }
        .sortedWith(compareBy<ScheduledSummaryTime> { it.hour }.thenBy { it.minute })
        .toList()

fun List<ScheduledSummaryTime>.toScheduledSummaryTimesStorageValue(): String =
    normalizedScheduledSummaryTimes().joinToString(",") { it.toStorageValue() }

fun String.toScheduledSummaryTimes(
    fallback: ScheduledSummaryTime = ScheduledSummaryTime.DEFAULT
): List<ScheduledSummaryTime> {
    val times = split(",")
        .mapNotNull(ScheduledSummaryTime::fromStorageValue)
        .normalizedScheduledSummaryTimes()
    return times.ifEmpty { listOf(fallback) }
}

data class ModelSettings(
    val strategy: AiStrategy,
    val modelPath: String?
)

data class ScheduledSummarySettings(
    val isEnabled: Boolean,
    val isPushEnabled: Boolean,
    val hour: Int,
    val minute: Int,
    val times: List<ScheduledSummaryTime>,
    val lastWorkRunTimestamp: Long
)

data class DeduplicationSettings(
    val isEnabled: Boolean,
    val strategy: DeduplicationStrategy,
    val localThreshold: Float,
    val cloudThreshold: Float,
    val minMentions: Int,
    val isHideSingleNewsEnabled: Boolean
)

data class AdaptiveSummarySettings(
    val isPreprocessingEnabled: Boolean,
    val extractiveOnlyBelowChars: Int,
    val highCompressionAboveChars: Int,
    val compressionPercentFirst: Int,
    val compressionPercentMedium: Int,
    val compressionPercentHigh: Int
)

data class FeedSummarySettings(
    val summaryItemsPerNewsInFeed: Int,
    val summaryItemsPerNewsInScheduled: Int,
    val summaryNewsInFeedCloud: Int,
    val summaryNewsInScheduledCloud: Int,
    val extractiveNewsInFeed: Int,
    val extractiveSentencesInScheduled: Int,
    val extractiveNewsInScheduled: Int,
    val aiMaxCharsSingleArticle: Int,
    val aiMaxCharsNewsCluster: Int,
    val aiMaxCharsSingleFeedArticle: Int,
    val aiMaxCharsFeedCluster: Int,
    val aiMaxCharsTotal: Int,
    val isUseFullTextEnabled: Boolean
)

data class FeedDisplaySettings(
    val isMediaEnabled: Boolean,
    val isDescriptionEnabled: Boolean,
    val isImportanceFilterEnabled: Boolean
)

data class SummaryPromptSettings(
    val prompt: String,
    val isCustomPromptEnabled: Boolean,
    val language: SummaryLanguage
)

data class RecommendationSettings(
    val isEnabled: Boolean,
    val showInfographicNewsCount: Int
)

data class CleanupSettings(
    val articleAutoCleanupHours: Int
)

data class AppSettings(
    val themeMode: AppThemeMode,
    val appLanguage: AppLanguage
)

data class UserSettings(
    val aiStrategy: AiStrategy = AiStrategy.ADAPTIVE,
    val isScheduledSummaryEnabled: Boolean = false,
    val isScheduledSummaryPushEnabled: Boolean = false,
    val scheduledHour: Int = 8,
    val scheduledMinute: Int = 0,
    val scheduledSummaryTimes: String = ScheduledSummaryTime.DEFAULT.toStorageValue(),
    val lastWorkRunTimestamp: Long = 0,
    val isDeduplicationEnabled: Boolean = true,
    val deduplicationStrategy: DeduplicationStrategy = DeduplicationStrategy.LOCAL,
    val localDeduplicationThreshold: Float = 0.87f,
    val cloudDeduplicationThreshold: Float = 0.87f,
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
    val showInfographicNewsCount: Int = 10,
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

    val model: ModelSettings
        get() = ModelSettings(aiStrategy, modelPath)

    val scheduledSummary: ScheduledSummarySettings
        get() = ScheduledSummarySettings(
            isEnabled = isScheduledSummaryEnabled,
            isPushEnabled = isScheduledSummaryPushEnabled,
            hour = scheduledHour,
            minute = scheduledMinute,
            times = scheduledSummaryTimeList,
            lastWorkRunTimestamp = lastWorkRunTimestamp
        )

    val deduplication: DeduplicationSettings
        get() = DeduplicationSettings(
            isEnabled = isDeduplicationEnabled,
            strategy = deduplicationStrategy,
            localThreshold = localDeduplicationThreshold,
            cloudThreshold = cloudDeduplicationThreshold,
            minMentions = minMentions,
            isHideSingleNewsEnabled = isHideSingleNewsEnabled
        )

    val adaptiveSummary: AdaptiveSummarySettings
        get() = AdaptiveSummarySettings(
            isPreprocessingEnabled = isAdaptiveExtractivePreprocessingEnabled,
            extractiveOnlyBelowChars = adaptiveExtractiveOnlyBelowChars,
            highCompressionAboveChars = adaptiveExtractiveHighCompressionAboveChars,
            compressionPercentFirst = adaptiveExtractiveCompressionPercentFirst,
            compressionPercentMedium = adaptiveExtractiveCompressionPercentMedium,
            compressionPercentHigh = adaptiveExtractiveCompressionPercentHigh
        )

    val feedSummary: FeedSummarySettings
        get() = FeedSummarySettings(
            summaryItemsPerNewsInFeed = summaryItemsPerNewsInFeed,
            summaryItemsPerNewsInScheduled = summaryItemsPerNewsInScheduled,
            summaryNewsInFeedCloud = summaryNewsInFeedCloud,
            summaryNewsInScheduledCloud = summaryNewsInScheduledCloud,
            extractiveNewsInFeed = extractiveNewsInFeed,
            extractiveSentencesInScheduled = extractiveSentencesInScheduled,
            extractiveNewsInScheduled = extractiveNewsInScheduled,
            aiMaxCharsSingleArticle = aiMaxCharsSingleArticle,
            aiMaxCharsNewsCluster = aiMaxCharsNewsCluster,
            aiMaxCharsSingleFeedArticle = aiMaxCharsSingleFeedArticle,
            aiMaxCharsFeedCluster = aiMaxCharsFeedCluster,
            aiMaxCharsTotal = aiMaxCharsTotal,
            isUseFullTextEnabled = isFeedSummaryUseFullTextEnabled
        )

    val feedDisplay: FeedDisplaySettings
        get() = FeedDisplaySettings(
            isMediaEnabled = isFeedMediaEnabled,
            isDescriptionEnabled = isFeedDescriptionEnabled,
            isImportanceFilterEnabled = isImportanceFilterEnabled
        )

    val promptSettings: SummaryPromptSettings
        get() = SummaryPromptSettings(
            prompt = summaryPrompt,
            isCustomPromptEnabled = isCustomSummaryPromptEnabled,
            language = summaryLanguage
        )

    val recommendations: RecommendationSettings
        get() = RecommendationSettings(
            isEnabled = isRecommendationsEnabled,
            showInfographicNewsCount = showInfographicNewsCount
        )

    val cleanup: CleanupSettings
        get() = CleanupSettings(articleAutoCleanupHours = articleAutoCleanupHours)

    val app: AppSettings
        get() = AppSettings(themeMode = appThemeMode, appLanguage = appLanguage)

    companion object {
        const val MIN_ARTICLE_AUTO_CLEANUP_HOURS = 6
        const val MAX_ARTICLE_AUTO_CLEANUP_HOURS = 24
        const val DEFAULT_ARTICLE_AUTO_CLEANUP_HOURS = 13
    }
}

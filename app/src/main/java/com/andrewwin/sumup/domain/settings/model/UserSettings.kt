package com.andrewwin.sumup.domain.settings.model

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
    val isHideSingleNewsEnabled: Boolean = true,
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
    val isFeedTitleExcludeRegexEnabled: Boolean = true,
    val feedTitleExcludeRegex: String = DEFAULT_FEED_TITLE_EXCLUDE_REGEX,
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
            isImportanceFilterEnabled = isImportanceFilterEnabled,
            isTitleExcludeRegexEnabled = isFeedTitleExcludeRegexEnabled,
            titleExcludeRegex = feedTitleExcludeRegex
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
        const val DEFAULT_ARTICLE_AUTO_CLEANUP_HOURS = 16
        const val DEFAULT_FEED_TITLE_EXCLUDE_REGEX =
            """(?:^|[^\p{L}\p{N}_])(?:(?:\d+\s*(?:х|x)?\s*)?(?:бпла(?:ми|х)?|шахед[и]?|ціл(?:ь|і)|балістик[аи]?|каби?|кабів|кр|кар|fpv|ракет(?:а|и|у)?|молні(?:я|ї|ю|єю))\s+(?:на|курсом|у\s+бік|в\s+бік|повз|над|в\s+районі|біля|південніше|північніше|східніше|західніше|з\s+моря|з)|(?:відбій(?:\s+(?:тривог\p{L}*|загроз\p{L}*))?|дорозвідк\p{L}*|локаційн\p{L}*\s+чисто|без\s+подальш\p{L}*\s+фіксаці\p{L}*|сигнал\s+втрачено|не\s+(?:спостеріга(?:ється|ють)|фіксу(?:ється|ють)))|(?:загроз\p{L}*|пуск(?:и|ів|ами)?|вихід)\s+(?:балістик\p{L}*|каб\p{L}*|шахед\p{L}*|бпла\p{L}*|ракет\p{L}*|швидкісн\p{L}*|орєшнік\p{L}*|кедр\p{L}*)|(?:повітрян\p{L}*\s+тривог\p{L}*|(?:оголошено|оголосили|скасовано|скасували|триває)\s+(?:повітрян\p{L}*\s+)?тривог\p{L}*|тривог\p{L}*\s+(?:оголошено|скасовано|триває)))(?=$|[^\p{L}\p{N}_])"""
    }
}

package com.andrewwin.sumup.data.mappers

import com.andrewwin.sumup.data.local.entities.AppLanguage as RoomAppLanguage
import com.andrewwin.sumup.data.local.entities.AppThemeMode as RoomAppThemeMode
import com.andrewwin.sumup.data.local.entities.AiStrategy as RoomAiStrategy
import com.andrewwin.sumup.data.local.entities.DeduplicationStrategy as RoomDeduplicationStrategy
import com.andrewwin.sumup.data.local.entities.PreparedScheduledSummary
import com.andrewwin.sumup.data.local.entities.ScheduledSummaryTime as RoomScheduledSummaryTime
import com.andrewwin.sumup.data.local.entities.Summary
import com.andrewwin.sumup.data.local.entities.SummaryLanguage as RoomSummaryLanguage
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.domain.settings.AppLanguage
import com.andrewwin.sumup.domain.settings.AppThemeMode
import com.andrewwin.sumup.domain.settings.AiStrategy
import com.andrewwin.sumup.domain.settings.DeduplicationStrategy
import com.andrewwin.sumup.domain.settings.ScheduledSummaryTime
import com.andrewwin.sumup.domain.settings.SummaryLanguage
import com.andrewwin.sumup.domain.settings.UserSettings
import com.andrewwin.sumup.domain.summary.ScheduledSummaryDraft
import com.andrewwin.sumup.domain.summary.SummaryRecord

fun UserPreferences.toDomainModel(): UserSettings {
    return UserSettings(
        aiStrategy = aiStrategy.toDomainModel(),
        isScheduledSummaryEnabled = isScheduledSummaryEnabled,
        isScheduledSummaryPushEnabled = isScheduledSummaryPushEnabled,
        scheduledHour = scheduledHour,
        scheduledMinute = scheduledMinute,
        scheduledSummaryTimes = scheduledSummaryTimes,
        lastWorkRunTimestamp = lastWorkRunTimestamp,
        isDeduplicationEnabled = isDeduplicationEnabled,
        deduplicationStrategy = deduplicationStrategy.toDomainModel(),
        localDeduplicationThreshold = localDeduplicationThreshold,
        cloudDeduplicationThreshold = cloudDeduplicationThreshold,
        minMentions = minMentions,
        isHideSingleNewsEnabled = isHideSingleNewsEnabled,
        modelPath = modelPath,
        isImportanceFilterEnabled = isImportanceFilterEnabled,
        isAdaptiveExtractivePreprocessingEnabled = isAdaptiveExtractivePreprocessingEnabled,
        adaptiveExtractiveOnlyBelowChars = adaptiveExtractiveOnlyBelowChars,
        adaptiveExtractiveHighCompressionAboveChars = adaptiveExtractiveHighCompressionAboveChars,
        adaptiveExtractiveCompressionPercentFirst = adaptiveExtractiveCompressionPercentFirst,
        adaptiveExtractiveCompressionPercentMedium = adaptiveExtractiveCompressionPercentMedium,
        adaptiveExtractiveCompressionPercentHigh = adaptiveExtractiveCompressionPercentHigh,
        summaryItemsPerNewsInFeed = summaryItemsPerNewsInFeed,
        summaryItemsPerNewsInScheduled = summaryItemsPerNewsInScheduled,
        summaryNewsInFeedCloud = summaryNewsInFeedCloud,
        summaryNewsInScheduledCloud = summaryNewsInScheduledCloud,
        extractiveNewsInFeed = extractiveNewsInFeed,
        extractiveSentencesInScheduled = extractiveSentencesInScheduled,
        extractiveNewsInScheduled = extractiveNewsInScheduled,
        showLastSummariesCount = showLastSummariesCount,
        showInfographicNewsCount = showInfographicNewsCount,
        aiMaxCharsSingleArticle = aiMaxCharsSingleArticle,
        aiMaxCharsNewsCluster = aiMaxCharsNewsCluster,
        aiMaxCharsSingleFeedArticle = aiMaxCharsSingleFeedArticle,
        aiMaxCharsFeedCluster = aiMaxCharsFeedCluster,
        aiMaxCharsTotal = aiMaxCharsTotal,
        summaryPrompt = summaryPrompt,
        isCustomSummaryPromptEnabled = isCustomSummaryPromptEnabled,
        isFeedMediaEnabled = isFeedMediaEnabled,
        isFeedDescriptionEnabled = isFeedDescriptionEnabled,
        isFeedSummaryUseFullTextEnabled = isFeedSummaryUseFullTextEnabled,
        isRecommendationsEnabled = isRecommendationsEnabled,
        articleAutoCleanupHours = articleAutoCleanupHours,
        appThemeMode = appThemeMode.toDomainModel(),
        appLanguage = appLanguage.toDomainModel(),
        summaryLanguage = summaryLanguage.toDomainModel()
    )
}

fun UserSettings.toRoomEntity(): UserPreferences {
    return UserPreferences(
        aiStrategy = aiStrategy.toRoomEntity(),
        isScheduledSummaryEnabled = isScheduledSummaryEnabled,
        isScheduledSummaryPushEnabled = isScheduledSummaryPushEnabled,
        scheduledHour = scheduledHour,
        scheduledMinute = scheduledMinute,
        scheduledSummaryTimes = scheduledSummaryTimeList.map(ScheduledSummaryTime::toRoomEntity).map { it.toStorageValue() }.joinToString(","),
        lastWorkRunTimestamp = lastWorkRunTimestamp,
        isDeduplicationEnabled = isDeduplicationEnabled,
        deduplicationStrategy = deduplicationStrategy.toRoomEntity(),
        localDeduplicationThreshold = localDeduplicationThreshold,
        cloudDeduplicationThreshold = cloudDeduplicationThreshold,
        minMentions = minMentions,
        isHideSingleNewsEnabled = isHideSingleNewsEnabled,
        modelPath = modelPath,
        isImportanceFilterEnabled = isImportanceFilterEnabled,
        isAdaptiveExtractivePreprocessingEnabled = isAdaptiveExtractivePreprocessingEnabled,
        adaptiveExtractiveOnlyBelowChars = adaptiveExtractiveOnlyBelowChars,
        adaptiveExtractiveHighCompressionAboveChars = adaptiveExtractiveHighCompressionAboveChars,
        adaptiveExtractiveCompressionPercentFirst = adaptiveExtractiveCompressionPercentFirst,
        adaptiveExtractiveCompressionPercentMedium = adaptiveExtractiveCompressionPercentMedium,
        adaptiveExtractiveCompressionPercentHigh = adaptiveExtractiveCompressionPercentHigh,
        summaryItemsPerNewsInFeed = summaryItemsPerNewsInFeed,
        summaryItemsPerNewsInScheduled = summaryItemsPerNewsInScheduled,
        summaryNewsInFeedCloud = summaryNewsInFeedCloud,
        summaryNewsInScheduledCloud = summaryNewsInScheduledCloud,
        extractiveNewsInFeed = extractiveNewsInFeed,
        extractiveSentencesInScheduled = extractiveSentencesInScheduled,
        extractiveNewsInScheduled = extractiveNewsInScheduled,
        showInfographicNewsCount = showInfographicNewsCount,
        aiMaxCharsSingleArticle = aiMaxCharsSingleArticle,
        aiMaxCharsNewsCluster = aiMaxCharsNewsCluster,
        aiMaxCharsSingleFeedArticle = aiMaxCharsSingleFeedArticle,
        aiMaxCharsFeedCluster = aiMaxCharsFeedCluster,
        aiMaxCharsTotal = aiMaxCharsTotal,
        summaryPrompt = summaryPrompt,
        isCustomSummaryPromptEnabled = isCustomSummaryPromptEnabled,
        isFeedMediaEnabled = isFeedMediaEnabled,
        isFeedDescriptionEnabled = isFeedDescriptionEnabled,
        isFeedSummaryUseFullTextEnabled = isFeedSummaryUseFullTextEnabled,
        isRecommendationsEnabled = isRecommendationsEnabled,
        articleAutoCleanupHours = articleAutoCleanupHours,
        appThemeMode = appThemeMode.toRoomEntity(),
        appLanguage = appLanguage.toRoomEntity(),
        summaryLanguage = summaryLanguage.toRoomEntity()
    )
}

fun Summary.toDomainModel(): SummaryRecord = SummaryRecord(
    id = id,
    content = content,
    strategy = strategy.toDomainModel(),
    createdAt = createdAt,
    isError = isError,
    isFavorite = isFavorite,
    executionLabel = executionLabel,
    executionNote = executionNote
)

fun SummaryRecord.toRoomEntity(): Summary = Summary(
    id = id,
    content = content,
    strategy = strategy.toRoomEntity(),
    createdAt = createdAt,
    isError = isError,
    isFavorite = isFavorite,
    executionLabel = executionLabel,
    executionNote = executionNote
)

fun PreparedScheduledSummary.toDomainModel(): ScheduledSummaryDraft = ScheduledSummaryDraft(
    scheduledAt = scheduledAt,
    content = content,
    strategy = strategy.toDomainModel(),
    createdAt = createdAt,
    isError = isError,
    executionLabel = executionLabel,
    executionNote = executionNote
)

fun ScheduledSummaryDraft.toRoomEntity(): PreparedScheduledSummary = PreparedScheduledSummary(
    scheduledAt = scheduledAt,
    content = content,
    strategy = strategy.toRoomEntity(),
    createdAt = createdAt,
    isError = isError,
    executionLabel = executionLabel,
    executionNote = executionNote
)

private fun RoomAiStrategy.toDomainModel(): AiStrategy = when (this) {
    RoomAiStrategy.CLOUD -> AiStrategy.CLOUD
    RoomAiStrategy.LOCAL -> AiStrategy.LOCAL
    RoomAiStrategy.ADAPTIVE -> AiStrategy.ADAPTIVE
}

private fun AiStrategy.toRoomEntity(): RoomAiStrategy = when (this) {
    AiStrategy.CLOUD -> RoomAiStrategy.CLOUD
    AiStrategy.LOCAL -> RoomAiStrategy.LOCAL
    AiStrategy.ADAPTIVE -> RoomAiStrategy.ADAPTIVE
}

private fun RoomDeduplicationStrategy.toDomainModel(): DeduplicationStrategy = when (this) {
    RoomDeduplicationStrategy.CLOUD -> DeduplicationStrategy.CLOUD
    RoomDeduplicationStrategy.LOCAL -> DeduplicationStrategy.LOCAL
}

private fun DeduplicationStrategy.toRoomEntity(): RoomDeduplicationStrategy = when (this) {
    DeduplicationStrategy.CLOUD -> RoomDeduplicationStrategy.CLOUD
    DeduplicationStrategy.LOCAL -> RoomDeduplicationStrategy.LOCAL
}

private fun RoomAppThemeMode.toDomainModel(): AppThemeMode = when (this) {
    RoomAppThemeMode.SYSTEM -> AppThemeMode.SYSTEM
    RoomAppThemeMode.LIGHT -> AppThemeMode.LIGHT
    RoomAppThemeMode.DARK -> AppThemeMode.DARK
}

private fun AppThemeMode.toRoomEntity(): RoomAppThemeMode = when (this) {
    AppThemeMode.SYSTEM -> RoomAppThemeMode.SYSTEM
    AppThemeMode.LIGHT -> RoomAppThemeMode.LIGHT
    AppThemeMode.DARK -> RoomAppThemeMode.DARK
}

private fun RoomAppLanguage.toDomainModel(): AppLanguage = when (this) {
    RoomAppLanguage.UK -> AppLanguage.UK
    RoomAppLanguage.EN -> AppLanguage.EN
}

private fun AppLanguage.toRoomEntity(): RoomAppLanguage = when (this) {
    AppLanguage.UK -> RoomAppLanguage.UK
    AppLanguage.EN -> RoomAppLanguage.EN
}

private fun RoomSummaryLanguage.toDomainModel(): SummaryLanguage = when (this) {
    RoomSummaryLanguage.UK -> SummaryLanguage.UK
    RoomSummaryLanguage.EN -> SummaryLanguage.EN
}

private fun SummaryLanguage.toRoomEntity(): RoomSummaryLanguage = when (this) {
    SummaryLanguage.UK -> RoomSummaryLanguage.UK
    SummaryLanguage.EN -> RoomSummaryLanguage.EN
}

private fun RoomScheduledSummaryTime.toDomainModel(): ScheduledSummaryTime = ScheduledSummaryTime(
    hour = hour,
    minute = minute
)

private fun ScheduledSummaryTime.toRoomEntity(): RoomScheduledSummaryTime = RoomScheduledSummaryTime(
    hour = hour,
    minute = minute
)

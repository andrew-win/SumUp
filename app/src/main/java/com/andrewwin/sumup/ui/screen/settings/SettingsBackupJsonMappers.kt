package com.andrewwin.sumup.ui.screen.settings

import com.andrewwin.sumup.data.local.entities.AiModelConfig
import com.andrewwin.sumup.data.local.entities.AiModelType
import com.andrewwin.sumup.data.local.entities.AiProvider
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.AppLanguage
import com.andrewwin.sumup.data.local.entities.AppThemeMode
import com.andrewwin.sumup.data.local.entities.DeduplicationStrategy
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.local.entities.SummaryLanguage
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.domain.repository.ImportedSource
import com.andrewwin.sumup.domain.repository.ImportedSourceGroup
import org.json.JSONArray
import org.json.JSONObject

internal fun UserPreferences.toBackupJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("aiStrategy", aiStrategy.name)
    put("isScheduledSummaryEnabled", isScheduledSummaryEnabled)
    put("isScheduledSummaryPushEnabled", isScheduledSummaryPushEnabled)
    put("scheduledHour", scheduledHour)
    put("scheduledMinute", scheduledMinute)
    put("lastWorkRunTimestamp", lastWorkRunTimestamp)
    put("isDeduplicationEnabled", isDeduplicationEnabled)
    put("deduplicationStrategy", deduplicationStrategy.name)
    put("localDeduplicationThreshold", localDeduplicationThreshold.toDouble())
    put("cloudDeduplicationThreshold", cloudDeduplicationThreshold.toDouble())
    put("minMentions", minMentions)
    put("isHideSingleNewsEnabled", isHideSingleNewsEnabled)
    // Do not sync local absolute paths between devices.
    put("modelPath", JSONObject.NULL)
    put("isImportanceFilterEnabled", isImportanceFilterEnabled)
    put("isAdaptiveExtractivePreprocessingEnabled", isAdaptiveExtractivePreprocessingEnabled)
    put("adaptiveExtractiveOnlyBelowChars", adaptiveExtractiveOnlyBelowChars)
    put("adaptiveExtractiveCompressAboveChars", adaptiveExtractiveCompressAboveChars)
    put("adaptiveExtractiveCompressionPercent", adaptiveExtractiveCompressionPercent)
    put("summaryItemsPerNewsInFeed", summaryItemsPerNewsInFeed)
    put("summaryItemsPerNewsInScheduled", summaryItemsPerNewsInScheduled)
    put("summaryNewsInFeedExtractive", summaryNewsInFeedExtractive)
    put("summaryNewsInFeedCloud", summaryNewsInFeedCloud)
    put("summaryNewsInScheduledExtractive", summaryNewsInScheduledExtractive)
    put("summaryNewsInScheduledCloud", summaryNewsInScheduledCloud)
    put("extractiveSentencesInFeed", extractiveSentencesInFeed)
    put("extractiveNewsInFeed", extractiveNewsInFeed)
    put("extractiveSentencesInScheduled", extractiveSentencesInScheduled)
    put("extractiveNewsInScheduled", extractiveNewsInScheduled)
    put("showLastSummariesCount", showLastSummariesCount)
    put("showInfographicNewsCount", showInfographicNewsCount)
    put("aiMaxCharsPerArticle", aiMaxCharsPerArticle)
    put("aiMaxCharsPerFeedArticle", aiMaxCharsPerFeedArticle)
    put("aiMaxCharsTotal", aiMaxCharsTotal)
    put("summaryPrompt", summaryPrompt)
    put("isCustomSummaryPromptEnabled", isCustomSummaryPromptEnabled)
    put("isFeedMediaEnabled", isFeedMediaEnabled)
    put("isFeedDescriptionEnabled", isFeedDescriptionEnabled)
    put("isFeedSummaryUseFullTextEnabled", isFeedSummaryUseFullTextEnabled)
    put("isRecommendationsEnabled", isRecommendationsEnabled)
    put("appThemeMode", appThemeMode.name)
    put("appLanguage", appLanguage.name)
    put("summaryLanguage", summaryLanguage.name)
}

internal fun JSONObject.toUserPreferencesFromBackup(): UserPreferences {
    val defaults = UserPreferences()
    return UserPreferences(
        id = optInt("id", defaults.id),
        aiStrategy = runCatching { AiStrategy.valueOf(optString("aiStrategy", defaults.aiStrategy.name)) }
            .getOrDefault(defaults.aiStrategy),
        isScheduledSummaryEnabled = optBoolean("isScheduledSummaryEnabled", defaults.isScheduledSummaryEnabled),
        isScheduledSummaryPushEnabled = optBoolean(
            "isScheduledSummaryPushEnabled",
            defaults.isScheduledSummaryPushEnabled
        ),
        scheduledHour = optInt("scheduledHour", defaults.scheduledHour),
        scheduledMinute = optInt("scheduledMinute", defaults.scheduledMinute),
        lastWorkRunTimestamp = optLong("lastWorkRunTimestamp", defaults.lastWorkRunTimestamp),
        isDeduplicationEnabled = optBoolean("isDeduplicationEnabled", defaults.isDeduplicationEnabled),
        deduplicationStrategy = runCatching {
            DeduplicationStrategy.valueOf(
                optString("deduplicationStrategy", defaults.deduplicationStrategy.name)
            )
        }.getOrDefault(defaults.deduplicationStrategy),
        localDeduplicationThreshold = optDouble(
            "localDeduplicationThreshold",
            defaults.localDeduplicationThreshold.toDouble()
        ).toFloat(),
        cloudDeduplicationThreshold = optDouble(
            "cloudDeduplicationThreshold",
            defaults.cloudDeduplicationThreshold.toDouble()
        ).toFloat(),
        minMentions = optInt("minMentions", defaults.minMentions),
        isHideSingleNewsEnabled = optBoolean("isHideSingleNewsEnabled", defaults.isHideSingleNewsEnabled),
        // Ignore imported modelPath because it's device-specific.
        modelPath = null,
        isImportanceFilterEnabled = optBoolean("isImportanceFilterEnabled", defaults.isImportanceFilterEnabled),
        isAdaptiveExtractivePreprocessingEnabled = optBoolean(
            "isAdaptiveExtractivePreprocessingEnabled",
            defaults.isAdaptiveExtractivePreprocessingEnabled
        ),
        adaptiveExtractiveOnlyBelowChars = optInt(
            "adaptiveExtractiveOnlyBelowChars",
            defaults.adaptiveExtractiveOnlyBelowChars
        ),
        adaptiveExtractiveCompressAboveChars = optInt(
            "adaptiveExtractiveCompressAboveChars",
            defaults.adaptiveExtractiveCompressAboveChars
        ),
        adaptiveExtractiveCompressionPercent = optInt(
            "adaptiveExtractiveCompressionPercent",
            defaults.adaptiveExtractiveCompressionPercent
        ),
        summaryItemsPerNewsInFeed = optInt("summaryItemsPerNewsInFeed", defaults.summaryItemsPerNewsInFeed),
        summaryItemsPerNewsInScheduled = optInt(
            "summaryItemsPerNewsInScheduled",
            defaults.summaryItemsPerNewsInScheduled
        ),
        summaryNewsInFeedExtractive = optInt(
            "summaryNewsInFeedExtractive",
            defaults.summaryNewsInFeedExtractive
        ),
        summaryNewsInFeedCloud = optInt("summaryNewsInFeedCloud", defaults.summaryNewsInFeedCloud),
        summaryNewsInScheduledExtractive = optInt(
            "summaryNewsInScheduledExtractive",
            defaults.summaryNewsInScheduledExtractive
        ),
        summaryNewsInScheduledCloud = optInt(
            "summaryNewsInScheduledCloud",
            defaults.summaryNewsInScheduledCloud
        ),
        extractiveSentencesInFeed = optInt("extractiveSentencesInFeed", defaults.extractiveSentencesInFeed),
        extractiveNewsInFeed = optInt("extractiveNewsInFeed", defaults.extractiveNewsInFeed),
        extractiveSentencesInScheduled = optInt(
            "extractiveSentencesInScheduled",
            defaults.extractiveSentencesInScheduled
        ),
        extractiveNewsInScheduled = optInt("extractiveNewsInScheduled", defaults.extractiveNewsInScheduled),
        showLastSummariesCount = optInt("showLastSummariesCount", defaults.showLastSummariesCount),
        showInfographicNewsCount = optInt("showInfographicNewsCount", defaults.showInfographicNewsCount),
        aiMaxCharsPerArticle = optInt("aiMaxCharsPerArticle", defaults.aiMaxCharsPerArticle),
        aiMaxCharsPerFeedArticle = optInt("aiMaxCharsPerFeedArticle", defaults.aiMaxCharsPerFeedArticle),
        aiMaxCharsTotal = optInt("aiMaxCharsTotal", defaults.aiMaxCharsTotal),
        summaryPrompt = optString("summaryPrompt", defaults.summaryPrompt),
        isCustomSummaryPromptEnabled = optBoolean(
            "isCustomSummaryPromptEnabled",
            defaults.isCustomSummaryPromptEnabled
        ),
        isFeedMediaEnabled = optBoolean("isFeedMediaEnabled", defaults.isFeedMediaEnabled),
        isFeedDescriptionEnabled = optBoolean("isFeedDescriptionEnabled", defaults.isFeedDescriptionEnabled),
        isFeedSummaryUseFullTextEnabled = optBoolean(
            "isFeedSummaryUseFullTextEnabled",
            defaults.isFeedSummaryUseFullTextEnabled
        ),
        isRecommendationsEnabled = optBoolean("isRecommendationsEnabled", defaults.isRecommendationsEnabled),
        appThemeMode = runCatching { AppThemeMode.valueOf(optString("appThemeMode", defaults.appThemeMode.name)) }
            .getOrDefault(defaults.appThemeMode),
        appLanguage = runCatching { AppLanguage.valueOf(optString("appLanguage", defaults.appLanguage.name)) }
            .getOrDefault(defaults.appLanguage),
        summaryLanguage = runCatching {
            SummaryLanguage.valueOf(optString("summaryLanguage", defaults.summaryLanguage.name))
        }.getOrDefault(defaults.summaryLanguage)
    )
}

internal fun AiModelConfig.toBackupJson(): JSONObject = JSONObject().apply {
    put("name", name)
    put("provider", provider.name)
    put("apiKey", apiKey)
    put("modelName", modelName)
    put("isEnabled", isEnabled)
    put("type", type.name)
}

internal fun JSONArray?.toAiConfigsFromBackup(): List<AiModelConfig> {
    if (this == null) return emptyList()
    val result = mutableListOf<AiModelConfig>()
    for (index in 0 until length()) {
        val item = optJSONObject(index) ?: continue
        val name = item.optString("name", "").trim()
        val apiKey = item.optString("apiKey", "").trim()
        val modelName = item.optString("modelName", "").trim()
        if (name.isBlank() || apiKey.isBlank() || modelName.isBlank()) continue
        val provider = runCatching { AiProvider.valueOf(item.optString("provider")) }.getOrNull() ?: continue
        val type = runCatching { AiModelType.valueOf(item.optString("type")) }.getOrNull() ?: continue
        result.add(
            AiModelConfig(
                name = name,
                provider = provider,
                apiKey = apiKey,
                modelName = modelName,
                isEnabled = item.optBoolean("isEnabled", true),
                type = type
            )
        )
    }
    return result
}

internal fun Source.toBackupJson(): JSONObject = JSONObject().apply {
    put("name", name)
    put("url", url)
    put("type", type.name)
    put("isEnabled", isEnabled)
    put("footerPattern", footerPattern)
    put("titleSelector", titleSelector)
    put("postLinkSelector", postLinkSelector)
    put("descriptionSelector", descriptionSelector)
    put("dateSelector", dateSelector)
    put("useHeadlessBrowser", useHeadlessBrowser)
}

internal fun JSONArray?.toImportedGroupsFromBackup(): List<ImportedSourceGroup> {
    if (this == null) return emptyList()
    val groups = mutableListOf<ImportedSourceGroup>()
    for (index in 0 until length()) {
        val item = optJSONObject(index) ?: continue
        val name = item.optString("name", "").trim()
        if (name.isBlank()) continue
        groups.add(
            ImportedSourceGroup(
                name = name,
                isEnabled = item.optBoolean("isEnabled", true),
                isDeletable = item.optBoolean("isDeletable", true),
                sources = item.optJSONArray("sources").toImportedSourcesFromBackup()
            )
        )
    }
    return groups
}

internal fun JSONArray?.toImportedSourcesFromBackup(): List<ImportedSource> {
    if (this == null) return emptyList()
    val sources = mutableListOf<ImportedSource>()
    for (index in 0 until length()) {
        val item = optJSONObject(index) ?: continue
        val name = item.optString("name", "").trim()
        val url = item.optString("url", "").trim()
        val type = runCatching {
            SourceType.valueOf(item.optString("type"))
        }.getOrNull() ?: continue
        if (name.isBlank() || url.isBlank()) continue
        sources.add(
            ImportedSource(
                name = name,
                url = url,
                type = type,
                isEnabled = item.optBoolean("isEnabled", true),
                footerPattern = item.optString("footerPattern", "").takeIf { it.isNotBlank() },
                titleSelector = item.optString("titleSelector", "").takeIf { it.isNotBlank() },
                postLinkSelector = item.optString("postLinkSelector", "").takeIf { it.isNotBlank() },
                descriptionSelector = item.optString("descriptionSelector", "").takeIf { it.isNotBlank() },
                dateSelector = item.optString("dateSelector", "").takeIf { it.isNotBlank() },
                useHeadlessBrowser = item.optBoolean("useHeadlessBrowser", false)
            )
        )
    }
    return sources
}








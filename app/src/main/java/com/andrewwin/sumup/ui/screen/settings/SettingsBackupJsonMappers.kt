package com.andrewwin.sumup.ui.screen.settings

import com.andrewwin.sumup.data.local.entities.AiModelConfig
import com.andrewwin.sumup.data.local.entities.AiConfigPriority
import com.andrewwin.sumup.data.local.entities.AiModelType
import com.andrewwin.sumup.data.local.entities.AiProvider
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.AppLanguage
import com.andrewwin.sumup.data.local.entities.AppThemeMode
import com.andrewwin.sumup.data.local.entities.DeduplicationStrategy
import com.andrewwin.sumup.data.local.entities.SavedArticle
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.local.entities.SummaryLanguage
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.data.security.SecretEncryptionManager
import com.andrewwin.sumup.domain.repository.ImportedSource
import com.andrewwin.sumup.domain.repository.ImportedSourceGroup
import org.json.JSONArray
import org.json.JSONObject

private fun JSONObject.optNullableString(name: String): String? {
    if (isNull(name)) return null
    val value = optString(name, "").trim()
    if (value.isBlank()) return null
    if (value.equals("null", ignoreCase = true)) return null
    return value
}

private fun parseDeduplicationStrategyOrDefault(
    rawValue: String?,
    defaultValue: DeduplicationStrategy
): DeduplicationStrategy {
    return when (rawValue?.uppercase()) {
        DeduplicationStrategy.LOCAL.name -> DeduplicationStrategy.LOCAL
        DeduplicationStrategy.CLOUD.name -> DeduplicationStrategy.CLOUD
        "ADAPTIVE" -> DeduplicationStrategy.CLOUD
        else -> defaultValue
    }
}

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
    put("adaptiveExtractiveHighCompressionAboveChars", adaptiveExtractiveHighCompressionAboveChars)
    put("adaptiveExtractiveCompressionPercentMedium", adaptiveExtractiveCompressionPercentMedium)
    put("adaptiveExtractiveCompressionPercentHigh", adaptiveExtractiveCompressionPercentHigh)
    put("summaryItemsPerNewsInFeed", summaryItemsPerNewsInFeed)
    put("summaryItemsPerNewsInScheduled", summaryItemsPerNewsInScheduled)
    put("summaryNewsInFeedExtractive", summaryNewsInFeedExtractive)
    put("summaryNewsInFeedCloud", summaryNewsInFeedCloud)
    put("summaryNewsInScheduledExtractive", summaryNewsInScheduledExtractive)
    put("summaryNewsInScheduledCloud", summaryNewsInScheduledCloud)
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
    put("articleAutoCleanupDays", articleAutoCleanupDays)
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
        deduplicationStrategy = parseDeduplicationStrategyOrDefault(
            rawValue = optString("deduplicationStrategy", defaults.deduplicationStrategy.name),
            defaultValue = defaults.deduplicationStrategy
        ),
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
        adaptiveExtractiveHighCompressionAboveChars = optInt(
            "adaptiveExtractiveHighCompressionAboveChars",
            optInt(
                "adaptiveExtractiveCompressAboveChars",
                defaults.adaptiveExtractiveHighCompressionAboveChars
            )
        ),
        adaptiveExtractiveCompressionPercentMedium = optInt(
            "adaptiveExtractiveCompressionPercentMedium",
            optInt(
                "adaptiveExtractiveCompressionPercent",
                defaults.adaptiveExtractiveCompressionPercentMedium
            )
        ),
        adaptiveExtractiveCompressionPercentHigh = optInt(
            "adaptiveExtractiveCompressionPercentHigh",
            defaults.adaptiveExtractiveCompressionPercentHigh
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
        articleAutoCleanupDays = optInt("articleAutoCleanupDays", defaults.articleAutoCleanupDays),
        appThemeMode = runCatching { AppThemeMode.valueOf(optString("appThemeMode", defaults.appThemeMode.name)) }
            .getOrDefault(defaults.appThemeMode),
        appLanguage = runCatching { AppLanguage.valueOf(optString("appLanguage", defaults.appLanguage.name)) }
            .getOrDefault(defaults.appLanguage),
        summaryLanguage = runCatching {
            when (optString("summaryLanguage", defaults.summaryLanguage.name)) {
                "ORIGINAL" -> when (
                    runCatching {
                        AppLanguage.valueOf(optString("appLanguage", defaults.appLanguage.name))
                    }.getOrDefault(defaults.appLanguage)
                ) {
                    AppLanguage.EN -> SummaryLanguage.EN
                    AppLanguage.UK -> SummaryLanguage.UK
                }

                else -> SummaryLanguage.valueOf(optString("summaryLanguage", defaults.summaryLanguage.name))
            }
        }.getOrDefault(defaults.summaryLanguage)
    )
}

internal fun AiModelConfig.toBackupJson(
    secretEncryptionManager: SecretEncryptionManager,
    syncPassphrase: String
): JSONObject = JSONObject().apply {
    put("name", name)
    put("provider", provider.name)
    put("apiKeyCiphertext", secretEncryptionManager.encryptForSync(apiKey, syncPassphrase))
    put("modelName", modelName)
    put("isEnabled", isEnabled)
    put("type", type.name)
    put("priority", priority.name)
    put("isUseNow", isUseNow)
}

internal fun AiModelConfig.Companion.fromBackupJson(
    item: JSONObject,
    secretEncryptionManager: SecretEncryptionManager,
    syncPassphrase: String?
): AiModelConfig {
    val name = item.optString("name", "").trim()
    val modelName = item.optString("modelName", "").trim()
    val encryptedApiKey = item.optString("apiKeyCiphertext", "").trim()
    val apiKey = when {
        encryptedApiKey.isNotBlank() -> {
            val passphrase = syncPassphrase?.trim().orEmpty()
            require(passphrase.isNotBlank()) { "Sync passphrase is required to import API keys." }
            secretEncryptionManager.decryptFromSync(encryptedApiKey, passphrase).trim()
        }
        else -> item.optString("apiKey", "").trim()
    }
    require(name.isNotBlank() && apiKey.isNotBlank() && modelName.isNotBlank()) { "Invalid AI config in backup." }
    val provider = runCatching { AiProvider.valueOf(item.optString("provider")) }.getOrThrow()
    val type = runCatching { AiModelType.valueOf(item.optString("type")) }.getOrThrow()
    val priority = runCatching {
        AiConfigPriority.valueOf(item.optString("priority", AiConfigPriority.MEDIUM.name))
    }.getOrDefault(AiConfigPriority.MEDIUM)
    
    return AiModelConfig(
        name = name,
        provider = provider,
        apiKey = apiKey,
        modelName = modelName,
        isEnabled = item.optBoolean("isEnabled", true),
        type = type,
        priority = priority,
        isUseNow = item.optBoolean("isUseNow", false)
    )
}

internal fun JSONArray?.toAiConfigsFromBackup(
    secretEncryptionManager: SecretEncryptionManager,
    syncPassphrase: String?
): List<AiModelConfig> {
    if (this == null) return emptyList()
    val result = mutableListOf<AiModelConfig>()
    for (index in 0 until length()) {
        val item = optJSONObject(index) ?: continue
        runCatching {
            AiModelConfig.fromBackupJson(item, secretEncryptionManager, syncPassphrase)
        }.onSuccess { result.add(it) }
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
                id = name.lowercase(),
                name = name,
                nameUk = name,
                nameEn = name,
                isEnabled = item.optBoolean("isEnabled", true),
                isDeletable = item.optBoolean("isDeletable", true),
                sources = item.optJSONArray("sources").toImportedSourcesFromBackup(),
                recommendationAnchors = item.optJSONArray("anchors").toStringListFromBackup(),
                sortOrder = item.optInt("sortOrder", 0)
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

private fun JSONArray?.toStringListFromBackup(): List<String> {
    if (this == null) return emptyList()
    val items = mutableListOf<String>()
    for (index in 0 until length()) {
        val value = optString(index, "").trim()
        if (value.isNotBlank()) items.add(value)
    }
    return items
}

internal fun SavedArticle.toBackupJson(): JSONObject = JSONObject().apply {
    put("url", url)
    put("title", title)
    put("content", content)
    put("mediaUrl", mediaUrl ?: JSONObject.NULL)
    put("videoId", videoId ?: JSONObject.NULL)
    put("publishedAt", publishedAt)
    put("viewCount", viewCount)
    put("sourceName", sourceName ?: JSONObject.NULL)
    put("groupName", groupName ?: JSONObject.NULL)
    put("savedAt", savedAt)
    put("clusterKey", clusterKey ?: JSONObject.NULL)
    put("clusterScore", clusterScore.toDouble())
}

internal fun JSONArray?.toSavedArticlesFromBackup(): List<SavedArticle> {
    if (this == null) return emptyList()
    val items = mutableListOf<SavedArticle>()
    for (index in 0 until length()) {
        val obj = optJSONObject(index) ?: continue
        val url = obj.optString("url", "").trim()
        if (url.isBlank()) continue
        val title = obj.optString("title", "").trim()
        val content = obj.optString("content", "").trim()
        items.add(
            SavedArticle(
                url = url,
                title = title.ifBlank { url },
                content = content.ifBlank { title.ifBlank { url } },
                mediaUrl = obj.optNullableString("mediaUrl"),
                videoId = obj.optNullableString("videoId"),
                publishedAt = obj.optLong("publishedAt", System.currentTimeMillis()),
                viewCount = obj.optLong("viewCount", 0L),
                sourceName = obj.optNullableString("sourceName"),
                groupName = obj.optNullableString("groupName"),
                savedAt = obj.optLong("savedAt", System.currentTimeMillis()),
                clusterKey = obj.optNullableString("clusterKey"),
                clusterScore = obj.optDouble("clusterScore", 0.0).toFloat()
            )
        )
    }
    return items
}







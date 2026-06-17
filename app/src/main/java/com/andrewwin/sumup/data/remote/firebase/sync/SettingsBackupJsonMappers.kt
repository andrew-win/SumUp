package com.andrewwin.sumup.data.remote.firebase.sync

import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.AppLanguage
import com.andrewwin.sumup.data.local.entities.AppThemeMode
import com.andrewwin.sumup.data.local.entities.DeduplicationStrategy
import com.andrewwin.sumup.data.local.entities.SavedArticle
import com.andrewwin.sumup.data.local.entities.ScheduledSummaryTime
import com.andrewwin.sumup.data.local.entities.SummaryLanguage
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.data.local.entities.toScheduledSummaryTimes
import com.andrewwin.sumup.data.local.entities.toScheduledSummaryTimesStorageValue
import com.andrewwin.sumup.data.security.SecretEncryptionManager
import com.andrewwin.sumup.domain.ai.model.AiModelConfig
import com.andrewwin.sumup.domain.ai.model.AiModelType
import com.andrewwin.sumup.domain.ai.model.AiProvider
import com.andrewwin.sumup.domain.article.model.SavedArticleSnapshot
import com.andrewwin.sumup.domain.source.repository.ImportedSource
import com.andrewwin.sumup.domain.source.repository.ImportedSourceGroup
import com.andrewwin.sumup.domain.source.model.Source
import com.andrewwin.sumup.domain.source.model.SourceGroupOrigin
import com.andrewwin.sumup.domain.source.model.SourceType
import org.json.JSONArray
import org.json.JSONObject

private fun JSONObject.optNullableString(name: String): String? {
    if (isNull(name)) return null
    val value = optString(name, EMPTY_STRING).trim()
    if (value.isBlank() || value.equals(NULL_STRING, ignoreCase = true)) return null
    return value
}

private fun parseDeduplicationStrategyOrDefault(
    rawValue: String?,
    defaultValue: DeduplicationStrategy
): DeduplicationStrategy {
    return when (rawValue?.uppercase()) {
        DeduplicationStrategy.LOCAL.name -> DeduplicationStrategy.LOCAL
        DeduplicationStrategy.CLOUD.name -> DeduplicationStrategy.CLOUD
        LEGACY_DEDUPLICATION_STRATEGY_ADAPTIVE -> DeduplicationStrategy.CLOUD
        else -> defaultValue
    }
}

private fun JSONObject.optArticleAutoCleanupHours(defaultValue: Int): Int {
    if (has(KEY_ARTICLE_AUTO_CLEANUP_HOURS)) {
        return optInt(KEY_ARTICLE_AUTO_CLEANUP_HOURS, defaultValue).coerceIn(
            UserPreferences.MIN_ARTICLE_AUTO_CLEANUP_HOURS,
            UserPreferences.MAX_ARTICLE_AUTO_CLEANUP_HOURS
        )
    }
    if (has(KEY_ARTICLE_AUTO_CLEANUP_DAYS)) {
        return (optInt(KEY_ARTICLE_AUTO_CLEANUP_DAYS, LEGACY_AUTO_CLEANUP_DAYS_DEFAULT) * HOURS_PER_DAY).coerceIn(
            UserPreferences.MIN_ARTICLE_AUTO_CLEANUP_HOURS,
            UserPreferences.MAX_ARTICLE_AUTO_CLEANUP_HOURS
        )
    }
    return defaultValue
}

private fun JSONObject.optScheduledSummaryTimes(defaults: UserPreferences): String {
    if (has(KEY_SCHEDULED_SUMMARY_TIMES)) {
        return optString(KEY_SCHEDULED_SUMMARY_TIMES, defaults.scheduledSummaryTimes)
            .toScheduledSummaryTimes()
            .toScheduledSummaryTimesStorageValue()
    }
    val legacyTime = ScheduledSummaryTime(
        hour = optInt(KEY_SCHEDULED_HOUR, defaults.scheduledHour),
        minute = optInt(KEY_SCHEDULED_MINUTE, defaults.scheduledMinute)
    )
    return listOf(legacyTime).toScheduledSummaryTimesStorageValue().ifBlank {
        defaults.scheduledSummaryTimes
    }
}

fun UserPreferences.toBackupJson(): JSONObject = JSONObject().apply {
    put(KEY_ID, id)
    put(KEY_AI_STRATEGY, aiStrategy.name)
    put(KEY_IS_SCHEDULED_SUMMARY_ENABLED, isScheduledSummaryEnabled)
    put(KEY_IS_SCHEDULED_SUMMARY_PUSH_ENABLED, isScheduledSummaryPushEnabled)
    put(KEY_SCHEDULED_HOUR, scheduledHour)
    put(KEY_SCHEDULED_MINUTE, scheduledMinute)
    put(KEY_SCHEDULED_SUMMARY_TIMES, scheduledSummaryTimeList.toScheduledSummaryTimesStorageValue())
    put(KEY_LAST_WORK_RUN_TIMESTAMP, lastWorkRunTimestamp)
    put(KEY_IS_DEDUPLICATION_ENABLED, isDeduplicationEnabled)
    put(KEY_DEDUPLICATION_STRATEGY, deduplicationStrategy.name)
    put(KEY_LOCAL_DEDUPLICATION_THRESHOLD, localDeduplicationThreshold.toDouble())
    put(KEY_CLOUD_DEDUPLICATION_THRESHOLD, cloudDeduplicationThreshold.toDouble())
    put(KEY_MIN_MENTIONS, minMentions)
    put(KEY_IS_HIDE_SINGLE_NEWS_ENABLED, isHideSingleNewsEnabled)
    put(KEY_MODEL_PATH, JSONObject.NULL)
    put(KEY_IS_IMPORTANCE_FILTER_ENABLED, isImportanceFilterEnabled)
    put(KEY_IS_ADAPTIVE_EXTRACTIVE_PREPROCESSING_ENABLED, isAdaptiveExtractivePreprocessingEnabled)
    put(KEY_ADAPTIVE_EXTRACTIVE_ONLY_BELOW_CHARS, adaptiveExtractiveOnlyBelowChars)
    put(KEY_ADAPTIVE_EXTRACTIVE_HIGH_COMPRESSION_ABOVE_CHARS, adaptiveExtractiveHighCompressionAboveChars)
    put(KEY_ADAPTIVE_EXTRACTIVE_COMPRESSION_PERCENT_FIRST, adaptiveExtractiveCompressionPercentFirst)
    put(KEY_ADAPTIVE_EXTRACTIVE_COMPRESSION_PERCENT_MEDIUM, adaptiveExtractiveCompressionPercentMedium)
    put(KEY_ADAPTIVE_EXTRACTIVE_COMPRESSION_PERCENT_HIGH, adaptiveExtractiveCompressionPercentHigh)
    put(KEY_SUMMARY_ITEMS_PER_NEWS_IN_FEED, summaryItemsPerNewsInFeed)
    put(KEY_SUMMARY_ITEMS_PER_NEWS_IN_SCHEDULED, summaryItemsPerNewsInScheduled)
    put(KEY_SUMMARY_NEWS_IN_FEED_CLOUD, summaryNewsInFeedCloud)
    put(KEY_SUMMARY_NEWS_IN_SCHEDULED_CLOUD, summaryNewsInScheduledCloud)
    put(KEY_EXTRACTIVE_NEWS_IN_FEED, extractiveNewsInFeed)
    put(KEY_EXTRACTIVE_SENTENCES_IN_SCHEDULED, extractiveSentencesInScheduled)
    put(KEY_EXTRACTIVE_NEWS_IN_SCHEDULED, extractiveNewsInScheduled)
    put(KEY_SHOW_LAST_SUMMARIES_COUNT, showLastSummariesCount)
    put(KEY_SHOW_INFOGRAPHIC_NEWS_COUNT, showInfographicNewsCount)
    put(KEY_AI_MAX_CHARS_SINGLE_ARTICLE, aiMaxCharsSingleArticle)
    put(KEY_AI_MAX_CHARS_NEWS_CLUSTER, aiMaxCharsNewsCluster)
    put(KEY_AI_MAX_CHARS_SINGLE_FEED_ARTICLE, aiMaxCharsSingleFeedArticle)
    put(KEY_AI_MAX_CHARS_FEED_CLUSTER, aiMaxCharsFeedCluster)
    put(KEY_AI_MAX_CHARS_TOTAL, aiMaxCharsTotal)
    put(KEY_SUMMARY_PROMPT, summaryPrompt)
    put(KEY_IS_CUSTOM_SUMMARY_PROMPT_ENABLED, isCustomSummaryPromptEnabled)
    put(KEY_IS_FEED_MEDIA_ENABLED, isFeedMediaEnabled)
    put(KEY_IS_FEED_DESCRIPTION_ENABLED, isFeedDescriptionEnabled)
    put(KEY_IS_FEED_SUMMARY_USE_FULL_TEXT_ENABLED, isFeedSummaryUseFullTextEnabled)
    put(KEY_IS_FEED_TITLE_EXCLUDE_REGEX_ENABLED, isFeedTitleExcludeRegexEnabled)
    put(KEY_FEED_TITLE_EXCLUDE_REGEX, feedTitleExcludeRegex)
    put(KEY_IS_RECOMMENDATIONS_ENABLED, isRecommendationsEnabled)
    put(KEY_ARTICLE_AUTO_CLEANUP_HOURS, articleAutoCleanupHours)
    put(KEY_APP_THEME_MODE, appThemeMode.name)
    put(KEY_APP_LANGUAGE, appLanguage.name)
    put(KEY_SUMMARY_LANGUAGE, summaryLanguage.name)
}

fun JSONObject.toUserPreferencesFromBackup(): UserPreferences {
    val defaults = UserPreferences()
    return UserPreferences(
        id = optInt("id", defaults.id),
        aiStrategy = runCatching { AiStrategy.valueOf(optString("aiStrategy", defaults.aiStrategy.name)) }
            .getOrDefault(defaults.aiStrategy),
        isScheduledSummaryEnabled = optBoolean("isScheduledSummaryEnabled", defaults.isScheduledSummaryEnabled),
        isScheduledSummaryPushEnabled = optBoolean("isScheduledSummaryPushEnabled", defaults.isScheduledSummaryPushEnabled),
        scheduledHour = optInt("scheduledHour", defaults.scheduledHour),
        scheduledMinute = optInt("scheduledMinute", defaults.scheduledMinute),
        scheduledSummaryTimes = optScheduledSummaryTimes(defaults),
        lastWorkRunTimestamp = optLong("lastWorkRunTimestamp", defaults.lastWorkRunTimestamp),
        isDeduplicationEnabled = optBoolean("isDeduplicationEnabled", defaults.isDeduplicationEnabled),
        deduplicationStrategy = parseDeduplicationStrategyOrDefault(
            rawValue = optString("deduplicationStrategy", defaults.deduplicationStrategy.name),
            defaultValue = defaults.deduplicationStrategy
        ),
        localDeduplicationThreshold = optDouble("localDeduplicationThreshold", defaults.localDeduplicationThreshold.toDouble()).toFloat(),
        cloudDeduplicationThreshold = optDouble("cloudDeduplicationThreshold", defaults.cloudDeduplicationThreshold.toDouble()).toFloat(),
        minMentions = optInt("minMentions", defaults.minMentions),
        isHideSingleNewsEnabled = optBoolean("isHideSingleNewsEnabled", defaults.isHideSingleNewsEnabled),
        modelPath = null,
        isImportanceFilterEnabled = optBoolean("isImportanceFilterEnabled", defaults.isImportanceFilterEnabled),
        isAdaptiveExtractivePreprocessingEnabled = optBoolean(
            "isAdaptiveExtractivePreprocessingEnabled",
            defaults.isAdaptiveExtractivePreprocessingEnabled
        ),
        adaptiveExtractiveOnlyBelowChars = optInt("adaptiveExtractiveOnlyBelowChars", defaults.adaptiveExtractiveOnlyBelowChars),
        adaptiveExtractiveHighCompressionAboveChars = optInt(
            "adaptiveExtractiveHighCompressionAboveChars",
            optInt("adaptiveExtractiveCompressAboveChars", defaults.adaptiveExtractiveHighCompressionAboveChars)
        ),
        adaptiveExtractiveCompressionPercentFirst = optInt(
            "adaptiveExtractiveCompressionPercentFirst",
            defaults.adaptiveExtractiveCompressionPercentFirst
        ),
        adaptiveExtractiveCompressionPercentMedium = optInt(
            "adaptiveExtractiveCompressionPercentMedium",
            optInt("adaptiveExtractiveCompressionPercent", defaults.adaptiveExtractiveCompressionPercentMedium)
        ),
        adaptiveExtractiveCompressionPercentHigh = optInt(
            "adaptiveExtractiveCompressionPercentHigh",
            defaults.adaptiveExtractiveCompressionPercentHigh
        ),
        summaryItemsPerNewsInFeed = optInt("summaryItemsPerNewsInFeed", defaults.summaryItemsPerNewsInFeed),
        summaryItemsPerNewsInScheduled = optInt("summaryItemsPerNewsInScheduled", defaults.summaryItemsPerNewsInScheduled),
        summaryNewsInFeedCloud = optInt("summaryNewsInFeedCloud", defaults.summaryNewsInFeedCloud),
        summaryNewsInScheduledCloud = optInt("summaryNewsInScheduledCloud", defaults.summaryNewsInScheduledCloud),
        extractiveNewsInFeed = optInt("extractiveNewsInFeed", defaults.extractiveNewsInFeed),
        extractiveSentencesInScheduled = optInt("extractiveSentencesInScheduled", defaults.extractiveSentencesInScheduled),
        extractiveNewsInScheduled = optInt("extractiveNewsInScheduled", defaults.extractiveNewsInScheduled),
        showLastSummariesCount = optInt("showLastSummariesCount", defaults.showLastSummariesCount),
        showInfographicNewsCount = optInt("showInfographicNewsCount", defaults.showInfographicNewsCount),
        aiMaxCharsSingleArticle = optInt("aiMaxCharsSingleArticle", optInt("aiMaxCharsPerArticle", defaults.aiMaxCharsSingleArticle)),
        aiMaxCharsNewsCluster = optInt("aiMaxCharsNewsCluster", optInt("aiMaxCharsPerArticle", defaults.aiMaxCharsNewsCluster)),
        aiMaxCharsSingleFeedArticle = optInt(
            "aiMaxCharsSingleFeedArticle",
            optInt("aiMaxCharsPerFeedArticle", defaults.aiMaxCharsSingleFeedArticle)
        ),
        aiMaxCharsFeedCluster = optInt("aiMaxCharsFeedCluster", optInt("aiMaxCharsPerFeedArticle", defaults.aiMaxCharsFeedCluster)),
        aiMaxCharsTotal = optInt("aiMaxCharsTotal", defaults.aiMaxCharsTotal),
        summaryPrompt = optString("summaryPrompt", defaults.summaryPrompt),
        isCustomSummaryPromptEnabled = optBoolean("isCustomSummaryPromptEnabled", defaults.isCustomSummaryPromptEnabled),
        isFeedMediaEnabled = optBoolean("isFeedMediaEnabled", defaults.isFeedMediaEnabled),
        isFeedDescriptionEnabled = optBoolean("isFeedDescriptionEnabled", defaults.isFeedDescriptionEnabled),
        isFeedSummaryUseFullTextEnabled = optBoolean(
            "isFeedSummaryUseFullTextEnabled",
            defaults.isFeedSummaryUseFullTextEnabled
        ),
        isFeedTitleExcludeRegexEnabled = optBoolean(
            "isFeedTitleExcludeRegexEnabled",
            defaults.isFeedTitleExcludeRegexEnabled
        ),
        feedTitleExcludeRegex = optString("feedTitleExcludeRegex", defaults.feedTitleExcludeRegex),
        isRecommendationsEnabled = optBoolean("isRecommendationsEnabled", defaults.isRecommendationsEnabled),
        articleAutoCleanupHours = optArticleAutoCleanupHours(defaults.articleAutoCleanupHours),
        appThemeMode = runCatching { AppThemeMode.valueOf(optString("appThemeMode", defaults.appThemeMode.name)) }
            .getOrDefault(defaults.appThemeMode),
        appLanguage = runCatching { AppLanguage.valueOf(optString("appLanguage", defaults.appLanguage.name)) }
            .getOrDefault(defaults.appLanguage),
        summaryLanguage = runCatching {
            when (optString(KEY_SUMMARY_LANGUAGE, defaults.summaryLanguage.name)) {
                LEGACY_SUMMARY_LANGUAGE_ORIGINAL -> when (runCatching {
                    AppLanguage.valueOf(optString(KEY_APP_LANGUAGE, defaults.appLanguage.name))
                }.getOrDefault(defaults.appLanguage)) {
                    AppLanguage.EN -> SummaryLanguage.EN
                    AppLanguage.UK -> SummaryLanguage.UK
                }
                else -> SummaryLanguage.valueOf(optString(KEY_SUMMARY_LANGUAGE, defaults.summaryLanguage.name))
            }
        }.getOrDefault(defaults.summaryLanguage)
    )
}

fun AiModelConfig.toBackupJson(encryptionSession: SecretEncryptionManager.SyncEncryptionSession): JSONObject = JSONObject().apply {
    put("name", name)
    put("provider", provider.name)
    put("apiKeyCiphertext", encryptionSession.encrypt(apiKey))
    put("modelName", modelName)
    put("isEnabled", isEnabled)
    put("type", type.name)
    put("sortOrder", sortOrder)
}

fun AiModelConfig.Companion.fromBackupJson(
    item: JSONObject,
    secretEncryptionManager: SecretEncryptionManager,
    syncPassphrase: String?,
    syncSaltBase64: String? = null
): AiModelConfig {
    val name = item.optString(KEY_NAME, EMPTY_STRING).trim()
    val modelName = item.optString(KEY_MODEL_NAME, EMPTY_STRING).trim()
    val encryptedApiKey = item.optString(KEY_API_KEY_CIPHERTEXT, EMPTY_STRING).trim()
    val apiKey = when {
        encryptedApiKey.isNotBlank() -> {
            val passphrase = syncPassphrase?.trim().orEmpty()
            require(passphrase.isNotBlank()) { SYNC_PASSPHRASE_REQUIRED_MESSAGE }
            when {
                syncSaltBase64.isNullOrBlank() -> secretEncryptionManager.decryptFromSync(encryptedApiKey, passphrase).trim()
                else -> secretEncryptionManager.decryptFromSyncSession(encryptedApiKey, passphrase, syncSaltBase64).trim()
            }
        }
        else -> item.optString(KEY_API_KEY, EMPTY_STRING).trim()
    }
    require(name.isNotBlank() && apiKey.isNotBlank() && modelName.isNotBlank()) { INVALID_AI_CONFIG_MESSAGE }
    val provider = runCatching { AiProvider.valueOf(item.optString(KEY_PROVIDER)) }.getOrThrow()
    val type = runCatching { AiModelType.valueOf(item.optString(KEY_TYPE)) }.getOrThrow()
    val sortOrder = if (item.has(KEY_SORT_ORDER)) item.optInt(KEY_SORT_ORDER, Int.MAX_VALUE) else legacyApiConfigSortOrder(
        priority = item.optString(KEY_PRIORITY, LEGACY_PRIORITY_MEDIUM),
        isUseNow = item.optBoolean(KEY_IS_USE_NOW, false)
    )
    return AiModelConfig(
        name = name,
        provider = provider,
        apiKey = apiKey,
        modelName = modelName,
        isEnabled = item.optBoolean("isEnabled", true),
        type = type,
        sortOrder = sortOrder
    )
}

private fun legacyApiConfigSortOrder(priority: String, isUseNow: Boolean): Int {
    val priorityOffset = when (priority) {
        LEGACY_PRIORITY_HIGH -> 0
        LEGACY_PRIORITY_MEDIUM -> 1000
        else -> 2000
    }
    return priorityOffset - if (isUseNow) 10000 else 0
}

private const val EMPTY_STRING = ""
private const val NULL_STRING = "null"
private const val LEGACY_DEDUPLICATION_STRATEGY_ADAPTIVE = "ADAPTIVE"
private const val LEGACY_SUMMARY_LANGUAGE_ORIGINAL = "ORIGINAL"
private const val LEGACY_PRIORITY_HIGH = "HIGH"
private const val LEGACY_PRIORITY_MEDIUM = "MEDIUM"
private const val LEGACY_AUTO_CLEANUP_DAYS_DEFAULT = 1
private const val HOURS_PER_DAY = 24
private const val KEY_ID = "id"
private const val KEY_AI_STRATEGY = "aiStrategy"
private const val KEY_IS_SCHEDULED_SUMMARY_ENABLED = "isScheduledSummaryEnabled"
private const val KEY_IS_SCHEDULED_SUMMARY_PUSH_ENABLED = "isScheduledSummaryPushEnabled"
private const val KEY_SCHEDULED_HOUR = "scheduledHour"
private const val KEY_SCHEDULED_MINUTE = "scheduledMinute"
private const val KEY_SCHEDULED_SUMMARY_TIMES = "scheduledSummaryTimes"
private const val KEY_LAST_WORK_RUN_TIMESTAMP = "lastWorkRunTimestamp"
private const val KEY_IS_DEDUPLICATION_ENABLED = "isDeduplicationEnabled"
private const val KEY_DEDUPLICATION_STRATEGY = "deduplicationStrategy"
private const val KEY_LOCAL_DEDUPLICATION_THRESHOLD = "localDeduplicationThreshold"
private const val KEY_CLOUD_DEDUPLICATION_THRESHOLD = "cloudDeduplicationThreshold"
private const val KEY_MIN_MENTIONS = "minMentions"
private const val KEY_IS_HIDE_SINGLE_NEWS_ENABLED = "isHideSingleNewsEnabled"
private const val KEY_MODEL_PATH = "modelPath"
private const val KEY_IS_IMPORTANCE_FILTER_ENABLED = "isImportanceFilterEnabled"
private const val KEY_IS_ADAPTIVE_EXTRACTIVE_PREPROCESSING_ENABLED = "isAdaptiveExtractivePreprocessingEnabled"
private const val KEY_ADAPTIVE_EXTRACTIVE_ONLY_BELOW_CHARS = "adaptiveExtractiveOnlyBelowChars"
private const val KEY_ADAPTIVE_EXTRACTIVE_HIGH_COMPRESSION_ABOVE_CHARS = "adaptiveExtractiveHighCompressionAboveChars"
private const val KEY_ADAPTIVE_EXTRACTIVE_COMPRESSION_PERCENT_FIRST = "adaptiveExtractiveCompressionPercentFirst"
private const val KEY_ADAPTIVE_EXTRACTIVE_COMPRESSION_PERCENT_MEDIUM = "adaptiveExtractiveCompressionPercentMedium"
private const val KEY_ADAPTIVE_EXTRACTIVE_COMPRESSION_PERCENT_HIGH = "adaptiveExtractiveCompressionPercentHigh"
private const val KEY_SUMMARY_ITEMS_PER_NEWS_IN_FEED = "summaryItemsPerNewsInFeed"
private const val KEY_SUMMARY_ITEMS_PER_NEWS_IN_SCHEDULED = "summaryItemsPerNewsInScheduled"
private const val KEY_SUMMARY_NEWS_IN_FEED_CLOUD = "summaryNewsInFeedCloud"
private const val KEY_SUMMARY_NEWS_IN_SCHEDULED_CLOUD = "summaryNewsInScheduledCloud"
private const val KEY_EXTRACTIVE_NEWS_IN_FEED = "extractiveNewsInFeed"
private const val KEY_EXTRACTIVE_SENTENCES_IN_SCHEDULED = "extractiveSentencesInScheduled"
private const val KEY_EXTRACTIVE_NEWS_IN_SCHEDULED = "extractiveNewsInScheduled"
private const val KEY_SHOW_LAST_SUMMARIES_COUNT = "showLastSummariesCount"
private const val KEY_SHOW_INFOGRAPHIC_NEWS_COUNT = "showInfographicNewsCount"
private const val KEY_AI_MAX_CHARS_SINGLE_ARTICLE = "aiMaxCharsSingleArticle"
private const val KEY_AI_MAX_CHARS_NEWS_CLUSTER = "aiMaxCharsNewsCluster"
private const val KEY_AI_MAX_CHARS_SINGLE_FEED_ARTICLE = "aiMaxCharsSingleFeedArticle"
private const val KEY_AI_MAX_CHARS_FEED_CLUSTER = "aiMaxCharsFeedCluster"
private const val KEY_AI_MAX_CHARS_TOTAL = "aiMaxCharsTotal"
private const val KEY_SUMMARY_PROMPT = "summaryPrompt"
private const val KEY_IS_CUSTOM_SUMMARY_PROMPT_ENABLED = "isCustomSummaryPromptEnabled"
private const val KEY_IS_FEED_MEDIA_ENABLED = "isFeedMediaEnabled"
private const val KEY_IS_FEED_DESCRIPTION_ENABLED = "isFeedDescriptionEnabled"
private const val KEY_IS_FEED_SUMMARY_USE_FULL_TEXT_ENABLED = "isFeedSummaryUseFullTextEnabled"
private const val KEY_IS_FEED_TITLE_EXCLUDE_REGEX_ENABLED = "isFeedTitleExcludeRegexEnabled"
private const val KEY_FEED_TITLE_EXCLUDE_REGEX = "feedTitleExcludeRegex"
private const val KEY_IS_RECOMMENDATIONS_ENABLED = "isRecommendationsEnabled"
private const val KEY_ARTICLE_AUTO_CLEANUP_HOURS = "articleAutoCleanupHours"
private const val KEY_ARTICLE_AUTO_CLEANUP_DAYS = "articleAutoCleanupDays"
private const val KEY_APP_THEME_MODE = "appThemeMode"
private const val KEY_APP_LANGUAGE = "appLanguage"
private const val KEY_SUMMARY_LANGUAGE = "summaryLanguage"
private const val KEY_NAME = "name"
private const val KEY_PROVIDER = "provider"
private const val KEY_API_KEY_CIPHERTEXT = "apiKeyCiphertext"
private const val KEY_API_KEY = "apiKey"
private const val KEY_MODEL_NAME = "modelName"
private const val KEY_TYPE = "type"
private const val KEY_SORT_ORDER = "sortOrder"
private const val KEY_PRIORITY = "priority"
private const val KEY_IS_USE_NOW = "isUseNow"
private const val SYNC_PASSPHRASE_REQUIRED_MESSAGE = "Sync passphrase is required to import API keys."
private const val INVALID_AI_CONFIG_MESSAGE = "Invalid AI config in backup."

fun JSONArray?.toAiConfigsFromBackup(
    secretEncryptionManager: SecretEncryptionManager,
    syncPassphrase: String?,
    syncSaltBase64: String? = null
): List<AiModelConfig> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            runCatching {
                AiModelConfig.fromBackupJson(item, secretEncryptionManager, syncPassphrase, syncSaltBase64)
            }.onSuccess(::add)
        }
    }
}

fun Source.toBackupJson(): JSONObject = JSONObject().apply {
    put("name", name)
    put("url", url)
    put("type", type.name)
    put("isEnabled", isEnabled)
    put("footerPattern", footerPattern)
    put("footerPatternCheckedAt", footerPatternCheckedAt)
    put("titleSelector", titleSelector)
    put("postLinkSelector", postLinkSelector)
    put("descriptionSelector", descriptionSelector)
    put("dateSelector", dateSelector)
    put("useHeadlessBrowser", useHeadlessBrowser)
}

fun JSONArray?.toImportedGroupsFromBackup(): List<ImportedSourceGroup> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val name = item.optString("name", "").trim()
            if (name.isBlank()) continue
            val isDeletable = item.optBoolean("isDeletable", true)
            val fallbackOrigin = if (isDeletable) SourceGroupOrigin.USER else SourceGroupOrigin.SYSTEM
            val rawOrigin = item.optString("origin", fallbackOrigin.name).trim()
            val origin = when (rawOrigin) {
                SourceGroupOrigin.USER.name -> SourceGroupOrigin.USER
                SourceGroupOrigin.PUBLIC_SUBSCRIPTION.name -> SourceGroupOrigin.PUBLIC_SUBSCRIPTION
                SourceGroupOrigin.SYSTEM.name -> SourceGroupOrigin.SYSTEM
                else -> fallbackOrigin
            }
            val subscriptionId = item.optNullableString("subscriptionId")
            add(
                ImportedSourceGroup(
                    id = subscriptionId ?: name.lowercase(),
                    name = name,
                    nameUk = name,
                    nameEn = name,
                    isEnabled = item.optBoolean("isEnabled", true),
                    isDeletable = isDeletable,
                    origin = origin,
                    subscriptionId = subscriptionId,
                    sources = item.optJSONArray("sources").toImportedSourcesFromBackup(),
                    recommendationAnchors = item.optJSONArray("anchors").toStringListFromBackup(),
                    sortOrder = item.optInt("sortOrder", 0)
                )
            )
        }
    }
}

private fun JSONArray?.toImportedSourcesFromBackup(): List<ImportedSource> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val name = item.optString("name", "").trim()
            val url = item.optString("url", "").trim()
            val type = runCatching { SourceType.valueOf(item.optString("type")) }.getOrNull() ?: continue
            if (name.isBlank() || url.isBlank()) continue
            add(
                ImportedSource(
                    name = name,
                    url = url,
                    type = type,
                    isEnabled = item.optBoolean("isEnabled", true),
                    footerPattern = item.optString("footerPattern", "").takeIf { it.isNotBlank() },
                    footerPatternCheckedAt = item.optLong("footerPatternCheckedAt", 0L),
                    titleSelector = item.optString("titleSelector", "").takeIf { it.isNotBlank() },
                    postLinkSelector = item.optString("postLinkSelector", "").takeIf { it.isNotBlank() },
                    descriptionSelector = item.optString("descriptionSelector", "").takeIf { it.isNotBlank() },
                    dateSelector = item.optString("dateSelector", "").takeIf { it.isNotBlank() },
                    useHeadlessBrowser = item.optBoolean("useHeadlessBrowser", false)
                )
            )
        }
    }
}

private fun JSONArray?.toStringListFromBackup(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            optString(index, "").trim().takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}

fun SavedArticle.toBackupJson(): JSONObject = JSONObject().apply {
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

fun SavedArticleSnapshot.toBackupJson(): JSONObject = JSONObject().apply {
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

fun JSONArray?.toSavedArticlesFromBackup(): List<SavedArticleSnapshot> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val obj = optJSONObject(index) ?: continue
            val url = obj.optString("url", "").trim()
            if (url.isBlank()) continue
            val title = obj.optString("title", "").trim()
            val content = obj.optString("content", "").trim()
            add(
                SavedArticleSnapshot(
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
    }
}

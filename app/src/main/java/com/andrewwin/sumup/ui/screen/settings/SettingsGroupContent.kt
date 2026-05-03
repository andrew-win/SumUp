package com.andrewwin.sumup.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.AiModelConfig
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.AppLanguage
import com.andrewwin.sumup.data.local.entities.AppThemeMode
import com.andrewwin.sumup.data.local.entities.SummaryLanguage
import com.andrewwin.sumup.data.local.entities.UserPreferences
import java.util.Locale
import kotlin.math.roundToInt

private const val DEDUPLICATION_THRESHOLD_MIN = 0.7f
private const val DEDUPLICATION_THRESHOLD_MAX = 1.0f
private const val DEDUPLICATION_THRESHOLD_STEP = 0.005f
private const val DEDUPLICATION_THRESHOLD_SLIDER_STEPS = 59

@Composable
internal fun SettingsHomeGroupsContent(
    isHelpMode: Boolean,
    onGroupClick: (SettingsGroup) -> Unit,
    onHelpRequest: (SettingsGroup) -> Unit,
    helpDescriptionForGroup: (SettingsGroup) -> String
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsHomeSection(
            title = stringResource(R.string.settings_section_account),
            groups = listOf(SettingsGroup.ACCOUNT),
            isHelpMode = isHelpMode,
            onGroupClick = onGroupClick,
            onHelpRequest = onHelpRequest,
            helpDescriptionForGroup = helpDescriptionForGroup
        )
        SettingsHomeSection(
            title = stringResource(R.string.settings_section_content_ai),
            groups = listOf(SettingsGroup.AI_PROCESSING, SettingsGroup.API_KEYS, SettingsGroup.RECOMMENDATIONS),
            isHelpMode = isHelpMode,
            onGroupClick = onGroupClick,
            onHelpRequest = onHelpRequest,
            helpDescriptionForGroup = helpDescriptionForGroup
        )
        SettingsHomeSection(
            title = stringResource(R.string.settings_section_interface),
            groups = listOf(
                SettingsGroup.FEED,
                SettingsGroup.SCHEDULED_SUMMARY,
                SettingsGroup.GENERAL,
                SettingsGroup.MEMORY
            ),
            isHelpMode = isHelpMode,
            onGroupClick = onGroupClick,
            onHelpRequest = onHelpRequest,
            helpDescriptionForGroup = helpDescriptionForGroup
        )
    }
}

@Composable
private fun SettingsHomeSection(
    title: String,
    groups: List<SettingsGroup>,
    isHelpMode: Boolean,
    onGroupClick: (SettingsGroup) -> Unit,
    onHelpRequest: (SettingsGroup) -> Unit,
    helpDescriptionForGroup: (SettingsGroup) -> String
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
        )
        SettingsGroupsPanel(
            groups = groups,
            isHelpMode = isHelpMode,
            onGroupClick = onGroupClick,
            onHelpRequest = onHelpRequest,
            helpDescriptionForGroup = helpDescriptionForGroup
        )
    }
}

@Composable
internal fun SettingsGeneralGroupContent(
    isHelpMode: Boolean,
    userPreferences: UserPreferences,
    onHelpRequest: (String) -> Unit,
    onAppLanguageChange: (AppLanguage) -> Unit,
    onSummaryLanguageChange: (SummaryLanguage) -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsSection(
            title = stringResource(R.string.settings_language),
            boxed = true,
            isHelpMode = isHelpMode,
            helpDescription = stringResource(R.string.settings_help_section_app_language),
            onHelpRequest = onHelpRequest
        ) {
            val languages = listOf(
                AppLanguage.UK to R.string.settings_language_uk,
                AppLanguage.EN to R.string.settings_language_en
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                languages.forEachIndexed { index, (lang, labelRes) ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = languages.size),
                        onClick = { onAppLanguageChange(lang) },
                        selected = userPreferences.appLanguage == lang
                    ) {
                        Text(text = stringResource(labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        SettingsSection(
            title = stringResource(R.string.settings_summary_language),
            boxed = true,
            isHelpMode = isHelpMode,
            helpDescription = stringResource(R.string.settings_help_section_summary_language),
            onHelpRequest = onHelpRequest
        ) {
            val summaryLanguages = listOf(
                SummaryLanguage.UK to R.string.settings_summary_language_uk,
                SummaryLanguage.EN to R.string.settings_summary_language_en
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                summaryLanguages.forEachIndexed { index, (lang, labelRes) ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = summaryLanguages.size),
                        onClick = { onSummaryLanguageChange(lang) },
                        selected = userPreferences.summaryLanguage == lang
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        SettingsSection(
            title = stringResource(R.string.settings_theme),
            boxed = true,
            isHelpMode = isHelpMode,
            helpDescription = stringResource(R.string.settings_help_section_theme),
            onHelpRequest = onHelpRequest
        ) {
            val themeModes = listOf(
                AppThemeMode.SYSTEM to R.string.settings_theme_system,
                AppThemeMode.LIGHT to R.string.settings_theme_light,
                AppThemeMode.DARK to R.string.settings_theme_dark
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                themeModes.forEachIndexed { index, (mode, labelRes) ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = themeModes.size),
                        onClick = { onThemeModeChange(mode) },
                        selected = userPreferences.appThemeMode == mode
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingsApiKeysGroupContent(
    isHelpMode: Boolean,
    summaryConfigs: List<AiModelConfig>,
    embeddingConfigs: List<AiModelConfig>,
    currentSummaryConfig: AiModelConfig?,
    currentEmbeddingConfig: AiModelConfig?,
    onHelpRequest: (String) -> Unit,
    onAddSummaryConfig: () -> Unit,
    onEditSummaryConfig: (AiModelConfig) -> Unit,
    onDeleteSummaryConfig: (AiModelConfig) -> Unit,
    onToggleSummaryConfig: (AiModelConfig, Boolean) -> Unit,
    onAddEmbeddingConfig: () -> Unit,
    onEditEmbeddingConfig: (AiModelConfig) -> Unit,
    onDeleteEmbeddingConfig: (AiModelConfig) -> Unit,
    onToggleEmbeddingConfig: (AiModelConfig, Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsSection(
            title = stringResource(R.string.settings_cloud_summary_api_keys),
            boxed = true,
            isHelpMode = isHelpMode,
            helpDescription = stringResource(R.string.settings_help_section_summary_api_keys),
            onHelpRequest = onHelpRequest,
            trailing = {
                AddSettingsActionButton(onClick = onAddSummaryConfig)
            }
        ) {
            AiConfigList(
                configs = summaryConfigs,
                onEdit = onEditSummaryConfig,
                onDelete = onDeleteSummaryConfig,
                onToggle = onToggleSummaryConfig
            )
            currentSummaryConfig?.let { activeConfig ->
                Text(
                    text = stringResource(
                        R.string.settings_api_current_model,
                        activeConfig.name
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
        }

        SettingsSection(
            title = stringResource(R.string.settings_cloud_vectorization_api_keys),
            boxed = true,
            isHelpMode = isHelpMode,
            helpDescription = stringResource(R.string.settings_help_section_embedding_api_keys),
            onHelpRequest = onHelpRequest,
            trailing = {
                AddSettingsActionButton(onClick = onAddEmbeddingConfig)
            }
        ) {
            AiConfigList(
                configs = embeddingConfigs,
                onEdit = onEditEmbeddingConfig,
                onDelete = onDeleteEmbeddingConfig,
                onToggle = onToggleEmbeddingConfig
            )
            currentEmbeddingConfig?.let { activeConfig ->
                Text(
                    text = stringResource(
                        R.string.settings_api_current_model,
                        activeConfig.name
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun AddSettingsActionButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun AiConfigList(
    configs: List<AiModelConfig>,
    onEdit: (AiModelConfig) -> Unit,
    onDelete: (AiModelConfig) -> Unit,
    onToggle: (AiModelConfig, Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (configs.isEmpty()) {
            Text(
                stringResource(R.string.settings_api_keys_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            configs.forEach { config ->
                SettingsAiKeyItem(
                    config = config,
                    onEdit = { onEdit(config) },
                    onDelete = { onDelete(config) },
                    onToggle = { onToggle(config, it) }
                )
            }
        }
    }
}

@Composable
internal fun SettingsAiProcessingGroupContent(
    isHelpMode: Boolean,
    userPreferences: UserPreferences,
    summaryPrompt: String,
    aiMaxCharsPerArticle: Float,
    aiMaxCharsPerFeedArticle: Float,
    aiMaxCharsTotal: Float,
    summaryNewsInFeedExtractive: Float,
    summaryNewsInScheduledExtractive: Float,
    adaptiveExtractiveOnlyBelowChars: Float,
    adaptiveExtractiveHighCompressionAboveChars: Float,
    adaptiveExtractiveCompressionPercentMedium: Float,
    adaptiveExtractiveCompressionPercentHigh: Float,
    onAiStrategyChange: (AiStrategy) -> Unit,
    onAiMaxCharsPerArticleChange: (Float) -> Unit,
    onAiMaxCharsPerArticleCommitted: () -> Unit,
    onAiMaxCharsPerFeedArticleChange: (Float) -> Unit,
    onAiMaxCharsPerFeedArticleCommitted: () -> Unit,
    onAiMaxCharsTotalChange: (Float) -> Unit,
    onAiMaxCharsTotalCommitted: () -> Unit,
    onSummaryNewsInFeedExtractiveChange: (Float) -> Unit,
    onSummaryNewsInFeedExtractiveCommitted: () -> Unit,
    onSummaryNewsInScheduledExtractiveChange: (Float) -> Unit,
    onSummaryNewsInScheduledExtractiveCommitted: () -> Unit,
    onCustomSummaryPromptEnabledChange: (Boolean) -> Unit,
    onSummaryPromptChange: (String) -> Unit,
    onAdaptiveExtractiveOnlyBelowCharsChange: (Float) -> Unit,
    onAdaptiveExtractiveOnlyBelowCharsCommitted: () -> Unit,
    onAdaptiveExtractiveHighCompressionAboveCharsChange: (Float) -> Unit,
    onAdaptiveExtractiveHighCompressionAboveCharsCommitted: () -> Unit,
    onAdaptiveExtractiveCompressionPercentMediumChange: (Float) -> Unit,
    onAdaptiveExtractiveCompressionPercentMediumCommitted: () -> Unit,
    onAdaptiveExtractiveCompressionPercentHighChange: (Float) -> Unit,
    onAdaptiveExtractiveCompressionPercentHighCommitted: () -> Unit,
    onHelpRequest: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsSection(
            title = stringResource(R.string.settings_ai_strategy),
            boxed = true,
            isHelpMode = isHelpMode,
            helpDescription = stringResource(R.string.settings_help_section_ai_strategy),
            onHelpRequest = onHelpRequest
        ) {
            val strategies = listOf(
                AiStrategy.LOCAL to R.string.ai_strategy_local,
                AiStrategy.CLOUD to R.string.ai_strategy_cloud,
                AiStrategy.ADAPTIVE to R.string.ai_strategy_adaptive
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                strategies.forEachIndexed { index, (strategy, labelRes) ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = strategies.size),
                        onClick = { onAiStrategyChange(strategy) },
                        selected = userPreferences.aiStrategy == strategy
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        SettingsSection(
            title = stringResource(R.string.settings_custom_summary_prompt),
            boxed = true,
            isHelpMode = isHelpMode,
            helpDescription = stringResource(R.string.settings_help_section_custom_prompt),
            onHelpRequest = onHelpRequest
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingsToggleRow(
                    label = stringResource(R.string.settings_custom_summary_prompt),
                    checked = userPreferences.isCustomSummaryPromptEnabled,
                    onCheckedChange = onCustomSummaryPromptEnabledChange
                )

                if (userPreferences.isCustomSummaryPromptEnabled) {
                    OutlinedTextField(
                        value = summaryPrompt,
                        onValueChange = onSummaryPromptChange,
                        label = { Text(stringResource(R.string.settings_summary_prompt)) },
                        placeholder = { Text(stringResource(R.string.settings_summary_prompt_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large
                    )
                }
            }
        }

        SettingsSection(
            title = stringResource(R.string.settings_ai_limits),
            boxed = true,
            isHelpMode = isHelpMode,
            helpDescription = stringResource(R.string.settings_help_section_ai_limits),
            onHelpRequest = onHelpRequest
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingsIntSliderItem(
                    label = stringResource(R.string.settings_ai_chars_per_article_processing, aiMaxCharsPerArticle.toInt()),
                    value = aiMaxCharsPerArticle,
                    onValueChange = onAiMaxCharsPerArticleChange,
                    onValueChangeFinished = onAiMaxCharsPerArticleCommitted,
                    valueRange = 200f..3000f,
                    steps = 28
                )
                SettingsIntSliderItem(
                    label = stringResource(R.string.settings_ai_chars_per_feed_article, aiMaxCharsPerFeedArticle.toInt()),
                    value = aiMaxCharsPerFeedArticle,
                    onValueChange = onAiMaxCharsPerFeedArticleChange,
                    onValueChangeFinished = onAiMaxCharsPerFeedArticleCommitted,
                    valueRange = 200f..3000f,
                    steps = 28
                )
                SettingsIntSliderItem(
                    label = stringResource(R.string.settings_ai_chars_total, aiMaxCharsTotal.toInt()),
                    value = aiMaxCharsTotal,
                    onValueChange = onAiMaxCharsTotalChange,
                    onValueChangeFinished = onAiMaxCharsTotalCommitted,
                    valueRange = 2000f..20000f,
                    steps = 35
                )
            }
        }

        SettingsSection(
            title = stringResource(R.string.settings_local_summary),
            boxed = true,
            isHelpMode = isHelpMode,
            helpDescription = stringResource(R.string.settings_help_section_local_summary),
            onHelpRequest = onHelpRequest
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingsIntSliderItem(
                    label = stringResource(R.string.settings_summary_news_feed_extractive, summaryNewsInFeedExtractive.toInt()),
                    value = summaryNewsInFeedExtractive,
                    onValueChange = onSummaryNewsInFeedExtractiveChange,
                    onValueChangeFinished = onSummaryNewsInFeedExtractiveCommitted,
                    valueRange = 1f..20f,
                    steps = 18
                )
                SettingsIntSliderItem(
                    label = stringResource(R.string.settings_summary_news_scheduled_extractive, summaryNewsInScheduledExtractive.toInt()),
                    value = summaryNewsInScheduledExtractive,
                    onValueChange = onSummaryNewsInScheduledExtractiveChange,
                    onValueChangeFinished = onSummaryNewsInScheduledExtractiveCommitted,
                    valueRange = 1f..20f,
                    steps = 18
                )
            }
        }

        SettingsSection(
            title = stringResource(R.string.settings_adaptive_summary),
            boxed = true,
            isHelpMode = isHelpMode,
            helpDescription = stringResource(R.string.settings_help_section_adaptive_summary),
            onHelpRequest = onHelpRequest
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingsIntSliderItem(
                    label = stringResource(
                        R.string.settings_adaptive_extractive_only_below_chars,
                        adaptiveExtractiveOnlyBelowChars.toInt()
                    ),
                    value = adaptiveExtractiveOnlyBelowChars,
                    onValueChange = onAdaptiveExtractiveOnlyBelowCharsChange,
                    onValueChangeFinished = onAdaptiveExtractiveOnlyBelowCharsCommitted,
                    valueRange = 300f..3000f,
                    steps = 53
                )
                SettingsIntSliderItem(
                    label = stringResource(
                        R.string.settings_adaptive_extractive_high_compression_above_chars,
                        adaptiveExtractiveHighCompressionAboveChars.toInt()
                    ),
                    value = adaptiveExtractiveHighCompressionAboveChars,
                    onValueChange = onAdaptiveExtractiveHighCompressionAboveCharsChange,
                    onValueChangeFinished = onAdaptiveExtractiveHighCompressionAboveCharsCommitted,
                    valueRange = 1000f..6000f,
                    steps = 99
                )
                SettingsIntSliderItem(
                    label = stringResource(
                        R.string.settings_adaptive_extractive_compression_percent_medium,
                        adaptiveExtractiveCompressionPercentMedium.toInt()
                    ),
                    value = adaptiveExtractiveCompressionPercentMedium,
                    onValueChange = onAdaptiveExtractiveCompressionPercentMediumChange,
                    onValueChangeFinished = onAdaptiveExtractiveCompressionPercentMediumCommitted,
                    valueRange = 10f..90f,
                    steps = 79
                )
                SettingsIntSliderItem(
                    label = stringResource(
                        R.string.settings_adaptive_extractive_compression_percent_high,
                        adaptiveExtractiveCompressionPercentHigh.toInt()
                    ),
                    value = adaptiveExtractiveCompressionPercentHigh,
                    onValueChange = onAdaptiveExtractiveCompressionPercentHighChange,
                    onValueChangeFinished = onAdaptiveExtractiveCompressionPercentHighCommitted,
                    valueRange = 10f..90f,
                    steps = 79
                )
            }
        }
    }
}

@Composable
internal fun SettingsFeedGroupContent(
    isHelpMode: Boolean,
    userPreferences: UserPreferences,
    localDeduplicationThreshold: Float,
    cloudDeduplicationThreshold: Float,
    minMentions: Float,
    onFeedMediaEnabledChange: (Boolean) -> Unit,
    onFeedDescriptionEnabledChange: (Boolean) -> Unit,
    onFeedSummaryUseFullTextEnabledChange: (Boolean) -> Unit,
    onImportanceFilterEnabledChange: (Boolean) -> Unit,
    onDeduplicationEnabledChange: (Boolean) -> Unit,
    onHideSingleNewsEnabledChange: (Boolean) -> Unit,
    onDeduplicationStrategyChange: (com.andrewwin.sumup.data.local.entities.DeduplicationStrategy) -> Unit,
    onLocalDeduplicationThresholdChange: (Float) -> Unit,
    onLocalDeduplicationThresholdCommitted: () -> Unit,
    onCloudDeduplicationThresholdChange: (Float) -> Unit,
    onCloudDeduplicationThresholdCommitted: () -> Unit,
    onMinMentionsChange: (Float) -> Unit,
    onMinMentionsCommitted: () -> Unit,
    onHelpRequest: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsSection(
            title = stringResource(R.string.settings_feed_display),
            boxed = true,
            isHelpMode = isHelpMode,
            helpDescription = stringResource(R.string.settings_help_section_feed),
            onHelpRequest = onHelpRequest
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingsToggleRow(
                    label = stringResource(R.string.settings_feed_media),
                    checked = userPreferences.isFeedMediaEnabled,
                    onCheckedChange = onFeedMediaEnabledChange
                )
                SettingsToggleRow(
                    label = stringResource(R.string.settings_feed_description),
                    checked = userPreferences.isFeedDescriptionEnabled,
                    onCheckedChange = onFeedDescriptionEnabledChange
                )
                SettingsToggleRow(
                    label = stringResource(R.string.settings_feed_summary_use_full_text),
                    checked = userPreferences.isFeedSummaryUseFullTextEnabled,
                    onCheckedChange = onFeedSummaryUseFullTextEnabledChange
                )
                SettingsToggleRow(
                    label = stringResource(R.string.settings_enable_importance_filter),
                    checked = userPreferences.isImportanceFilterEnabled,
                    onCheckedChange = onImportanceFilterEnabledChange
                )
            }
        }

        SettingsSection(
            title = stringResource(R.string.settings_deduplication),
            boxed = true,
            isHelpMode = isHelpMode,
            helpDescription = stringResource(R.string.settings_help_section_feed),
            onHelpRequest = onHelpRequest
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.settings_deduplication_strategy),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val strategies = listOf(
                        com.andrewwin.sumup.data.local.entities.DeduplicationStrategy.LOCAL to R.string.ai_strategy_local,
                        com.andrewwin.sumup.data.local.entities.DeduplicationStrategy.CLOUD to R.string.ai_strategy_cloud
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        strategies.forEachIndexed { index, (strategy, labelRes) ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = strategies.size),
                                onClick = { onDeduplicationStrategyChange(strategy) },
                                selected = userPreferences.deduplicationStrategy == strategy
                            ) {
                                Text(
                                    text = stringResource(labelRes),
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.settings_deduplication_strategy_recalculate_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                SettingsToggleRow(
                    label = stringResource(R.string.settings_enable_deduplication),
                    checked = userPreferences.isDeduplicationEnabled,
                    onCheckedChange = onDeduplicationEnabledChange
                )
                SettingsToggleRow(
                    label = stringResource(R.string.settings_hide_single_news),
                    checked = userPreferences.isHideSingleNewsEnabled,
                    onCheckedChange = onHideSingleNewsEnabledChange
                )

                SettingsFloatSliderItem(
                    label = stringResource(
                        R.string.settings_local_deduplication_threshold,
                        String.format(Locale.US, "%.3f", localDeduplicationThreshold.coerceDeduplicationThreshold())
                    ),
                    value = localDeduplicationThreshold.coerceDeduplicationThreshold(),
                    onValueChange = { onLocalDeduplicationThresholdChange(it.roundDeduplicationThreshold()) },
                    onValueChangeFinished = onLocalDeduplicationThresholdCommitted,
                    valueRange = DEDUPLICATION_THRESHOLD_MIN..DEDUPLICATION_THRESHOLD_MAX,
                    steps = DEDUPLICATION_THRESHOLD_SLIDER_STEPS
                )
                SettingsFloatSliderItem(
                    label = stringResource(
                        R.string.settings_cloud_deduplication_threshold,
                        String.format(Locale.US, "%.3f", cloudDeduplicationThreshold.coerceDeduplicationThreshold())
                    ),
                    value = cloudDeduplicationThreshold.coerceDeduplicationThreshold(),
                    onValueChange = { onCloudDeduplicationThresholdChange(it.roundDeduplicationThreshold()) },
                    onValueChangeFinished = onCloudDeduplicationThresholdCommitted,
                    valueRange = DEDUPLICATION_THRESHOLD_MIN..DEDUPLICATION_THRESHOLD_MAX,
                    steps = DEDUPLICATION_THRESHOLD_SLIDER_STEPS
                )
                SettingsIntSliderItem(
                    label = stringResource(R.string.settings_min_mentions, minMentions.toInt()),
                    value = minMentions,
                    onValueChange = onMinMentionsChange,
                    onValueChangeFinished = onMinMentionsCommitted,
                    valueRange = 1f..10f,
                    steps = 8
                )
            }
        }
    }
}

private fun Float.coerceDeduplicationThreshold(): Float =
    coerceIn(DEDUPLICATION_THRESHOLD_MIN, DEDUPLICATION_THRESHOLD_MAX)

private fun Float.roundDeduplicationThreshold(): Float {
    val steppedValue = ((coerceDeduplicationThreshold() - DEDUPLICATION_THRESHOLD_MIN) /
        DEDUPLICATION_THRESHOLD_STEP).roundToInt() * DEDUPLICATION_THRESHOLD_STEP
    return (DEDUPLICATION_THRESHOLD_MIN + steppedValue).coerceDeduplicationThreshold()
}

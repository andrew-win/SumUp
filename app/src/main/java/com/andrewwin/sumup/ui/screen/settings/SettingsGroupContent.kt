package com.andrewwin.sumup.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
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
import com.andrewwin.sumup.data.local.entities.DeduplicationStrategy
import com.andrewwin.sumup.data.local.entities.SummaryLanguage
import com.andrewwin.sumup.data.local.entities.UserPreferences
import java.util.Locale

@Composable
internal fun SettingsHomeGroupsContent(
    isHelpMode: Boolean,
    onGroupClick: (SettingsGroup) -> Unit,
    onHelpRequest: (SettingsGroup) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsHomeSection(
            title = stringResource(R.string.settings_section_account),
            groups = listOf(SettingsGroup.ACCOUNT),
            isHelpMode = isHelpMode,
            onGroupClick = onGroupClick,
            onHelpRequest = onHelpRequest
        )
        SettingsHomeSection(
            title = stringResource(R.string.settings_section_content_ai),
            groups = listOf(SettingsGroup.AI_PROCESSING, SettingsGroup.API_KEYS, SettingsGroup.RECOMMENDATIONS),
            isHelpMode = isHelpMode,
            onGroupClick = onGroupClick,
            onHelpRequest = onHelpRequest
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
            onHelpRequest = onHelpRequest
        )
    }
}

@Composable
private fun SettingsHomeSection(
    title: String,
    groups: List<SettingsGroup>,
    isHelpMode: Boolean,
    onGroupClick: (SettingsGroup) -> Unit,
    onHelpRequest: (SettingsGroup) -> Unit
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
            onHelpRequest = onHelpRequest
        )
    }
}

@Composable
internal fun SettingsGeneralGroupContent(
    userPreferences: UserPreferences,
    onAppLanguageChange: (AppLanguage) -> Unit,
    onSummaryLanguageChange: (SummaryLanguage) -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsSection(title = stringResource(R.string.settings_language), boxed = true) {
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

        SettingsSection(title = stringResource(R.string.settings_summary_language), boxed = true) {
            val summaryLanguages = listOf(
                SummaryLanguage.ORIGINAL to R.string.settings_summary_language_original,
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

        SettingsSection(title = stringResource(R.string.settings_theme), boxed = true) {
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
    summaryConfigs: List<AiModelConfig>,
    embeddingConfigs: List<AiModelConfig>,
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
        }

        SettingsSection(
            title = stringResource(R.string.settings_cloud_vectorization_api_keys),
            boxed = true,
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
    userPreferences: UserPreferences,
    summaryPrompt: String,
    aiMaxCharsPerArticle: Float,
    aiMaxCharsPerFeedArticle: Float,
    aiMaxCharsTotal: Float,
    summaryNewsInFeedExtractive: Float,
    summaryNewsInScheduledExtractive: Float,
    adaptiveExtractiveOnlyBelowChars: Float,
    adaptiveExtractiveCompressAboveChars: Float,
    adaptiveExtractiveCompressionPercent: Float,
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
    onDeduplicationStrategyChange: (DeduplicationStrategy) -> Unit,
    onCustomSummaryPromptEnabledChange: (Boolean) -> Unit,
    onSummaryPromptChange: (String) -> Unit,
    onAdaptiveExtractiveOnlyBelowCharsChange: (Float) -> Unit,
    onAdaptiveExtractiveOnlyBelowCharsCommitted: () -> Unit,
    onAdaptiveExtractiveCompressAboveCharsChange: (Float) -> Unit,
    onAdaptiveExtractiveCompressAboveCharsCommitted: () -> Unit,
    onAdaptiveExtractiveCompressionPercentChange: (Float) -> Unit,
    onAdaptiveExtractiveCompressionPercentCommitted: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsSection(title = stringResource(R.string.settings_ai_strategy), boxed = true) {
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

        SettingsSection(title = stringResource(R.string.settings_ai_limits), boxed = true) {
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

        SettingsSection(title = stringResource(R.string.settings_local_summary), boxed = true) {
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

        SettingsSection(title = stringResource(R.string.settings_deduplication_strategy), boxed = true) {
            val strategies = listOf(
                DeduplicationStrategy.LOCAL to R.string.ai_strategy_local,
                DeduplicationStrategy.CLOUD to R.string.ai_strategy_cloud,
                DeduplicationStrategy.ADAPTIVE to R.string.ai_strategy_adaptive
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
        }

        SettingsSection(title = stringResource(R.string.settings_custom_summary_prompt), boxed = true) {
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

        SettingsSection(title = stringResource(R.string.settings_adaptive_summary), boxed = true) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingsIntSliderItem(
                    label = stringResource(
                        R.string.settings_adaptive_extractive_only_below_chars,
                        adaptiveExtractiveOnlyBelowChars.toInt()
                    ),
                    value = adaptiveExtractiveOnlyBelowChars,
                    onValueChange = onAdaptiveExtractiveOnlyBelowCharsChange,
                    onValueChangeFinished = onAdaptiveExtractiveOnlyBelowCharsCommitted,
                    valueRange = 500f..5000f,
                    steps = 45
                )
                SettingsIntSliderItem(
                    label = stringResource(
                        R.string.settings_adaptive_extractive_compress_above_chars,
                        adaptiveExtractiveCompressAboveChars.toInt()
                    ),
                    value = adaptiveExtractiveCompressAboveChars,
                    onValueChange = onAdaptiveExtractiveCompressAboveCharsChange,
                    onValueChangeFinished = onAdaptiveExtractiveCompressAboveCharsCommitted,
                    valueRange = 1000f..10000f,
                    steps = 90
                )
                SettingsIntSliderItem(
                    label = stringResource(
                        R.string.settings_adaptive_extractive_compression_percent,
                        adaptiveExtractiveCompressionPercent.toInt()
                    ),
                    value = adaptiveExtractiveCompressionPercent,
                    onValueChange = onAdaptiveExtractiveCompressionPercentChange,
                    onValueChangeFinished = onAdaptiveExtractiveCompressionPercentCommitted,
                    valueRange = 10f..90f,
                    steps = 79
                )
            }
        }
    }
}

@Composable
internal fun SettingsFeedGroupContent(
    userPreferences: UserPreferences,
    localDeduplicationThreshold: Float,
    cloudDeduplicationThreshold: Float,
    minMentions: Float,
    downloadState: ModelDownloadState,
    onFeedMediaEnabledChange: (Boolean) -> Unit,
    onFeedDescriptionEnabledChange: (Boolean) -> Unit,
    onFeedSummaryUseFullTextEnabledChange: (Boolean) -> Unit,
    onImportanceFilterEnabledChange: (Boolean) -> Unit,
    onDeduplicationEnabledChange: (Boolean) -> Unit,
    onHideSingleNewsEnabledChange: (Boolean) -> Unit,
    onLocalDeduplicationThresholdChange: (Float) -> Unit,
    onLocalDeduplicationThresholdCommitted: () -> Unit,
    onCloudDeduplicationThresholdChange: (Float) -> Unit,
    onCloudDeduplicationThresholdCommitted: () -> Unit,
    onMinMentionsChange: (Float) -> Unit,
    onMinMentionsCommitted: () -> Unit,
    onModelActionClick: () -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_feed), boxed = true) {
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
                    String.format(Locale.US, "%.2f", localDeduplicationThreshold)
                ),
                value = localDeduplicationThreshold,
                onValueChange = onLocalDeduplicationThresholdChange,
                onValueChangeFinished = onLocalDeduplicationThresholdCommitted,
                valueRange = 0.3f..0.99f,
                steps = 69
            )
            SettingsFloatSliderItem(
                label = stringResource(
                    R.string.settings_cloud_deduplication_threshold,
                    String.format(Locale.US, "%.2f", cloudDeduplicationThreshold)
                ),
                value = cloudDeduplicationThreshold,
                onValueChange = onCloudDeduplicationThresholdChange,
                onValueChangeFinished = onCloudDeduplicationThresholdCommitted,
                valueRange = 0.3f..0.99f,
                steps = 69
            )
            SettingsIntSliderItem(
                label = stringResource(R.string.settings_min_mentions, minMentions.toInt()),
                value = minMentions,
                onValueChange = onMinMentionsChange,
                onValueChangeFinished = onMinMentionsCommitted,
                valueRange = 1f..10f,
                steps = 8
            )

            Spacer(Modifier.height(4.dp))
            val isModelReady = downloadState is ModelDownloadState.Ready
            val statusText = when (val state = downloadState) {
                is ModelDownloadState.Idle -> stringResource(R.string.model_status_idle)
                is ModelDownloadState.Downloading -> stringResource(R.string.model_status_downloading, state.progress)
                is ModelDownloadState.Loading -> stringResource(R.string.model_status_loading)
                is ModelDownloadState.Ready -> stringResource(R.string.model_status_ready)
                is ModelDownloadState.Error -> stringResource(R.string.model_status_error, state.message)
            }

            Text(
                stringResource(R.string.settings_model_status, statusText),
                style = MaterialTheme.typography.bodyLarge
            )

            Button(
                onClick = onModelActionClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = if (isModelReady) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(
                    text = stringResource(
                        if (isModelReady) R.string.settings_delete_model else R.string.settings_download_model
                    ),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

package com.andrewwin.sumup.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.AiModelConfig
import com.andrewwin.sumup.data.local.entities.AiModelType
import com.andrewwin.sumup.data.local.entities.AiProvider
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.AppLanguage
import com.andrewwin.sumup.data.local.entities.AppThemeMode
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val summaryConfigs by viewModel.summaryConfigs.collectAsState()
    val embeddingConfigs by viewModel.embeddingConfigs.collectAsState()
    val userPreferences by viewModel.userPreferences.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    
    var showConfigDialog by remember { mutableStateOf<Pair<AiModelConfig?, AiModelType>?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }
    var summaryPrompt by remember(userPreferences.summaryPrompt) { mutableStateOf(userPreferences.summaryPrompt) }
    var showClearArticlesDialog by remember { mutableStateOf(false) }
    var showClearEmbeddingsDialog by remember { mutableStateOf(false) }
    var aiMaxCharsPerArticle by rememberSaveable(userPreferences.aiMaxCharsPerArticle) { mutableStateOf(userPreferences.aiMaxCharsPerArticle.toFloat()) }
    var aiMaxCharsPerFeedArticle by rememberSaveable(userPreferences.aiMaxCharsPerFeedArticle) { mutableStateOf(userPreferences.aiMaxCharsPerFeedArticle.toFloat()) }
    var aiMaxCharsTotal by rememberSaveable(userPreferences.aiMaxCharsTotal) { mutableStateOf(userPreferences.aiMaxCharsTotal.toFloat()) }
    var extractiveSentencesInFeed by rememberSaveable(userPreferences.extractiveSentencesInFeed) { mutableStateOf(userPreferences.extractiveSentencesInFeed.toFloat()) }
    var extractiveSentencesInScheduled by rememberSaveable(userPreferences.extractiveSentencesInScheduled) { mutableStateOf(userPreferences.extractiveSentencesInScheduled.toFloat()) }
    var extractiveNewsInScheduled by rememberSaveable(userPreferences.extractiveNewsInScheduled) { mutableStateOf(userPreferences.extractiveNewsInScheduled.toFloat()) }
    var localDeduplicationThreshold by rememberSaveable(userPreferences.localDeduplicationThreshold) { mutableStateOf(userPreferences.localDeduplicationThreshold) }
    var cloudDeduplicationThreshold by rememberSaveable(userPreferences.cloudDeduplicationThreshold) { mutableStateOf(userPreferences.cloudDeduplicationThreshold) }
    var minMentions by rememberSaveable(userPreferences.minMentions) { mutableStateOf(userPreferences.minMentions.toFloat()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                actions = {
                    FilledIconButton(
                        onClick = {},
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                SettingsSection(title = stringResource(R.string.settings_ai_strategy)) {
                    val strategies = listOf(
                        AiStrategy.LOCAL to R.string.ai_strategy_local,
                        AiStrategy.CLOUD to R.string.ai_strategy_cloud,
                        AiStrategy.ADAPTIVE to R.string.ai_strategy_adaptive
                    )
                    
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        strategies.forEachIndexed { index, (strategy, labelRes) ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = strategies.size),
                                onClick = { viewModel.updateAiStrategy(strategy) },
                                selected = userPreferences.aiStrategy == strategy
                            ) {
                                Text(
                                    text = stringResource(labelRes),
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_language)) {
                    val languages = listOf(
                        AppLanguage.UK to R.string.settings_language_uk,
                        AppLanguage.EN to R.string.settings_language_en
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        languages.forEachIndexed { index, (lang, labelRes) ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = languages.size),
                                onClick = { viewModel.updateAppLanguage(lang) },
                                selected = userPreferences.appLanguage == lang
                            ) {
                                Text(text = stringResource(labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_theme)) {
                    val themeModes = listOf(
                        AppThemeMode.SYSTEM to R.string.settings_theme_system,
                        AppThemeMode.LIGHT to R.string.settings_theme_light,
                        AppThemeMode.DARK to R.string.settings_theme_dark
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        themeModes.forEachIndexed { index, (mode, labelRes) ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = themeModes.size),
                                onClick = { viewModel.updateAppThemeMode(mode) },
                                selected = userPreferences.appThemeMode == mode
                            ) {
                                Text(
                                    text = stringResource(labelRes),
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingsSection(
                    title = stringResource(R.string.settings_cloud_summary),
                    trailing = {
                        IconButton(
                            onClick = { showConfigDialog = null to AiModelType.SUMMARY },
                            modifier = Modifier.size(32.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (summaryConfigs.isEmpty()) {
                            Text(
                                stringResource(R.string.settings_api_keys_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            summaryConfigs.forEach { config ->
                                AiKeyItem(
                                    config = config,
                                    onEdit = { showConfigDialog = config to AiModelType.SUMMARY },
                                    onDelete = { viewModel.deleteAiConfig(config) },
                                    onToggle = { viewModel.toggleAiConfig(config, it) }
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.settings_custom_summary_prompt),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Switch(
                                checked = userPreferences.isCustomSummaryPromptEnabled,
                                onCheckedChange = { viewModel.updateCustomSummaryPromptEnabled(it) },
                                modifier = Modifier.scale(0.85f)
                            )
                        }

                        if (userPreferences.isCustomSummaryPromptEnabled) {
                            OutlinedTextField(
                                value = summaryPrompt,
                                onValueChange = {
                                    summaryPrompt = it
                                    viewModel.updateSummaryPrompt(it)
                                },
                                label = { Text(stringResource(R.string.settings_summary_prompt)) },
                                placeholder = { Text(stringResource(R.string.settings_summary_prompt_hint)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large
                            )
                        }
                    }
                }
            }

            item {
                SettingsSection(
                    title = stringResource(R.string.settings_cloud_vectorization),
                    trailing = {
                        IconButton(
                            onClick = { showConfigDialog = null to AiModelType.EMBEDDING },
                            modifier = Modifier.size(32.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (embeddingConfigs.isEmpty()) {
                            Text(
                                stringResource(R.string.settings_api_keys_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            embeddingConfigs.forEach { config ->
                                AiKeyItem(
                                    config = config,
                                    onEdit = { showConfigDialog = config to AiModelType.EMBEDDING },
                                    onDelete = { viewModel.deleteAiConfig(config) },
                                    onToggle = { viewModel.toggleAiConfig(config, it) }
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_ai_limits)) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column {
                            Text(
                                stringResource(R.string.settings_ai_chars_per_article_processing, aiMaxCharsPerArticle.toInt()),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = aiMaxCharsPerArticle,
                                onValueChange = { aiMaxCharsPerArticle = it },
                                onValueChangeFinished = {
                                    viewModel.updateAiMaxCharsPerArticle(aiMaxCharsPerArticle.toInt())
                                },
                                valueRange = 200f..3000f,
                                steps = 28
                            )
                        }
                        Column {
                            Text(
                                stringResource(R.string.settings_ai_chars_per_feed_article, aiMaxCharsPerFeedArticle.toInt()),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = aiMaxCharsPerFeedArticle,
                                onValueChange = { aiMaxCharsPerFeedArticle = it },
                                onValueChangeFinished = {
                                    viewModel.updateAiMaxCharsPerFeedArticle(aiMaxCharsPerFeedArticle.toInt())
                                },
                                valueRange = 200f..3000f,
                                steps = 28
                            )
                        }
                        Column {
                            Text(
                                stringResource(R.string.settings_ai_chars_total, aiMaxCharsTotal.toInt()),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = aiMaxCharsTotal,
                                onValueChange = { aiMaxCharsTotal = it },
                                onValueChangeFinished = {
                                    viewModel.updateAiMaxCharsTotal(aiMaxCharsTotal.toInt())
                                },
                                valueRange = 2000f..20000f,
                                steps = 35
                            )
                        }
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.ai_strategy_local)) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column {
                            Text(
                                stringResource(R.string.settings_extractive_sentences_feed, extractiveSentencesInFeed.toInt()),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = extractiveSentencesInFeed,
                                onValueChange = { extractiveSentencesInFeed = it },
                                onValueChangeFinished = {
                                    viewModel.updateExtractiveSentencesInFeed(extractiveSentencesInFeed.toInt())
                                },
                                valueRange = 1f..10f,
                                steps = 8
                            )
                        }

                        Column {
                            Text(
                                stringResource(R.string.settings_extractive_sentences_scheduled, extractiveSentencesInScheduled.toInt()),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = extractiveSentencesInScheduled,
                                onValueChange = { extractiveSentencesInScheduled = it },
                                onValueChangeFinished = {
                                    viewModel.updateExtractiveSentencesInScheduled(extractiveSentencesInScheduled.toInt())
                                },
                                valueRange = 1f..10f,
                                steps = 8
                            )
                        }

                        Column {
                            Text(
                                stringResource(R.string.settings_extractive_news_scheduled, extractiveNewsInScheduled.toInt()),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = extractiveNewsInScheduled,
                                onValueChange = { extractiveNewsInScheduled = it },
                                onValueChangeFinished = {
                                    viewModel.updateExtractiveNewsInScheduled(extractiveNewsInScheduled.toInt())
                                },
                                valueRange = 1f..20f,
                                steps = 18
                            )
                        }
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_filtering)) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.settings_feed_media),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Switch(
                                checked = userPreferences.isFeedMediaEnabled,
                                onCheckedChange = { viewModel.updateFeedMediaEnabled(it) },
                                modifier = Modifier.scale(0.85f)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.settings_enable_importance_filter),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Switch(
                                checked = userPreferences.isImportanceFilterEnabled,
                                onCheckedChange = { viewModel.updateImportanceFilterEnabled(it) },
                                modifier = Modifier.scale(0.85f)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.settings_enable_deduplication),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Switch(
                                checked = userPreferences.isDeduplicationEnabled,
                                onCheckedChange = { viewModel.updateDeduplicationEnabled(it) },
                                modifier = Modifier.scale(0.85f)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.settings_hide_single_news),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Switch(
                                checked = userPreferences.isHideSingleNewsEnabled,
                                onCheckedChange = { viewModel.updateHideSingleNewsEnabled(it) },
                                modifier = Modifier.scale(0.85f)
                            )
                        }
                        
                        Column {
                            Text(
                                stringResource(R.string.settings_local_deduplication_threshold, String.format(Locale.US, "%.2f", localDeduplicationThreshold)),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = localDeduplicationThreshold,
                                onValueChange = { localDeduplicationThreshold = it },
                                onValueChangeFinished = {
                                    viewModel.updateLocalDeduplicationThreshold(localDeduplicationThreshold)
                                },
                                valueRange = 0.3f..0.99f,
                                steps = 69,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    activeTickColor = MaterialTheme.colorScheme.primaryContainer,
                                    inactiveTickColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )
                        }

                        Column {
                            Text(
                                stringResource(R.string.settings_cloud_deduplication_threshold, String.format(Locale.US, "%.2f", cloudDeduplicationThreshold)),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = cloudDeduplicationThreshold,
                                onValueChange = { cloudDeduplicationThreshold = it },
                                onValueChangeFinished = {
                                    viewModel.updateCloudDeduplicationThreshold(cloudDeduplicationThreshold)
                                },
                                valueRange = 0.3f..0.99f,
                                steps = 69,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    activeTickColor = MaterialTheme.colorScheme.primaryContainer,
                                    inactiveTickColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )
                        }
                        
                        Column {
                            Text(
                                stringResource(R.string.settings_min_mentions, minMentions.toInt()),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = minMentions,
                                onValueChange = { minMentions = it },
                                onValueChangeFinished = {
                                    viewModel.updateMinMentions(minMentions.toInt())
                                },
                                valueRange = 1f..10f,
                                steps = 8,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    activeTickColor = MaterialTheme.colorScheme.primaryContainer,
                                    inactiveTickColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )
                        }

                        Spacer(Modifier.height(4.dp))
                        val statusText = when (val s = downloadState) {
                            is ModelDownloadState.Idle -> stringResource(R.string.model_status_idle)
                            is ModelDownloadState.Downloading -> stringResource(R.string.model_status_downloading, s.progress)
                            is ModelDownloadState.Loading -> stringResource(R.string.model_status_loading)
                            is ModelDownloadState.Ready -> stringResource(R.string.model_status_ready)
                            is ModelDownloadState.Error -> stringResource(R.string.model_status_error, s.message)
                        }
                        
                        Text(
                            stringResource(R.string.settings_model_status, statusText),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        
                        Button(
                            onClick = { if (downloadState is ModelDownloadState.Ready) viewModel.deleteModel() else viewModel.downloadModel() },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = if (downloadState is ModelDownloadState.Ready) 
                                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                            else ButtonDefaults.buttonColors()
                        ) {
                            Text(
                                text = stringResource(if (downloadState is ModelDownloadState.Ready) R.string.settings_delete_model else R.string.settings_download_model),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_scheduled_summary)) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.settings_scheduled_summary),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Switch(
                                checked = userPreferences.isScheduledSummaryEnabled,
                                onCheckedChange = { viewModel.updateScheduledSummary(it, userPreferences.scheduledHour, userPreferences.scheduledMinute) },
                                modifier = Modifier.scale(0.85f)
                            )
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = userPreferences.isScheduledSummaryEnabled) { showTimePicker = true }
                                .padding(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.settings_time_label, String.format(Locale.getDefault(), "%02d:%02d", userPreferences.scheduledHour, userPreferences.scheduledMinute)),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_adaptive_summary)) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.settings_adaptive_extractive_preprocess),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Switch(
                                checked = userPreferences.isAdaptiveExtractivePreprocessingEnabled,
                                onCheckedChange = { viewModel.updateAdaptiveExtractivePreprocessingEnabled(it) },
                                modifier = Modifier.scale(0.85f)
                            )
                        }
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_memory)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { showClearArticlesDialog = true },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.settings_clear_articles),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        Button(
                            onClick = { showClearEmbeddingsDialog = true },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.settings_clear_embeddings),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }

        showConfigDialog?.let { (config, type) ->
            AiConfigDialog(
                viewModel = viewModel,
                config = config,
                type = type,
                onDismiss = { showConfigDialog = null },
                onConfirm = { viewModel.addAiConfig(it); showConfigDialog = null }
            )
        }

        if (showTimePicker) {
            ScheduledTimePickerDialog(
                hour = userPreferences.scheduledHour,
                minute = userPreferences.scheduledMinute,
                onDismiss = { showTimePicker = false },
                onConfirm = { h, m -> viewModel.updateScheduledSummary(true, h, m); showTimePicker = false }
            )
        }

        if (showClearArticlesDialog) {
            AlertDialog(
                onDismissRequest = { showClearArticlesDialog = false },
                title = { Text(stringResource(R.string.settings_clear_articles)) },
                text = { Text(stringResource(R.string.settings_clear_articles_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.clearAllArticles()
                        showClearArticlesDialog = false
                    }) { Text(stringResource(R.string.delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { showClearArticlesDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }

        if (showClearEmbeddingsDialog) {
            AlertDialog(
                onDismissRequest = { showClearEmbeddingsDialog = false },
                title = { Text(stringResource(R.string.settings_clear_embeddings)) },
                text = { Text(stringResource(R.string.settings_clear_embeddings_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.clearEmbeddings()
                        showClearEmbeddingsDialog = false
                    }) { Text(stringResource(R.string.delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { showClearEmbeddingsDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    trailing: @Composable (() -> Unit)? = null,
    headerContent: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    trailing?.invoke()
                }
                HorizontalDivider(
                    modifier = Modifier.padding(bottom = 12.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                headerContent?.invoke()
                content()
            }
        }
    }
}

@Composable
fun AiKeyItem(
    config: AiModelConfig,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val providerIconRes = when (config.provider) {
        AiProvider.GEMINI -> R.drawable.ic_gemini_ai_provider
        AiProvider.GROQ -> R.drawable.ic_groq_ai_provider
        AiProvider.OPENROUTER -> R.drawable.ic_openrouter_ai_provider
        AiProvider.COHERE -> R.drawable.ic_cohere_ai_provider
        AiProvider.CHATGPT -> R.drawable.ic_chatgpt_ai_provider
        AiProvider.CLAUDE -> R.drawable.ic_claude_ai_provider
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(providerIconRes),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = config.name, 
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal
            )
            Text(
                text = config.modelName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Switch(
            checked = config.isEnabled, 
            onCheckedChange = onToggle, 
            modifier = Modifier.scale(0.75f)
        )
        IconButton(
            onClick = onEdit, 
            modifier = Modifier.size(32.dp)
        ) {
            Icon(androidx.compose.material.icons.Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        IconButton(
            onClick = onDelete, 
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiConfigDialog(
    viewModel: SettingsViewModel,
    config: AiModelConfig? = null,
    type: AiModelType,
    onDismiss: () -> Unit,
    onConfirm: (AiModelConfig) -> Unit
) {
    var name by remember(config?.id) { mutableStateOf(config?.name ?: "") }
    var apiKey by remember(config?.id) { mutableStateOf(config?.apiKey ?: "") }
    var provider by remember(config?.id) { mutableStateOf(config?.provider ?: AiProvider.GEMINI) }
    var modelName by remember(config?.id) { mutableStateOf(config?.modelName ?: "") }
    
    val availableModels by viewModel.availableModels.collectAsState()
    val isLoadingModels by viewModel.isLoadingModels.collectAsState()
    
    var expandedProvider by remember { mutableStateOf(false) }
    var expandedModel by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (config == null) R.string.settings_add_api_key else R.string.settings_edit_api_key)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.dialog_config_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = MaterialTheme.shapes.large)
                ExposedDropdownMenuBox(expanded = expandedProvider, onExpandedChange = { expandedProvider = !expandedProvider }) {
                OutlinedTextField(value = stringResource(provider.labelRes), onValueChange = {}, readOnly = true, label = { Text(stringResource(R.string.dialog_provider)) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProvider) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(), shape = MaterialTheme.shapes.large)
                ExposedDropdownMenu(expanded = expandedProvider, onDismissRequest = { expandedProvider = false }) {
                    AiProvider.entries.forEach { entry ->
                        val iconRes = when (entry) {
                            AiProvider.GEMINI -> R.drawable.ic_gemini_ai_provider
                            AiProvider.GROQ -> R.drawable.ic_groq_ai_provider
                            AiProvider.OPENROUTER -> R.drawable.ic_openrouter_ai_provider
                            AiProvider.COHERE -> R.drawable.ic_cohere_ai_provider
                            AiProvider.CHATGPT -> R.drawable.ic_chatgpt_ai_provider
                            AiProvider.CLAUDE -> R.drawable.ic_claude_ai_provider
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(entry.labelRes)) },
                            onClick = { provider = entry; modelName = ""; expandedProvider = false },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(iconRes),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )
                    }
                }
                }
                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text(stringResource(R.string.dialog_api_key)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = MaterialTheme.shapes.large)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ExposedDropdownMenuBox(expanded = expandedModel, onExpandedChange = { if (availableModels.isNotEmpty()) expandedModel = !expandedModel }, modifier = Modifier.weight(1f)) {
                        OutlinedTextField(value = modelName, onValueChange = { modelName = it }, label = { Text(stringResource(R.string.dialog_model)) }, placeholder = { Text(if (isLoadingModels) stringResource(R.string.dialog_model_loading) else stringResource(R.string.dialog_model_select)) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedModel) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, true).fillMaxWidth(), shape = MaterialTheme.shapes.large)
                        if (availableModels.isNotEmpty()) {
                            ExposedDropdownMenu(expanded = expandedModel, onDismissRequest = { expandedModel = false }) {
                                availableModels.forEach { model ->
                                    DropdownMenuItem(text = { Text(model) }, onClick = { modelName = model; expandedModel = false })
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    if (isLoadingModels) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    else TextButton(onClick = { viewModel.loadModels(provider, apiKey, type) }) { Text(stringResource(R.string.dialog_load)) }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank() && apiKey.isNotBlank() && modelName.isNotBlank()) onConfirm(config?.copy(name = name, provider = provider, apiKey = apiKey, modelName = modelName) ?: AiModelConfig(name = name, provider = provider, apiKey = apiKey, modelName = modelName, type = type)) }, enabled = name.isNotBlank() && apiKey.isNotBlank() && modelName.isNotBlank() && !isLoadingModels, shape = MaterialTheme.shapes.large) { Text(stringResource(if (config == null) R.string.add else R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledTimePickerDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    val timeState = rememberTimePickerState(initialHour = hour, initialMinute = minute)
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = { onConfirm(timeState.hour, timeState.minute) }) { Text(stringResource(R.string.ok)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }, text = { TimePicker(state = timeState) })
}

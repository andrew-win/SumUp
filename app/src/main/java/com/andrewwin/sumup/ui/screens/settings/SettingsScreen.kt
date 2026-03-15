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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.AiModelConfig
import com.andrewwin.sumup.data.local.entities.AiProvider
import com.andrewwin.sumup.data.local.entities.AiStrategy
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val aiConfigs by viewModel.aiConfigs.collectAsState()
    val userPreferences by viewModel.userPreferences.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    
    var showConfigDialog by remember { mutableStateOf<AiModelConfig?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

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
                        AiStrategy.CLOUD to R.string.ai_strategy_cloud,
                        AiStrategy.EXTRACTIVE to R.string.ai_strategy_extractive,
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
                SettingsSection(
                    title = stringResource(R.string.settings_api_keys),
                    trailing = {
                        IconButton(
                            onClick = { isAddingNew = true },
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
                        if (aiConfigs.isEmpty()) {
                            Text(
                                stringResource(R.string.settings_api_keys_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            aiConfigs.forEach { config ->
                                AiKeyItem(
                                    config = config,
                                    onEdit = { showConfigDialog = config },
                                    onDelete = { viewModel.deleteAiConfig(config) },
                                    onToggle = { viewModel.toggleAiConfig(config, it) }
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.ai_strategy_extractive)) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column {
                            Text(
                                stringResource(R.string.settings_extractive_sentences_feed, userPreferences.extractiveSentencesInFeed),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = userPreferences.extractiveSentencesInFeed.toFloat(),
                                onValueChange = { viewModel.updateExtractiveSentencesInFeed(it.toInt()) },
                                valueRange = 1f..10f,
                                steps = 8
                            )
                        }

                        Column {
                            Text(
                                stringResource(R.string.settings_extractive_sentences_scheduled, userPreferences.extractiveSentencesInScheduled),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = userPreferences.extractiveSentencesInScheduled.toFloat(),
                                onValueChange = { viewModel.updateExtractiveSentencesInScheduled(it.toInt()) },
                                valueRange = 1f..10f,
                                steps = 8
                            )
                        }

                        Column {
                            Text(
                                stringResource(R.string.settings_extractive_news_scheduled, userPreferences.extractiveNewsInScheduled),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = userPreferences.extractiveNewsInScheduled.toFloat(),
                                onValueChange = { viewModel.updateExtractiveNewsInScheduled(it.toInt()) },
                                valueRange = 1f..20f,
                                steps = 18
                            )
                        }
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_deduplication)) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                                stringResource(R.string.settings_deduplication_threshold, String.format(Locale.US, "%.2f", userPreferences.deduplicationThreshold)),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = userPreferences.deduplicationThreshold,
                                onValueChange = { viewModel.updateDeduplicationThreshold(it) },
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
                                stringResource(R.string.settings_min_mentions, userPreferences.minMentions),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = userPreferences.minMentions.toFloat(),
                                onValueChange = { viewModel.updateMinMentions(it.toInt()) },
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
                SettingsSection(title = stringResource(R.string.settings_importance_filter)) {
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
        }

        if (isAddingNew) {
            AiConfigDialog(
                viewModel = viewModel,
                onDismiss = { isAddingNew = false },
                onConfirm = { viewModel.addAiConfig(it); isAddingNew = false }
            )
        }

        showConfigDialog?.let { config ->
            AiConfigDialog(
                viewModel = viewModel,
                config = config,
                onDismiss = { showConfigDialog = null },
                onConfirm = { viewModel.updateAiConfig(it); showConfigDialog = null }
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
    onDismiss: () -> Unit,
    onConfirm: (AiModelConfig) -> Unit
) {
    var name by remember { mutableStateOf(config?.name ?: "") }
    var apiKey by remember { mutableStateOf(config?.apiKey ?: "") }
    var provider by remember { mutableStateOf(config?.provider ?: AiProvider.GEMINI) }
    var modelName by remember { mutableStateOf(config?.modelName ?: "") }
    
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
                    OutlinedTextField(value = provider.displayName, onValueChange = {}, readOnly = true, label = { Text(stringResource(R.string.dialog_provider)) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProvider) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(), shape = MaterialTheme.shapes.large)
                    ExposedDropdownMenu(expanded = expandedProvider, onDismissRequest = { expandedProvider = false }) {
                        AiProvider.entries.forEach { entry ->
                            DropdownMenuItem(text = { Text(entry.displayName) }, onClick = { provider = entry; modelName = ""; expandedProvider = false })
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
                    else TextButton(onClick = { viewModel.loadModels(provider, apiKey) }) { Text(stringResource(R.string.dialog_load)) }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank() && apiKey.isNotBlank() && modelName.isNotBlank()) onConfirm(config?.copy(name = name, provider = provider, apiKey = apiKey, modelName = modelName) ?: AiModelConfig(name = name, provider = provider, apiKey = apiKey, modelName = modelName)) }, enabled = name.isNotBlank() && apiKey.isNotBlank() && modelName.isNotBlank() && !isLoadingModels, shape = MaterialTheme.shapes.large) { Text(stringResource(if (config == null) R.string.add else R.string.save)) }
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

package com.andrewwin.sumup.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.AiModelConfig
import com.andrewwin.sumup.data.local.entities.AiProvider
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel()
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
                    IconButton(onClick = {}) {
                        Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null)
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                SettingsSectionTitle(icon = Icons.Default.Person, title = stringResource(R.string.settings_profile))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_user_name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.settings_user_email), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {},
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(stringResource(R.string.settings_login_logout))
                            }
                        }
                    }
                }
            }

            item {
                Text(stringResource(R.string.settings_ai_models_label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            item {
                SettingsSectionHeader(title = stringResource(R.string.settings_cloud_api_label), onAdd = { isAddingNew = true })
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (aiConfigs.isEmpty()) {
                            Text(
                                stringResource(R.string.settings_api_keys_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        } else {
                            aiConfigs.forEach { config ->
                                AiKeyItem(
                                    config = config,
                                    onEdit = { showConfigDialog = config },
                                    onDelete = { viewModel.deleteAiConfig(config) },
                                    onToggle = { viewModel.toggleAiConfig(config, it) }
                                )
                                if (config != aiConfigs.last()) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                    }
                }
            }

            item {
                SettingsSectionHeader(title = stringResource(R.string.settings_deduplication))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.settings_enable_deduplication), modifier = Modifier.weight(1f))
                            Switch(
                                checked = userPreferences.isDeduplicationEnabled,
                                onCheckedChange = { viewModel.updateDeduplicationEnabled(it) },
                                enabled = downloadState is ModelDownloadState.Ready
                            )
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        Text(stringResource(R.string.settings_deduplication_threshold, String.format(Locale.US, "%.2f", userPreferences.deduplicationThreshold)))
                        Slider(
                            value = userPreferences.deduplicationThreshold,
                            onValueChange = { viewModel.updateDeduplicationThreshold(it) },
                            valueRange = 0.5f..0.99f,
                            steps = 48
                        )

                        Spacer(Modifier.height(16.dp))

                        val statusText = when (val s = downloadState) {
                            is ModelDownloadState.Idle -> stringResource(R.string.model_status_idle)
                            is ModelDownloadState.Downloading -> stringResource(R.string.model_status_downloading, s.progress)
                            is ModelDownloadState.Loading -> stringResource(R.string.model_status_loading)
                            is ModelDownloadState.Ready -> stringResource(R.string.model_status_ready)
                            is ModelDownloadState.Error -> stringResource(R.string.model_status_error, s.message)
                        }
                        
                        Text(stringResource(R.string.settings_model_status, statusText), style = MaterialTheme.typography.bodySmall)
                        
                        Spacer(Modifier.height(8.dp))
                        
                        if (downloadState is ModelDownloadState.Ready) {
                            OutlinedButton(onClick = { viewModel.deleteModel() }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.settings_delete_model))
                            }
                        } else {
                            Button(
                                onClick = { viewModel.downloadModel() }, 
                                modifier = Modifier.fillMaxWidth(),
                                enabled = downloadState !is ModelDownloadState.Downloading
                            ) {
                                Text(stringResource(R.string.settings_download_model))
                            }
                        }
                    }
                }
            }

            item {
                SettingsSectionHeader(title = stringResource(R.string.settings_scheduling))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.settings_scheduled_summary), modifier = Modifier.weight(1f))
                            Switch(
                                checked = userPreferences.isScheduledSummaryEnabled,
                                onCheckedChange = { viewModel.updateScheduledSummary(it, userPreferences.scheduledHour, userPreferences.scheduledMinute) }
                            )
                        }
                        if (userPreferences.isScheduledSummaryEnabled) {
                            TextButton(onClick = { showTimePicker = true }) {
                                Icon(Icons.Default.AccessTime, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.settings_time_label, String.format(Locale.getDefault(), "%02d:%02d", userPreferences.scheduledHour, userPreferences.scheduledMinute)))
                            }
                        }
                    }
                }
            }

            item {
                SettingsSectionHeader(title = stringResource(R.string.settings_local_models))
                Card(
                    modifier = Modifier.fillMaxWidth().alpha(0.5f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Text(stringResource(R.string.settings_coming_soon), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
                }
            }

            item {
                SettingsSectionHeader(title = stringResource(R.string.settings_extractive_models))
                Card(
                    modifier = Modifier.fillMaxWidth().alpha(0.5f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Text(stringResource(R.string.settings_coming_soon), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
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
fun SettingsSectionTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SettingsSectionHeader(title: String, onAdd: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        onAdd?.let {
            IconButton(onClick = it, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.AddCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
    val maskedKey = remember(config.apiKey) {
        if (config.apiKey.length > 8) config.apiKey.take(4) + "...." + config.apiKey.takeLast(4)
        else "...."
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = config.name, style = MaterialTheme.typography.titleSmall)
            Text(text = "${config.provider.displayName} • ${config.modelName} • $maskedKey", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = config.isEnabled, onCheckedChange = onToggle, modifier = Modifier.scale(0.7f))
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
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
        title = { Text(if (config == null) stringResource(R.string.settings_add_api_key) else stringResource(R.string.settings_edit_api_key)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.dialog_config_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                ExposedDropdownMenuBox(expanded = expandedProvider, onExpandedChange = { expandedProvider = !expandedProvider }) {
                    OutlinedTextField(value = provider.displayName, onValueChange = {}, readOnly = true, label = { Text(stringResource(R.string.dialog_provider)) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProvider) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth())
                    ExposedDropdownMenu(expanded = expandedProvider, onDismissRequest = { expandedProvider = false }) {
                        AiProvider.entries.forEach { entry ->
                            DropdownMenuItem(text = { Text(entry.displayName) }, onClick = { provider = entry; modelName = ""; expandedProvider = false })
                        }
                    }
                }
                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text(stringResource(R.string.dialog_api_key)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ExposedDropdownMenuBox(expanded = expandedModel, onExpandedChange = { if (availableModels.isNotEmpty()) expandedModel = !expandedModel }, modifier = Modifier.weight(1f)) {
                        OutlinedTextField(value = modelName, onValueChange = { modelName = it }, label = { Text(stringResource(R.string.dialog_model)) }, placeholder = { Text(if (isLoadingModels) stringResource(R.string.dialog_model_loading) else stringResource(R.string.dialog_model_select)) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedModel) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, true).fillMaxWidth())
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
            Button(onClick = { if (name.isNotBlank() && apiKey.isNotBlank() && modelName.isNotBlank()) onConfirm(config?.copy(name = name, provider = provider, apiKey = apiKey, modelName = modelName) ?: AiModelConfig(name = name, provider = provider, apiKey = apiKey, modelName = modelName)) }, enabled = name.isNotBlank() && apiKey.isNotBlank() && modelName.isNotBlank() && !isLoadingModels) { Text(if (config == null) stringResource(R.string.add) else stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledTimePickerDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    val timeState = rememberTimePickerState(initialHour = hour, initialMinute = minute)
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = { onConfirm(timeState.hour, timeState.minute) }) { Text("OK") } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }, text = { TimePicker(state = timeState) })
}

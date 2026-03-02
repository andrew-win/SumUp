package com.andrewwin.sumup.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.AiModelConfig
import com.andrewwin.sumup.data.local.entities.AiProvider
import com.andrewwin.sumup.data.local.entities.UserPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    val aiConfigs by viewModel.aiConfigs.collectAsState()
    val userPreferences by viewModel.userPreferences.collectAsState()
    
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsSectionTitle(icon = Icons.Default.Key, title = "API Ключі")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (aiConfigs.isEmpty()) {
                            Text(
                                "API ключі ще не додано",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
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

                        Button(
                            onClick = { isAddingNew = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Додати API Ключ")
                        }
                    }
                }
            }

            item {
                SettingsSectionTitle(icon = Icons.Default.Schedule, title = "Планування")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Створювати заплановані зведення",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = userPreferences.isScheduledSummaryEnabled,
                                onCheckedChange = { 
                                    viewModel.updateScheduledSummary(it, userPreferences.scheduledHour, userPreferences.scheduledMinute)
                                }
                            )
                        }
                        
                        if (userPreferences.isScheduledSummaryEnabled) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(
                                onClick = { showTimePicker = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AccessTime, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = String.format("Час: %02d:%02d", userPreferences.scheduledHour, userPreferences.scheduledMinute),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isAddingNew) {
            AiConfigDialog(
                viewModel = viewModel,
                onDismiss = { isAddingNew = false },
                onConfirm = { 
                    viewModel.addAiConfig(it)
                    isAddingNew = false
                }
            )
        }

        showConfigDialog?.let { config ->
            AiConfigDialog(
                viewModel = viewModel,
                config = config,
                onDismiss = { showConfigDialog = null },
                onConfirm = { 
                    viewModel.updateAiConfig(it)
                    showConfigDialog = null
                }
            )
        }

        if (showTimePicker) {
            ScheduledTimePickerDialog(
                hour = userPreferences.scheduledHour,
                minute = userPreferences.scheduledMinute,
                onDismiss = { showTimePicker = false },
                onConfirm = { h, m ->
                    viewModel.updateScheduledSummary(true, h, m)
                    showTimePicker = false
                }
            )
        }
    }
}

@Composable
fun SettingsSectionTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
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
        if (config.apiKey.length > 8) {
            config.apiKey.take(4) + "...." + config.apiKey.takeLast(4)
        } else "...."
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = config.name, style = MaterialTheme.typography.titleSmall)
            Text(
                text = "${config.provider.displayName} • $maskedKey",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = config.isEnabled, 
            onCheckedChange = onToggle,
            modifier = Modifier.scale(0.8f)
        )
        IconButton(onClick = onEdit) {
            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
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
        title = { Text(if (config == null) "Додати API Ключ" else "Редагувати API Ключ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Назва конфігурації") },
                    placeholder = { Text("Напр. Мій Gemini") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                ExposedDropdownMenuBox(
                    expanded = expandedProvider,
                    onExpandedChange = { expandedProvider = !expandedProvider }
                ) {
                    OutlinedTextField(
                        value = provider.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Провайдер") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProvider) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expandedProvider, onDismissRequest = { expandedProvider = false }) {
                        AiProvider.entries.forEach { entry ->
                            DropdownMenuItem(
                                text = { Text(entry.displayName) },
                                onClick = {
                                    provider = entry
                                    modelName = ""
                                    expandedProvider = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Ключ") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    ExposedDropdownMenuBox(
                        expanded = expandedModel,
                        onExpandedChange = { if (availableModels.isNotEmpty()) expandedModel = !expandedModel },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = modelName,
                            onValueChange = { modelName = it },
                            label = { Text("Модель") },
                            placeholder = { Text(if (isLoadingModels) "Завантаження..." else "Оберіть модель") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedModel) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, true).fillMaxWidth()
                        )
                        if (availableModels.isNotEmpty()) {
                            ExposedDropdownMenu(expanded = expandedModel, onDismissRequest = { expandedModel = false }) {
                                availableModels.forEach { model ->
                                    DropdownMenuItem(
                                        text = { Text(model) },
                                        onClick = {
                                            modelName = model
                                            expandedModel = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    if (isLoadingModels) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = { viewModel.loadModels(provider, apiKey) }) {
                            Text("Завантажити")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && apiKey.isNotBlank() && modelName.isNotBlank()) {
                        onConfirm(
                            config?.copy(name = name, provider = provider, apiKey = apiKey, modelName = modelName)
                                ?: AiModelConfig(name = name, provider = provider, apiKey = apiKey, modelName = modelName)
                        )
                    }
                },
                enabled = name.isNotBlank() && apiKey.isNotBlank() && modelName.isNotBlank() && !isLoadingModels
            ) {
                Text(if (config == null) "Додати" else "Зберегти")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Скасувати")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledTimePickerDialog(
    hour: Int,
    minute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val timeState = rememberTimePickerState(initialHour = hour, initialMinute = minute)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(timeState.hour, timeState.minute) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Скасувати")
            }
        },
        text = {
            TimePicker(state = timeState)
        }
    )
}

private fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.padding(((1f - scale) * 24f).dp).graphicsLayer(scaleX = scale, scaleY = scale)
)

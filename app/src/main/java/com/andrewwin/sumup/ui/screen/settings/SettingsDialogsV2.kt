package com.andrewwin.sumup.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.AiModelConfig
import com.andrewwin.sumup.data.local.entities.AiModelType
import com.andrewwin.sumup.data.local.entities.AiProvider
import com.andrewwin.sumup.ui.components.AppAnimatedDialog

@Composable
fun SettingsConfirmDeleteDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun SettingsBackupOptionRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsToggleRow(
        label = title,
        checked = checked,
        onCheckedChange = onCheckedChange
    )
}

@Composable
fun SettingsBackupCheckboxRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun SettingsEmailAuthDialog(
    onDismiss: () -> Unit,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String) -> Unit,
    onGoogleLogin: () -> Unit
) {
    val emailRequiredMessage = stringResource(R.string.settings_validation_email_required)
    val emailInvalidMessage = stringResource(R.string.settings_validation_email_invalid)
    val passwordShortMessage = stringResource(R.string.settings_validation_password_short)
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }
    AppAnimatedDialog(
        visible = true,
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_login_register),
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        validationError = null
                    },
                    label = { Text(stringResource(R.string.settings_email)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        validationError = null
                    },
                    label = { Text(stringResource(R.string.settings_password)) },
                    singleLine = true,
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        val normalizedEmail = email.trim()
                        when {
                            normalizedEmail.isBlank() -> validationError = emailRequiredMessage
                            !android.util.Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches() -> {
                                validationError = emailInvalidMessage
                            }
                            password.length < 6 -> validationError = passwordShortMessage
                            else -> {
                                onLogin(normalizedEmail, password)
                                onDismiss()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Text(stringResource(R.string.settings_login))
                }

                OutlinedButton(
                    onClick = {
                        onGoogleLogin()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_google_logo),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.settings_login_google))
                }

                OutlinedButton(
                    onClick = {
                        val normalizedEmail = email.trim()
                        when {
                            normalizedEmail.isBlank() -> validationError = emailRequiredMessage
                            !android.util.Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches() -> {
                                validationError = emailInvalidMessage
                            }
                            password.length < 6 -> validationError = passwordShortMessage
                            else -> {
                                onRegister(normalizedEmail, password)
                                onDismiss()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Text(stringResource(R.string.settings_register))
                }

                validationError?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSyncPassphraseDialog(
    hasExistingPassphrase: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onClear: () -> Unit
) {
    val tooShortMessage = stringResource(R.string.settings_sync_passphrase_too_short)
    val mismatchMessage = stringResource(R.string.settings_sync_passphrase_mismatch)
    var passphrase by rememberSaveable { mutableStateOf("") }
    var confirmPassphrase by rememberSaveable { mutableStateOf("") }
    var isPassphraseVisible by rememberSaveable { mutableStateOf(false) }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }

    AppAnimatedDialog(
        visible = true,
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_sync_passphrase_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }

                Text(
                    text = stringResource(R.string.settings_sync_passphrase_dialog_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = passphrase,
                    onValueChange = {
                        passphrase = it
                        validationError = null
                    },
                    label = { Text(stringResource(R.string.settings_sync_passphrase_field)) },
                    singleLine = true,
                    visualTransformation = if (isPassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPassphraseVisible = !isPassphraseVisible }) {
                            Icon(
                                imageVector = if (isPassphraseVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = confirmPassphrase,
                    onValueChange = {
                        confirmPassphrase = it
                        validationError = null
                    },
                    label = { Text(stringResource(R.string.settings_sync_passphrase_confirm_field)) },
                    singleLine = true,
                    visualTransformation = if (isPassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                validationError?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (hasExistingPassphrase) {
                        OutlinedButton(
                            onClick = {
                                onClear()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text(stringResource(R.string.settings_sync_passphrase_clear))
                        }
                    }
                    Button(
                        onClick = {
                            val normalized = passphrase.trim()
                            when {
                                normalized.length < 8 -> validationError = tooShortMessage
                                normalized != confirmPassphrase.trim() -> validationError = mismatchMessage
                                else -> {
                                    onSave(normalized)
                                    onDismiss()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsAiKeyItem(
    config: AiModelConfig,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(config.provider.iconRes),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.size(14.dp))
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
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAiConfigDialog(
    viewModel: SettingsViewModel,
    config: AiModelConfig? = null,
    type: AiModelType,
    existingConfigs: List<AiModelConfig>,
    onDismiss: () -> Unit,
    onConfirm: (AiModelConfig) -> Unit
) {
    var name by remember(config?.id) { mutableStateOf(config?.name ?: "") }
    var apiKey by remember(config?.id) { mutableStateOf(config?.apiKey ?: "") }
    var provider by remember(config?.id) { mutableStateOf(config?.provider ?: AiProvider.GEMINI) }
    var modelName by remember(config?.id) { mutableStateOf(config?.modelName ?: "") }
    val providerLabel = stringResource(provider.labelRes)

    val availableModels by viewModel.availableModels.collectAsState()
    val isLoadingModels by viewModel.isLoadingModels.collectAsState()

    var expandedProvider by remember { mutableStateOf(false) }
    var expandedModel by remember { mutableStateOf(false) }

    AppAnimatedDialog(
        visible = true,
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(if (config == null) R.string.settings_add_api_key else R.string.settings_edit_api_key),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.dialog_config_name)) },
                        placeholder = { Text(stringResource(R.string.dialog_config_name_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.large
                    )

                    ExposedDropdownMenuBox(
                        expanded = expandedProvider,
                        onExpandedChange = { expandedProvider = !expandedProvider }
                    ) {
                        OutlinedTextField(
                            value = stringResource(provider.labelRes),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.dialog_provider)) },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(provider.iconRes),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProvider) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                .fillMaxWidth(),
                            shape = MaterialTheme.shapes.large
                        )
                        ExposedDropdownMenu(
                            expanded = expandedProvider,
                            onDismissRequest = { expandedProvider = false }
                        ) {
                            AiProvider.entries.forEach { entry ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(entry.labelRes)) },
                                    onClick = {
                                        provider = entry
                                        modelName = ""
                                        expandedProvider = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(entry.iconRes),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text(stringResource(R.string.dialog_api_key)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.large
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
                                label = { Text(stringResource(R.string.dialog_model)) },
                                placeholder = {
                                    Text(
                                        if (isLoadingModels) stringResource(R.string.dialog_model_loading)
                                        else stringResource(R.string.dialog_model_select)
                                    )
                                },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedModel) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryEditable, true)
                                    .fillMaxWidth(),
                                shape = MaterialTheme.shapes.large
                            )
                            if (availableModels.isNotEmpty()) {
                                ExposedDropdownMenu(
                                    expanded = expandedModel,
                                    onDismissRequest = { expandedModel = false }
                                ) {
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
                        Spacer(Modifier.size(8.dp))
                        if (isLoadingModels) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            TextButton(onClick = { viewModel.loadModels(provider, apiKey, type) }) {
                                Text(stringResource(R.string.dialog_load))
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = {
                            val trimmedApiKey = apiKey.trim()
                            val trimmedModelName = modelName.trim()
                            val resolvedName = name.trim().ifBlank {
                                buildAutoAiConfigNameV2(
                                    providerLabel = providerLabel,
                                    provider = provider,
                                    existingConfigs = existingConfigs,
                                    editingConfigId = config?.id
                                )
                            }
                            if (trimmedApiKey.isNotBlank() && trimmedModelName.isNotBlank()) {
                                onConfirm(
                                    config?.copy(
                                        name = resolvedName,
                                        provider = provider,
                                        apiKey = trimmedApiKey,
                                        modelName = trimmedModelName
                                    ) ?: AiModelConfig(
                                        name = resolvedName,
                                        provider = provider,
                                        apiKey = trimmedApiKey,
                                        modelName = trimmedModelName,
                                        type = type
                                    )
                                )
                            }
                        },
                        enabled = apiKey.isNotBlank() && modelName.isNotBlank() && !isLoadingModels,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(stringResource(if (config == null) R.string.add else R.string.save))
                    }
                }
            }
        }
    }
}

private fun buildAutoAiConfigNameV2(
    providerLabel: String,
    provider: AiProvider,
    existingConfigs: List<AiModelConfig>,
    editingConfigId: Long?
): String {
    val usedNumbers = existingConfigs
        .asSequence()
        .filter { it.id != editingConfigId && it.provider == provider }
        .mapNotNull { config ->
            val normalizedName = config.name.trim()
            val prefix = "$providerLabel "
            if (normalizedName.startsWith(prefix)) {
                normalizedName.removePrefix(prefix).toIntOrNull()
            } else {
                null
            }
        }
        .toSet()

    val nextNumber = generateSequence(1) { it + 1 }
        .first { it !in usedNumbers }

    return "$providerLabel $nextNumber"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScheduledTimePickerDialog(
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
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TimePicker(state = timeState)
            }
        }
    )
}

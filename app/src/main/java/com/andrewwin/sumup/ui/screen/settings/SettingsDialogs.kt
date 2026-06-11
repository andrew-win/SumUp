package com.andrewwin.sumup.ui.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.andrewwin.sumup.R
import com.andrewwin.sumup.domain.ai.model.AiModelConfig
import com.andrewwin.sumup.domain.ai.model.AiModelType
import com.andrewwin.sumup.domain.ai.model.AiProvider
import com.andrewwin.sumup.ui.components.AppAnimatedDialog

private const val GEMINI_API_KEY_URL = "https://aistudio.google.com/app/apikey"
private const val GROQ_API_KEY_URL = "https://console.groq.com/keys"
private const val OPENROUTER_API_KEY_URL = "https://openrouter.ai/settings/keys"
private const val COHERE_API_KEY_URL = "https://dashboard.cohere.com/api-keys"
private const val OPENAI_API_KEY_URL = "https://platform.openai.com/api-keys"
private const val ANTHROPIC_API_KEY_URL = "https://console.anthropic.com/settings/keys"

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
fun SettingsCloudSyncApiKeysWarningDialog(
    onEnterPassphrase: () -> Unit,
    onEnableWithoutApiKeys: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_sync_api_keys_warning_title)) },
        text = { Text(stringResource(R.string.settings_sync_api_keys_warning_body)) },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    onEnableWithoutApiKeys()
                    onDismiss()
                }) {
                    Text(stringResource(R.string.settings_sync_api_keys_warning_enable_without_keys))
                }
                TextButton(onClick = {
                    onEnterPassphrase()
                    onDismiss()
                }) {
                    Text(stringResource(R.string.settings_sync_api_keys_warning_enter_passphrase))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun ApiKeySecurityNoticeDialog(
    onConfirm: (doNotShowAgain: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var doNotShowAgain by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_api_key_security_notice_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.settings_api_key_security_notice_body))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { doNotShowAgain = !doNotShowAgain },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = doNotShowAgain,
                        onCheckedChange = { doNotShowAgain = it }
                    )
                    Text(
                        text = stringResource(R.string.settings_api_key_security_notice_do_not_show),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(doNotShowAgain) }) {
                Text(stringResource(R.string.settings_api_key_security_notice_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun ApiKeyHelpDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current

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
                        text = stringResource(R.string.api_key_help_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.api_key_help_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.api_key_help_instruction),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    ApiKeyHelpSectionTitle(text = stringResource(R.string.api_key_help_free_title))
                    ApiKeyProviderLink(
                        name = stringResource(R.string.api_key_help_gemini_name),
                        description = stringResource(R.string.api_key_help_gemini_desc),
                        onClick = { uriHandler.openUri(GEMINI_API_KEY_URL) }
                    )
                    ApiKeyProviderLink(
                        name = stringResource(R.string.api_key_help_groq_name),
                        description = stringResource(R.string.api_key_help_groq_desc),
                        onClick = { uriHandler.openUri(GROQ_API_KEY_URL) }
                    )
                    ApiKeyProviderLink(
                        name = stringResource(R.string.api_key_help_openrouter_name),
                        description = stringResource(R.string.api_key_help_openrouter_desc),
                        onClick = { uriHandler.openUri(OPENROUTER_API_KEY_URL) }
                    )
                    ApiKeyProviderLink(
                        name = stringResource(R.string.api_key_help_cohere_name),
                        description = stringResource(R.string.api_key_help_cohere_desc),
                        onClick = { uriHandler.openUri(COHERE_API_KEY_URL) }
                    )

                    ApiKeyHelpSectionTitle(text = stringResource(R.string.api_key_help_paid_title))
                    ApiKeyProviderLink(
                        name = stringResource(R.string.api_key_help_openai_name),
                        description = stringResource(R.string.api_key_help_openai_desc),
                        onClick = { uriHandler.openUri(OPENAI_API_KEY_URL) }
                    )
                    ApiKeyProviderLink(
                        name = stringResource(R.string.api_key_help_claude_name),
                        description = stringResource(R.string.api_key_help_claude_desc),
                        onClick = { uriHandler.openUri(ANTHROPIC_API_KEY_URL) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ApiKeyHelpSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun ApiKeyProviderLink(
    name: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.api_key_help_open_link),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SettingsBackupOptionRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isHelpMode: Boolean = false,
    helpDescription: String? = null,
    onHelpRequest: ((String) -> Unit)? = null
) {
    SettingsToggleRow(
        label = title,
        checked = checked,
        onCheckedChange = onCheckedChange,
        isHelpMode = isHelpMode,
        helpDescription = helpDescription,
        onHelpRequest = onHelpRequest
    )
}

@Composable
fun SettingsBackupCheckboxRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isHelpMode: Boolean = false,
    helpDescription: String? = null,
    onHelpRequest: ((String) -> Unit)? = null
) {
    SettingsHelpTarget(
        isHelpMode = isHelpMode,
        helpDescription = helpDescription,
        onHelpRequest = onHelpRequest,
        contentDescription = title
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
                onCheckedChange = onCheckedChange,
                modifier = Modifier.semantics {
                    contentDescription = title
                }
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
        }
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
    isSignedIn: Boolean,
    isMatchingExistingPassphrase: (String) -> Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onClear: () -> Unit
) {
    val tooShortMessage = stringResource(R.string.settings_sync_passphrase_too_short)
    val mismatchMessage = stringResource(R.string.settings_sync_passphrase_mismatch)
    val replaceWarningMessage = stringResource(R.string.settings_sync_passphrase_replace_warning)
    val passphraseMatchMessage = stringResource(R.string.settings_sync_passphrase_matches_existing)
    val passphraseMismatchMessage = stringResource(R.string.settings_sync_passphrase_differs_existing)
    val passphraseUnknownMessage = stringResource(R.string.settings_sync_passphrase_match_unknown_signed_out)
    val passphraseNoLocalMessage = stringResource(R.string.settings_sync_passphrase_no_local_existing)
    var passphrase by rememberSaveable { mutableStateOf("") }
    var confirmPassphrase by rememberSaveable { mutableStateOf("") }
    var isPassphraseVisible by rememberSaveable { mutableStateOf(false) }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }
    val normalizedPassphrase = passphrase.trim()
    val passphraseMatchStatus = remember(
        hasExistingPassphrase,
        isSignedIn,
        normalizedPassphrase
    ) {
        when {
            normalizedPassphrase.isBlank() -> null
            !isSignedIn -> passphraseUnknownMessage
            !hasExistingPassphrase -> passphraseNoLocalMessage
            isMatchingExistingPassphrase(normalizedPassphrase) -> passphraseMatchMessage
            else -> passphraseMismatchMessage
        }
    }
    val isPassphraseMatchWarning = passphraseMatchStatus == passphraseMismatchMessage ||
        passphraseMatchStatus == passphraseUnknownMessage
    val shouldShowReplaceWarning = remember(
        hasExistingPassphrase,
        isSignedIn,
        normalizedPassphrase,
        confirmPassphrase
    ) {
        hasExistingPassphrase &&
            isSignedIn &&
            normalizedPassphrase.length >= 8 &&
            normalizedPassphrase == confirmPassphrase.trim() &&
            !isMatchingExistingPassphrase(normalizedPassphrase)
    }

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

                passphraseMatchStatus?.let { message ->
                    Text(
                        text = message,
                        color = when {
                            isPassphraseMatchWarning -> MaterialTheme.colorScheme.error
                            message == passphraseMatchMessage -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }

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

                if (shouldShowReplaceWarning) {
                    Text(
                        text = replaceWarningMessage,
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
    onToggle: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    isHelpMode: Boolean,
    onHelpRequest: (String) -> Unit,
    helpDescription: String
) {
    val itemContentDescription = stringResource(R.string.settings_cd_api_key_item, config.name)
    val providerLabel = stringResource(config.provider.labelRes)
    val toggleContentDescription = stringResource(R.string.settings_cd_toggle_api_key, config.name)
    SettingsHelpTarget(
        isHelpMode = isHelpMode,
        helpDescription = helpDescription,
        onHelpRequest = onHelpRequest,
        contentDescription = itemContentDescription
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .semantics {
                    contentDescription = itemContentDescription
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(config.provider.iconRes),
                contentDescription = providerLabel,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = config.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = config.modelName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.settings_api_key_move_up),
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.settings_api_key_move_down),
                    modifier = Modifier.size(20.dp)
                )
            }
            Box(
                modifier = Modifier.size(width = 44.dp, height = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Switch(
                    checked = config.isEnabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier
                        .scale(0.75f)
                        .semantics {
                            contentDescription = toggleContentDescription
                        }
                )
            }
            Box {
                var showDropdown by remember { mutableStateOf(false) }
                IconButton(
                    onClick = { showDropdown = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.settings_cd_api_key_menu, config.name),
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = showDropdown,
                    onDismissRequest = { showDropdown = false },
                    shape = MaterialTheme.shapes.large,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_edit_api_key)) },
                        trailingIcon = {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            onEdit()
                            showDropdown = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                        trailingIcon = {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            onDelete()
                            showDropdown = false
                        }
                    )
                }
            }
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
    var isApiKeyVisible by remember { mutableStateOf(false) }
    val providerLabel = stringResource(provider.labelRes)

    val availableModels by viewModel.availableModels.collectAsState()
    val isLoadingModels by viewModel.isLoadingModels.collectAsState()

    var expandedProvider by remember { mutableStateOf(false) }
    var expandedModel by remember { mutableStateOf(false) }
    val normalizedName = name.trim()
    val normalizedApiKey = apiKey.trim()
    val hasDuplicateName = remember(normalizedName, existingConfigs, config?.id) {
        normalizedName.isNotBlank() && existingConfigs.any {
            it.id != config?.id && it.name.trim().equals(normalizedName, ignoreCase = true)
        }
    }
    val hasDuplicateApiKey = remember(normalizedApiKey, existingConfigs, config?.id) {
        normalizedApiKey.isNotBlank() && existingConfigs.any {
            it.id != config?.id && it.apiKey.trim() == normalizedApiKey
        }
    }
    val configNameErrorText = if (hasDuplicateName) {
        stringResource(R.string.validation_ai_config_name_exists)
    } else if (normalizedName.isBlank()) {
        stringResource(R.string.validation_name_required)
    } else {
        null
    }
    val apiKeyErrorText = if (hasDuplicateApiKey) {
        stringResource(R.string.validation_api_key_exists)
    } else {
        null
    }

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
                        visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val icon = if (isApiKeyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility
                            IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                Icon(imageVector = icon, contentDescription = null)
                            }
                        },
                        shape = MaterialTheme.shapes.large,
                        isError = apiKeyErrorText != null,
                        supportingText = {
                            if (apiKeyErrorText != null) {
                                Text(apiKeyErrorText)
                            }
                        }
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

                    if (type == AiModelType.SUMMARY) {
                        Text(
                            text = stringResource(R.string.dialog_summary_model_recommendation),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(stringResource(R.string.dialog_config_name)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = MaterialTheme.shapes.large,
                            isError = configNameErrorText != null,
                            supportingText = {
                                if (configNameErrorText != null) {
                                    Text(configNameErrorText)
                                }
                            }
                        )
                        Spacer(Modifier.size(8.dp))
                        TextButton(
                            onClick = {
                                name = buildAutoAiConfigNameV2(
                                    providerLabel = providerLabel,
                                    provider = provider,
                                    existingConfigs = existingConfigs,
                                    editingConfigId = config?.id
                                )
                            }
                        ) {
                            Text(stringResource(R.string.dialog_config_name_fetch))
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
                            val trimmedApiKey = normalizedApiKey
                            val trimmedModelName = modelName.trim()
                            if (
                                normalizedName.isNotBlank() &&
                                trimmedApiKey.isNotBlank() &&
                                trimmedModelName.isNotBlank() &&
                                configNameErrorText == null &&
                                apiKeyErrorText == null
                            ) {
                                onConfirm(
                                    config?.copy(
                                        name = normalizedName,
                                        provider = provider,
                                        apiKey = trimmedApiKey,
                                        modelName = trimmedModelName
                                    ) ?: AiModelConfig(
                                        name = normalizedName,
                                        provider = provider,
                                        apiKey = trimmedApiKey,
                                        modelName = trimmedModelName,
                                        type = type
                                    )
                                )
                            }
                        },
                        enabled = normalizedName.isNotBlank() &&
                            apiKey.isNotBlank() &&
                            modelName.isNotBlank() &&
                            !isLoadingModels &&
                            configNameErrorText == null &&
                            apiKeyErrorText == null,
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

package com.andrewwin.sumup.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andrewwin.sumup.R
import com.andrewwin.sumup.ui.components.AppHelpOverlayTarget
import com.andrewwin.sumup.ui.theme.appCardBorder
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAccountGroup(
    isHelpMode: Boolean,
    authUiState: AuthUiState,
    isCloudSyncEnabled: Boolean,
    syncIntervalHours: Int,
    syncStrategy: SyncConflictStrategy,
    syncOverwritePriority: SyncOverwritePriority,
    lastSyncAt: Long,
    syncSelection: BackupSelection,
    hasSyncPassphrase: Boolean,
    transferState: TransferState,
    onHelpRequest: (String) -> Unit,
    onSyncIntervalSelect: (Int) -> Unit,
    onSyncStrategySelect: (SyncConflictStrategy) -> Unit,
    onSyncOverwritePrioritySelect: (SyncOverwritePriority) -> Unit,
    onSyncEnabledChange: (Boolean) -> Unit,
    onSyncSelectionChange: (BackupSelection) -> Unit,
    onManageSyncPassphrase: () -> Unit,
    onSignInOutClick: () -> Unit,
    onSyncNowClick: () -> Unit
) {
    var syncIntervalExpanded by remember { mutableStateOf(false) }
    val unifiedButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSection(
            title = "",
            boxed = true,
            isHelpMode = isHelpMode,
            helpDescription = stringResource(R.string.settings_help_section_account_profile),
            onHelpRequest = onHelpRequest
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)
            ) {
                if (authUiState.isSignedIn) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = authUiState.displayName.ifBlank { stringResource(R.string.settings_user_name) },
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = authUiState.email.ifBlank { stringResource(R.string.settings_not_signed_in) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onSignInOutClick,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = unifiedButtonColors
                    ) {
                        Text(stringResource(R.string.settings_logout))
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .border(
                                border = appCardBorder(),
                                shape = CircleShape
                            )
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.settings_account_login_title),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(R.string.settings_account_login_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    Button(
                        onClick = onSignInOutClick,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = unifiedButtonColors
                    ) {
                        Text(
                            text = stringResource(R.string.settings_account_login_btn),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
        SettingsSection(
            title = stringResource(R.string.settings_sync),
            boxed = true,
            isHelpMode = isHelpMode,
            helpDescription = stringResource(R.string.settings_help_section_sync),
            onHelpRequest = onHelpRequest
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_sync_strategy_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val options = listOf(
                        SyncConflictStrategy.OVERWRITE to R.string.settings_sync_strategy_overwrite,
                        SyncConflictStrategy.MERGE to R.string.settings_sync_strategy_merge
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        options.forEachIndexed { index, (value, labelRes) ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                                onClick = { onSyncStrategySelect(value) },
                                selected = syncStrategy == value
                            ) {
                                Text(text = stringResource(labelRes))
                            }
                        }
                    }
                }
                if (syncStrategy == SyncConflictStrategy.OVERWRITE) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.settings_sync_overwrite_priority_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val overwritePriorityOptions = listOf(
                            SyncOverwritePriority.LOCAL to R.string.settings_sync_overwrite_priority_local,
                            SyncOverwritePriority.CLOUD to R.string.settings_sync_overwrite_priority_cloud
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            overwritePriorityOptions.forEachIndexed { index, (value, labelRes) ->
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = overwritePriorityOptions.size
                                    ),
                                    onClick = { onSyncOverwritePrioritySelect(value) },
                                    selected = syncOverwritePriority == value
                                ) {
                                    Text(text = stringResource(labelRes))
                                }
                            }
                        }
                    }
                }
                SettingsBackupOptionRow(
                    title = stringResource(R.string.settings_sync_enabled),
                    checked = isCloudSyncEnabled,
                    onCheckedChange = onSyncEnabledChange
                )
                ExposedDropdownMenuBox(
                    expanded = syncIntervalExpanded,
                    onExpandedChange = { syncIntervalExpanded = !syncIntervalExpanded }
                ) {
                    OutlinedTextField(
                        value = stringResource(R.string.settings_sync_interval_hours, syncIntervalHours),
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        label = { Text(stringResource(R.string.settings_sync_interval_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = syncIntervalExpanded) },
                        shape = MaterialTheme.shapes.large
                    )
                    DropdownMenu(
                        expanded = syncIntervalExpanded,
                        onDismissRequest = { syncIntervalExpanded = false }
                    ) {
                        listOf(1, 3, 6, 12, 24).forEach { h ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings_sync_interval_hours, h)) },
                                onClick = { syncIntervalExpanded = false; onSyncIntervalSelect(h) }
                            )
                        }
                    }
                }
                SettingsBackupCheckboxRow(
                    title = stringResource(R.string.settings_backup_sources),
                    checked = syncSelection.includeSources,
                    onCheckedChange = { onSyncSelectionChange(syncSelection.copy(includeSources = it)) }
                )
                SettingsBackupCheckboxRow(
                    title = stringResource(R.string.settings_backup_subscriptions),
                    checked = syncSelection.includeSubscriptions,
                    onCheckedChange = { onSyncSelectionChange(syncSelection.copy(includeSubscriptions = it)) }
                )
                SettingsBackupCheckboxRow(
                    title = stringResource(R.string.settings_backup_saved_articles),
                    checked = syncSelection.includeSavedArticles,
                    onCheckedChange = { onSyncSelectionChange(syncSelection.copy(includeSavedArticles = it)) }
                )
                SettingsBackupCheckboxRow(
                    title = stringResource(R.string.settings_backup_settings_no_api),
                    checked = syncSelection.includeSettingsNoApi,
                    onCheckedChange = { onSyncSelectionChange(syncSelection.copy(includeSettingsNoApi = it)) }
                )
                SettingsBackupCheckboxRow(
                    title = stringResource(R.string.settings_backup_api_keys),
                    checked = syncSelection.includeApiKeys,
                    onCheckedChange = { onSyncSelectionChange(syncSelection.copy(includeApiKeys = it)) }
                )
                SyncPassphraseField(
                    isHelpMode = isHelpMode,
                    hasSyncPassphrase = hasSyncPassphrase,
                    onHelpRequest = onHelpRequest,
                    onManageSyncPassphrase = onManageSyncPassphrase
                )
                if (authUiState.isSignedIn) {
                    Button(
                        onClick = onSyncNowClick,
                        enabled = isCloudSyncEnabled && transferState !is TransferState.Working,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = unifiedButtonColors
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.settings_sync_now))
                    }
                    Text(
                        text = if (lastSyncAt > 0L) {
                            stringResource(
                                R.string.settings_sync_last_time,
                                formatLastSyncDateTime(lastSyncAt)
                            )
                        } else {
                            stringResource(R.string.settings_sync_last_time_never)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private fun formatLastSyncDateTime(timestamp: Long): String {
    return DateFormat.getDateTimeInstance(
        DateFormat.MEDIUM,
        DateFormat.SHORT,
        Locale.getDefault()
    ).format(Date(timestamp))
}

@Composable
fun SettingsTransferGroupContent(
    isHelpMode: Boolean,
    exportSelection: BackupSelection,
    importSelection: BackupSelection,
    importStrategy: SyncConflictStrategy,
    hasSyncPassphrase: Boolean,
    transferState: TransferState,
    onHelpRequest: (String) -> Unit,
    onExportSelectionChange: (BackupSelection) -> Unit,
    onImportSelectionChange: (BackupSelection) -> Unit,
    onImportStrategyChange: (SyncConflictStrategy) -> Unit,
    onManageSyncPassphrase: () -> Unit,
    onImportClick: () -> Unit,
    onExportClick: (String) -> Unit
) {
    val unifiedButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsTransferSection(
            title = stringResource(R.string.settings_export_block_title),
            isHelpMode = isHelpMode,
            helpDescription = stringResource(R.string.settings_help_section_export),
            selection = exportSelection,
            onSelectionChange = onExportSelectionChange,
            buttonLabel = stringResource(R.string.settings_export_button),
            buttonIcon = Icons.Default.ArrowUpward,
            transferState = transferState,
            onHelpRequest = onHelpRequest,
            onActionClick = {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                onExportClick("sumup-backup-$date.json")
            },
            buttonColors = unifiedButtonColors
        )
        SettingsTransferSection(
            title = stringResource(R.string.settings_import_block_title),
            isHelpMode = isHelpMode,
            helpDescription = stringResource(R.string.settings_help_section_import),
            selection = importSelection,
            onSelectionChange = onImportSelectionChange,
            importStrategy = importStrategy,
            onImportStrategyChange = onImportStrategyChange,
            hasSyncPassphrase = hasSyncPassphrase,
            onManageSyncPassphrase = onManageSyncPassphrase,
            buttonLabel = stringResource(R.string.settings_import_button),
            buttonIcon = Icons.Default.ArrowDownward,
            transferState = transferState,
            onHelpRequest = onHelpRequest,
            onActionClick = onImportClick,
            buttonColors = unifiedButtonColors
        )
    }
}

@Composable
private fun SettingsTransferSection(
    title: String,
    isHelpMode: Boolean,
    helpDescription: String,
    selection: BackupSelection,
    onSelectionChange: (BackupSelection) -> Unit,
    importStrategy: SyncConflictStrategy? = null,
    onImportStrategyChange: ((SyncConflictStrategy) -> Unit)? = null,
    hasSyncPassphrase: Boolean? = null,
    onManageSyncPassphrase: (() -> Unit)? = null,
    buttonLabel: String,
    buttonIcon: androidx.compose.ui.graphics.vector.ImageVector,
    transferState: TransferState,
    onHelpRequest: (String) -> Unit,
    onActionClick: () -> Unit,
    buttonColors: androidx.compose.material3.ButtonColors
) {
    SettingsSection(
        title = title,
        boxed = true,
        isHelpMode = isHelpMode,
        helpDescription = helpDescription,
        onHelpRequest = onHelpRequest
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (importStrategy != null && onImportStrategyChange != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_import_strategy_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val importStrategyOptions = listOf(
                        SyncConflictStrategy.OVERWRITE to R.string.settings_sync_strategy_overwrite,
                        SyncConflictStrategy.MERGE to R.string.settings_sync_strategy_merge
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        importStrategyOptions.forEachIndexed { index, (value, labelRes) ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = importStrategyOptions.size
                                ),
                                onClick = { onImportStrategyChange(value) },
                                selected = importStrategy == value
                            ) {
                                Text(text = stringResource(labelRes))
                            }
                        }
                    }
                }
            }
            SettingsBackupCheckboxRow(
                title = stringResource(R.string.settings_backup_sources),
                checked = selection.includeSources,
                onCheckedChange = { onSelectionChange(selection.copy(includeSources = it)) }
            )
            SettingsBackupCheckboxRow(
                title = stringResource(R.string.settings_backup_subscriptions),
                checked = selection.includeSubscriptions,
                onCheckedChange = { onSelectionChange(selection.copy(includeSubscriptions = it)) }
            )
            SettingsBackupCheckboxRow(
                title = stringResource(R.string.settings_backup_saved_articles),
                checked = selection.includeSavedArticles,
                onCheckedChange = { onSelectionChange(selection.copy(includeSavedArticles = it)) }
            )
            SettingsBackupCheckboxRow(
                title = stringResource(R.string.settings_backup_settings_no_api),
                checked = selection.includeSettingsNoApi,
                onCheckedChange = { onSelectionChange(selection.copy(includeSettingsNoApi = it)) }
            )
            SettingsBackupCheckboxRow(
                title = stringResource(R.string.settings_backup_api_keys),
                checked = selection.includeApiKeys,
                onCheckedChange = { onSelectionChange(selection.copy(includeApiKeys = it)) }
            )
            if (hasSyncPassphrase != null && onManageSyncPassphrase != null) {
                SyncPassphraseField(
                    isHelpMode = isHelpMode,
                    hasSyncPassphrase = hasSyncPassphrase,
                    onHelpRequest = onHelpRequest,
                    onManageSyncPassphrase = onManageSyncPassphrase
                )
            }
            Button(
                onClick = onActionClick,
                enabled = transferState !is TransferState.Working,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = buttonColors
            ) {
                Icon(buttonIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(buttonLabel)
            }
        }
    }
}

@Composable
private fun SyncPassphraseField(
    isHelpMode: Boolean,
    hasSyncPassphrase: Boolean,
    onHelpRequest: (String) -> Unit,
    onManageSyncPassphrase: () -> Unit
) {
    AppHelpOverlayTarget(
        isEnabled = isHelpMode,
        description = stringResource(R.string.settings_help_sync_passphrase),
        onShowDescription = onHelpRequest
    ) {
        OutlinedTextField(
            value = stringResource(
                if (hasSyncPassphrase) R.string.settings_sync_passphrase_configured
                else R.string.settings_sync_passphrase_missing
            ),
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_sync_passphrase_title)) },
            supportingText = {
                Text(stringResource(R.string.settings_sync_passphrase_support))
            },
            trailingIcon = {
                TextButton(onClick = onManageSyncPassphrase) {
                    Text(
                        stringResource(
                            if (hasSyncPassphrase) R.string.settings_sync_passphrase_update
                            else R.string.settings_sync_passphrase_set
                        )
                    )
                }
            },
            shape = MaterialTheme.shapes.large
        )
    }
}

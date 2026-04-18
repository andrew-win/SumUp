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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAccountGroup(
    authUiState: AuthUiState,
    isCloudSyncEnabled: Boolean,
    syncIntervalHours: Int,
    backupSelection: BackupSelection,
    transferState: TransferState,
    onSyncIntervalSelect: (Int) -> Unit,
    onSyncEnabledChange: (Boolean) -> Unit,
    onBackupSelectionChange: (BackupSelection) -> Unit,
    onSignInOutClick: () -> Unit,
    onSyncNowClick: () -> Unit,
    onImportClick: () -> Unit,
    onExportClick: (String) -> Unit
) {
    var syncIntervalExpanded by remember { mutableStateOf(false) }
    val unifiedButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSection(title = "", boxed = true) {
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
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                ),
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
        SettingsSection(title = stringResource(R.string.settings_sync), boxed = true) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BackupOptionRow(
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
                BackupCheckboxRow(
                    title = stringResource(R.string.settings_backup_sources),
                    checked = backupSelection.includeSources,
                    onCheckedChange = { onBackupSelectionChange(backupSelection.copy(includeSources = it)) }
                )
                BackupCheckboxRow(
                    title = stringResource(R.string.settings_backup_subscriptions),
                    checked = backupSelection.includeSubscriptions,
                    onCheckedChange = { onBackupSelectionChange(backupSelection.copy(includeSubscriptions = it)) }
                )
                BackupCheckboxRow(
                    title = stringResource(R.string.settings_backup_settings_no_api),
                    checked = backupSelection.includeSettingsNoApi,
                    onCheckedChange = { onBackupSelectionChange(backupSelection.copy(includeSettingsNoApi = it)) }
                )
                BackupCheckboxRow(
                    title = stringResource(R.string.settings_backup_api_keys),
                    checked = backupSelection.includeApiKeys,
                    onCheckedChange = { onBackupSelectionChange(backupSelection.copy(includeApiKeys = it)) }
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
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onImportClick,
                        enabled = transferState !is TransferState.Working,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = unifiedButtonColors
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.settings_import_button))
                    }
                    Button(
                        onClick = {
                            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                            onExportClick("sumup-backup-$date.json")
                        },
                        enabled = transferState !is TransferState.Working,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = unifiedButtonColors
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.settings_export_button))
                    }
                }
            }
        }
    }
}

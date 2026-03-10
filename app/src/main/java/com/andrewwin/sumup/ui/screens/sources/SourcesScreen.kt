package com.andrewwin.sumup.ui.screens.sources

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.dao.GroupWithSources
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.local.entities.SourceGroup
import com.andrewwin.sumup.data.local.entities.SourceType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(
    viewModel: SourcesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddGroupDialog by remember { mutableStateOf(false) }
    var editGroup by remember { mutableStateOf<SourceGroup?>(null) }
    var editSource by remember { mutableStateOf<Source?>(null) }
    var selectedGroupIdForSource by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_sources)) },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddGroupDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_group))
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(uiState, key = { it.group.id }) { groupWithSources ->
                GroupCard(
                    groupWithSources = groupWithSources,
                    onAddSource = { selectedGroupIdForSource = groupWithSources.group.id },
                    onToggleGroup = { viewModel.toggleGroup(groupWithSources.group, it) },
                    onEditGroup = { editGroup = it },
                    onDeleteGroup = { viewModel.deleteGroup(it) },
                    onToggleSource = { viewModel.updateSource(it) },
                    onEditSource = { editSource = it },
                    onDeleteSource = { viewModel.deleteSource(it) }
                )
            }
        }

        if (showAddGroupDialog) {
            GroupDialog(
                onDismiss = { showAddGroupDialog = false },
                onConfirm = { viewModel.addGroup(it) }
            )
        }

        editGroup?.let { group ->
            GroupDialog(
                group = group,
                onDismiss = { editGroup = null },
                onConfirm = { viewModel.updateGroup(group.copy(name = it)) }
            )
        }

        selectedGroupIdForSource?.let { groupId ->
            SourceDialog(
                onDismiss = { selectedGroupIdForSource = null },
                onConfirm = { name, url, type ->
                    viewModel.addSource(groupId, name, url, type)
                }
            )
        }

        editSource?.let { source ->
            SourceDialog(
                source = source,
                onDismiss = { editSource = null },
                onConfirm = { name, url, type ->
                    viewModel.updateSource(source.copy(name = name, url = url, type = type))
                }
            )
        }
    }
}

@Composable
fun GroupCard(
    groupWithSources: GroupWithSources,
    onAddSource: () -> Unit,
    onToggleGroup: (Boolean) -> Unit,
    onEditGroup: (SourceGroup) -> Unit,
    onDeleteGroup: (SourceGroup) -> Unit,
    onToggleSource: (Source) -> Unit,
    onEditSource: (Source) -> Unit,
    onDeleteSource: (Source) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Folder, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = groupWithSources.group.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = groupWithSources.group.isEnabled,
                    onCheckedChange = onToggleGroup
                )
                IconButton(
                    onClick = { onEditGroup(groupWithSources.group) },
                    enabled = groupWithSources.group.isDeletable
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = null)
                }
                IconButton(
                    onClick = { onDeleteGroup(groupWithSources.group) },
                    enabled = groupWithSources.group.isDeletable
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                }
            }

            groupWithSources.sources.forEach { source ->
                SourceItem(
                    source = source,
                    isGroupEnabled = groupWithSources.group.isEnabled,
                    onToggle = { onToggleSource(it) },
                    onEdit = { onEditSource(it) },
                    onDelete = { onDeleteSource(it) }
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.sources_count, groupWithSources.sources.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            TextButton(
                onClick = onAddSource,
                modifier = Modifier.align(Alignment.End),
                enabled = groupWithSources.group.isEnabled
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.add_source))
            }
        }
    }
}

@Composable
fun SourceItem(
    source: Source,
    isGroupEnabled: Boolean,
    onToggle: (Source) -> Unit,
    onEdit: (Source) -> Unit,
    onDelete: (Source) -> Unit
) {
    val icon = when (source.type) {
        SourceType.TELEGRAM -> Icons.AutoMirrored.Filled.Send
        SourceType.RSS -> Icons.Default.RssFeed
        SourceType.YOUTUBE -> Icons.Default.PlayCircle
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .alpha(if (isGroupEnabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = source.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = source.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = source.isEnabled,
            onCheckedChange = { onToggle(source.copy(isEnabled = it)) },
            enabled = isGroupEnabled
        )
        IconButton(onClick = { onEdit(source) }, enabled = isGroupEnabled) {
            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = { onDelete(source) }, enabled = isGroupEnabled) {
            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun GroupDialog(
    group: SourceGroup? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(group?.name ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (group == null) stringResource(R.string.add_group) else "Редагувати групу") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.group_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { 
                if (name.isNotBlank()) {
                    onConfirm(name)
                    onDismiss()
                }
            }) {
                Text(if (group == null) stringResource(R.string.add) else "Зберегти")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceDialog(
    source: Source? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String, SourceType) -> Unit
) {
    var name by remember { mutableStateOf(source?.name ?: "") }
    var url by remember { mutableStateOf(source?.url ?: "") }
    var type by remember { mutableStateOf(source?.type ?: SourceType.RSS) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (source == null) stringResource(R.string.add_source) else "Редагувати джерело") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.source_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.source_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = type.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.source_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        SourceType.entries.forEach { entry ->
                            DropdownMenuItem(
                                text = { Text(entry.name) },
                                onClick = {
                                    type = entry
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { 
                if (name.isNotBlank() && url.isNotBlank()) {
                    onConfirm(name, url, type)
                    onDismiss()
                }
            }) {
                Text(if (source == null) stringResource(R.string.add) else "Зберегти")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

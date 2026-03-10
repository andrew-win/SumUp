package com.andrewwin.sumup.ui.screens.sources

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.dao.GroupWithSources
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.local.entities.SourceGroup
import com.andrewwin.sumup.data.local.entities.SourceType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(
    viewModel: SourcesViewModel = hiltViewModel()
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
            FloatingActionButton(
                onClick = { showAddGroupDialog = true },
                shape = MaterialTheme.shapes.extraLarge
            ) {
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
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = groupWithSources.group.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = groupWithSources.group.isEnabled,
                    onCheckedChange = onToggleGroup,
                    modifier = Modifier.scale(0.85f)
                )
                IconButton(
                    onClick = { onEditGroup(groupWithSources.group) },
                    enabled = groupWithSources.group.isDeletable
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                IconButton(
                    onClick = { onDeleteGroup(groupWithSources.group) },
                    enabled = groupWithSources.group.isDeletable
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            groupWithSources.sources.forEach { source ->
                SourceItem(
                    source = source,
                    isGroupEnabled = groupWithSources.group.isEnabled,
                    onToggle = { onToggleSource(it) },
                    onEdit = { onEditSource(it) },
                    onDelete = { onDeleteSource(it) }
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.sources_count, groupWithSources.sources.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                FilledTonalButton(
                    onClick = onAddSource,
                    enabled = groupWithSources.group.isEnabled,
                    shape = MaterialTheme.shapes.large,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.add_source), style = MaterialTheme.typography.labelLarge)
                }
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
    val painter = when (source.type) {
        SourceType.TELEGRAM -> painterResource(R.drawable.ic_telegram_source)
        SourceType.RSS -> painterResource(R.drawable.ic_rss_source)
        SourceType.YOUTUBE -> painterResource(R.drawable.ic_youtube_source)
    }

    val displayUrl = remember(source.url, source.type) {
        when (source.type) {
            SourceType.RSS -> {
                source.url.removePrefix("https://")
                    .removePrefix("http://")
                    .removePrefix("www.")
                    .substringBefore("/")
            }
            SourceType.TELEGRAM -> {
                val handle = source.url.substringAfterLast("/").removePrefix("@")
                if (handle.isNotEmpty()) "@$handle" else source.url
            }
            SourceType.YOUTUBE -> {
                val id = source.url.substringAfterLast("/")
                if (id.isNotEmpty()) "id=${id.take(7)}…" else source.url
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isGroupEnabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.name, 
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = displayUrl,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Switch(
            checked = source.isEnabled,
            onCheckedChange = { onToggle(source.copy(isEnabled = it)) },
            enabled = isGroupEnabled,
            modifier = Modifier.scale(0.75f)
        )
        IconButton(
            onClick = { onEdit(source) }, 
            enabled = isGroupEnabled,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        IconButton(
            onClick = { onDelete(source) }, 
            enabled = isGroupEnabled,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
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
        title = { Text(stringResource(if (group == null) R.string.add_group else R.string.edit_group)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.group_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            )
        },
        confirmButton = {
            Button(
                onClick = { 
                    if (name.isNotBlank()) {
                        onConfirm(name)
                        onDismiss()
                    }
                },
                shape = MaterialTheme.shapes.large
            ) {
                Text(stringResource(if (group == null) R.string.add else R.string.save))
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
        title = { Text(stringResource(if (source == null) R.string.add_source else R.string.edit_source)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.source_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.source_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                )
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = stringResource(when(type) {
                            SourceType.RSS -> R.string.source_type_rss
                            SourceType.TELEGRAM -> R.string.source_type_telegram
                            SourceType.YOUTUBE -> R.string.source_type_youtube
                        }),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.source_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth(),
                        shape = MaterialTheme.shapes.large
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        SourceType.entries.forEach { entry ->
                            DropdownMenuItem(
                                text = { 
                                    Text(stringResource(when(entry) {
                                        SourceType.RSS -> R.string.source_type_rss
                                        SourceType.TELEGRAM -> R.string.source_type_telegram
                                        SourceType.YOUTUBE -> R.string.source_type_youtube
                                    })) 
                                },
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
            Button(
                onClick = { 
                    if (name.isNotBlank() && url.isNotBlank()) {
                        onConfirm(name, url, type)
                        onDismiss()
                    }
                },
                shape = MaterialTheme.shapes.large
            ) {
                Text(stringResource(if (source == null) R.string.add else R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

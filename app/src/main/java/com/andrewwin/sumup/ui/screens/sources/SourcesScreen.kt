package com.andrewwin.sumup.ui.screens.sources

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddGroupDialog = true },
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.size(60.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_group), modifier = Modifier.size(26.dp))
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

            item {
                Spacer(modifier = Modifier.height(32.dp))
                val isModelLoaded by viewModel.isModelLoaded.collectAsState()
                val suggestedThemes by viewModel.suggestedThemes.collectAsState()
                val titleTextRes = if (isModelLoaded) {
                    R.string.sources_suggested_themes_title
                } else {
                    R.string.sources_suggested_themes_hint
                }

                Text(
                    text = stringResource(titleTextRes),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Normal),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 12.dp)
                )

                suggestedThemes.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { suggestion ->
                            SuggestedThemeItem(
                                suggestion = suggestion,
                                modifier = Modifier.weight(1f),
                                onToggle = { isSubscribed ->
                                    viewModel.toggleThemeSubscription(suggestion, isSubscribed)
                                }
                            )
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
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
                onConfirm = { name, url, type, titleSelector, postLinkSelector, descriptionSelector, dateSelector, useHeadlessBrowser ->
                    viewModel.addSource(
                        groupId = groupId,
                        name = name,
                        url = url,
                        type = type,
                        titleSelector = titleSelector,
                        postLinkSelector = postLinkSelector,
                        descriptionSelector = descriptionSelector,
                        dateSelector = dateSelector,
                        useHeadlessBrowser = useHeadlessBrowser
                    )
                }
            )
        }

        editSource?.let { source ->
            SourceDialog(
                source = source,
                onDismiss = { editSource = null },
                onConfirm = { name, url, type, titleSelector, postLinkSelector, descriptionSelector, dateSelector, useHeadlessBrowser ->
                    viewModel.updateSource(
                        source.copy(
                            name = name,
                            url = url,
                            type = type,
                            titleSelector = titleSelector,
                            postLinkSelector = postLinkSelector,
                            descriptionSelector = descriptionSelector,
                            dateSelector = dateSelector,
                            useHeadlessBrowser = useHeadlessBrowser
                        )
                    )
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
    var isExpanded by rememberSaveable(groupWithSources.group.id) { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .padding(vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = groupWithSources.group.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { isExpanded = !isExpanded }
                ) {
                    Icon(
                        if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Box {
                    var showDropdown by rememberSaveable(groupWithSources.group.id) { mutableStateOf(false) }
                    IconButton(onClick = { showDropdown = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null, modifier = Modifier.size(24.dp))
                    }
                    DropdownMenu(
                        expanded = showDropdown,
                        onDismissRequest = { showDropdown = false },
                        shape = MaterialTheme.shapes.large,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        DropdownMenuItem(
                            text = { 
                                Text(
                                    stringResource(R.string.status_enabled),
                                    style = MaterialTheme.typography.bodyMedium
                                ) 
                            },
                            trailingIcon = {
                                Switch(
                                    checked = groupWithSources.group.isEnabled,
                                    onCheckedChange = { onToggleGroup(it) },
                                    modifier = Modifier.scale(0.7f).padding(start = 8.dp)
                                )
                            },
                            onClick = { onToggleGroup(!groupWithSources.group.isEnabled) }
                        )

                        if (groupWithSources.group.isDeletable) {
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        stringResource(R.string.edit_group),
                                        style = MaterialTheme.typography.bodyMedium
                                    ) 
                                },
                                trailingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                onClick = { onEditGroup(groupWithSources.group); showDropdown = false }
                            )
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        stringResource(R.string.delete),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    ) 
                                },
                                trailingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) },
                                onClick = { onDeleteGroup(groupWithSources.group); showDropdown = false }
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(durationMillis = 200)) + fadeIn(animationSpec = tween(durationMillis = 200)),
                exit = shrinkVertically(animationSpec = tween(durationMillis = 200)) + fadeOut(animationSpec = tween(durationMillis = 200))
            ) {
                Column {
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 8.dp),
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
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.add_source), style = MaterialTheme.typography.labelLarge)
                        }
                    }
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
        SourceType.WEBSITE -> painterResource(R.drawable.ic_usual_website_source)
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
            SourceType.WEBSITE -> {
                source.url.removePrefix("https://")
                    .removePrefix("http://")
                    .removePrefix("www.")
                    .substringBefore("/")
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
                fontWeight = FontWeight.Normal
            )
            Text(
                text = displayUrl,
                style = MaterialTheme.typography.labelMedium,
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
    var name by remember(group?.id) { mutableStateOf(group?.name ?: "") }
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
    onConfirm: (String, String, SourceType, String?, String?, String?, String?, Boolean) -> Unit
) {
    var name by remember(source?.id) { mutableStateOf(source?.name ?: "") }
    var url by remember(source?.id) { mutableStateOf(source?.url ?: "") }
    var type by remember(source?.id) { mutableStateOf(source?.type ?: SourceType.RSS) }
    var titleSelector by remember(source?.id) { mutableStateOf(source?.titleSelector ?: "") }
    var postLinkSelector by remember(source?.id) { mutableStateOf(source?.postLinkSelector ?: "") }
    var descriptionSelector by remember(source?.id) { mutableStateOf(source?.descriptionSelector ?: "") }
    var dateSelector by remember(source?.id) { mutableStateOf(source?.dateSelector ?: "") }
    var useHeadlessBrowser by remember(source?.id) { mutableStateOf(source?.useHeadlessBrowser ?: false) }
    var expanded by remember { mutableStateOf(false) }

    Dialog(
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
                    text = stringResource(if (source == null) R.string.add_source else R.string.edit_source),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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
                        label = {
                            val labelRes = if (type == SourceType.WEBSITE) {
                                R.string.source_url_website
                            } else {
                                R.string.source_url
                            }
                            Text(stringResource(labelRes))
                        },
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
                                SourceType.WEBSITE -> R.string.source_type_website
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
                                            SourceType.WEBSITE -> R.string.source_type_website
                                        }))
                                    },
                                    onClick = {
                                        type = entry
                                        if (entry != SourceType.WEBSITE) {
                                            titleSelector = ""
                                            postLinkSelector = ""
                                            descriptionSelector = ""
                                            dateSelector = ""
                                            useHeadlessBrowser = false
                                        }
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    if (type == SourceType.WEBSITE) {
                        OutlinedTextField(
                            value = titleSelector,
                            onValueChange = { titleSelector = it },
                            label = { Text(stringResource(R.string.website_title_selector)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large
                        )
                        OutlinedTextField(
                            value = postLinkSelector,
                            onValueChange = { postLinkSelector = it },
                            label = { Text(stringResource(R.string.website_post_link_selector_optional)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large
                        )
                        OutlinedTextField(
                            value = descriptionSelector,
                            onValueChange = { descriptionSelector = it },
                            label = { Text(stringResource(R.string.website_description_selector_optional)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large
                        )
                        OutlinedTextField(
                            value = dateSelector,
                            onValueChange = { dateSelector = it },
                            label = { Text(stringResource(R.string.website_date_selector_optional)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.website_use_headless_browser),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Switch(
                                checked = useHeadlessBrowser,
                                onCheckedChange = { useHeadlessBrowser = it }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
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
                            val hasRequiredWebsiteSelector = type != SourceType.WEBSITE || titleSelector.isNotBlank()
                            if (name.isNotBlank() && url.isNotBlank() && hasRequiredWebsiteSelector) {
                                onConfirm(
                                    name,
                                    url,
                                    type,
                                    titleSelector.takeIf { it.isNotBlank() },
                                    postLinkSelector.takeIf { it.isNotBlank() },
                                    descriptionSelector.takeIf { it.isNotBlank() },
                                    dateSelector.takeIf { it.isNotBlank() },
                                    useHeadlessBrowser
                                )
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(stringResource(if (source == null) R.string.add else R.string.save))
                    }
                }
            }
        }
    }
}

@Composable
fun SuggestedThemeItem(
    suggestion: com.andrewwin.sumup.domain.usecase.sources.ThemeSuggestion,
    modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit
) {
    val targetSubscribedState = !suggestion.isSubscribed
    Surface(
        onClick = { onToggle(targetSubscribedState) },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .padding(vertical = 6.dp)
            .heightIn(min = 72.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(targetSubscribedState) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = suggestion.theme.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                    maxLines = 2
                )
                Icon(
                    imageVector = if (suggestion.isSubscribed) Icons.Outlined.Done else Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (suggestion.isSubscribed) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            if (suggestion.isSubscribed) {
                Text(
                    text = stringResource(R.string.sources_subscribed),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (suggestion.isRecommended) {
                    Text(
                        text = stringResource(R.string.sources_recommended_badge),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                    )
                }
        }
    }
}


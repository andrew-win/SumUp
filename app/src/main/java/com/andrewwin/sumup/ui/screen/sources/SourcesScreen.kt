package com.andrewwin.sumup.ui.screen.sources

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.dao.GroupWithSources
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.local.entities.SourceGroup
import com.andrewwin.sumup.data.local.entities.SourceType

private val SourceType.iconRes: Int
    get() = when (this) {
        SourceType.TELEGRAM -> R.drawable.ic_telegram_source
        SourceType.RSS -> R.drawable.ic_rss_source
        SourceType.YOUTUBE -> R.drawable.ic_youtube_source
        SourceType.WEBSITE -> R.drawable.ic_usual_website_source
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SourcesScreen(
    viewModel: SourcesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedGroupIds = remember { mutableStateListOf<Long>() }
    val isSelectionMode = selectedGroupIds.isNotEmpty()
    var showAddGroupDialog by remember { mutableStateOf(false) }
    var editGroup by remember { mutableStateOf<SourceGroup?>(null) }
    var editSource by remember { mutableStateOf<Source?>(null) }
    var selectedGroupIdForSource by remember { mutableStateOf<Long?>(null) }
    var isHelpMode by rememberSaveable { mutableStateOf(false) }
    var helpDescription by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isSelectionMode) {
        if (isSelectionMode && isHelpMode) {
            isHelpMode = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text("Вибрано: ${selectedGroupIds.size}")
                    } else {
                        Text(stringResource(R.string.nav_sources))
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledIconButton(
                                onClick = { selectedGroupIds.clear() },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Exit selection mode")
                            }
                            FilledIconButton(
                                onClick = {
                                    val selected = uiState
                                        .map { it.group }
                                        .filter { selectedGroupIds.contains(it.id) }
                                    selected.forEach { viewModel.deleteGroup(it) }
                                    selectedGroupIds.clear()
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete selected groups")
                            }
                        }
                    } else {
                        FilledIconButton(
                            onClick = { isHelpMode = !isHelpMode },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            if (isHelpMode) {
                                Icon(Icons.Default.Close, contentDescription = "Вимкнути підказки")
                            } else {
                                Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = "Увімкнути підказки")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (isHelpMode) {
                        helpDescription = "Кнопка '+' додає нову папку (групу) джерел. " +
                            "Група використовується для логічного розділення підписок за темами або задачами. " +
                            "Наприклад: 'Україна', 'Технології', 'Крипта'. " +
                            "Після створення папки в неї додаються конкретні джерела: RSS, Telegram, YouTube або сайт."
                    } else {
                        showAddGroupDialog = true
                    }
                },
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(width = 75.dp, height = 65.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_group), modifier = Modifier.size(48.dp))
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(uiState, key = { it.group.id }) { groupWithSources ->
                HelpOverlayTarget(
                    isEnabled = isHelpMode,
                    description = "Група джерел (папка): контейнер для підписок. " +
                        "Усередині можна вмикати/вимикати всю групу або окремі джерела, редагувати назву і склад. " +
                        "Коли групу вимкнено, її джерела не беруть участі у формуванні стрічки.",
                    onShowDescription = { helpDescription = it }
                ) {
                    GroupCard(
                        groupWithSources = groupWithSources,
                        isSelected = selectedGroupIds.contains(groupWithSources.group.id),
                        isSelectionMode = isSelectionMode,
                        onLongSelectGroup = {
                            val id = groupWithSources.group.id
                            if (!selectedGroupIds.contains(id)) selectedGroupIds.add(id)
                        },
                        onToggleSelectGroup = {
                            val id = groupWithSources.group.id
                            if (selectedGroupIds.contains(id)) selectedGroupIds.remove(id) else selectedGroupIds.add(id)
                        },
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

            item {
                val isModelLoaded by viewModel.isModelLoaded.collectAsState()
                val isRecommendationsEnabled by viewModel.isRecommendationsEnabled.collectAsState()
                val suggestedThemes by viewModel.suggestedThemes.collectAsState()
                
                if (isRecommendationsEnabled) {
                    HelpOverlayTarget(
                        isEnabled = isHelpMode,
                        description = "Рекомендовані теми: швидкий спосіб додати готові тематичні підписки. " +
                            "При підписці застосунок додає джерела у структурованому вигляді як окремі папки/групи, " +
                            "щоб їх можна було незалежно фільтрувати у стрічці та гнучко керувати ввімкненням.",
                        onShowDescription = { helpDescription = it }
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                            Row(
                                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_recommend),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(if (isModelLoaded) R.string.sources_suggested_themes_title else R.string.sources_suggested_themes_hint),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            suggestedThemes.chunked(2).forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
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
                }
            }
        }

        if (helpDescription != null) {
            AlertDialog(
                onDismissRequest = { helpDescription = null },
                title = { Text("Пояснення блоку") },
                text = { Text(helpDescription.orEmpty()) },
                confirmButton = {
                    TextButton(onClick = { helpDescription = null }) {
                        Text("OK")
                    }
                }
            )
        }

        if (showAddGroupDialog) {
            GroupDialog(
                existingGroupNames = uiState.map { it.group.name },
                onDismiss = { showAddGroupDialog = false },
                onConfirm = { viewModel.addGroup(it) }
            )
        }

        editGroup?.let { group ->
            GroupDialog(
                group = group,
                existingGroupNames = uiState.map { it.group.name },
                onDismiss = { editGroup = null },
                onConfirm = { viewModel.updateGroup(group.copy(name = it)) }
            )
        }

        selectedGroupIdForSource?.let { groupId ->
            SourceDialog(
                existingSources = uiState.flatMap { it.sources },
                existingSourceNames = uiState.flatMap { it.sources }.map { it.name },
                showDetailedHelp = isHelpMode,
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
                existingSources = uiState.flatMap { it.sources },
                existingSourceNames = uiState.flatMap { it.sources }.map { it.name },
                showDetailedHelp = isHelpMode,
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
private fun HelpOverlayTarget(
    isEnabled: Boolean,
    description: String,
    onShowDescription: (String) -> Unit,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        content()
        if (isEnabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(MaterialTheme.shapes.large)
                    .background(Color.Gray.copy(alpha = 0.45f))
                    .clickable { onShowDescription(description) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupCard(
    groupWithSources: GroupWithSources,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onLongSelectGroup: () -> Unit,
    onToggleSelectGroup: () -> Unit,
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
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.05f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            if (isSelectionMode) onToggleSelectGroup() else isExpanded = !isExpanded
                        },
                        onLongClick = onLongSelectGroup
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = groupWithSources.group.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f)
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        var showDropdown by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { showDropdown = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(
                            expanded = showDropdown,
                            onDismissRequest = { showDropdown = false },
                            shape = MaterialTheme.shapes.large,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.status_enabled)) },
                                trailingIcon = {
                                    Switch(
                                        checked = groupWithSources.group.isEnabled,
                                        onCheckedChange = { onToggleGroup(it) },
                                        modifier = Modifier.scale(0.7f)
                                    )
                                },
                                onClick = { onToggleGroup(!groupWithSources.group.isEnabled) }
                            )
                            if (groupWithSources.group.isDeletable) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.edit_group)) },
                                    trailingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    onClick = { onEditGroup(groupWithSources.group); showDropdown = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                                    trailingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) },
                                    onClick = { onDeleteGroup(groupWithSources.group); showDropdown = false }
                                )
                            }
                        }
                    }
                    
                    Spacer(Modifier.width(4.dp))
                    
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(180)),
                exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(120))
            ) {
                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    if (groupWithSources.sources.isEmpty()) {
                        Text(
                            text = "Немає джерел у цій групі",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        groupWithSources.sources.forEach { source ->
                            SourceItem(
                                source = source,
                                isGroupEnabled = groupWithSources.group.isEnabled,
                                onToggle = { onToggleSource(it) },
                                onEdit = { onEditSource(it) },
                                onDelete = { onDeleteSource(it) }
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.sources_count, groupWithSources.sources.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        
                        FilledTonalButton(
                            onClick = onAddSource,
                            enabled = groupWithSources.group.isEnabled,
                            shape = MaterialTheme.shapes.medium,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
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
    val displayUrl = remember(source.url, source.type) {
        when (source.type) {
            SourceType.RSS, SourceType.WEBSITE -> {
                source.url.removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore("/")
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
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .alpha(if (isGroupEnabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(source.type.iconRes),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.name, 
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
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
            modifier = Modifier.scale(0.7f)
        )
        IconButton(onClick = { onEdit(source) }, enabled = isGroupEnabled, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { onDelete(source) }, enabled = isGroupEnabled, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun GroupDialog(
    group: SourceGroup? = null,
    existingGroupNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(group?.id) { mutableStateOf(group?.name ?: "") }
    val normalizedName = name.trim()
    val isDuplicate = remember(normalizedName, existingGroupNames, group?.id) {
        existingGroupNames.any {
            it.trim().equals(normalizedName, ignoreCase = true) &&
                !it.trim().equals(group?.name?.trim().orEmpty(), ignoreCase = true)
        }
    }
    val errorText = when {
        normalizedName.isBlank() -> stringResource(R.string.validation_name_required)
        isDuplicate -> stringResource(R.string.validation_group_name_exists)
        else -> null
    }
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
                shape = MaterialTheme.shapes.large,
                isError = errorText != null,
                supportingText = { if (errorText != null) Text(errorText) }
            )
        },
        confirmButton = {
            Button(
                onClick = { 
                    if (errorText == null) {
                        onConfirm(normalizedName)
                        onDismiss()
                    }
                },
                enabled = errorText == null,
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
    existingSources: List<Source>,
    existingSourceNames: List<String>,
    showDetailedHelp: Boolean = false,
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
    val normalizedName = name.trim()
    val normalizedUrl = normalizeSourceUrl(url, type)
    val isDuplicate = remember(normalizedName, existingSourceNames, source?.id) {
        existingSourceNames.any {
            it.trim().equals(normalizedName, ignoreCase = true) &&
                !it.trim().equals(source?.name?.trim().orEmpty(), ignoreCase = true)
        }
    }
    val isDuplicateUrl = remember(normalizedUrl, type, existingSources, source?.id) {
        existingSources.any {
            it.id != source?.id &&
                it.type == type &&
                normalizeSourceUrl(it.url, it.type).equals(normalizedUrl, ignoreCase = true)
        }
    }
    val hasRequiredWebsiteSelector = type != SourceType.WEBSITE || titleSelector.isNotBlank()
    val errorText = when {
        normalizedName.isBlank() -> stringResource(R.string.validation_name_required)
        isDuplicate -> stringResource(R.string.validation_source_name_exists)
        url.isBlank() -> stringResource(R.string.validation_source_url_required)
        isDuplicateUrl -> stringResource(R.string.validation_source_url_exists)
        !hasRequiredWebsiteSelector -> stringResource(R.string.validation_website_selector_required)
        else -> null
    }

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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (showDetailedHelp) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Як правильно додавати джерело",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "1. Назва: коротка і зрозуміла (це підпис у стрічці та списках).",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "2. URL: посилання на RSS/канал/сайт. Для сайтів бажано головну сторінку або сторінку розділу.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "3. Тип джерела: визначає, як парсер читає контент (RSS/Telegram/YouTube/Website).",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "4. Для Website: селектор заголовка обов'язковий; інші селектори покращують точність і структуру.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "5. Підписки організовуються по папках (групах), щоб їх можна було окремо вмикати та фільтрувати.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.source_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        isError = errorText != null
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = {
                            val labelRes = if (type == SourceType.WEBSITE) R.string.source_url_website else R.string.source_url
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
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(type.iconRes),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
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
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(entry.iconRes),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    onClick = {
                                        type = entry
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
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large
                        )
                        OutlinedTextField(
                            value = postLinkSelector,
                            onValueChange = { postLinkSelector = it },
                            label = { Text(stringResource(R.string.website_post_link_selector_optional)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large
                        )
                        OutlinedTextField(
                            value = descriptionSelector,
                            onValueChange = { descriptionSelector = it },
                            label = { Text(stringResource(R.string.website_description_selector_optional)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large
                        )
                        OutlinedTextField(
                            value = dateSelector,
                            onValueChange = { dateSelector = it },
                            label = { Text(stringResource(R.string.website_date_selector_optional)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = stringResource(R.string.website_use_headless_browser), style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = useHeadlessBrowser, onCheckedChange = { useHeadlessBrowser = it })
                        }
                    }
                    if (errorText != null) {
                        Text(text = errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.large) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = {
                            if (errorText == null) {
                                onConfirm(normalizedName, url, type, titleSelector.takeIf { it.isNotBlank() }, postLinkSelector.takeIf { it.isNotBlank() }, descriptionSelector.takeIf { it.isNotBlank() }, dateSelector.takeIf { it.isNotBlank() }, useHeadlessBrowser)
                                onDismiss()
                            }
                        },
                        enabled = errorText == null,
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

private fun normalizeSourceUrl(url: String, type: SourceType): String {
    val trimmed = url.trim()
    if (trimmed.isBlank() || (type != SourceType.RSS && type != SourceType.WEBSITE)) return trimmed
    return when {
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        trimmed.startsWith("http://", ignoreCase = true) -> "https://${trimmed.removePrefix("http://")}"
        trimmed.startsWith("//") -> "https:$trimmed"
        else -> "https://$trimmed"
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
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.05f)),
        modifier = modifier.padding(vertical = 4.dp).heightIn(min = 72.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = suggestion.theme.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (suggestion.isSubscribed) FontWeight.Bold else FontWeight.Medium,
                    color = if (suggestion.isSubscribed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 2
                )
                Icon(
                    imageVector = if (suggestion.isSubscribed) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (suggestion.isSubscribed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (suggestion.isRecommended && !suggestion.isSubscribed) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.sources_recommended_badge).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

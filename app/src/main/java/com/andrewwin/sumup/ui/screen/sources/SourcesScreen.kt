package com.andrewwin.sumup.ui.screen.sources

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.dao.GroupWithSources
import com.andrewwin.sumup.data.local.entities.AppLanguage
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.local.entities.SourceGroup
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.domain.source.SourceUrlValidator
import com.andrewwin.sumup.ui.displayName
import com.andrewwin.sumup.ui.components.AppAnimatedDialog
import com.andrewwin.sumup.ui.components.AppExplanationDialog
import com.andrewwin.sumup.ui.components.AppHelpOverlayTarget
import com.andrewwin.sumup.ui.components.AppHelpToggleAction
import com.andrewwin.sumup.ui.components.AppProminentFab
import com.andrewwin.sumup.ui.components.AppSearchField
import com.andrewwin.sumup.ui.components.AppSelectionActions
import com.andrewwin.sumup.ui.components.AppTopBar
import com.andrewwin.sumup.ui.screen.settings.SettingsConfirmDeleteDialog
import com.andrewwin.sumup.ui.theme.AppCardShape
import com.andrewwin.sumup.ui.theme.AppDimens
import com.andrewwin.sumup.ui.theme.appCardBorder
import com.andrewwin.sumup.ui.theme.appCardColors

private val SourceType.iconRes: Int
    get() = when (this) {
        SourceType.TELEGRAM -> R.drawable.ic_telegram_source
        SourceType.RSS -> R.drawable.ic_rss_source
        SourceType.YOUTUBE -> R.drawable.ic_youtube_source
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SourcesScreen(
    viewModel: SourcesViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val reservedGroupNames by viewModel.reservedGroupNames.collectAsState()
    val selectedGroupIds = remember { mutableStateListOf<Long>() }
    val isSelectionMode = selectedGroupIds.isNotEmpty()
    var showAddGroupDialog by remember { mutableStateOf(false) }
    var editGroup by remember { mutableStateOf<SourceGroup?>(null) }
    var editSource by remember { mutableStateOf<Source?>(null) }
    var deleteGroupConfirm by remember { mutableStateOf<SourceGroup?>(null) }
    var deleteSourceConfirm by remember { mutableStateOf<Source?>(null) }
    var showDeleteSelectedGroupsConfirm by remember { mutableStateOf(false) }
    var selectedGroupIdForSource by remember { mutableStateOf<Long?>(null) }
    var isHelpMode by rememberSaveable { mutableStateOf(false) }
    var helpDescription by remember { mutableStateOf<String?>(null) }
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabIcons = listOf(Icons.Default.Person, Icons.Default.Subscriptions)
    val tabLabels = listOf(
        stringResource(R.string.sources_tab_custom),
        stringResource(R.string.sources_tab_presets)
    )
    val addGroupHelpDescription = stringResource(R.string.sources_help_add_group)
    val groupCardHelpDescription = stringResource(R.string.sources_help_group_card)
    val suggestedThemesHelpDescription = stringResource(R.string.sources_help_recommended_themes)
    val currentGroups = (uiState as? SourcesUiState.Content)?.groups.orEmpty()

    LaunchedEffect(isSelectionMode) {
        if (isSelectionMode && isHelpMode) {
            isHelpMode = false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { messageResId ->
            snackbarHostState.showSnackbar(context.getString(messageResId))
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            AppTopBar(
                title = {
                    if (isSelectionMode) {
                        Text("Вибрано: ${selectedGroupIds.size}")
                    } else {
                        Text(stringResource(R.string.nav_sources))
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        AppSelectionActions(
                            onClear = { selectedGroupIds.clear() },
                            onDelete = {
                                showDeleteSelectedGroupsConfirm = true
                            },
                            clearDescription = "Exit selection mode",
                            deleteDescription = "Delete selected groups"
                        )
                    } else {
                        AppHelpToggleAction(
                            isHelpMode = isHelpMode,
                            onToggle = { isHelpMode = !isHelpMode }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTabIndex == 0) {
                AppProminentFab(
                    onClick = {
                        if (isHelpMode) {
                            helpDescription = addGroupHelpDescription
                        } else {
                            showAddGroupDialog = true
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_add_group),
                        contentDescription = stringResource(R.string.add_group),
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = AppDimens.ScreenHorizontalPadding,
                end = AppDimens.ScreenHorizontalPadding,
                top = 8.dp,
                bottom = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(AppDimens.ScreenItemSpacing)
        ) {
            item {
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    tabIcons.forEachIndexed { index, icon ->
                        LeadingIconTab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Text(
                                    text = tabLabels[index],
                                    style = MaterialTheme.typography.labelLarge
                                )
                            },
                            icon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )
                    }
                }
            }

            if (selectedTabIndex == 0) {
                item {
                    AppSearchField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChange(it) },
                        placeholder = stringResource(R.string.sources_search_placeholder),
                        leadingIcon = Icons.Default.Search,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.sources_header_folders),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        val sortOrder by viewModel.sortOrder.collectAsState()
                        var showSortDropdown by remember { mutableStateOf(false) }

                        Box {
                            Row(
                                modifier = Modifier
                                    .heightIn(min = 44.dp)
                                    .clickable { showSortDropdown = true }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(
                                        if (sortOrder == SourceSortOrder.BY_NAME) R.string.sources_sort_name
                                        else R.string.sources_sort_date
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }

                            DropdownMenu(
                                expanded = showSortDropdown,
                                onDismissRequest = { showSortDropdown = false },
                                shape = MaterialTheme.shapes.large,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sources_sort_name)) },
                                    onClick = {
                                        viewModel.setSortOrder(SourceSortOrder.BY_NAME)
                                        showSortDropdown = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.SortByAlpha, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sources_sort_date)) },
                                    onClick = {
                                        viewModel.setSortOrder(SourceSortOrder.BY_DATE)
                                        showSortDropdown = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                )
                            }
                        }
                    }
                }

                when (val sourcesState = uiState) {
                    SourcesUiState.Loading -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                    is SourcesUiState.Content -> {
                        if (sourcesState.groups.isEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.sources_groups_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
                                )
                            }
                        } else {
                            items(sourcesState.groups, key = { it.group.id }) { groupWithSources ->
                                AppHelpOverlayTarget(
                                    isEnabled = isHelpMode,
                                    description = groupCardHelpDescription,
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
                                        onDeleteGroup = { deleteGroupConfirm = it },
                                        onToggleSource = { viewModel.updateSource(it) },
                                        onEditSource = { editSource = it },
                                        onDeleteSource = { deleteSourceConfirm = it }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (selectedTabIndex == 1) {
                item {
                    val isRecommendationsEnabled by viewModel.isRecommendationsEnabled.collectAsState()
                    val suggestedThemes by viewModel.suggestedThemes.collectAsState()
                    val subscriptionsSyncFailed by viewModel.subscriptionsSyncFailed.collectAsState()
                    val isRefreshingThemeRecommendations by viewModel.isRefreshingThemeRecommendations.collectAsState()

                    AppHelpOverlayTarget(
                        isEnabled = isHelpMode,
                        description = suggestedThemesHelpDescription,
                        onShowDescription = { helpDescription = it }
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (subscriptionsSyncFailed) {
                                Text(
                                    text = stringResource(R.string.sources_sync_failed),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
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
                                            appLanguage = appLanguage,
                                            showRecommendedBadge = isRecommendationsEnabled,
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

                            if (isRefreshingThemeRecommendations) {
                                Row(
                                    modifier = Modifier
                                        .padding(start = 4.dp, top = 4.dp, bottom = 8.dp)
                                        .animateContentSize(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.sources_refreshing_recommendations),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = { viewModel.refreshSuggestedThemes(forceRefresh = true) },
                                modifier = Modifier
                                    .align(Alignment.Start)
                                    .padding(top = 8.dp),
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.sources_refresh_themes))
                            }
                        }
                    }
                }
            }
        }

        AppExplanationDialog(
            visible = helpDescription != null,
            description = helpDescription.orEmpty(),
            onDismiss = { helpDescription = null }
        )

        if (showAddGroupDialog) {
            GroupDialog(
                existingGroupNames = currentGroups.map { it.group.name },
                reservedGroupNames = reservedGroupNames,
                onDismiss = { showAddGroupDialog = false },
                onConfirm = { viewModel.addGroup(it) }
            )
        }

        editGroup?.let { group ->
            GroupDialog(
                group = group,
                existingGroupNames = currentGroups.map { it.group.name },
                reservedGroupNames = reservedGroupNames,
                onDismiss = { editGroup = null },
                onConfirm = { viewModel.updateGroup(group.copy(name = it)) }
            )
        }

        selectedGroupIdForSource?.let { groupId ->
            SourceDialog(
                existingSources = currentGroups.flatMap { it.sources },
                existingSourceNames = currentGroups.flatMap { it.sources }.map { it.name },
                showDetailedHelp = isHelpMode,
                onFetchGeneratedName = viewModel::fetchGeneratedSourceName,
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
                existingSources = currentGroups.flatMap { it.sources },
                existingSourceNames = currentGroups.flatMap { it.sources }.map { it.name },
                showDetailedHelp = isHelpMode,
                onFetchGeneratedName = viewModel::fetchGeneratedSourceName,
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

        deleteGroupConfirm?.let { group ->
            SettingsConfirmDeleteDialog(
                title = stringResource(R.string.delete),
                text = stringResource(R.string.delete_group_confirm, group.displayName()),
                onConfirm = {
                    viewModel.deleteGroup(group)
                    deleteGroupConfirm = null
                },
                onDismiss = { deleteGroupConfirm = null }
            )
        }

        deleteSourceConfirm?.let { source ->
            SettingsConfirmDeleteDialog(
                title = stringResource(R.string.delete),
                text = stringResource(R.string.delete_source_confirm, source.name),
                onConfirm = {
                    viewModel.deleteSource(source)
                    deleteSourceConfirm = null
                },
                onDismiss = { deleteSourceConfirm = null }
            )
        }

        if (showDeleteSelectedGroupsConfirm) {
            SettingsConfirmDeleteDialog(
                title = stringResource(R.string.delete),
                text = stringResource(R.string.delete_selected_groups_confirm, selectedGroupIds.size),
                onConfirm = {
                    val selected = currentGroups
                        .map { it.group }
                        .filter { selectedGroupIds.contains(it.id) }
                    viewModel.deleteGroups(selected)
                    selectedGroupIds.clear()
                    showDeleteSelectedGroupsConfirm = false
                },
                onDismiss = { showDeleteSelectedGroupsConfirm = false }
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
        shape = AppCardShape,
        colors = appCardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ),
        border = appCardBorder(
            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            if (isSelectionMode) onToggleSelectGroup() else isExpanded = !isExpanded
                        },
                        onLongClick = onLongSelectGroup
                    )
                    .padding(horizontal = AppDimens.CardContentPadding, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(AppDimens.InlineItemSpacing))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = groupWithSources.group.displayName(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = stringResource(R.string.sources_count, groupWithSources.sources.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        var showDropdown by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { showDropdown = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.MoreHoriz, contentDescription = null, modifier = Modifier.size(20.dp))
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
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.add_source)) },
                                trailingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                enabled = canAddSourceToGroup(groupWithSources.group),
                                onClick = {
                                    onAddSource()
                                    showDropdown = false
                                }
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

            if (isExpanded) {
                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                    )

                    if (groupWithSources.sources.isEmpty()) {
                        Text(
                            text = stringResource(R.string.sources_group_empty),
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
            SourceType.RSS -> {
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
            .padding(horizontal = AppDimens.CardContentPadding, vertical = AppDimens.CompactItemSpacing)
            .alpha(if (isGroupEnabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(source.type.iconRes),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.width(AppDimens.InlineItemSpacing))
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
        Box {
            var showDropdown by remember { mutableStateOf(false) }
            IconButton(
                onClick = { showDropdown = true },
                enabled = isGroupEnabled,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false },
                shape = MaterialTheme.shapes.large,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit_source)) },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        onEdit(source)
                        showDropdown = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        onDelete(source)
                        showDropdown = false
                    }
                )
            }
        }
    }
}

@Composable
fun GroupDialog(
    group: SourceGroup? = null,
    existingGroupNames: List<String>,
    reservedGroupNames: List<String>,
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
    val isReservedSubscriptionName = remember(normalizedName, reservedGroupNames, group?.id) {
        reservedGroupNames.any {
            it.trim().equals(normalizedName, ignoreCase = true) &&
                !it.trim().equals(group?.name?.trim().orEmpty(), ignoreCase = true)
        }
    }
    val errorText = when {
        normalizedName.isBlank() -> stringResource(R.string.validation_name_required)
        isDuplicate -> stringResource(R.string.validation_group_name_exists)
        isReservedSubscriptionName -> stringResource(R.string.validation_group_name_reserved_by_subscription)
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
    onFetchGeneratedName: (String, SourceType, (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String, String, SourceType, String?, String?, String?, String?, Boolean) -> Unit
) {
    val availableSourceTypes = remember { listOf(SourceType.RSS, SourceType.TELEGRAM, SourceType.YOUTUBE) }
    var name by remember(source?.id) { mutableStateOf(source?.name ?: "") }
    var url by remember(source?.id) { mutableStateOf(source?.url ?: "") }
    var type by remember(source?.id) { mutableStateOf(source?.type ?: SourceType.RSS) }
    var titleSelector by remember(source?.id) { mutableStateOf(source?.titleSelector ?: "") }
    var postLinkSelector by remember(source?.id) { mutableStateOf(source?.postLinkSelector ?: "") }
    var descriptionSelector by remember(source?.id) { mutableStateOf(source?.descriptionSelector ?: "") }
    var dateSelector by remember(source?.id) { mutableStateOf(source?.dateSelector ?: "") }
    var useHeadlessBrowser by remember(source?.id) { mutableStateOf(source?.useHeadlessBrowser ?: false) }
    var expanded by remember { mutableStateOf(false) }
    var isFetchingName by remember { mutableStateOf(false) }
    val normalizedName = name.trim()
    val normalizedUrl = normalizeSourceUrl(url, type)
    val isDuplicate = remember(normalizedName, existingSourceNames, source?.id) {
        if (normalizedName.isBlank()) return@remember false
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
    val isSourceUrlValid = SourceUrlValidator.isValid(url, type)
    val nameErrorText = when {
        normalizedName.isBlank() -> stringResource(R.string.validation_name_required)
        isDuplicate -> stringResource(R.string.validation_source_name_exists)
        else -> null
    }
    val urlErrorText = when {
        url.isBlank() -> stringResource(R.string.validation_source_url_required)
        !isSourceUrlValid -> stringResource(R.string.validation_source_url_type_mismatch)
        isDuplicateUrl -> stringResource(R.string.validation_source_url_exists)
        else -> null
    }
    val isNameError = nameErrorText != null
    val isUrlError = urlErrorText != null
    val formHasError = isNameError || isUrlError

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
                            shape = AppCardShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = appCardBorder(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = stringResource(R.string.source_dialog_help_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.source_dialog_help_name),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = stringResource(R.string.source_dialog_help_url),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = stringResource(R.string.source_dialog_help_type),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = stringResource(R.string.source_dialog_help_groups),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = stringResource(type.labelRes),
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
                            availableSourceTypes.forEach { entry ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(entry.labelRes)) },
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
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(stringResource(R.string.source_url)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        isError = isUrlError,
                        supportingText = urlErrorText?.let { error ->
                            { Text(error) }
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(stringResource(R.string.source_name)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.large,
                            isError = isNameError,
                            supportingText = nameErrorText?.let { error ->
                                { Text(error) }
                            }
                        )
                        TextButton(
                            onClick = {
                                isFetchingName = true
                                onFetchGeneratedName(url, type) { generatedName ->
                                    generatedName?.let { name = it }
                                    isFetchingName = false
                                }
                            },
                            enabled = url.isNotBlank() && isSourceUrlValid && !isFetchingName,
                            modifier = Modifier.offset(y = (-2).dp)
                        ) {
                            if (isFetchingName) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(stringResource(R.string.source_name_fetch))
                            }
                        }
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
                            if (!formHasError) {
                                onConfirm(
                                    normalizedName,
                                    url,
                                    type,
                                    null,
                                    null,
                                    null,
                                    null,
                                    false
                                )
                                onDismiss()
                            }
                        },
                        enabled = !formHasError,
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
    return com.andrewwin.sumup.domain.source.SourceUrlNormalizer.normalize(url, type)
}

@Composable
fun SuggestedThemeItem(
    suggestion: FirebaseThemeSuggestion,
    appLanguage: AppLanguage,
    showRecommendedBadge: Boolean,
    modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit
) {
    val targetSubscribedState = !suggestion.isSubscribed
    Surface(
        onClick = { onToggle(targetSubscribedState) },
        shape = AppCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = appCardBorder(),
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
                    text = suggestion.group.displayName(appLanguage),
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
              if (showRecommendedBadge && suggestion.isRecommended) {
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                      text = stringResource(R.string.sources_recommended_badge),
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.primary.copy(alpha = 0.78f),
                      fontWeight = FontWeight.SemiBold
                  )
              }
          }
      }
  }

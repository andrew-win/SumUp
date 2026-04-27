package com.andrewwin.sumup.ui.screen.summary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.andrewwin.sumup.R
import com.andrewwin.sumup.ui.components.AppCardSurface
import com.andrewwin.sumup.data.local.entities.Summary
import com.andrewwin.sumup.ui.components.AppExportPdfButton
import com.andrewwin.sumup.ui.components.AppFilterMenuChip
import com.andrewwin.sumup.ui.components.AppHelpOverlayTarget
import com.andrewwin.sumup.ui.components.AppSearchField
import com.andrewwin.sumup.ui.components.AppAnimatedDialog
import com.andrewwin.sumup.ui.util.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun SummaryHistoryListSection(
    summaries: List<Summary>,
    selectedSummaryIds: List<Long>,
    isSelectionMode: Boolean,
    activeSummaryModelName: String?,
    listState: LazyListState,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchFocusChanged: (Boolean) -> Unit,
    dateFilter: HistoryDateFilter,
    onDateFilterChange: (HistoryDateFilter) -> Unit,
    savedFilter: HistorySavedFilter,
    onSavedFilterChange: (HistorySavedFilter) -> Unit,
    onExportPdf: () -> Unit,
    isExportEnabled: Boolean,
    modifier: Modifier = Modifier,
    onOpenSummary: (Summary) -> Unit,
    onToggleFavorite: (Summary) -> Unit,
    onDeleteSummary: (Summary) -> Unit,
    onLongSelect: (Summary) -> Unit,
    onToggleSelect: (Summary) -> Unit,
    isHelpMode: Boolean,
    historyFiltersHelpDescription: String,
    historyCardHelpDescription: String,
    onShowHelpDescription: (String) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            AppHelpOverlayTarget(
                isEnabled = isHelpMode,
                description = historyFiltersHelpDescription,
                onShowDescription = onShowHelpDescription
            ) {
                SummaryHistoryFiltersRow(
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    onSearchFocusChanged = onSearchFocusChanged,
                    dateFilter = dateFilter,
                    onDateFilterChange = onDateFilterChange,
                    savedFilter = savedFilter,
                    onSavedFilterChange = onSavedFilterChange,
                    onExportPdf = onExportPdf,
                    isExportEnabled = isExportEnabled,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
        if (summaries.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.summary_history_list_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 24.dp)
                )
            }
        }
        items(summaries, key = { it.id }) { summary ->
            AppHelpOverlayTarget(
                isEnabled = isHelpMode,
                description = historyCardHelpDescription,
                onShowDescription = onShowHelpDescription
            ) {
                SummaryHistoryCard(
                    summary = summary,
                    isSelected = selectedSummaryIds.contains(summary.id),
                    isSelectionMode = isSelectionMode,
                    activeSummaryModelName = activeSummaryModelName,
                    onClick = { if (isSelectionMode) onToggleSelect(summary) else onOpenSummary(summary) },
                    onLongClick = { onLongSelect(summary) },
                    onToggleFavorite = { onToggleFavorite(summary) },
                    onDelete = { onDeleteSummary(summary) }
                )
            }
        }
    }
}

@Composable
private fun SummaryHistoryFiltersRow(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchFocusChanged: (Boolean) -> Unit,
    dateFilter: HistoryDateFilter,
    onDateFilterChange: (HistoryDateFilter) -> Unit,
    savedFilter: HistorySavedFilter,
    onSavedFilterChange: (HistorySavedFilter) -> Unit,
    onExportPdf: () -> Unit,
    isExportEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    var showDateMenu by remember { mutableStateOf(false) }
    var showSavedMenu by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppSearchField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = stringResource(R.string.summary_search_placeholder),
                leadingIcon = Icons.Default.Search,
                modifier = Modifier.weight(1f),
                onFocusChanged = { focused ->
                    if (focused) onSearchFocusChanged(true)
                }
            )
            AppExportPdfButton(
                onClick = onExportPdf,
                enabled = isExportEnabled,
                contentDescription = stringResource(R.string.export_feed_pdf)
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryHistoryFilterChip(
                icon = Icons.Default.CalendarToday,
                label = stringResource(dateFilter.labelRes),
                onClick = { showDateMenu = true }
            )
            SummaryHistoryFilterChip(
                icon = Icons.Default.Bookmark,
                label = stringResource(savedFilter.labelRes),
                onClick = { showSavedMenu = true }
            )

            DropdownMenu(expanded = showDateMenu, onDismissRequest = { showDateMenu = false }) {
                HistoryDateFilter.entries.forEach { filter ->
                    DropdownMenuItem(
                        text = { Text(stringResource(filter.labelRes)) },
                        onClick = {
                            onDateFilterChange(filter)
                            showDateMenu = false
                        }
                    )
                }
            }

            DropdownMenu(expanded = showSavedMenu, onDismissRequest = { showSavedMenu = false }) {
                HistorySavedFilter.entries.forEach { filter ->
                    DropdownMenuItem(
                        text = { Text(stringResource(filter.labelRes)) },
                        onClick = {
                            onSavedFilterChange(filter)
                            showSavedMenu = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryHistoryFilterChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    AppFilterMenuChip(
        icon = icon,
        label = label,
        onClick = onClick,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.05f))
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SummaryHistoryCard(
    summary: Summary,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    activeSummaryModelName: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    val preview = remember(summary.content) { summaryHistoryPreview(summary.content) }
    val dateLabel = remember(summary.createdAt) { summaryHistoryDate(summary.createdAt) }
    val context = LocalContext.current

    AppCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = preview.ifBlank { stringResource(R.string.summary_default_title) },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "...",
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelectionMode) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!isSelectionMode) {
                SummaryFooterRow(
                    summary = summary,
                    isError = summary.isError,
                    context = context,
                    modelName = activeSummaryModelName,
                    onToggleFavorite = onToggleFavorite,
                    onDelete = onDelete
                )
            }
        }
    }
}

@Composable
internal fun SummaryHistoryDialogView(
    summary: Summary,
    activeSummaryModelName: String?,
    onDismiss: () -> Unit,
    onOpenWebView: (String) -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val context = LocalContext.current
    val blocks = remember(summary.content) { parseSummaryBlocks(summary.content) }
    AppAnimatedDialog(
        visible = true,
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 34.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = summaryHistoryDate(summary.createdAt),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = true)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StandardSummaryView(
                        result = null,
                        blocks = blocks,
                        onOpenWebView = onOpenWebView
                    )
                    Spacer(Modifier.height(2.dp))
                    SummaryFooterRow(
                        summary = summary,
                        isError = false,
                        context = context,
                        modelName = activeSummaryModelName,
                        onToggleFavorite = onToggleFavorite,
                        onDelete = onDelete
                    )
                }
            }
        }
    }
}

internal fun summaryHistoryPreview(raw: String): String {
    return parseSummaryBlocks(raw).firstOrNull()?.let { block ->
        when (block) {
            is SummaryBlockUi.Section -> block.body.lineSequence().firstOrNull()?.trim().orEmpty()
            is SummaryBlockUi.Theme -> block.items.firstOrNull()?.text.orEmpty()
            is SummaryBlockUi.PlainList -> block.items.firstOrNull()?.text.orEmpty()
        }
    }.orEmpty().ifBlank { "" }
}

internal fun filterHistorySummariesV2(
    summaries: List<Summary>,
    query: String,
    dateFilter: HistoryDateFilter,
    savedFilter: HistorySavedFilter
): List<Summary> {
    val now = System.currentTimeMillis()
    val queryText = query.trim()
    return summaries.filter { summary ->
        val matchesQuery = queryText.isBlank() || summary.content.contains(queryText, ignoreCase = true)
        val matchesSaved = !savedFilter.favoritesOnly || summary.isFavorite
        val matchesDate = dateFilter.hours == null || summary.createdAt >= now - dateFilter.hours * 60L * 60L * 1000L
        matchesQuery && matchesSaved && matchesDate
    }
}

internal fun summaryHistoryDate(timestamp: Long): String {
    return SimpleDateFormat("HH:mm, dd MMMM", Locale.getDefault()).format(Date(timestamp))
}

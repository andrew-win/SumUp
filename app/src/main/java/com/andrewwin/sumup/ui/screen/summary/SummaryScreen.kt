package com.andrewwin.sumup.ui.screen.summary

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.Summary
import com.andrewwin.sumup.ui.util.SummaryBlockUi
import com.andrewwin.sumup.ui.util.ThemeItem
import com.andrewwin.sumup.ui.util.cleanSummaryTextForSharing
import com.andrewwin.sumup.ui.util.normalizeSummaryUrlForWebView
import com.andrewwin.sumup.ui.util.parseSummaryBlocks
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

private const val InlineSourceChipMaxWidthDp = 92
private const val InlineSourceAnnotationTag = "summary_source"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    viewModel: SummaryViewModel = hiltViewModel(),
    onOpenWebView: (String) -> Unit = {}
) {
    val summaries by viewModel.summaries.collectAsState()
    val userPreferences by viewModel.userPreferences.collectAsState()
    val activeSummaryModelName by viewModel.activeSummaryModelName.collectAsState()
    val chartData by viewModel.chartData.collectAsState()
    val chartType by viewModel.chartType.collectAsState()
    val isVectorizationEnabled by viewModel.isVectorizationEnabled.collectAsState()

    val lastSummary = remember(summaries) { summaries.firstOrNull() }
    val selectedSummaryIds = remember { mutableStateListOf<Long>() }
    val isSelectionMode = selectedSummaryIds.isNotEmpty()
    var isHelpMode by rememberSaveable { mutableStateOf(false) }
    var helpDescription by remember { mutableStateOf<String?>(null) }
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var isHistoryScreen by rememberSaveable { mutableStateOf(false) }
    var openedHistorySummaryId by rememberSaveable { mutableStateOf<Long?>(null) }
    val tabs = listOf("Заплановані", "Статистика")
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val historySummaries = remember(summaries) { summaries.drop(1) }
    val openedHistorySummary = remember(openedHistorySummaryId, summaries) {
        summaries.firstOrNull { it.id == openedHistorySummaryId }
    }
    val showBackToTop by remember {
        derivedStateOf {
            !isHistoryScreen &&
                selectedTabIndex == 1 &&
                (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 100)
        }
    }

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
                        Text("Вибрано: ${selectedSummaryIds.size}")
                    } else if (isHistoryScreen) {
                        Text(stringResource(R.string.summary_history_title))
                    } else {
                        Text(stringResource(R.string.nav_summary))
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledIconButton(
                                onClick = { selectedSummaryIds.clear() },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Exit selection mode")
                            }
                            FilledIconButton(
                                onClick = {
                                    viewModel.deleteSummaries(selectedSummaryIds.toList())
                                    selectedSummaryIds.clear()
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete selected summaries")
                            }
                        }
                    } else if (isHistoryScreen) {
                        FilledIconButton(
                            onClick = { isHistoryScreen = false },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Закрити історію")
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
            when {
                isHistoryScreen -> Unit
                selectedTabIndex == 0 && historySummaries.isNotEmpty() && !isSelectionMode -> {
                    FloatingActionButton(
                        onClick = { isHistoryScreen = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.size(width = 75.dp, height = 65.dp)
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = stringResource(R.string.summary_history_title),
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
                else -> AnimatedVisibility(
                    visible = showBackToTop,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    SmallFloatingActionButton(
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.back_to_top))
                    }
                }
            }
        }
    ) { innerPadding ->
        if (isHistoryScreen) {
            HistorySummaryList(
                summaries = historySummaries,
                selectedSummaryIds = selectedSummaryIds,
                isSelectionMode = isSelectionMode,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onOpenSummary = { openedHistorySummaryId = it.id },
                onLongSelect = { summary ->
                    if (!selectedSummaryIds.contains(summary.id)) selectedSummaryIds.add(summary.id)
                },
                onToggleSelect = { summary ->
                    if (selectedSummaryIds.contains(summary.id)) selectedSummaryIds.remove(summary.id)
                    else selectedSummaryIds.add(summary.id)
                }
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(top = 0.dp, bottom = 80.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color.Transparent
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                        imageVector = if (index == 0) Icons.Default.Schedule else Icons.Default.BarChart,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(title)
                                    }
                                }
                            )
                        }
                    }
                }

                if (selectedTabIndex == 0) {
                    item {
                        HelpOverlayTarget(
                            isEnabled = isHelpMode,
                            description = "Статус: показує час останнього зведення або коли заплановано наступне.",
                            onShowDescription = { helpDescription = it }
                        ) {
                            PrevNextStatusRow(
                                previousSummaryAt = lastSummary?.createdAt,
                                isScheduledEnabled = userPreferences.isScheduledSummaryEnabled,
                                nextScheduledAt = if (userPreferences.isScheduledSummaryEnabled) {
                                    getNextScheduledTimeMillis(
                                        userPreferences.scheduledHour,
                                        userPreferences.scheduledMinute
                                    )
                                } else null
                            )
                        }
                    }

                    if (lastSummary != null) {
                        item {
                            HelpOverlayTarget(
                                isEnabled = isHelpMode,
                                description = "Останнє зведення: поточний актуальний підсумок з діями копіювання та поширення.",
                                onShowDescription = { helpDescription = it }
                            ) {
                            LatestScheduledSummaryView(
                                summary = lastSummary,
                                activeSummaryModelName = activeSummaryModelName,
                                onOpenWebView = onOpenWebView,
                                onDelete = { viewModel.deleteSummary(lastSummary.id) }
                                )
                            }
                        }
                    }
                }

                if (selectedTabIndex == 1) {
                    item {
                        HelpOverlayTarget(
                            isEnabled = isHelpMode,
                            description = "Інфографіка: ключові новини та метрики. Натисни на рядок, щоб відкрити джерело.",
                            onShowDescription = { helpDescription = it }
                        ) {
                            SummaryChart(
                                items = chartData,
                                currentType = chartType,
                                onTypeChange = viewModel::setChartType,
                                isModelEnabled = isVectorizationEnabled,
                                onOpenWebView = onOpenWebView
                            )
                        }
                    }
                }
            }
        }

        if (openedHistorySummary != null) {
            HistorySummaryDialog(
                summary = openedHistorySummary,
                activeSummaryModelName = activeSummaryModelName,
                onDismiss = { openedHistorySummaryId = null },
                onOpenWebView = onOpenWebView
            )
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
    }
}

@Composable
private fun HelpOverlayTarget(
    isEnabled: Boolean,
    description: String,
    onShowDescription: (String) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
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
}

@Composable
private fun HistorySummaryList(
    summaries: List<Summary>,
    selectedSummaryIds: List<Long>,
    isSelectionMode: Boolean,
    modifier: Modifier = Modifier,
    onOpenSummary: (Summary) -> Unit,
    onLongSelect: (Summary) -> Unit,
    onToggleSelect: (Summary) -> Unit
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(summaries, key = { it.id }) { summary ->
            HistorySummaryCard(
                summary = summary,
                isSelected = selectedSummaryIds.contains(summary.id),
                isSelectionMode = isSelectionMode,
                onClick = { if (isSelectionMode) onToggleSelect(summary) else onOpenSummary(summary) },
                onLongClick = { onLongSelect(summary) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistorySummaryCard(
    summary: Summary,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val preview = remember(summary.content) { extractSummaryPreview(summary.content) }
    val dateLabel = remember(summary.createdAt) {
        SimpleDateFormat("HH:mm, dd MMMM", Locale("uk", "UA")).format(Date(summary.createdAt))
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(18.dp),
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
                text = preview,
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
        }
    }
}

@Composable
private fun HistorySummaryDialog(
    summary: Summary,
    activeSummaryModelName: String?,
    onDismiss: () -> Unit,
    onOpenWebView: (String) -> Unit
) {
    val context = LocalContext.current
    val blocks = remember(summary.content) { parseSummaryBlocks(summary.content) }
    Dialog(
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
                        .padding(top = 24.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = SimpleDateFormat("HH:mm, dd MMMM", Locale("uk", "UA"))
                            .format(Date(summary.createdAt)),
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
                    blocks.forEach { block ->
                        when (block) {
                            is SummaryBlockUi.Section -> SummaryLegacyBlock(
                                text = block.body,
                                sourceName = block.source?.name,
                                sourceUrl = block.source?.url,
                                onOpenWebView = onOpenWebView
                            )
                            is SummaryBlockUi.PlainList -> SummaryPlainListBlock(
                                items = block.items,
                                onOpenWebView = onOpenWebView
                            )
                            is SummaryBlockUi.Theme -> SummaryThemeBlock(
                                heading = block.heading,
                                items = block.items,
                                onOpenWebView = onOpenWebView
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    SummaryFooterRow(
                        summary = summary,
                        isError = false,
                        context = context,
                        modelName = activeSummaryModelName
                    )
                }
            }
        }
    }
}

private fun extractSummaryPreview(raw: String): String {
    return parseSummaryBlocks(raw).firstOrNull()?.let { block ->
        when (block) {
            is SummaryBlockUi.Section -> block.body.lineSequence().firstOrNull()?.trim().orEmpty()
            is SummaryBlockUi.Theme -> block.items.firstOrNull()?.text.orEmpty()
            is SummaryBlockUi.PlainList -> block.items.firstOrNull()?.text.orEmpty()
        }
    }.orEmpty().ifBlank { "Зведення" }
}

@Composable
private fun SectionHeader(text: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SummaryChart(
    items: List<SummaryChartItem>,
    currentType: SummaryChartType,
    onTypeChange: (SummaryChartType) -> Unit,
    isModelEnabled: Boolean,
    onOpenWebView: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChartTypeChip(
                selected = currentType == SummaryChartType.VIEWS,
                onClick = { onTypeChange(SummaryChartType.VIEWS) },
                label = stringResource(R.string.chart_views)
            )
            ChartTypeChip(
                selected = currentType == SummaryChartType.MENTIONS,
                onClick = { onTypeChange(SummaryChartType.MENTIONS) },
                label = stringResource(R.string.chart_mentions)
            )
            ChartTypeChip(
                selected = currentType == SummaryChartType.FACTUALITY,
                onClick = { onTypeChange(SummaryChartType.FACTUALITY) },
                label = stringResource(R.string.chart_factuality)
            )
        }

        if (items.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.no_articles_prefix),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.forEachIndexed { index, item ->
                    ChartBar(
                        item = item,
                        index = index,
                        maxValue = 1f,
                        onOpenWebView = onOpenWebView
                    )
                }
            }
        }
    }
}

@Composable
fun ChartTypeChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.05f))
    )
}

@Composable
fun ChartBar(
    item: SummaryChartItem,
    index: Int,
    maxValue: Float,
    onOpenWebView: (String) -> Unit
) {
    val rankColor = when (index) {
        0 -> Color(0xFFFFC857)
        1 -> Color(0xFFB8B9FF)
        2 -> Color(0xFF65F0B5)
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !item.sourceUrl.isNullOrBlank()) {
                item.sourceUrl?.let { onOpenWebView(normalizeSummaryUrlForWebView(it)) }
            },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 42.dp, height = 36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(rankColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = rankColor
                )
            }

            Spacer(Modifier.width(14.dp))

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.headline,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = item.displayValue,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                            color = if (item.isValueUnavailable) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                        item.sourceName?.takeIf { it.isNotBlank() }?.let { sourceName ->
                            Text(
                                text = sourceName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PrevNextStatusRow(
    previousSummaryAt: Long?,
    isScheduledEnabled: Boolean,
    nextScheduledAt: Long?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatusMiniCard(
                label = stringResource(R.string.summary_previous_short),
                status = previousSummaryAt?.let { formatStatusTimeAndDate(it) }
                    ?: Pair(stringResource(R.string.summary_not_ready), ""),
                modifier = Modifier.weight(1f)
            )
            StatusMiniCard(
                label = stringResource(R.string.summary_next_short),
                status = if (isScheduledEnabled && nextScheduledAt != null) {
                    formatStatusTimeAndDate(nextScheduledAt)
                } else {
                    Pair(stringResource(R.string.summary_not_ready), "")
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatusMiniCard(
    label: String,
    status: Pair<String, String>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = status.first,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (status.second.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = status.second,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LatestScheduledSummaryView(
    summary: Summary,
    activeSummaryModelName: String?,
    onOpenWebView: (String) -> Unit,
    onDelete: () -> Unit
) {
    val isError = summary.content.startsWith(stringResource(R.string.error_prefix)) ||
            summary.content.startsWith(stringResource(R.string.no_articles_prefix))
    val context = LocalContext.current
    val blocks = remember(summary.content) { parseSummaryBlocks(summary.content) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isError) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.18f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.18f))
            ) {
                Text(
                    text = summary.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
        } else {
            blocks.forEach { block ->
                when (block) {
                    is SummaryBlockUi.Section -> SummaryLegacyBlock(
                        text = block.body,
                        sourceName = block.source?.name,
                        sourceUrl = block.source?.url,
                        onOpenWebView = onOpenWebView
                    )
                    is SummaryBlockUi.PlainList -> SummaryPlainListBlock(
                        items = block.items,
                        onOpenWebView = onOpenWebView
                    )
                    is SummaryBlockUi.Theme -> SummaryThemeBlock(
                        heading = block.heading,
                        items = block.items,
                        onOpenWebView = onOpenWebView
                    )
                }
            }
        }

        SummaryFooterRow(
            summary = summary,
            isError = isError,
            context = context,
            modelName = activeSummaryModelName,
            onDelete = onDelete
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SummaryCard(
    summary: Summary,
    activeSummaryModelName: String?,
    onOpenWebView: (String) -> Unit,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    startExpanded: Boolean = false,
    onDelete: () -> Unit,
    onLongSelect: () -> Unit,
    onToggleSelect: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm, dd MMMM", Locale("uk", "UA")) }
    val isError = summary.content.startsWith(stringResource(R.string.error_prefix)) ||
            summary.content.startsWith(stringResource(R.string.no_articles_prefix))
    val context = LocalContext.current

    val blocks = remember(summary.content) { parseSummaryBlocks(summary.content) }
    var isExpanded by rememberSaveable(summary.id, startExpanded) { mutableStateOf(startExpanded) }
    val expandRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "summaryExpandRotation"
    )

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(
            text = dateFormat.format(Date(summary.createdAt)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (isSelectionMode) onToggleSelect() else isExpanded = !isExpanded
                    },
                    onLongClick = onLongSelect
                ),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                    else -> MaterialTheme.colorScheme.surfaceContainer
                }
            ),
            border = BorderStroke(
                1.dp,
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.05f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 44.dp, top = if (isExpanded) 4.dp else 0.dp)
                            .animateContentSize(animationSpec = tween(durationMillis = 130))
                    ) {
                        if (isExpanded && !isError) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                blocks.forEach { block ->
                    when (block) {
                        is SummaryBlockUi.Section -> SummaryLegacyBlock(
                            text = block.body,
                            sourceName = block.source?.name,
                            sourceUrl = block.source?.url,
                            onOpenWebView = onOpenWebView
                        )
                        is SummaryBlockUi.PlainList -> SummaryPlainListBlock(
                            items = block.items,
                            onOpenWebView = onOpenWebView
                        )
                        is SummaryBlockUi.Theme -> SummaryThemeBlock(
                            heading = block.heading,
                            items = block.items,
                            onOpenWebView = onOpenWebView
                        )
                                    }
                                }
                            }
                        } else {
                            SummaryCollapsedPreview(blocks = blocks, isError = isError)
                        }
                    }

                    Surface(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.align(Alignment.TopEnd),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.05f))
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier
                                .size(28.dp)
                                .padding(4.dp)
                                .graphicsLayer { rotationZ = expandRotation },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                SummaryFooterRow(
                    summary = summary,
                    isError = isError,
                    context = context,
                    modelName = activeSummaryModelName
                )
            }
        }
    }
}

@Composable
private fun SummaryFooterRow(
    summary: Summary,
    isError: Boolean,
    context: Context,
    modelName: String?,
    onDelete: (() -> Unit)? = null
) {
    val compactModel = modelName
        ?.substringAfter('/', modelName)
        ?.takeIf { it.isNotBlank() }
    val strategyLabel = when (summary.strategy) {
        AiStrategy.CLOUD -> "Хмарна"
        AiStrategy.LOCAL -> "Локальна"
        AiStrategy.ADAPTIVE -> "Адаптивна"
    }
    val metaText = if (isError) {
        stringResource(R.string.summary_system_notice)
    } else {
        buildString {
            append(strategyLabel)
            if (summary.strategy != AiStrategy.LOCAL && !compactModel.isNullOrBlank()) {
                append(", ")
                append(compactModel)
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = metaText,
            style = MaterialTheme.typography.labelSmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(6.dp))
        IconButton(
            onClick = {
                copySummaryText(context, summary.content)
                Toast.makeText(
                    context,
                    context.getString(R.string.summary_copied),
                    Toast.LENGTH_SHORT
                ).show()
            },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = { shareSummaryText(context, summary.content) },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onDelete != null) {
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Delete summary",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun SummaryCollapsedPreview(
    blocks: List<SummaryBlockUi>,
    isError: Boolean
) {
    val previewText = remember(blocks) {
        buildAnnotatedString {
            blocks.forEachIndexed { blockIndex, block ->
                if (blockIndex > 0) append("\n\n")
                when (block) {
                    is SummaryBlockUi.Section -> append(block.body)
                    is SummaryBlockUi.PlainList -> {
                        block.items.take(3).forEachIndexed { itemIndex, item ->
                            if (itemIndex > 0) append("\n")
                            append(item.marker)
                            append(' ')
                            append(item.text)
                        }
                    }
                    is SummaryBlockUi.Theme -> {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(block.heading)
                        pop()
                        block.items.take(2).forEach { item ->
                            append("\n")
                            append(item.marker)
                            append(' ')
                            append(item.text)
                        }
                    }
                }
            }
        }
    }
    SelectionContainer {
        Text(
            text = previewText,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 26.sp
        )
    }
}

private fun isSameDay(t1: Long, t2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = t1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = t2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

private fun getNextScheduledTimeMillis(hour: Int, minute: Int): Long {
    val now = Calendar.getInstance()
    val next = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (next.timeInMillis <= now.timeInMillis) {
        next.add(Calendar.DAY_OF_YEAR, 1)
    }
    return next.timeInMillis
}

private fun formatStatusTimeAndDate(timestamp: Long): Pair<String, String> {
    val timeFormat = SimpleDateFormat("HH:mm", Locale("uk", "UA"))
    val dateFormat = SimpleDateFormat("dd MMM", Locale("uk", "UA"))
    val date = Date(timestamp)
    return Pair(timeFormat.format(date), dateFormat.format(date))
}

private fun shareSummaryText(
    context: Context,
    summaryText: String
) {
    val cleaned = cleanSummaryTextForSharing(summaryText)
    if (cleaned.isBlank()) return
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, cleaned)
    }
    val chooser = Intent.createChooser(
        shareIntent,
        context.getString(R.string.summary_share_chooser_title)
    )
    context.startActivity(chooser)
}

private fun copySummaryText(
    context: Context,
    summaryText: String
) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("summary_text", cleanSummaryTextForSharing(summaryText))
    clipboardManager.setPrimaryClip(clip)
}

@Composable
private fun SummaryLegacyBlock(
    text: String,
    sourceName: String?,
    sourceUrl: String?,
    onOpenWebView: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            InlineSummaryRow(
                text = text,
                sourceName = sourceName,
                sourceUrl = sourceUrl,
                onOpenWebView = onOpenWebView,
                textStyle = MaterialTheme.typography.bodyMedium,
                lineHeight = 24.sp
            )
        }
    }
}

@Composable
private fun SummaryThemeBlock(
    heading: String,
    items: List<ThemeItem>,
    onOpenWebView: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = heading,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            items.forEach { item ->
                InlineSummaryRow(
                    text = "${item.marker} ${item.text}",
                    sourceName = item.source?.name,
                    sourceUrl = item.source?.url,
                    onOpenWebView = onOpenWebView,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    lineHeight = 23.sp
                )
            }
        }
    }
}

@Composable
private fun SummaryPlainListBlock(
    items: List<ThemeItem>,
    onOpenWebView: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { item ->
                InlineSummaryRow(
                    text = "${item.marker} ${item.text}",
                    sourceName = item.source?.name,
                    sourceUrl = item.source?.url,
                    onOpenWebView = onOpenWebView,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    lineHeight = 23.sp
                )
            }
        }
    }
}

@Composable
private fun InlineSummaryRow(
    text: String,
    sourceName: String?,
    sourceUrl: String?,
    onOpenWebView: (String) -> Unit,
    textStyle: TextStyle,
    lineHeight: TextUnit
) {
    val effectiveStyle = textStyle.copy(lineHeight = lineHeight)
    val annotated = buildAnnotatedString {
        append(text)
        if (!sourceName.isNullOrBlank() && !sourceUrl.isNullOrBlank()) {
            append(" ")
            pushStringAnnotation(tag = InlineSourceAnnotationTag, annotation = sourceUrl)
            pushStyle(
                SpanStyle(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.86f),
                    fontSize = 12.sp
                )
            )
            append("[")
            append(sourceName)
            append("]")
            pop()
            pop()
        }
    }
    ClickableText(
        text = annotated,
        style = effectiveStyle.copy(color = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier.fillMaxWidth(),
        onClick = { offset ->
            annotated
                .getStringAnnotations(tag = InlineSourceAnnotationTag, start = offset, end = offset)
                .firstOrNull()
                ?.let { onOpenWebView(normalizeSummaryUrlForWebView(it.item)) }
        }
    )
}

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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.Summary
import com.andrewwin.sumup.domain.support.SummarySourceMeta
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

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

    val todaySummary = remember(summaries) {
        summaries.firstOrNull { isSameDay(it.createdAt, System.currentTimeMillis()) }
    }
    val olderSummaries = remember(summaries, todaySummary, userPreferences.showLastSummariesCount) {
        summaries
            .filter { it.id != todaySummary?.id }
            .take(userPreferences.showLastSummariesCount.coerceAtLeast(1))
    }
    val lastSummary = remember(summaries) { summaries.firstOrNull() }
    val selectedSummaryIds = remember { mutableStateListOf<Long>() }
    val isSelectionMode = selectedSummaryIds.isNotEmpty()
    var isHelpMode by rememberSaveable { mutableStateOf(false) }
    var helpDescription by remember { mutableStateOf<String?>(null) }
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Статистика", "Заплановані")
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showBackToTop by remember {
        derivedStateOf {
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
            AnimatedVisibility(
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
    ) { innerPadding ->
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
                                        imageVector = if (index == 0) Icons.Default.BarChart else Icons.Default.Schedule,
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

            if (selectedTabIndex == 1) {
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

                if (todaySummary != null) {
                    item {
                        HelpOverlayTarget(
                            isEnabled = isHelpMode,
                            description = "Сьогоднішнє зведення: останній підсумок за поточний день з діями копіювання, поширення та видалення.",
                            onShowDescription = { helpDescription = it }
                        ) {
                            SummaryCard(
                                summary = todaySummary,
                                activeSummaryModelName = activeSummaryModelName,
                                onOpenWebView = onOpenWebView,
                                isSelected = selectedSummaryIds.contains(todaySummary.id),
                                isSelectionMode = isSelectionMode,
                                onDelete = { viewModel.deleteSummary(todaySummary.id) },
                                onLongSelect = {
                                    if (!selectedSummaryIds.contains(todaySummary.id)) {
                                        selectedSummaryIds.add(todaySummary.id)
                                    }
                                },
                                onToggleSelect = {
                                    if (selectedSummaryIds.contains(todaySummary.id)) {
                                        selectedSummaryIds.remove(todaySummary.id)
                                    } else {
                                        selectedSummaryIds.add(todaySummary.id)
                                    }
                                }
                            )
                        }
                    }
                }

                if (olderSummaries.isNotEmpty()) {
                    items(olderSummaries, key = { it.id }) { summary ->
                        HelpOverlayTarget(
                            isEnabled = isHelpMode,
                            description = "Історія зведень: попередні підсумки. Натисни, щоб розгорнути або згорнути.",
                            onShowDescription = { helpDescription = it }
                        ) {
                            SummaryCard(
                                summary = summary,
                                activeSummaryModelName = activeSummaryModelName,
                                onOpenWebView = onOpenWebView,
                                isSelected = selectedSummaryIds.contains(summary.id),
                                isSelectionMode = isSelectionMode,
                                onDelete = { viewModel.deleteSummary(summary.id) },
                                onLongSelect = {
                                    if (!selectedSummaryIds.contains(summary.id)) {
                                        selectedSummaryIds.add(summary.id)
                                    }
                                },
                                onToggleSelect = {
                                    if (selectedSummaryIds.contains(summary.id)) {
                                        selectedSummaryIds.remove(summary.id)
                                    } else {
                                        selectedSummaryIds.add(summary.id)
                                    }
                                }
                            )
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
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.05f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (items.isEmpty()) {
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
                } else {
                    val maxValue = items.maxOfOrNull { it.value }?.takeIf { it > 0 } ?: 1f
                    Column {
                        items.forEachIndexed { index, item ->
                            ChartBar(
                                item = item,
                                index = index,
                                maxValue = maxValue,
                                onOpenWebView = onOpenWebView
                            )
                            if (index != items.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChartTypeChip(
                        selected = currentType == SummaryChartType.VIEWS,
                        onClick = { onTypeChange(SummaryChartType.VIEWS) },
                        label = stringResource(R.string.chart_views)
                    )
                    if (isModelEnabled) {
                        ChartTypeChip(
                            selected = currentType == SummaryChartType.MENTIONS,
                            onClick = { onTypeChange(SummaryChartType.MENTIONS) },
                            label = stringResource(R.string.chart_mentions)
                        )
                    }
                    ChartTypeChip(
                        selected = currentType == SummaryChartType.FACTUALITY,
                        onClick = { onTypeChange(SummaryChartType.FACTUALITY) },
                        label = stringResource(R.string.chart_factuality)
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
    val fraction = (item.value / maxValue).coerceIn(0.05f, 1f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !item.sourceUrl.isNullOrBlank()) {
                item.sourceUrl?.let { onOpenWebView(normalizeForWebView(it)) }
            }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            modifier = Modifier.width(32.dp)
        )
        
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.headline,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(Modifier.height(10.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
                
                Spacer(Modifier.width(12.dp))
                
                Text(
                    text = item.displayValue,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
    val statusLabel = if (isScheduledEnabled && nextScheduledAt != null) {
        stringResource(R.string.summary_next_short)
    } else {
        stringResource(R.string.summary_previous_short)
    }

    val statusTuple = if (isScheduledEnabled && nextScheduledAt != null) {
        formatStatusTimeAndDate(nextScheduledAt)
    } else {
        previousSummaryAt?.let { formatStatusTimeAndDate(it) } ?: Pair(stringResource(R.string.summary_not_ready), "")
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.05f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = statusTuple.first,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (statusTuple.second.isNotEmpty()) {
                            Text(
                                text = statusTuple.second,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                    }
                }
            }
        }
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
    onDelete: () -> Unit,
    onLongSelect: () -> Unit,
    onToggleSelect: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm, dd MMMM", Locale("uk", "UA")) }
    val isError = summary.content.startsWith(stringResource(R.string.error_prefix)) ||
            summary.content.startsWith(stringResource(R.string.no_articles_prefix))
    val context = LocalContext.current

    val sections = remember(summary.content) { parseSummarySections(summary.content) }
    var isExpanded by rememberSaveable(summary.id) { mutableStateOf(false) }
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
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .animateContentSize(animationSpec = tween(durationMillis = 130))
                    ) {
                        if (isExpanded && !isError) {
                            SelectionContainer {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    sections.forEach { section ->
                                        Column {
                                            Text(
                                                text = section.body,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                lineHeight = 26.sp
                                            )
                                            section.source?.let { source ->
                                                Spacer(Modifier.height(8.dp))
                                                AssistChip(
                                                    onClick = { onOpenWebView(normalizeForWebView(source.url)) },
                                                    shape = MaterialTheme.shapes.medium,
                                                    label = {
                                                        Text(
                                                            text = source.name,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = Icons.Default.Link,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    },
                                                    colors = AssistChipDefaults.assistChipColors(
                                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                                    ),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.05f))
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            SelectionContainer {
                                Text(
                                    text = sections.joinToString("\n\n") { it.body },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isError) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 26.sp
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.padding(start = 12.dp),
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
                if (summary.strategy != AiStrategy.LOCAL && !activeSummaryModelName.isNullOrBlank()) {
                    Text(
                        text = "Модель: $activeSummaryModelName",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
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
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Delete summary",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    val strategyLabel = when (summary.strategy) {
                        AiStrategy.CLOUD -> stringResource(R.string.ai_strategy_cloud)
                        AiStrategy.LOCAL -> stringResource(R.string.ai_strategy_local)
                        AiStrategy.ADAPTIVE -> stringResource(R.string.ai_strategy_adaptive)
                    }
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.05f) 
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                        border = BorderStroke(1.dp, (if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary).copy(alpha = 0.05f))
                    ) {
                        Text(
                            text = if (isError) stringResource(R.string.summary_system_notice)
                            else strategyLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
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
    val cleaned = cleanSummaryText(summaryText)
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
    val clip = ClipData.newPlainText("summary_text", cleanSummaryText(summaryText))
    clipboardManager.setPrimaryClip(clip)
}

private fun cleanSummaryText(raw: String): String {
    return raw
        .lines()
        .filterNot { it.trim().startsWith(SummarySourceMeta.PREFIX) }
        .joinToString("\n")
}

private data class SourceMetaUi(val name: String, val url: String)
private data class SummarySection(val body: String, val source: SourceMetaUi?)

private fun parseSummarySections(raw: String): List<SummarySection> {
    val blocks = raw
        .split(Regex("\\n\\s*\\n"))
        .map { it.trim() }
        .filter { it.isNotBlank() }

    return blocks.map { block ->
        val lines = block.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
        val source = lines.lastOrNull()?.let { parseSourceMeta(it.trim()) }
        val bodyLines = if (source != null) lines.dropLast(1) else lines
        SummarySection(body = bodyLines.joinToString("\n"), source = source)
    }
}

private fun parseSourceMeta(line: String): SourceMetaUi? {
    if (!line.startsWith(SummarySourceMeta.PREFIX)) return null
    val payload = line.removePrefix(SummarySourceMeta.PREFIX)
    val separator = payload.lastIndexOf('|')
    if (separator <= 0 || separator >= payload.lastIndex) return null
    val name = payload.substring(0, separator).trim()
    val url = payload.substring(separator + 1).trim()
    if (name.isBlank() || url.isBlank()) return null
    return SourceMetaUi(name, url)
}

private fun normalizeForWebView(url: String): String {
    val trimmed = url.trim()
    if (!trimmed.contains("t.me/")) return trimmed
    val marker = "t.me/"
    val idx = trimmed.indexOf(marker)
    if (idx == -1) return trimmed
    val prefix = trimmed.substring(0, idx + marker.length)
    var path = trimmed.substring(idx + marker.length).trimStart('/')
    if (path.startsWith("s/")) return "$prefix$path"
    if (path.startsWith("c/")) return "$prefix$path"
    val segments = path.split("/").filter { it.isNotBlank() }
    if (segments.size >= 2) {
        path = "s/${segments[0]}/${segments[1]}"
        return "$prefix$path"
    }
    if (segments.size == 1) {
        return "$prefix" + "s/${segments[0]}"
    }
    return trimmed
}

package com.andrewwin.sumup.ui.screen.summary

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.filled.Search
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.SimpleColorFilter
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.Summary
import com.andrewwin.sumup.ui.components.AppCardSurface
import com.andrewwin.sumup.ui.components.AppAnimatedSwap
import com.andrewwin.sumup.ui.components.AppBackToTopFab
import com.andrewwin.sumup.ui.components.AppExplanationDialog
import com.andrewwin.sumup.ui.components.AppExportPdfButton
import com.andrewwin.sumup.ui.components.AppFilterMenuChip
import com.andrewwin.sumup.ui.components.AppFilledIconAction
import com.andrewwin.sumup.ui.components.AppHelpOverlayTarget
import com.andrewwin.sumup.ui.components.AppHelpToggleAction
import com.andrewwin.sumup.ui.components.AppProminentFab
import com.andrewwin.sumup.ui.components.AppSearchField
import com.andrewwin.sumup.ui.components.AppSelectionActions
import com.andrewwin.sumup.ui.components.AppTopBar
import com.andrewwin.sumup.ui.util.SummaryBlockUi
import com.andrewwin.sumup.ui.util.ThemeItem
import com.andrewwin.sumup.ui.util.cleanSummaryTextForSharing
import com.andrewwin.sumup.ui.util.normalizeSummaryUrlForWebView
import com.andrewwin.sumup.ui.util.parseSummaryBlocks
import com.andrewwin.sumup.ui.util.PdfExporter
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

internal enum class HistoryDateFilter(val labelRes: Int, val hours: Int?) {
    HOUR_1(R.string.filter_date_1h, 1),
    HOUR_12(R.string.filter_date_12h, 12),
    HOUR_24(R.string.filter_date_24h, 24),
    DAY_3(R.string.summary_history_filter_3d, 24 * 3),
    DAY_7(R.string.summary_history_filter_week, 24 * 7)
}

internal enum class HistorySavedFilter(val labelRes: Int, val favoritesOnly: Boolean) {
    ALL(R.string.filter_saved_all, false),
    FAVORITES(R.string.summary_history_filter_favorites, true)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SummaryScreen(
    viewModel: SummaryViewModel = hiltViewModel(),
    onOpenWebView: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val summaryHistoryFabHelpDescription = stringResource(R.string.summary_help_history_fab)
    val summaryHistoryFiltersHelpDescription = stringResource(R.string.summary_help_history_filters)
    val summaryHistoryCardHelpDescription = stringResource(R.string.summary_help_history_card)
    val summaryStatusHelpDescription = stringResource(R.string.summary_help_status)
    val summaryLatestHelpDescription = stringResource(R.string.summary_help_latest)
    val summaryChartHelpDescription = stringResource(R.string.summary_help_chart)
    val focusManager = LocalFocusManager.current
    val imeVisible = WindowInsets.isImeVisible
    val summaries by viewModel.summaries.collectAsState()
    val userPreferences by viewModel.userPreferences.collectAsState()
    val activeSummaryModelName by viewModel.activeSummaryModelName.collectAsState()
    val chartData by viewModel.chartData.collectAsState()
    val chartType by viewModel.chartType.collectAsState()
    val isVectorizationEnabled by viewModel.isVectorizationEnabled.collectAsState()

    val lastSummary = remember(summaries) { summaries.firstOrNull() }
    val hasAnySummaries = summaries.isNotEmpty()
    val selectedSummaryIds = remember { mutableStateListOf<Long>() }
    val isSelectionMode = selectedSummaryIds.isNotEmpty()
    var isHelpMode by rememberSaveable { mutableStateOf(false) }
    var helpDescription by remember { mutableStateOf<String?>(null) }
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var isHistoryScreen by rememberSaveable { mutableStateOf(false) }
    var openedHistorySummaryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var historySearchQuery by rememberSaveable { mutableStateOf("") }
    var isHistorySearchFocused by remember { mutableStateOf(false) }
    var historyDateFilter by rememberSaveable { mutableStateOf(HistoryDateFilter.HOUR_24) }
    var historySavedFilter by rememberSaveable { mutableStateOf(HistorySavedFilter.ALL) }
    val tabIcons = listOf(Icons.Default.Schedule, Icons.Default.BarChart)
    val listState = rememberLazyListState()
    val historyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val historySummariesRaw = remember(summaries) { summaries }
    val historySummaries = remember(
        historySummariesRaw,
        historySearchQuery,
        historyDateFilter,
        historySavedFilter
    ) {
        filterHistorySummariesV2(
            summaries = historySummariesRaw,
            query = historySearchQuery,
            dateFilter = historyDateFilter,
            savedFilter = historySavedFilter
        )
    }
    val openedHistorySummary = remember(openedHistorySummaryId, summaries) {
        summaries.firstOrNull { it.id == openedHistorySummaryId }
    }
    val showHistoryBackToTop by remember {
        derivedStateOf {
            isHistoryScreen &&
                (historyListState.firstVisibleItemIndex > 0 || historyListState.firstVisibleItemScrollOffset > 100)
        }
    }
    var wasImeVisible by remember { mutableStateOf(false) }

    BackHandler(enabled = isHistoryScreen && isHistorySearchFocused) {
        focusManager.clearFocus(force = true)
        isHistorySearchFocused = false
    }

    LaunchedEffect(imeVisible, isHistorySearchFocused, isHistoryScreen) {
        if (!isHistoryScreen) {
            wasImeVisible = false
            isHistorySearchFocused = false
            return@LaunchedEffect
        }
        if (imeVisible) {
            wasImeVisible = true
        } else if (wasImeVisible && isHistorySearchFocused) {
            focusManager.clearFocus(force = true)
            isHistorySearchFocused = false
            wasImeVisible = false
        } else if (!isHistorySearchFocused) {
            wasImeVisible = false
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val summariesToExport = when {
            isHistoryScreen -> historySummaries
            selectedTabIndex == 0 && lastSummary != null -> listOf(lastSummary)
            else -> emptyList()
        }
        if (summariesToExport.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val result = PdfExporter.exportSummariesToPdf(
                context = context,
                summaries = summariesToExport,
                uri = uri
            )
            if (result.isFailure) {
                Toast
                    .makeText(context, context.getString(R.string.export_pdf_error), Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    LaunchedEffect(isSelectionMode) {
        if (isSelectionMode && isHelpMode) {
            isHelpMode = false
        }
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = !(isHistoryScreen && isHistorySearchFocused),
                enter = fadeIn(animationSpec = tween(320, easing = FastOutSlowInEasing)) +
                    expandVertically(animationSpec = tween(380, easing = FastOutSlowInEasing)),
                exit = fadeOut(animationSpec = tween(300, easing = FastOutSlowInEasing)) +
                    shrinkVertically(animationSpec = tween(360, easing = FastOutSlowInEasing))
            ) {
                AppTopBar(
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
                            AppSelectionActions(
                                onClear = { selectedSummaryIds.clear() },
                                onDelete = {
                                    viewModel.deleteSummaries(selectedSummaryIds.toList())
                                    selectedSummaryIds.clear()
                                },
                                clearDescription = "Exit selection mode",
                                deleteDescription = "Delete selected summaries"
                            )
                        } else if (isHistoryScreen) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppHelpToggleAction(
                                    isHelpMode = isHelpMode,
                                    onToggle = { isHelpMode = !isHelpMode }
                                )
                                AppFilledIconAction(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Закрити історію",
                                    onClick = {
                                        focusManager.clearFocus(force = true)
                                        isHistorySearchFocused = false
                                        isHistoryScreen = false
                                    }
                                )
                            }
                        } else {
                            AppHelpToggleAction(
                                isHelpMode = isHelpMode,
                                onToggle = { isHelpMode = !isHelpMode }
                            )
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            when {
                showHistoryBackToTop -> {
                    AppBackToTopFab(onClick = { scope.launch { historyListState.animateScrollToItem(0) } })
                }
                !isHistoryScreen && selectedTabIndex == 0 && !isSelectionMode -> {
                    AppHelpOverlayTarget(
                        isEnabled = isHelpMode,
                        description = summaryHistoryFabHelpDescription,
                        onShowDescription = { helpDescription = it }
                    ) {
                        AppProminentFab(
                            enabled = hasAnySummaries,
                            onClick = { isHistoryScreen = true }
                        ) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = stringResource(R.string.summary_history_title),
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                }
                else -> Unit
            }
        }
    ) { innerPadding ->
        AppAnimatedSwap(
            targetState = isHistoryScreen,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            label = "summaryMainContent"
        ) { showHistory ->
            if (showHistory) {
                SummaryHistoryListSection(
                    summaries = historySummaries,
                    selectedSummaryIds = selectedSummaryIds,
                    isSelectionMode = isSelectionMode,
                    activeSummaryModelName = activeSummaryModelName,
                    listState = historyListState,
                    modifier = Modifier.fillMaxSize(),
                    searchQuery = historySearchQuery,
                    onSearchQueryChange = { historySearchQuery = it },
                    onSearchFocusChanged = { focused ->
                        if (focused) isHistorySearchFocused = true
                    },
                    dateFilter = historyDateFilter,
                    onDateFilterChange = { historyDateFilter = it },
                    savedFilter = historySavedFilter,
                    onSavedFilterChange = { historySavedFilter = it },
                    onExportPdf = {
                        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                        val name = context.getString(R.string.summary_pdf_file_name, date)
                        exportLauncher.launch(name)
                    },
                    isExportEnabled = historySummaries.isNotEmpty(),
                    onOpenSummary = { openedHistorySummaryId = it.id },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onDeleteSummary = { summary ->
                        if (openedHistorySummaryId == summary.id) openedHistorySummaryId = null
                        viewModel.deleteSummary(summary.id)
                    },
                    onLongSelect = { summary ->
                        if (!selectedSummaryIds.contains(summary.id)) selectedSummaryIds.add(summary.id)
                    },
                    onToggleSelect = { summary ->
                        if (selectedSummaryIds.contains(summary.id)) selectedSummaryIds.remove(summary.id)
                        else selectedSummaryIds.add(summary.id)
                    },
                    isHelpMode = isHelpMode,
                    historyFiltersHelpDescription = summaryHistoryFiltersHelpDescription,
                    historyCardHelpDescription = summaryHistoryCardHelpDescription,
                    onShowHelpDescription = { helpDescription = it }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 0.dp, bottom = 80.dp, start = 16.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                item {
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color.Transparent
                    ) {
                        tabIcons.forEachIndexed { index, icon ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                icon = {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                if (selectedTabIndex == 0) {
                    item {
                        AppHelpOverlayTarget(
                            isEnabled = isHelpMode,
                            description = summaryStatusHelpDescription,
                            onShowDescription = { helpDescription = it }
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
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
                    }

                    if (lastSummary != null) {
                        item {
                        AppHelpOverlayTarget(
                            isEnabled = isHelpMode,
                            description = summaryLatestHelpDescription,
                            onShowDescription = { helpDescription = it }
                        ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    LatestScheduledSummaryView(
                                        summary = lastSummary,
                                        activeSummaryModelName = activeSummaryModelName,
                                        onOpenWebView = onOpenWebView,
                                        onDelete = { viewModel.deleteSummary(lastSummary.id) },
                                        onToggleFavorite = { viewModel.toggleFavorite(lastSummary) }
                                    )
                                }
                            }
                        }
                    } else {
                        item {
                            val composition by rememberLottieComposition(
                                LottieCompositionSpec.RawRes(R.raw.empty_animation)
                            )
                            val progress by animateLottieCompositionAsState(
                                composition = composition,
                                iterations = LottieConstants.IterateForever
                            )
                            val dynamicProperties = rememberLottieDynamicProperties(
                                rememberLottieDynamicProperty(
                                    property = LottieProperty.COLOR_FILTER,
                                    value = SimpleColorFilter(
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.72f).toArgb()
                                    ),
                                    keyPath = arrayOf("**")
                                )
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AppCardSurface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surfaceContainer
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 18.dp, vertical = 20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        LottieAnimation(
                                            composition = composition,
                                            progress = { progress },
                                            dynamicProperties = dynamicProperties,
                                            modifier = Modifier.size(84.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.summary_empty_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }

                                Text(
                                    text = stringResource(R.string.summary_empty_actions_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                SummaryEmptyHintCard(
                                    icon = Icons.Default.FilterList,
                                    text = stringResource(R.string.summary_empty_hint_filters)
                                )

                                SummaryEmptyHintCard(
                                    icon = Icons.Default.Schedule,
                                    text = stringResource(R.string.summary_empty_hint_scheduled)
                                )
                            }
                        }
                    }
                }

                if (selectedTabIndex == 1) {
                    item {
                        AppHelpOverlayTarget(
                            isEnabled = isHelpMode,
                            description = summaryChartHelpDescription,
                            onShowDescription = { helpDescription = it }
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
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
            }
        }

        if (openedHistorySummary != null) {
            SummaryHistoryDialogView(
                summary = openedHistorySummary,
                activeSummaryModelName = activeSummaryModelName,
                onDismiss = { openedHistorySummaryId = null },
                onOpenWebView = onOpenWebView,
                onDelete = {
                    viewModel.deleteSummary(openedHistorySummary.id)
                    openedHistorySummaryId = null
                },
                onToggleFavorite = { viewModel.toggleFavorite(openedHistorySummary) }
            )
        }

        AppExplanationDialog(
            visible = helpDescription != null,
            description = helpDescription.orEmpty(),
            onDismiss = { helpDescription = null }
        )
    }
}

@Composable
private fun SummaryEmptyHintCard(
    icon: ImageVector,
    text: String
) {
    AppCardSurface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun filterHistorySummaries(
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

internal fun formatStatusTimeAndDate(timestamp: Long): Pair<String, String> {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
    val date = Date(timestamp)
    return Pair(timeFormat.format(date), dateFormat.format(date))
}

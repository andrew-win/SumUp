package com.andrewwin.sumup.ui.screen.summary

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.Summary
import com.andrewwin.sumup.ui.components.AppCardSurface
import com.andrewwin.sumup.ui.components.AppExplanationDialog
import com.andrewwin.sumup.ui.components.AppHelpOverlayTarget
import com.andrewwin.sumup.ui.components.AppHelpToggleAction
import com.andrewwin.sumup.ui.components.AppMessageState
import com.andrewwin.sumup.ui.components.AppProminentFab
import com.andrewwin.sumup.ui.components.AppSelectionActions
import com.andrewwin.sumup.ui.components.AppTopBar
import com.andrewwin.sumup.ui.theme.AppDimens
import com.andrewwin.sumup.ui.util.SummaryBlockUi
import com.andrewwin.sumup.ui.util.ThemeItem
import com.andrewwin.sumup.ui.util.cleanSummaryTextForSharing
import com.andrewwin.sumup.ui.util.normalizeSummaryUrlForWebView
import com.andrewwin.sumup.ui.util.parseSummaryBlocks
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
    onOpenWebView: (String) -> Unit = {},
    onOpenSummaryHistory: () -> Unit = {}
) {
    val context = LocalContext.current
    val summaryHistoryFabHelpDescription = stringResource(R.string.summary_help_history_fab)
    val summaryStatusHelpDescription = stringResource(R.string.summary_help_status)
    val summaryLatestHelpDescription = stringResource(R.string.summary_help_latest)
    val summaryChartHelpDescription = stringResource(R.string.summary_help_chart)
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

    val tabIcons = listOf(Icons.Default.Schedule, Icons.Default.BarChart)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(isSelectionMode) {
        if (isSelectionMode && isHelpMode) {
            isHelpMode = false
        }
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(320, easing = FastOutSlowInEasing)) +
                        expandVertically(animationSpec = tween(380, easing = FastOutSlowInEasing)),
                exit = fadeOut(animationSpec = tween(300, easing = FastOutSlowInEasing)) +
                        shrinkVertically(animationSpec = tween(360, easing = FastOutSlowInEasing))
            ) {
                AppTopBar(
                    title = {
                        if (isSelectionMode) {
                            Text(stringResource(R.string.summary_selected_count, selectedSummaryIds.size))
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
                                clearDescription = stringResource(R.string.summary_selection_clear),
                                deleteDescription = stringResource(R.string.summary_selection_delete)
                            )
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
                selectedTabIndex == 0 && !isSelectionMode -> {
                    AppHelpOverlayTarget(
                        isEnabled = isHelpMode,
                        description = summaryHistoryFabHelpDescription,
                        onShowDescription = { helpDescription = it }
                    ) {
                        AppProminentFab(
                            enabled = hasAnySummaries,
                            onClick = onOpenSummaryHistory
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
                        AppMessageState(
                            message = stringResource(R.string.summary_empty_title),
                            modifier = Modifier.fillParentMaxHeight(0.55f)
                        )
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

        AppExplanationDialog(
            visible = helpDescription != null,
            description = helpDescription.orEmpty(),
            onDismiss = { helpDescription = null }
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

internal fun formatStatusTimeAndDate(timestamp: Long): Pair<String, String> {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
    val date = Date(timestamp)
    return Pair(timeFormat.format(date), dateFormat.format(date))
}

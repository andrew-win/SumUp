package com.andrewwin.sumup.ui.screen.summary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AreaChart
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.InsertChartOutlined
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.PieChartOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SsidChart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WaterfallChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.andrewwin.sumup.R
import com.andrewwin.sumup.ui.components.AppExplanationDialog
import com.andrewwin.sumup.ui.components.AppHelpOverlayTarget
import com.andrewwin.sumup.ui.components.AppHelpToggleAction
import com.andrewwin.sumup.ui.components.AppMessageState
import com.andrewwin.sumup.ui.components.AppProminentFab
import com.andrewwin.sumup.ui.components.AppSelectionActions
import com.andrewwin.sumup.ui.components.AppTopBar
import com.andrewwin.sumup.ui.theme.AppDimens
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

    val tabIcons = listOf(Icons.Default.CalendarToday, Icons.Default.BarChart)
    val tabLabels = listOf(
        stringResource(R.string.summary_tab_scheduled),
        stringResource(R.string.summary_tab_statistics)
    )
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
                            modifier = Modifier.padding(bottom = AppDimens.ScreenBottomPadding),
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

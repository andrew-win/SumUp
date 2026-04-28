package com.andrewwin.sumup.ui.screen.summary

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.andrewwin.sumup.R
import com.andrewwin.sumup.ui.components.AppBackToTopFab
import com.andrewwin.sumup.ui.components.AppExplanationDialog
import com.andrewwin.sumup.ui.components.AppFilledIconAction
import com.andrewwin.sumup.ui.components.AppHelpToggleAction
import com.andrewwin.sumup.ui.components.AppSelectionActions
import com.andrewwin.sumup.ui.components.AppTopBar
import com.andrewwin.sumup.ui.util.PdfExporter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SummaryHistoryScreen(
    viewModel: SummaryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onOpenWebView: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val imeVisible = WindowInsets.isImeVisible
    val summaries by viewModel.summaries.collectAsState()
    val activeSummaryModelName by viewModel.activeSummaryModelName.collectAsState()
    val selectedSummaryIds = remember { mutableStateListOf<Long>() }
    val isSelectionMode = selectedSummaryIds.isNotEmpty()
    var isHelpMode by rememberSaveable { mutableStateOf(false) }
    var helpDescription by remember { mutableStateOf<String?>(null) }
    var openedHistorySummaryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var historySearchQuery by rememberSaveable { mutableStateOf("") }
    var isHistorySearchFocused by remember { mutableStateOf(false) }
    var historyDateFilter by rememberSaveable { mutableStateOf(HistoryDateFilter.HOUR_24) }
    var historySavedFilter by rememberSaveable { mutableStateOf(HistorySavedFilter.ALL) }
    val historyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val historySummaries = remember(
        summaries,
        historySearchQuery,
        historyDateFilter,
        historySavedFilter
    ) {
        filterHistorySummariesV2(
            summaries = summaries,
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
            historyListState.firstVisibleItemIndex > 0 || historyListState.firstVisibleItemScrollOffset > 100
        }
    }
    var wasImeVisible by remember { mutableStateOf(false) }

    val summaryHistoryFiltersHelpDescription = stringResource(R.string.summary_help_history_filters)
    val summaryHistoryCardHelpDescription = stringResource(R.string.summary_help_history_card)
    val closeHistoryDescription = stringResource(R.string.summary_history_close)
    val selectionClearDescription = stringResource(R.string.summary_selection_clear)
    val selectionDeleteDescription = stringResource(R.string.summary_selection_delete)

    BackHandler(enabled = isHistorySearchFocused) {
        focusManager.clearFocus(force = true)
        isHistorySearchFocused = false
    }

    LaunchedEffect(imeVisible, isHistorySearchFocused) {
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

    LaunchedEffect(isSelectionMode) {
        if (isSelectionMode && isHelpMode) {
            isHelpMode = false
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri == null || historySummaries.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val result = PdfExporter.exportSummariesToPdf(
                context = context,
                summaries = historySummaries,
                uri = uri
            )
            if (result.isFailure) {
                Toast.makeText(context, context.getString(R.string.export_pdf_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = !isHistorySearchFocused,
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
                            Text(stringResource(R.string.summary_history_title))
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
                                clearDescription = selectionClearDescription,
                                deleteDescription = selectionDeleteDescription
                            )
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AppHelpToggleAction(
                                    isHelpMode = isHelpMode,
                                    onToggle = { isHelpMode = !isHelpMode }
                                )
                                AppFilledIconAction(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = closeHistoryDescription,
                                    onClick = onNavigateBack
                                )
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (showHistoryBackToTop) {
                AppBackToTopFab(onClick = { scope.launch { historyListState.animateScrollToItem(0) } })
            }
        }
    ) { innerPadding ->
        SummaryHistoryListSection(
            summaries = historySummaries,
            selectedSummaryIds = selectedSummaryIds,
            isSelectionMode = isSelectionMode,
            activeSummaryModelName = activeSummaryModelName,
            listState = historyListState,
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
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
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

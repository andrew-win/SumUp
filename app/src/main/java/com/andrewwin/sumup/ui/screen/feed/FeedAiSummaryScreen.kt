package com.andrewwin.sumup.ui.screen.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext

@Composable
fun FeedAiSummaryScreen(
    viewModel: FeedAiSummaryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onOpenWebView: (String) -> Unit
) {
    val context = LocalContext.current
    val aiResult by viewModel.aiResult.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()
    val userPreferences by viewModel.userPreferences.collectAsState()
    val activeSummaryModelName by viewModel.activeSummaryModelName.collectAsState()
    val userQuestion by viewModel.userQuestion.collectAsState()
    val summaryTitle by viewModel.summaryTitle.collectAsState()

    FeedAiSummaryContent(
        context = context,
        isFeedAiActive = viewModel.isFeedMode,
        summaryTitle = summaryTitle,
        isFeedEmpty = false,
        emptyFeedMessage = "",
        aiResult = aiResult,
        isAiLoading = isAiLoading,
        aiStrategy = userPreferences.aiStrategy,
        activeSummaryModelName = activeSummaryModelName,
        userQuestion = userQuestion,
        onQuestionChange = viewModel::onQuestionChange,
        onClose = onNavigateBack,
        onAsk = viewModel::askQuestion,
        onRegenerate = viewModel::regenerate,
        onOpenWebView = onOpenWebView
    )
}

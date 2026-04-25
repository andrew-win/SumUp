package com.andrewwin.sumup.ui.screen.feed

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
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
import com.airbnb.lottie.model.KeyPath
import coil.compose.AsyncImage
import com.andrewwin.sumup.R
import com.andrewwin.sumup.ui.components.AppCardSurface
import com.andrewwin.sumup.ui.components.AppAnimatedDialog
import com.andrewwin.sumup.ui.components.AppBackToTopFab
import com.andrewwin.sumup.ui.components.AppExplanationDialog
import com.andrewwin.sumup.ui.components.AppHelpOverlayTarget
import com.andrewwin.sumup.ui.components.AppHelpToggleAction
import com.andrewwin.sumup.ui.components.AppMessageState
import com.andrewwin.sumup.ui.components.AppProminentFab
import com.andrewwin.sumup.ui.components.AppTopBar
import com.andrewwin.sumup.ui.screen.feed.model.ArticleClusterUiModel
import com.andrewwin.sumup.ui.screen.feed.model.ArticleUiModel
import com.andrewwin.sumup.ui.theme.AppDimens
import com.andrewwin.sumup.ui.util.PdfExporter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FeedScreen(
    viewModel: FeedViewModel = hiltViewModel(),
    onOpenWebView: (String) -> Unit
) {
    val articleClusters by viewModel.articleClusters.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedGroupId by viewModel.selectedGroupId.collectAsState()
    val dateFilter by viewModel.dateFilter.collectAsState()
    val savedFilter by viewModel.savedFilter.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val aiResult by viewModel.aiResult.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()
    val isDedupInProgress by viewModel.isDedupInProgress.collectAsState()
    val userPreferences by viewModel.userPreferences.collectAsState()
    val activeSummaryModelName by viewModel.activeSummaryModelName.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val imeVisible = WindowInsets.isImeVisible
    val feedAiHelpDescription = stringResource(R.string.feed_help_ai_fab)
    val feedFiltersHelpDescription = stringResource(R.string.feed_help_filters)
    val feedProcessingHelpDescription = stringResource(R.string.feed_help_processing)
    val feedEmptyHelpDescription = stringResource(R.string.feed_help_empty)
    val feedCardHelpDescription = stringResource(R.string.feed_help_article_card)

    var articleForAiId by rememberSaveable { mutableStateOf<Long?>(null) }
    var isFeedAiActive by rememberSaveable { mutableStateOf(false) }
    var userQuestion by rememberSaveable { mutableStateOf("") }
    var expandedImageUrl by remember { mutableStateOf<String?>(null) }
    var isHelpMode by rememberSaveable { mutableStateOf(false) }
    var helpDescription by remember { mutableStateOf<String?>(null) }
    var isSearchFocused by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val articles = articleClusters.map { it.representative }
        scope.launch {
            val result = PdfExporter.exportFeedToPdf(
                context = context,
                articles = articles,
                uri = uri,
                includeMedia = userPreferences.isFeedMediaEnabled
            )
            if (result.isFailure) {
                android.widget.Toast
                    .makeText(context, context.getString(R.string.export_pdf_error), android.widget.Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    val showBackToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 100
        }
    }
    val isFeedAiEnabled = articleClusters.isNotEmpty()
    var wasImeVisible by remember { mutableStateOf(false) }

    BackHandler(enabled = isSearchFocused) {
        focusManager.clearFocus(force = true)
        isSearchFocused = false
    }

    LaunchedEffect(imeVisible, isSearchFocused) {
        if (imeVisible) {
            wasImeVisible = true
        } else if (wasImeVisible && isSearchFocused) {
            focusManager.clearFocus(force = true)
            isSearchFocused = false
            wasImeVisible = false
        } else if (!isSearchFocused) {
            wasImeVisible = false
        }
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = !isSearchFocused,
                enter = fadeIn(animationSpec = tween(320, easing = FastOutSlowInEasing)) +
                    expandVertically(animationSpec = tween(380, easing = FastOutSlowInEasing)),
                exit = fadeOut(animationSpec = tween(300, easing = FastOutSlowInEasing)) +
                    shrinkVertically(animationSpec = tween(360, easing = FastOutSlowInEasing))
            ) {
                AppTopBar(
                    title = {
                        Text(text = stringResource(R.string.nav_feed))
                    },
                    actions = {
                        AppHelpToggleAction(
                            isHelpMode = isHelpMode,
                            onToggle = { isHelpMode = !isHelpMode }
                        )
                    }
                )
            }
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnimatedVisibility(
                    visible = showBackToTop,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    AppBackToTopFab(onClick = { scope.launch { listState.animateScrollToItem(0) } })
                }
                AppProminentFab(
                    enabled = isFeedAiEnabled,
                    onClick = {
                        if (isHelpMode) {
                            helpDescription = feedAiHelpDescription
                        } else {
                            isFeedAiActive = true
                            articleForAiId = null
                            viewModel.openCachedFeedSummary()
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_ask_ai),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 0.dp,
                    bottom = 16.dp,
                    start = AppDimens.ScreenHorizontalPadding,
                    end = AppDimens.ScreenHorizontalPadding
                ),
                verticalArrangement = Arrangement.spacedBy(AppDimens.ScreenSectionSpacing)
            ) {
                item {
                    AppHelpOverlayTarget(
                        isEnabled = isHelpMode,
                        description = feedFiltersHelpDescription,
                        onShowDescription = { helpDescription = it }
                    ) {
                        FeedFilters(
                            searchQuery = searchQuery,
                            onSearchQueryChange = viewModel::onSearchQueryChange,
                            onSearchFocusChanged = { focused ->
                                if (focused) isSearchFocused = true
                            },
                            dateFilter = dateFilter,
                            onDateFilterChange = viewModel::setDateFilter,
                            savedFilter = savedFilter,
                            onSavedFilterChange = viewModel::setSavedFilter,
                            selectedGroupId = selectedGroupId,
                            onGroupSelect = viewModel::selectGroup,
                            groups = groups,
                            onExportPdf = {
                                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                val name = context.getString(R.string.feed_pdf_file_name, date)
                                exportLauncher.launch(name)
                            },
                            isExportEnabled = articleClusters.isNotEmpty()
                        )
                    }
                }

                val showLoading = userPreferences.isDeduplicationEnabled &&
                        articleClusters.isEmpty() &&
                        isDedupInProgress

                if (showLoading) {
                    item {
                        AppHelpOverlayTarget(
                            isEnabled = isHelpMode,
                            description = feedProcessingHelpDescription,
                            onShowDescription = { helpDescription = it }
                        ) {
                            AppMessageState(
                                message = stringResource(R.string.feed_searching_similar),
                                modifier = Modifier.fillParentMaxHeight(0.7f)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(AppDimens.StateIconSize),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                } else if (articleClusters.isEmpty()) {
                    item {
                        AppHelpOverlayTarget(
                            isEnabled = isHelpMode,
                            description = feedEmptyHelpDescription,
                            onShowDescription = { helpDescription = it }
                        ) {
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
                                modifier = Modifier.fillMaxWidth(),
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
                                            text = stringResource(R.string.feed_empty_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }

                                Text(
                                    text = stringResource(R.string.feed_empty_actions_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                FeedEmptyHintCard(
                                    icon = Icons.Default.FilterList,
                                    text = stringResource(R.string.feed_empty_hint_filters)
                                )

                                FeedEmptyHintCard(
                                    icon = Icons.Default.FolderOpen,
                                    text = stringResource(R.string.feed_empty_hint_sources)
                                )
                            }
                        }
                    }
                } else {
                    items(articleClusters, key = { it.representative.article.id }) { cluster ->
                        AppHelpOverlayTarget(
                            isEnabled = isHelpMode,
                            description = feedCardHelpDescription,
                            onShowDescription = { helpDescription = it }
                        ) {
                            ArticleClusterCard(
                                cluster = cluster,
                                isMediaEnabled = userPreferences.isFeedMediaEnabled,
                                isDescriptionEnabled = userPreferences.isFeedDescriptionEnabled,
                                onMediaClick = { expandedImageUrl = it },
                                onOpenSource = { onOpenWebView(it.article.url) },
                                onAiClick = {
                                    articleForAiId = it.article.id
                                    isFeedAiActive = false
                                    viewModel.openCachedArticleSummary(it.article)
                                },
                                onClusterAiClick = {
                                    articleForAiId = cluster.representative.article.id
                                    isFeedAiActive = false
                                    userQuestion = ""
                                    viewModel.openCachedClusterSummary(cluster)
                                },
                                onToggleSaved = {
                                    val willBeAddedToSaved =
                                        !cluster.representative.article.isFavorite ||
                                            cluster.duplicates.any { duplicate -> !duplicate.first.article.isFavorite }
                                    viewModel.toggleSaved(cluster)
                                    if (willBeAddedToSaved) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.feed_added_to_saved),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                isDedupInProgress = isDedupInProgress,
                                minMentions = userPreferences.minMentions
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

        val articleForAi = remember(articleForAiId, articleClusters) {
            articleForAiId?.let { targetId ->
                articleClusters.asSequence()
                    .map { it.representative }
                    .plus(
                        articleClusters.asSequence()
                            .flatMap { cluster -> cluster.duplicates.asSequence().map { it.first } }
                    )
                    .firstOrNull { it.article.id == targetId }
            }
        }

        FeedAiDialog(
            isVisible = articleForAiId != null || isFeedAiActive,
            context = context,
            isFeedAiActive = isFeedAiActive,
            articleForAi = articleForAi,
            articleClusters = articleClusters,
            aiResult = aiResult,
            isAiLoading = isAiLoading,
            aiStrategy = userPreferences.aiStrategy,
            activeSummaryModelName = activeSummaryModelName,
            userQuestion = userQuestion,
            onQuestionChange = { userQuestion = it },
            onDismiss = {
                articleForAiId = null
                isFeedAiActive = false
                viewModel.clearAiResult()
                userQuestion = ""
            },
            onAsk = {
                if (isFeedAiActive) {
                    viewModel.askFeed(userQuestion)
                } else {
                    val cluster = articleClusters.firstOrNull { c ->
                        c.representative.article.id == articleForAiId
                    }
                    if (cluster != null) {
                        viewModel.askClusterQuestion(cluster, userQuestion)
                    } else {
                        articleForAi?.let { viewModel.askQuestion(it.article, userQuestion) }
                    }
                }
            },
            onRegenerate = {
                if (isFeedAiActive) {
                    viewModel.summarizeFeed(forceRefresh = true)
                } else {
                    val cluster = articleClusters.firstOrNull { c ->
                        c.representative.article.id == articleForAiId
                    }
                    if (cluster != null && cluster.duplicates.isNotEmpty()) {
                        viewModel.summarizeCluster(cluster, forceRefresh = true)
                    } else {
                        articleForAi?.let { viewModel.summarizeArticle(it.article, forceRefresh = true) }
                    }
                }
            },
            onOpenWebView = onOpenWebView
        )

        // Expanded image dialog
        AppAnimatedDialog(
            visible = expandedImageUrl != null,
            onDismissRequest = { expandedImageUrl = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            var scale by remember(expandedImageUrl) { mutableStateOf(1f) }
            var offset by remember(expandedImageUrl) { mutableStateOf(Offset.Zero) }
            var viewportSize by remember(expandedImageUrl) { mutableStateOf(Size.Zero) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(8.dp)
                    .clipToBounds()
                    .onSizeChanged { viewportSize = Size(it.width.toFloat(), it.height.toFloat()) }
            ) {
                AsyncImage(
                    model = expandedImageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(expandedImageUrl, scale, viewportSize) {
                            detectTapGestures(
                                onDoubleTap = { tapOffset ->
                                    if (viewportSize == Size.Zero) return@detectTapGestures

                                    if (scale > 1f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        val nextScale = 2f
                                        scale = nextScale
                                        offset = clampImageOffset(
                                            rawOffset = Offset(
                                                x = (viewportSize.width / 2f - tapOffset.x) * (nextScale - 1f),
                                                y = (viewportSize.height / 2f - tapOffset.y) * (nextScale - 1f)
                                            ),
                                            scale = nextScale,
                                            viewportSize = viewportSize
                                        )
                                    }
                                }
                            )
                        }
                        .pointerInput(expandedImageUrl) {
                            detectTransformGestures { _: Offset, pan: Offset, zoom: Float, _: Float ->
                                val nextScale = (scale * zoom).coerceIn(1f, 5f)
                                if (nextScale <= 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = nextScale
                                    offset = clampImageOffset(
                                        rawOffset = offset + pan,
                                        scale = nextScale,
                                        viewportSize = viewportSize
                                    )
                                }
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        ),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { expandedImageUrl = null },
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedEmptyHintCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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

private fun clampImageOffset(
    rawOffset: Offset,
    scale: Float,
    viewportSize: Size
): Offset {
    if (scale <= 1f || viewportSize == Size.Zero) return Offset.Zero

    val maxX = ((viewportSize.width * scale) - viewportSize.width) / 2f
    val maxY = ((viewportSize.height * scale) - viewportSize.height) / 2f

    return Offset(
        x = rawOffset.x.coerceIn(-maxX, maxX),
        y = rawOffset.y.coerceIn(-maxY, maxY)
    )
}









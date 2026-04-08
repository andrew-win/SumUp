package com.andrewwin.sumup.ui.screen.feed

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.animation.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.ui.screen.feed.model.ArticleClusterUiModel
import com.andrewwin.sumup.ui.screen.feed.model.ArticleUiModel
import com.andrewwin.sumup.ui.util.PdfExporter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
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

    var articleForAi by remember { mutableStateOf<ArticleUiModel?>(null) }
    var isFeedAiActive by remember { mutableStateOf(false) }
    var userQuestion by remember { mutableStateOf("") }
    var expandedImageUrl by remember { mutableStateOf<String?>(null) }
    var isHelpMode by rememberSaveable { mutableStateOf(false) }
    var helpDescription by remember { mutableStateOf<String?>(null) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.nav_feed)
                    )
                },
                actions = {
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
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
                    SmallFloatingActionButton(
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.back_to_top))
                    }
                }
                FloatingActionButton(
                    onClick = {
                        if (isHelpMode) {
                            helpDescription = "Кнопка AI-асистента стрічки (робот): відкриває узагальнення по всій поточній стрічці. " +
                                "Асистент враховує активні фільтри, тому відповідь формується саме по видимій вибірці новин. " +
                                "У вікні можна ставити уточнюючі запитання, перегенерувати зведення або перейти до джерел."
                        } else {
                            isFeedAiActive = true
                            articleForAi = null
                            viewModel.openCachedFeedSummary()
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.size(width = 75.dp, height = 65.dp)
                ) {
                    Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(48.dp))
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
                contentPadding = PaddingValues(top = 0.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    HelpOverlayTarget(
                        isEnabled = isHelpMode,
                        description = "Фільтри стрічки: тут задаються параметри того, що потрапляє у список нижче. " +
                            "Пошук працює по заголовках/контенту, фільтр дати обмежує період, а перемикач збережених показує лише обрані матеріали. " +
                            "Вибір групи дозволяє звузити стрічку до конкретного набору джерел. " +
                            "Кнопка PDF експортує поточну відфільтровану стрічку у файл, тобто у PDF потрапляють саме ті елементи, які зараз на екрані.",
                        onShowDescription = { helpDescription = it }
                    ) {
                        FeedFilters(
                            searchQuery = searchQuery,
                            onSearchQueryChange = viewModel::onSearchQueryChange,
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
                        HelpOverlayTarget(
                            isEnabled = isHelpMode,
                            description = "Стан обробки: застосунок зараз групує схожі новини та прибирає дублікати. " +
                                "Після завершення тут з'явиться фінальний список карток без повторів.",
                            onShowDescription = { helpDescription = it }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillParentMaxHeight(0.7f)
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = stringResource(R.string.feed_searching_similar),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 24.sp
                                    )
                                }
                            }
                        }
                    }
                } else if (articleClusters.isEmpty()) {
                    item {
                        HelpOverlayTarget(
                            isEnabled = isHelpMode,
                            description = "Порожня стрічка: немає елементів, що відповідають поточним фільтрам/підпискам. " +
                                "Спробуйте оновлення, зміну дати, групи або вимкнення фільтра збережених.",
                            onShowDescription = { helpDescription = it }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillParentMaxHeight(0.7f)
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.feed_empty_message),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 24.sp
                                )
                            }
                        }
                    }
                } else {
                    items(articleClusters, key = { it.representative.article.id }) { cluster ->
                        HelpOverlayTarget(
                            isEnabled = isHelpMode,
                            description = "Картка новини: основна сутність стрічки. " +
                                "З картки можна відкрити оригінальне джерело, запустити AI-аналіз для конкретної новини або кластера, " +
                                "додати у збережені, переглянути медіа та взаємодіяти з дублікатами (якщо вони знайдені).",
                            onShowDescription = { helpDescription = it }
                        ) {
                            ArticleClusterCard(
                                cluster = cluster,
                                isMediaEnabled = userPreferences.isFeedMediaEnabled,
                                isDescriptionEnabled = userPreferences.isFeedDescriptionEnabled,
                                onMediaClick = { expandedImageUrl = it },
                                onOpenSource = { onOpenWebView(it.article.url) },
                                onAiClick = {
                                    articleForAi = it
                                    isFeedAiActive = false
                                    viewModel.openCachedArticleSummary(it.article)
                                },
                                onClusterAiClick = {
                                    articleForAi = cluster.representative
                                    isFeedAiActive = false
                                    userQuestion = ""
                                    viewModel.openCachedClusterSummary(cluster)
                                },
                                onToggleSaved = {
                                    val willBeAddedToSaved = !it.article.isFavorite
                                    viewModel.toggleSaved(it.article)
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
        
        if (articleForAi != null || isFeedAiActive) {
            FeedAiDialog(
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
                    articleForAi = null
                    isFeedAiActive = false
                    viewModel.clearAiResult()
                    userQuestion = ""
                },
                onAsk = {
                    if (isFeedAiActive) viewModel.askFeed(userQuestion)
                    else articleForAi?.let { viewModel.askQuestion(it.article, userQuestion) }
                },
                onRegenerate = {
                    if (isFeedAiActive) {
                        viewModel.summarizeFeed(forceRefresh = true)
                    } else {
                        val cluster = articleClusters.firstOrNull { c ->
                            c.representative.article.id == articleForAi?.article?.id
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
        }

        // Expanded image dialog
        if (expandedImageUrl != null) {
            var scale by remember(expandedImageUrl) { mutableStateOf(1f) }
            var offsetX by remember(expandedImageUrl) { mutableStateOf(0f) }
            var offsetY by remember(expandedImageUrl) { mutableStateOf(0f) }

            Dialog(
                onDismissRequest = { expandedImageUrl = null },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    AsyncImage(
                        model = expandedImageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(expandedImageUrl) {
                                detectTransformGestures { _: Offset, pan: Offset, zoom: Float, _: Float ->
                                    val nextScale = (scale * zoom).coerceIn(1f, 5f)
                                    if (nextScale <= 1f) {
                                        scale = 1f
                                        offsetX = 0f
                                        offsetY = 0f
                                    } else {
                                        scale = nextScale
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    }
                                }
                            }
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            )
                            .padding(8.dp),
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
}

@Composable
private fun HelpOverlayTarget(
    isEnabled: Boolean,
    description: String,
    onShowDescription: (String) -> Unit,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        content()
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








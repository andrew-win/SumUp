package com.andrewwin.sumup.ui.screens.feed

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.animation.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.ui.screens.feed.model.ArticleClusterUiModel
import com.andrewwin.sumup.ui.screens.feed.model.ArticleUiModel
import com.andrewwin.sumup.ui.theme.Rubik
import com.andrewwin.sumup.ui.util.PdfExporter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private val ClusterDateFormat = SimpleDateFormat("HH:mm, dd MMMM", Locale("uk", "UA"))

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
    val groups by viewModel.groups.collectAsState()
    val context = LocalContext.current

    var articleForAi by remember { mutableStateOf<ArticleUiModel?>(null) }
    var isFeedAiActive by remember { mutableStateOf(false) }
    var userQuestion by remember { mutableStateOf("") }
    var expandedImageUrl by remember { mutableStateOf<String?>(null) }

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
                        text = stringResource(R.string.nav_feed),
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = Rubik)
                    )
                },
                actions = {
                    FilledIconButton(
                        onClick = {},
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null)
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
                    onClick = { isFeedAiActive = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.size(width = 60.dp, height = 52.dp)
                ) {
                    Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(38.dp))
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

                val showLoading = userPreferences.isDeduplicationEnabled &&
                        articleClusters.isEmpty() &&
                        isDedupInProgress

                if (showLoading) {
                    item {
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
                } else if (articleClusters.isEmpty()) {
                    item {
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
                } else {
                    items(articleClusters, key = { it.representative.article.id }) { cluster ->
                        ArticleClusterCard(
                            cluster = cluster,
                            isMediaEnabled = userPreferences.isFeedMediaEnabled,
                            isDescriptionEnabled = userPreferences.isFeedDescriptionEnabled,
                            onMediaClick = { expandedImageUrl = it },
                            onOpenSource = { onOpenWebView(it.article.url) },
                            onAiClick = {
                                articleForAi = it
                                isFeedAiActive = false
                                viewModel.clearAiResult()
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

        // AI Fullscreen dialog
        if (articleForAi != null || isFeedAiActive) {
            Dialog(
                onDismissRequest = {
                    articleForAi = null
                    isFeedAiActive = false
                    viewModel.clearAiResult()
                    userQuestion = ""
                },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .navigationBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isFeedAiActive) stringResource(R.string.ai_summarize_feed) else stringResource(R.string.ai_summarize_article),
                                style = MaterialTheme.typography.titleLarge
                            )
                            IconButton(
                                onClick = {
                                    articleForAi = null
                                    isFeedAiActive = false
                                    viewModel.clearAiResult()
                                    userQuestion = ""
                                }
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f, fill = true)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (isAiLoading) {
                                Box(
                                    Modifier.fillMaxWidth().height(150.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                                }
                            } else if (aiResult != null) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = aiResult ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        lineHeight = 28.sp
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                copyTextToClipboard(context, aiResult.orEmpty())
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.ai_result_copied),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.ContentCopy,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                shareText(
                                                    context = context,
                                                    text = aiResult.orEmpty(),
                                                    chooserTitle = context.getString(R.string.summary_share_chooser_title)
                                                )
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Share,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if ((aiResult != null || !isAiLoading) && userPreferences.aiStrategy != AiStrategy.LOCAL) {
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = userQuestion,
                                onValueChange = { userQuestion = it },
                                label = null,
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                trailingIcon = {
                                    IconButton(onClick = {
                                        if (isFeedAiActive) viewModel.askFeed(userQuestion)
                                        else articleForAi?.let { viewModel.askQuestion(it.article, userQuestion) }
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                                    }
                                }
                            )
                        }

                        if (aiResult != null || !isAiLoading) {
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    if (isFeedAiActive) viewModel.summarizeFeed()
                                    else articleForAi?.let { viewModel.summarizeArticle(it.article) }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                contentPadding = PaddingValues(16.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = if (isFeedAiActive) stringResource(R.string.ai_summarize_feed)
                                    else stringResource(R.string.ai_summarize_article),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            } 
        }

        // Expanded image dialog
        if (expandedImageUrl != null) {
            Dialog(onDismissRequest = { expandedImageUrl = null }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    AsyncImage(
                        model = expandedImageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                    IconButton(
                        onClick = { expandedImageUrl = null },
                        modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }
            }
        }
    }
}

private fun copyTextToClipboard(context: Context, text: String) {
    if (text.isBlank()) return
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboardManager.setPrimaryClip(ClipData.newPlainText("ai_result_text", text))
}

private fun shareText(
    context: Context,
    text: String,
    chooserTitle: String
) {
    if (text.isBlank()) return
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(shareIntent, chooserTitle))
}

// ─────────────────────────────────────────────────────────────────────────────
// Feed Filters
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedFilters(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    dateFilter: DateFilter,
    onDateFilterChange: (DateFilter) -> Unit,
    savedFilter: SavedFilter,
    onSavedFilterChange: (SavedFilter) -> Unit,
    selectedGroupId: Long?,
    onGroupSelect: (Long?) -> Unit,
    groups: List<com.andrewwin.sumup.data.local.entities.SourceGroup>,
    onExportPdf: () -> Unit,
    isExportEnabled: Boolean
) {
    var showDateMenu by remember { mutableStateOf(false) }
    var showSavedMenu by remember { mutableStateOf(false) }
    var showGroupMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = CircleShape,
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )
            IconButton(
                onClick = onExportPdf,
                enabled = isExportEnabled,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.PictureAsPdf,
                    contentDescription = stringResource(R.string.export_feed_pdf),
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.filter_label),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box {
                    Row(
                        modifier = Modifier.clickable { showDateMenu = true },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Today,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(stringResource(dateFilter.labelRes), color = MaterialTheme.colorScheme.onSurface)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = showDateMenu, onDismissRequest = { showDateMenu = false }) {
                        DateFilter.entries.forEach { filter ->
                            DropdownMenuItem(
                                text = { Text(stringResource(filter.labelRes)) },
                                onClick = { onDateFilterChange(filter); showDateMenu = false }
                            )
                        }
                    }
                }
                Box {
                    Row(
                        modifier = Modifier.clickable { showSavedMenu = true },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bookmark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(stringResource(savedFilter.labelRes), color = MaterialTheme.colorScheme.onSurface)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = showSavedMenu, onDismissRequest = { showSavedMenu = false }) {
                        SavedFilter.entries.forEach { filter ->
                            DropdownMenuItem(
                                text = { Text(stringResource(filter.labelRes)) },
                                onClick = { onSavedFilterChange(filter); showSavedMenu = false }
                            )
                        }
                    }
                }
                Box {
                    val groupName = remember(selectedGroupId, groups) {
                        groups.find { it.id == selectedGroupId }?.name
                    } ?: stringResource(R.string.filter_group)
                    Row(
                        modifier = Modifier.clickable { showGroupMenu = true },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(groupName, color = MaterialTheme.colorScheme.onSurface)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = showGroupMenu, onDismissRequest = { showGroupMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.all_groups)) },
                            onClick = { onGroupSelect(null); showGroupMenu = false }
                        )
                        groups.forEach { group ->
                            DropdownMenuItem(
                                text = { Text(group.name) },
                                onClick = { onGroupSelect(group.id); showGroupMenu = false }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Cluster card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ArticleClusterCard(
    cluster: ArticleClusterUiModel,
    isMediaEnabled: Boolean,
    isDescriptionEnabled: Boolean,
    onMediaClick: (String) -> Unit,
    onOpenSource: (ArticleUiModel) -> Unit,
    onAiClick: (ArticleUiModel) -> Unit,
    onToggleSaved: (ArticleUiModel) -> Unit,
    isDedupInProgress: Boolean,
    minMentions: Int
) {
    val publishedAt = cluster.representative.article.publishedAt

    Column {
        Text(
            text = ClusterDateFormat.format(Date(publishedAt)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenSource(cluster.representative) },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column {
                ArticleItem(
                    uiModel = cluster.representative,
                    isMediaEnabled = isMediaEnabled,
                    isDescriptionEnabled = isDescriptionEnabled,
                    onMediaClick = onMediaClick,
                    onOpenSource = { onOpenSource(cluster.representative) },
                    onAiClick = { onAiClick(cluster.representative) },
                    onToggleSaved = { onToggleSaved(cluster.representative) }
                )

                if (cluster.duplicates.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp)) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.feed_similar_news, cluster.duplicates.size),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        cluster.duplicates.forEach { (uiModel, score) ->
                            DuplicateItem(
                                uiModel = uiModel,
                                score = score,
                                onOpenSource = { onOpenSource(uiModel) },
                                onAiClick = { onAiClick(uiModel) }
                            )
                        }

                        if (isDedupInProgress) {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.feed_searching_similar),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Main article item (representative)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ArticleItem(
    uiModel: ArticleUiModel,
    isMediaEnabled: Boolean,
    isDescriptionEnabled: Boolean,
    onMediaClick: (String) -> Unit,
    onOpenSource: () -> Unit,
    onAiClick: () -> Unit,
    onToggleSaved: () -> Unit
) {
    val context = LocalContext.current
    val mediaUrl = uiModel.article.mediaUrl
    val shouldShowMedia = remember(isMediaEnabled, mediaUrl, uiModel.sourceType) {
        isMediaEnabled &&
                !mediaUrl.isNullOrBlank() &&
                (uiModel.sourceType == SourceType.RSS ||
                        uiModel.sourceType == SourceType.TELEGRAM ||
                        uiModel.sourceType == SourceType.YOUTUBE ||
                        uiModel.sourceType == SourceType.WEBSITE)
    }

    Column(modifier = Modifier.padding(16.dp)) {

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (shouldShowMedia && mediaUrl != null) {
                Box(
                    modifier = Modifier
                        .size(width = 72.dp, height = 56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onMediaClick(mediaUrl) }
                ) {
                    AsyncImage(
                        model = mediaUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                // Group + source chips
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    uiModel.groupName?.let { SourceChip(label = it) }
                    uiModel.sourceName?.let { SourceChip(label = it) }
                }
                // Increased gap between chips row and title
                Spacer(Modifier.height(10.dp))
                Text(
                    text = uiModel.displayTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = Rubik,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 20.sp
                    ),
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        val hasDescription = isDescriptionEnabled && uiModel.displayContent.isNotBlank()
        if (hasDescription) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = uiModel.displayContent,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(16.dp))
        } else {
            Spacer(Modifier.height(10.dp))
        }

        // Three equal-width action buttons — same color
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionChip(
                onClick = onAiClick,
                icon = { Icon(painterResource(R.drawable.ic_ask_ai), contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ActionChip(
                onClick = onToggleSaved,
                icon = {
                    val bookmarkIcon = if (uiModel.article.isFavorite) {
                        Icons.Default.Bookmark
                    } else {
                        Icons.Outlined.BookmarkBorder
                    }
                    Icon(bookmarkIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ActionChip(
                onClick = {
                    shareArticleLink(
                        context = context,
                        articleUrl = uiModel.article.url
                    )
                },
                icon = { Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun shareArticleLink(
    context: Context,
    articleUrl: String
) {
    if (articleUrl.isBlank()) return
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, articleUrl)
    }
    val chooser = Intent.createChooser(
        shareIntent,
        context.getString(R.string.feed_share_chooser_title)
    )
    context.startActivity(chooser)
}

// ─────────────────────────────────────────────────────────────────────────────
// Duplicate / similar item
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DuplicateItem(
    uiModel: ArticleUiModel,
    score: Float,
    onOpenSource: () -> Unit,
    onAiClick: () -> Unit
) {
    val scoreInt = (score * 100).toInt()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onOpenSource() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Score pill — uniform colour for all values
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.width(44.dp).height(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.feed_similarity_score, scoreInt),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Title + source name only (no group chip)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = uiModel.displayTitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = Rubik,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 17.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            uiModel.sourceName?.let { src ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 3.dp)
                ) {
                    Text(
                        text = src,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Small reusable composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SourceChip(label: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ActionChip(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                icon()
                if (label != null) {
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = contentColor
                    )
                }
            }
        }
    }
}

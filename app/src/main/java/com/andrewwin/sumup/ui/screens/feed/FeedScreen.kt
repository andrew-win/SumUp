package com.andrewwin.sumup.ui.screens.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.animation.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
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
                title = { Text(stringResource(R.string.nav_feed)) },
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
                        onClick = {
                            scope.launch {
                                listState.animateScrollToItem(0)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = androidx.compose.foundation.shape.CircleShape
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
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
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
                            onMediaClick = { expandedImageUrl = it },
                            onOpenSource = { onOpenWebView(it.article.url) },
                            onAiClick = { 
                                articleForAi = it
                                isFeedAiActive = false
                                viewModel.clearAiResult()
                            },
                            isDedupInProgress = isDedupInProgress,
                            minMentions = userPreferences.minMentions
                        )
                    }
                }
            }
        }

        if (articleForAi != null || isFeedAiActive) {
            ModalBottomSheet(
                onDismissRequest = { 
                    articleForAi = null
                    isFeedAiActive = false
                    viewModel.clearAiResult()
                    userQuestion = ""
                },
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (isAiLoading) {
                            Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                            }
                        } else if (aiResult != null) {
                            Text(
                                text = aiResult ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 28.sp
                            )
                        }
                    }

                    if ((aiResult != null || !isAiLoading) && userPreferences.aiStrategy != AiStrategy.EXTRACTIVE) {
                        Spacer(Modifier.height(24.dp))
                        OutlinedTextField(
                            value = userQuestion,
                            onValueChange = { userQuestion = it },
                            label = { Text(stringResource(R.string.ai_ask_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            trailingIcon = {
                                IconButton(onClick = { 
                                    if (isFeedAiActive) {
                                        viewModel.askFeed(userQuestion)
                                    } else {
                                        articleForAi?.let { viewModel.askQuestion(it.article.content, userQuestion) }
                                    }
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                                }
                            }
                        )
                    }
                    
                    if (aiResult != null || !isAiLoading) {
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { 
                                if (isFeedAiActive) {
                                    viewModel.summarizeFeed()
                                } else {
                                    articleForAi?.let { viewModel.summarizeArticle(it.article) }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (isFeedAiActive) stringResource(R.string.ai_summarize_feed) 
                                else stringResource(R.string.ai_summarize_article),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                    Spacer(Modifier.height(48.dp))
                }
            }
        }

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
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                    IconButton(
                        onClick = { expandedImageUrl = null },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedFilters(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    dateFilter: DateFilter,
    onDateFilterChange: (DateFilter) -> Unit,
    selectedGroupId: Long?,
    onGroupSelect: (Long?) -> Unit,
    groups: List<com.andrewwin.sumup.data.local.entities.SourceGroup>,
    onExportPdf: () -> Unit,
    isExportEnabled: Boolean
) {
    var showDateMenu by remember { mutableStateOf(false) }
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
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.filter_label),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.weight(1f))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box {
                    Row(
                        modifier = Modifier.clickable { showDateMenu = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(dateFilter.labelRes), color = MaterialTheme.colorScheme.onSurface)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(
                        expanded = showDateMenu,
                        onDismissRequest = { showDateMenu = false }
                    ) {
                        DateFilter.entries.forEach { filter ->
                            DropdownMenuItem(
                                text = { Text(stringResource(filter.labelRes)) },
                                onClick = {
                                    onDateFilterChange(filter)
                                    showDateMenu = false
                                }
                            )
                        }
                    }
                }

                Box {
                    Row(
                        modifier = Modifier.clickable { showGroupMenu = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(groups.find { it.id == selectedGroupId }?.name ?: stringResource(R.string.filter_group), color = MaterialTheme.colorScheme.onSurface)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(
                        expanded = showGroupMenu,
                        onDismissRequest = { showGroupMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.all_groups)) },
                            onClick = {
                                onGroupSelect(null)
                                showGroupMenu = false
                            }
                        )
                        groups.forEach { group ->
                            DropdownMenuItem(
                                text = { Text(group.name) },
                                onClick = {
                                    onGroupSelect(group.id)
                                    showGroupMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArticleClusterCard(
    cluster: ArticleClusterUiModel,
    isMediaEnabled: Boolean,
    onMediaClick: (String) -> Unit,
    onOpenSource: (ArticleUiModel) -> Unit,
    onAiClick: (ArticleUiModel) -> Unit,
    isDedupInProgress: Boolean,
    minMentions: Int
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm, dd MMMM", Locale("uk", "UA")) }
    val publishedAt = cluster.representative.article.publishedAt
    val url = cluster.representative.article.url

    Column {
        Text(
            text = dateFormat.format(Date(publishedAt)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column {
                ArticleItem(
                    uiModel = cluster.representative,
                    isMediaEnabled = isMediaEnabled,
                    onMediaClick = onMediaClick,
                    onOpenSource = { onOpenSource(cluster.representative) },
                    onAiClick = { onAiClick(cluster.representative) }
                )

                if (cluster.duplicates.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                    
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = stringResource(R.string.feed_similar_news, cluster.duplicates.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        
                        cluster.duplicates.forEach { (uiModel, score) ->
                            DuplicateItem(
                                uiModel = uiModel,
                                score = score,
                                onOpenSource = { onOpenSource(uiModel) },
                                onAiClick = { onAiClick(uiModel) }
                            )
                            if (uiModel != cluster.duplicates.last().first) {
                                Spacer(Modifier.height(12.dp))
                            }
                        }

                        if (isDedupInProgress) {
                            Spacer(Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.feed_searching_similar),
                                    style = MaterialTheme.typography.bodySmall,
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

@Composable
fun ArticleItem(
    uiModel: ArticleUiModel,
    isMediaEnabled: Boolean,
    onMediaClick: (String) -> Unit,
    onOpenSource: () -> Unit,
    onAiClick: () -> Unit
) {
    val context = LocalContext.current
    val mediaUrl = uiModel.article.mediaUrl
    val shouldShowMedia = isMediaEnabled &&
        !mediaUrl.isNullOrBlank() &&
        (uiModel.sourceType == SourceType.RSS || uiModel.sourceType == SourceType.TELEGRAM || uiModel.sourceType == SourceType.YOUTUBE)
    Column(modifier = Modifier.padding(24.dp)) {
        if (shouldShowMedia) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { mediaUrl?.let(onMediaClick) }
            ) {
                AsyncImage(
                    model = mediaUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(Modifier.height(16.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            uiModel.groupName?.let { name ->
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = androidx.compose.foundation.shape.CircleShape) {
                    Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            uiModel.sourceName?.let { name ->
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = androidx.compose.foundation.shape.CircleShape) {
                    Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }
        Text(
            text = uiModel.displayTitle,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold
            ),
            lineHeight = 22.sp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = uiModel.displayContent,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Normal
        )
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledIconButton(
                    onClick = onOpenSource,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .width(56.dp)
                        .height(34.dp),
                    shape = RoundedCornerShape(50.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.MoreHoriz, contentDescription = null)
                }
                FilledIconButton(
                    onClick = onAiClick,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .width(56.dp)
                        .height(34.dp),
                    shape = RoundedCornerShape(50.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(painterResource(R.drawable.ic_ask_ai), contentDescription = null)
                }
            }
        }
    }
}

@Composable
fun DuplicateItem(
    uiModel: ArticleUiModel,
    score: Float,
    onOpenSource: () -> Unit,
    onAiClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = androidx.compose.foundation.shape.CircleShape,
            modifier = Modifier.width(44.dp).height(30.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.feed_similarity_score, (score * 100).toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = uiModel.displayTitle,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = Rubik,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(
                onClick = onOpenSource,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.MoreHoriz,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onAiClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    painterResource(R.drawable.ic_ask_ai),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

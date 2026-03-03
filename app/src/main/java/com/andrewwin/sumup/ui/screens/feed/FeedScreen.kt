package com.andrewwin.sumup.ui.screens.feed

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.domain.ArticleCluster
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: FeedViewModel = viewModel()
) {
    val articleClusters by viewModel.articleClusters.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val selectedGroupId by viewModel.selectedGroupId.collectAsState()
    val dateFilter by viewModel.dateFilter.collectAsState()
    val aiResult by viewModel.aiResult.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()

    var showGroupMenu by remember { mutableStateOf(false) }
    var showDateMenu by remember { mutableStateOf(false) }
    var articleForAi by remember { mutableStateOf<Article?>(null) }
    var isFeedAiActive by remember { mutableStateOf(false) }
    var userQuestion by remember { mutableStateOf("") }
    
    val uriHandler = LocalUriHandler.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    val showBackToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    TopAppBar(
                        title = { Text(stringResource(R.string.nav_feed)) },
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent
                        )
                    )
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        SearchBar(
                            inputField = {
                                SearchBarDefaults.InputField(
                                    query = searchQuery,
                                    onQueryChange = viewModel::onSearchQueryChange,
                                    onSearch = {},
                                    expanded = false,
                                    onExpandedChange = {},
                                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    trailingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) }
                                )
                            },
                            expanded = false,
                            onExpandedChange = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {}

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(stringResource(R.string.filter_label), style = MaterialTheme.typography.bodyMedium)
                            
                            Box {
                                FilterChip(
                                    selected = dateFilter != DateFilter.ALL,
                                    onClick = { showDateMenu = true },
                                    label = { Text(dateFilter.label) },
                                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                                )
                                DropdownMenu(
                                    expanded = showDateMenu,
                                    onDismissRequest = { showDateMenu = false }
                                ) {
                                    DateFilter.entries.forEach { filter ->
                                        DropdownMenuItem(
                                            text = { Text(filter.label) },
                                            onClick = {
                                                viewModel.setDateFilter(filter)
                                                showDateMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            Box {
                                FilterChip(
                                    selected = selectedGroupId != null,
                                    onClick = { showGroupMenu = true },
                                    label = { 
                                        Text(groups.find { it.id == selectedGroupId }?.name ?: stringResource(R.string.filter_group)) 
                                    },
                                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                                )
                                DropdownMenu(
                                    expanded = showGroupMenu,
                                    onDismissRequest = { showGroupMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.all_groups)) },
                                        onClick = {
                                            viewModel.selectGroup(null)
                                            showGroupMenu = false
                                        }
                                    )
                                    groups.forEach { group ->
                                        DropdownMenuItem(
                                            text = { Text(group.name) },
                                            onClick = {
                                                viewModel.selectGroup(group.id)
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
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                AnimatedVisibility(
                    visible = showBackToTop,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            coroutineScope.launch { listState.animateScrollToItem(0) }
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.back_to_top))
                    }
                }
                
                FloatingActionButton(
                    onClick = { isFeedAiActive = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(Icons.Default.SmartToy, contentDescription = stringResource(R.string.ai_feed_title))
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
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(articleClusters, key = { it.representative.id }) { cluster ->
                    ArticleClusterCard(
                        cluster = cluster,
                        onOpenSource = { uriHandler.openUri(it.url) },
                        onAiClick = { articleForAi = it }
                    )
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
                }
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Text(
                        text = if (isFeedAiActive) stringResource(R.string.ai_feed_title) else (articleForAi?.title ?: ""),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(16.dp))
                    
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (isAiLoading) {
                            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (aiResult != null) {
                            Text(
                                text = aiResult ?: "",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    if (aiResult != null || !isAiLoading) {
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = userQuestion,
                            onValueChange = { userQuestion = it },
                            label = { Text(stringResource(R.string.ai_ask_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { 
                                    if (isFeedAiActive) {
                                        viewModel.askFeed(userQuestion)
                                    } else {
                                        articleForAi?.let { viewModel.askQuestion(it.content, userQuestion) }
                                    }
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                                }
                            }
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { 
                                if (isFeedAiActive) {
                                    viewModel.summarizeFeed()
                                } else {
                                    articleForAi?.let { viewModel.summarizeContent(it.content) }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (isFeedAiActive) stringResource(R.string.ai_summarize_feed) else stringResource(R.string.ai_summarize_article))
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun ArticleClusterCard(
    cluster: ArticleCluster,
    onOpenSource: (Article) -> Unit,
    onAiClick: (Article) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column {
            ArticleItem(
                article = cluster.representative,
                onOpenSource = { onOpenSource(cluster.representative) },
                onAiClick = { onAiClick(cluster.representative) }
            )

            if (cluster.duplicates.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = stringResource(R.string.feed_similar_news, cluster.duplicates.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    cluster.duplicates.forEach { (article, score) ->
                        DuplicateItem(
                            article = article,
                            score = score,
                            onOpenSource = { onOpenSource(article) },
                            onAiClick = { onAiClick(article) }
                        )
                        if (article != cluster.duplicates.last().first) {
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArticleItem(
    article: Article,
    onOpenSource: () -> Unit,
    onAiClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm, dd MMMM", Locale("uk", "UA")) }
    
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = dateFormat.format(Date(article.publishedAt)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = article.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = article.content,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onOpenSource, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreHoriz, contentDescription = null)
                }
                IconButton(onClick = onAiClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                }
            }
            Text(
                text = stringResource(R.string.direct_text),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DuplicateItem(
    article: Article,
    score: Float,
    onOpenSource: () -> Unit,
    onAiClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.extraSmall
        ) {
            Text(
                text = stringResource(R.string.feed_similarity_score, (score * 100).toInt()),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                fontSize = 10.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = article.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onOpenSource, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.MoreHoriz, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            IconButton(onClick = onAiClick, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
            }
        }
    }
}

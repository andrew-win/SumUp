package com.andrewwin.sumup.ui.screens.feed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.SmartToy
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.domain.ArticleCluster
import com.andrewwin.sumup.ui.theme.Rubik
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: FeedViewModel = hiltViewModel()
) {
    val articleClusters by viewModel.articleClusters.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedGroupId by viewModel.selectedGroupId.collectAsState()
    val dateFilter by viewModel.dateFilter.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val aiResult by viewModel.aiResult.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()
    val userPreferences by viewModel.userPreferences.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val sources by viewModel.sources.collectAsState()

    var showDateMenu by remember { mutableStateOf(false) }
    var showGroupMenu by remember { mutableStateOf(false) }
    var articleForAi by remember { mutableStateOf<Article?>(null) }
    var isFeedAiActive by remember { mutableStateOf(false) }
    var userQuestion by remember { mutableStateOf("") }

    val listState = rememberLazyListState()
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_feed), fontWeight = FontWeight.SemiBold) },
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
            FloatingActionButton(
                onClick = { isFeedAiActive = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = androidx.compose.foundation.shape.CircleShape,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(28.dp))
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
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
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
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                                )
                            },
                            expanded = false,
                            onExpandedChange = {},
                            modifier = Modifier.fillMaxWidth(),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            colors = SearchBarDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {}

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
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
                                                    viewModel.setDateFilter(filter)
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

                items(articleClusters, key = { it.representative.id }) { cluster ->
                    ArticleClusterCard(
                        cluster = cluster,
                        sources = sources,
                        groups = groups,
                        onOpenSource = { uriHandler.openUri(it.url) },
                        onAiClick = { 
                            articleForAi = it
                            isFeedAiActive = false
                            viewModel.clearAiResult()
                        }
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
                                        articleForAi?.let { viewModel.askQuestion(it.content, userQuestion) }
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
                                    articleForAi?.let { viewModel.summarizeContent(it.content) }
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
    }
}

@Composable
fun ArticleClusterCard(
    cluster: ArticleCluster,
    sources: List<com.andrewwin.sumup.data.local.entities.Source>,
    groups: List<com.andrewwin.sumup.data.local.entities.SourceGroup>,
    onOpenSource: (Article) -> Unit,
    onAiClick: (Article) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm, dd MMMM", Locale("uk", "UA")) }

    Column {
        Text(
            text = dateFormat.format(Date(cluster.representative.publishedAt)),
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
            val source = sources.find { it.id == cluster.representative.sourceId }
            val group = groups.find { it.id == source?.groupId }
            
            ArticleItem(
                article = cluster.representative,
                sourceName = source?.name,
                groupName = group?.name,
                onOpenSource = { onOpenSource(cluster.representative) },
                onAiClick = { onAiClick(cluster.representative) }
            )

            if (cluster.duplicates.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
                
                Column(modifier = Modifier.padding(24.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = stringResource(R.string.feed_similar_news, cluster.duplicates.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    
                    cluster.duplicates.forEach { (article, score) ->
                        DuplicateItem(
                            article = article,
                            score = score,
                            onOpenSource = { onOpenSource(article) },
                            onAiClick = { onAiClick(article) }
                        )
                        if (article != cluster.duplicates.last().first) {
                            Spacer(Modifier.height(12.dp))
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
    article: Article,
    sourceName: String?,
    groupName: String?,
    onOpenSource: () -> Unit,
    onAiClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm, dd MMMM", Locale("uk", "UA")) }
    
    Column(modifier = Modifier.padding(24.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            if (groupName != null) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = androidx.compose.foundation.shape.CircleShape) {
                    Text(groupName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            if (sourceName != null) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = androidx.compose.foundation.shape.CircleShape) {
                    Text(sourceName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }
        Text(
            text = article.title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold
            ),
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 22.sp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = article.content,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                FilledTonalIconButton(
                    onClick = onOpenSource,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .width(50.dp)
                        .height(30.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.MoreHoriz, contentDescription = null)
                }
                FilledTonalIconButton(
                    onClick = onAiClick,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .width(50.dp)
                        .height(30.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(painterResource(R.drawable.ic_ask_ai), contentDescription = null)
                }
            }
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = androidx.compose.foundation.shape.CircleShape,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "${(score * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = article.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = Rubik,
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onOpenSource, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.MoreHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onAiClick, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

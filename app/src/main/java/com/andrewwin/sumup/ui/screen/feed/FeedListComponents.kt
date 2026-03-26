package com.andrewwin.sumup.ui.screen.feed

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
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
import coil.compose.AsyncImage
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.SourceGroup
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.ui.screen.feed.model.ArticleClusterUiModel
import com.andrewwin.sumup.ui.screen.feed.model.ArticleUiModel
import com.andrewwin.sumup.ui.theme.Rubik
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ClusterDateFormatThreadLocal = ThreadLocal.withInitial {
    SimpleDateFormat("HH:mm, dd MMMM", Locale("uk", "UA"))
}

private fun formatClusterDate(timestamp: Long): String {
    return ClusterDateFormatThreadLocal.get().format(Date(timestamp))
}

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
    groups: List<SourceGroup>,
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

@Composable
fun ArticleClusterCard(
    cluster: ArticleClusterUiModel,
    isMediaEnabled: Boolean,
    isDescriptionEnabled: Boolean,
    onMediaClick: (String) -> Unit,
    onOpenSource: (ArticleUiModel) -> Unit,
    onAiClick: (ArticleUiModel) -> Unit,
    onClusterAiClick: () -> Unit,
    onToggleSaved: (ArticleUiModel) -> Unit,
    isDedupInProgress: Boolean,
    minMentions: Int
) {
    val publishedAt = cluster.representative.article.publishedAt

    Column {
        Text(
            text = formatClusterDate(publishedAt),
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
                    onAiClick = {
                        if (cluster.duplicates.isNotEmpty()) onClusterAiClick() else onAiClick(cluster.representative)
                    },
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    uiModel.groupName?.let { SourceChip(label = it) }
                    uiModel.sourceName?.let { SourceChip(label = it) }
                }
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
                onClick = { shareArticleLink(context = context, articleUrl = uiModel.article.url) },
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
    enabled: Boolean = true,
    label: String? = null,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
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








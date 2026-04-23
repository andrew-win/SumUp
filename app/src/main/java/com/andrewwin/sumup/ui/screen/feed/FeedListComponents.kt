package com.andrewwin.sumup.ui.screen.feed

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.andrewwin.sumup.ui.displayName
import com.andrewwin.sumup.ui.components.AppExportPdfButton
import com.andrewwin.sumup.ui.components.AppFilterMenuChip
import com.andrewwin.sumup.ui.components.AppSearchField
import com.andrewwin.sumup.ui.screen.feed.model.ArticleClusterUiModel
import com.andrewwin.sumup.ui.screen.feed.model.ArticleUiModel
import com.andrewwin.sumup.ui.theme.AppDimens
import com.andrewwin.sumup.ui.theme.AppCardShape
import com.andrewwin.sumup.ui.theme.appCardBorder
import com.andrewwin.sumup.ui.theme.appCardColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatClusterDate(timestamp: Long): String {
    return SimpleDateFormat("HH:mm, dd MMMM", Locale.getDefault()).format(Date(timestamp))
}

private fun formatSavedDate(timestamp: Long): String {
    return SimpleDateFormat("HH:mm, dd MMMM", Locale.getDefault()).format(Date(timestamp))
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
            .padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppSearchField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = stringResource(R.string.search_placeholder),
                leadingIcon = Icons.Default.Search,
                modifier = Modifier.weight(1f)
            )
            AppExportPdfButton(
                onClick = onExportPdf,
                enabled = isExportEnabled,
                contentDescription = stringResource(R.string.export_feed_pdf)
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val dateLabel = stringResource(dateFilter.labelRes)
            val savedLabel = stringResource(savedFilter.labelRes)
            FilterMenuChip(
                icon = Icons.Filled.Today,
                label = dateLabel,
                onClick = { showDateMenu = true },
                modifier = Modifier
            )
            FilterMenuChip(
                icon = Icons.Filled.Bookmark,
                label = savedLabel,
                onClick = { showSavedMenu = true },
                modifier = Modifier
            )
            val groupName = groups.find { it.id == selectedGroupId }?.displayName()
                ?: stringResource(R.string.all_groups)
            FilterMenuChip(
                icon = Icons.Filled.Folder,
                label = groupName,
                onClick = { showGroupMenu = true },
                modifier = Modifier
            )

            DropdownMenu(expanded = showDateMenu, onDismissRequest = { showDateMenu = false }) {
                DateFilter.entries.forEach { filter ->
                    DropdownMenuItem(
                        text = { Text(stringResource(filter.labelRes)) },
                        onClick = { onDateFilterChange(filter); showDateMenu = false }
                    )
                }
            }
            DropdownMenu(expanded = showSavedMenu, onDismissRequest = { showSavedMenu = false }) {
                SavedFilter.entries.forEach { filter ->
                    DropdownMenuItem(
                        text = { Text(stringResource(filter.labelRes)) },
                        onClick = { onSavedFilterChange(filter); showSavedMenu = false }
                    )
                }
            }
            DropdownMenu(expanded = showGroupMenu, onDismissRequest = { showGroupMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.all_groups)) },
                    onClick = { onGroupSelect(null); showGroupMenu = false }
                )
                groups.forEach { group ->
                    DropdownMenuItem(
                        text = { Text(group.displayName()) },
                        onClick = { onGroupSelect(group.id); showGroupMenu = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterMenuChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppFilterMenuChip(
        icon = icon,
        label = label,
        onClick = onClick,
        modifier = modifier
    )
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
    onToggleSaved: () -> Unit,
    isDedupInProgress: Boolean,
    minMentions: Int
) {
    val context = LocalContext.current
    val publishedAt = cluster.representative.article.publishedAt
    val formattedDate = formatClusterDate(publishedAt)

    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)) {
            Text(
                text = formattedDate,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            val savedAt = cluster.representative.savedAt ?: 0L
            if (cluster.representative.article.isFavorite && savedAt > 0L) {
                Text(
                    text = stringResource(R.string.feed_saved_at, formatSavedDate(savedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenSource(cluster.representative) },
            shape = AppCardShape,
            colors = appCardColors(),
            border = appCardBorder(),
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
                    onToggleSaved = onToggleSaved,
                    showActions = false
                )

                if (cluster.duplicates.isNotEmpty()) {
                    Column(
                        modifier = Modifier.padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 0.dp,
                            bottom = 8.dp
                        )
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.feed_similar_news, cluster.duplicates.size),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            items(cluster.duplicates, key = { it.first.article.id }) { (uiModel, score) ->
                                DuplicateItemCard(
                                    uiModel = uiModel,
                                    score = score,
                                    onOpenSource = { onOpenSource(uiModel) }
                                )
                            }
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
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                ClusterActionRow(
                    isFavorite = cluster.representative.article.isFavorite,
                    onAiClick = {
                        if (cluster.duplicates.isNotEmpty()) onClusterAiClick() else onAiClick(cluster.representative)
                    },
                    onToggleSaved = onToggleSaved,
                    onShare = { shareArticleLink(context, cluster.representative.article.url) }
                )
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
    onToggleSaved: () -> Unit,
    showActions: Boolean = true
) {
    val context = LocalContext.current
    val mediaUrl = uiModel.article.mediaUrl
    val shouldShowMedia = remember(isMediaEnabled, mediaUrl, uiModel.sourceType) {
        isMediaEnabled &&
            !mediaUrl.isNullOrBlank() &&
            (uiModel.sourceType == SourceType.RSS ||
                uiModel.sourceType == SourceType.TELEGRAM ||
                uiModel.sourceType == SourceType.YOUTUBE)
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    uiModel.sourceName?.let { src ->
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            text = src,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (uiModel.sourceName != null && uiModel.groupName != null) {
                        Text(
                            "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    uiModel.groupName?.let { grp ->
                        Text(
                            text = grp,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = uiModel.displayTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 22.sp
                    ),
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (shouldShowMedia && mediaUrl != null) {
                Box(
                    modifier = Modifier
                        .size(width = 80.dp, height = 80.dp)
                        .clip(MaterialTheme.shapes.medium)
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
        }

        val hasDescription = isDescriptionEnabled && uiModel.displayContent.isNotBlank()
        if (hasDescription) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = uiModel.displayContent,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (showActions) {
            Spacer(Modifier.height(8.dp))
            ClusterActionRow(
                isFavorite = uiModel.article.isFavorite,
                onAiClick = onAiClick,
                onToggleSaved = onToggleSaved,
                onShare = { shareArticleLink(context = context, articleUrl = uiModel.article.url) }
            )
        }
    }
}

@Composable
private fun ClusterActionRow(
    isFavorite: Boolean,
    onAiClick: () -> Unit,
    onToggleSaved: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.CardCompactPadding, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        IconButton(
            onClick = onAiClick,
            modifier = Modifier.size(AppDimens.ActionIconButtonSize)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_ask_ai),
                contentDescription = "Summarize",
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick = onToggleSaved,
            modifier = Modifier.size(AppDimens.ActionIconButtonSize)
        ) {
            val bookmarkIcon = if (isFavorite) {
                Icons.Default.Bookmark
            } else {
                Icons.Outlined.BookmarkBorder
            }
            val tint = if (isFavorite) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Icon(
                imageVector = bookmarkIcon,
                contentDescription = "Bookmark",
                modifier = Modifier.size(22.dp),
                tint = tint
            )
        }
        IconButton(
            onClick = onShare,
            modifier = Modifier.size(AppDimens.ActionIconButtonSize)
        ) {
            Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = "Share",
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
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
fun DuplicateItemCard(
    uiModel: ArticleUiModel,
    score: Float,
    onOpenSource: () -> Unit
) {
    val scoreInt = (score * 100).toInt()

    Card(
        modifier = Modifier
            .width(264.dp)
            .height(132.dp)
            .clickable { onOpenSource() },
        shape = AppCardShape,
        colors = appCardColors(),
        border = appCardBorder(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = uiModel.sourceName ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (score > 0f) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "${scoreInt}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = uiModel.displayTitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    lineHeight = 19.sp
                ),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

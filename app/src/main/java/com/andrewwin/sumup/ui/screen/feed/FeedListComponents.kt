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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
            .padding(bottom = 8.dp)
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
                shape = MaterialTheme.shapes.extraLarge,
                placeholder = { 
                    Text(
                        stringResource(R.string.search_placeholder),
                        style = MaterialTheme.typography.bodyLarge
                    ) 
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                    modifier = Modifier.size(28.dp),
                    tint = if (isExportEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterMenuChip(
                icon = Icons.Filled.Today,
                label = stringResource(dateFilter.labelRes),
                onClick = { showDateMenu = true }
            )
            FilterMenuChip(
                icon = Icons.Filled.Bookmark,
                label = stringResource(savedFilter.labelRes),
                onClick = { showSavedMenu = true }
            )
            val groupName = remember(selectedGroupId, groups) {
                groups.find { it.id == selectedGroupId }?.name
            } ?: stringResource(R.string.filter_group)
            FilterMenuChip(
                icon = Icons.Filled.Folder,
                label = groupName,
                onClick = { showGroupMenu = true }
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
                        text = { Text(group.name) },
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
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.05f)),
        modifier = Modifier.height(32.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
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
    val formattedDate = formatClusterDate(publishedAt)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = formattedDate,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenSource(cluster.representative) },
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.05f)),
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

                    Column(modifier = Modifier.padding(16.dp)) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.feed_similar_news, cluster.duplicates.size),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
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

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onAiClick,
                modifier = Modifier.weight(1f).height(40.dp),
                shape = MaterialTheme.shapes.large,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_ask_ai),
                    contentDescription = "Summarize",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Button(
                onClick = onToggleSaved,
                modifier = Modifier.weight(1f).height(40.dp),
                shape = MaterialTheme.shapes.large,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiModel.article.isFavorite) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (uiModel.article.isFavorite) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            ) {
                val bookmarkIcon = if (uiModel.article.isFavorite) {
                    Icons.Default.Bookmark
                } else {
                    Icons.Outlined.BookmarkBorder
                }
                val tint = if (uiModel.article.isFavorite) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Icon(
                    imageVector = bookmarkIcon,
                    contentDescription = "Bookmark",
                    modifier = Modifier.size(20.dp),
                    tint = tint
                )
            }
            Button(
                onClick = { shareArticleLink(context = context, articleUrl = uiModel.article.url) },
                modifier = Modifier.weight(1f).height(40.dp),
                shape = MaterialTheme.shapes.large,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = "Share",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
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
            .clip(MaterialTheme.shapes.medium)
            .clickable { onOpenSource() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.05f)),
            modifier = Modifier.width(48.dp).height(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "${scoreInt}%",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = uiModel.displayTitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            uiModel.sourceName?.let { src ->
                Text(
                    text = src,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

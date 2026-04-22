package com.andrewwin.sumup.ui.screen.summary

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.Summary
import com.andrewwin.sumup.ui.util.SummaryBlockUi
import com.andrewwin.sumup.ui.util.cleanSummaryTextForSharing
import com.andrewwin.sumup.ui.util.parseSummaryBlocks
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun LatestScheduledSummaryView(
    summary: Summary,
    activeSummaryModelName: String?,
    onOpenWebView: (String) -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val isError = summary.content.startsWith(stringResource(R.string.error_prefix)) ||
        summary.content.startsWith(stringResource(R.string.no_articles_prefix))
    val context = LocalContext.current
    val blocks = remember(summary.content) { parseSummaryBlocks(summary.content) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isError) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.18f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.18f))
            ) {
                Text(
                    text = summary.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
        } else {
            blocks.forEach { block ->
                when (block) {
                    is SummaryBlockUi.Section -> SummaryLegacyBlock(
                        text = block.body,
                        sourceName = block.sources.firstOrNull()?.name,
                        sourceUrl = block.sources.firstOrNull()?.url,
                        onOpenWebView = onOpenWebView,
                        sourceStyle = SummarySourceStyle.InlineChip,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        lineHeight = 26.sp
                    )
                    is SummaryBlockUi.PlainList -> SummaryPlainListBlock(
                        items = block.items,
                        onOpenWebView = onOpenWebView,
                        sourceStyle = SummarySourceStyle.InlineChip,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        lineHeight = 26.sp
                    )
                    is SummaryBlockUi.Theme -> SummaryThemeBlock(
                        heading = block.heading,
                        items = block.items,
                        onOpenWebView = onOpenWebView,
                        sourceStyle = SummarySourceStyle.InlineChip,
                        itemTextStyle = MaterialTheme.typography.bodyLarge,
                        itemLineHeight = 26.sp
                    )
                }
            }
        }

        SummaryFooterRow(
            summary = summary,
            isError = isError,
            context = context,
            modelName = activeSummaryModelName,
            onToggleFavorite = onToggleFavorite,
            onDelete = onDelete
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SummaryCard(
    summary: Summary,
    activeSummaryModelName: String?,
    onOpenWebView: (String) -> Unit,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    startExpanded: Boolean = false,
    onDelete: () -> Unit,
    onLongSelect: () -> Unit,
    onToggleSelect: () -> Unit
) {
    val isError = summary.content.startsWith(stringResource(R.string.error_prefix)) ||
        summary.content.startsWith(stringResource(R.string.no_articles_prefix))
    val context = LocalContext.current
    val blocks = remember(summary.content) { parseSummaryBlocks(summary.content) }
    var isExpanded by rememberSaveable(summary.id, startExpanded) { androidx.compose.runtime.mutableStateOf(startExpanded) }
    val expandRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "summaryExpandRotation"
    )

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(
            text = formatSummaryDate(summary.createdAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (isSelectionMode) onToggleSelect() else isExpanded = !isExpanded
                    },
                    onLongClick = onLongSelect
                ),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                    else -> MaterialTheme.colorScheme.surfaceContainer
                }
            ),
            border = BorderStroke(
                1.dp,
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.05f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 44.dp, top = if (isExpanded) 4.dp else 0.dp)
                            .animateContentSize(animationSpec = tween(durationMillis = 130))
                    ) {
                        if (isExpanded && !isError) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                blocks.forEach { block ->
                                    when (block) {
                                        is SummaryBlockUi.Section -> SummaryLegacyBlock(
                                            text = block.body,
                                            sourceName = block.sources.firstOrNull()?.name,
                                            sourceUrl = block.sources.firstOrNull()?.url,
                                            onOpenWebView = onOpenWebView
                                        )
                                        is SummaryBlockUi.PlainList -> SummaryPlainListBlock(
                                            items = block.items,
                                            onOpenWebView = onOpenWebView
                                        )
                                        is SummaryBlockUi.Theme -> SummaryThemeBlock(
                                            heading = block.heading,
                                            items = block.items,
                                            onOpenWebView = onOpenWebView
                                        )
                                    }
                                }
                            }
                        } else {
                            SummaryCollapsedPreview(blocks = blocks, isError = isError)
                        }
                    }

                    Surface(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.align(Alignment.TopEnd),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.05f))
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier
                                .size(28.dp)
                                .padding(4.dp)
                                .graphicsLayer { rotationZ = expandRotation },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.size(16.dp))
                SummaryFooterRow(
                    summary = summary,
                    isError = isError,
                    context = context,
                    modelName = activeSummaryModelName
                )
            }
        }
    }
}

@Composable
internal fun SummaryFooterRow(
    summary: Summary,
    isError: Boolean,
    context: Context,
    modelName: String?,
    onToggleFavorite: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val compactModel = modelName
        ?.substringAfter('/', modelName)
        ?.takeIf { it.isNotBlank() }
    val strategyLabel = when (summary.strategy) {
        AiStrategy.CLOUD -> context.getString(R.string.ai_strategy_cloud)
        AiStrategy.LOCAL -> context.getString(R.string.ai_strategy_local)
        AiStrategy.ADAPTIVE -> context.getString(R.string.ai_strategy_adaptive)
    }
    val metaText = if (isError) {
        stringResource(R.string.summary_system_notice)
    } else {
        buildString {
            append(strategyLabel)
            if (summary.strategy != AiStrategy.LOCAL && !compactModel.isNullOrBlank()) {
                append(", ")
                append(compactModel)
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = metaText,
            style = MaterialTheme.typography.labelSmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(6.dp))
        IconButton(
            onClick = {
                copySummaryText(context, summary.content)
                Toast.makeText(
                    context,
                    context.getString(R.string.summary_copied),
                    Toast.LENGTH_SHORT
                ).show()
            },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = { shareSummaryText(context, summary.content) },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onToggleFavorite != null) {
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (summary.isFavorite) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (summary.isFavorite) "Remove from favorites" else "Add to favorites",
                    modifier = Modifier.size(18.dp),
                    tint = if (summary.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (onDelete != null) {
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Delete summary",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun SummaryCollapsedPreview(
    blocks: List<SummaryBlockUi>,
    isError: Boolean
) {
    val previewText = remember(blocks) {
        buildAnnotatedString {
            blocks.forEachIndexed { blockIndex, block ->
                if (blockIndex > 0) append("\n\n")
                when (block) {
                    is SummaryBlockUi.Section -> append(block.body)
                    is SummaryBlockUi.PlainList -> {
                        block.items.take(3).forEachIndexed { itemIndex, item ->
                            if (itemIndex > 0) append("\n")
                            append(item.marker)
                            append(' ')
                            append(item.text)
                        }
                    }
                    is SummaryBlockUi.Theme -> {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(block.heading)
                        pop()
                        block.items.take(2).forEach { item ->
                            append("\n")
                            append(item.marker)
                            append(' ')
                            append(item.text)
                        }
                    }
                }
            }
        }
    }
    androidx.compose.foundation.text.selection.SelectionContainer {
        Text(
            text = previewText,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 26.sp
        )
    }
}

private fun formatSummaryDate(timestamp: Long): String {
    return SimpleDateFormat("HH:mm, dd MMMM", Locale.getDefault()).format(Date(timestamp))
}

private fun shareSummaryText(
    context: Context,
    summaryText: String
) {
    val cleaned = cleanSummaryTextForSharing(summaryText)
    if (cleaned.isBlank()) return
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, cleaned)
    }
    val chooser = Intent.createChooser(
        shareIntent,
        context.getString(R.string.summary_share_chooser_title)
    )
    context.startActivity(chooser)
}

private fun copySummaryText(
    context: Context,
    summaryText: String
) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("summary_text", cleanSummaryTextForSharing(summaryText))
    clipboardManager.setPrimaryClip(clip)
}

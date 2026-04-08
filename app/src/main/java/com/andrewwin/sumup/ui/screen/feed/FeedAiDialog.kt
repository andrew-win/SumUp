package com.andrewwin.sumup.ui.screen.feed

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.domain.support.SummarySourceMeta
import com.andrewwin.sumup.ui.screen.feed.model.ArticleClusterUiModel
import com.andrewwin.sumup.ui.screen.feed.model.ArticleUiModel

@Composable
fun FeedAiDialog(
    context: Context,
    isFeedAiActive: Boolean,
    articleForAi: ArticleUiModel?,
    articleClusters: List<ArticleClusterUiModel>,
    aiResult: String?,
    isAiLoading: Boolean,
    aiStrategy: AiStrategy,
    activeSummaryModelName: String?,
    userQuestion: String,
    onQuestionChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onAsk: () -> Unit,
    onRegenerate: () -> Unit,
    onOpenWebView: (String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
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
                val summaryTitle = articleForAi?.displayTitle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isFeedAiActive) {
                            context.getString(R.string.ai_summarize_feed)
                        } else {
                            context.getString(R.string.ai_summarize_article)
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = onDismiss) {
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
                            Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                        }
                    } else if (aiResult != null) {
                        val compareBlocks = parseCompareBlocks(aiResult)
                        val sections = parseSummarySections(aiResult)
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (!summaryTitle.isNullOrBlank()) {
                                Text(
                                    text = summaryTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (compareBlocks != null) {
                                CompareBlocksView(
                                    commonItems = compareBlocks.common,
                                    differentItems = compareBlocks.different,
                                    onOpenWebView = onOpenWebView
                                )
                            } else {
                                SingleSummaryCard(
                                    sections = sections,
                                    onOpenWebView = onOpenWebView
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        if (aiStrategy != AiStrategy.LOCAL && !activeSummaryModelName.isNullOrBlank()) {
                                            Text(
                                                text = "Модель: $activeSummaryModelName",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            Spacer(Modifier)
                                        }
                                        Surface(
                                            shape = MaterialTheme.shapes.extraSmall,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                                            border = BorderStroke(
                                                1.dp,
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                            )
                                        ) {
                                            Text(
                                                text = when (aiStrategy) {
                                                    AiStrategy.CLOUD -> context.getString(R.string.ai_strategy_cloud)
                                                    AiStrategy.LOCAL -> context.getString(R.string.ai_strategy_local)
                                                    AiStrategy.ADAPTIVE -> context.getString(R.string.ai_strategy_adaptive)
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Start
                                    ) {
                                        IconButton(
                                            onClick = {
                                                copyTextToClipboard(context, aiResult)
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
                                                    text = aiResult,
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
                    }
                }

                if ((aiResult != null || !isAiLoading) && aiStrategy != AiStrategy.LOCAL) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = userQuestion,
                        onValueChange = onQuestionChange,
                        label = null,
                        placeholder = {
                            Text(text = context.getString(R.string.ai_question_input_placeholder))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        trailingIcon = {
                            IconButton(onClick = onAsk) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                            }
                        }
                    )
                }

                if (aiResult != null || !isAiLoading) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onRegenerate,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.size(12.dp))
                        Text(
                            text = if (isFeedAiActive) {
                                context.getString(R.string.ai_summarize_feed)
                            } else {
                                context.getString(R.string.ai_summarize_article)
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleSummaryCard(
    sections: List<SummarySection>,
    onOpenWebView: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                sections.forEach { section ->
                    Text(
                        text = section.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 26.sp
                    )
                    section.source?.let { source ->
                        AssistChip(
                            onClick = { onOpenWebView(normalizeForWebView(source.url)) },
                            shape = MaterialTheme.shapes.medium,
                            label = {
                                Text(
                                    source.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun copyTextToClipboard(context: Context, text: String) {
    val cleaned = cleanSummaryText(text)
    if (cleaned.isBlank()) return
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboardManager.setPrimaryClip(ClipData.newPlainText("ai_result_text", cleaned))
}

private fun shareText(
    context: Context,
    text: String,
    chooserTitle: String
) {
    val cleaned = cleanSummaryText(text)
    if (cleaned.isBlank()) return
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, cleaned)
    }
    context.startActivity(Intent.createChooser(shareIntent, chooserTitle))
}

private data class SourceMetaUi(val name: String, val url: String)
private data class SummarySection(val body: String, val source: SourceMetaUi?)
private data class CompareItemUi(val sourceName: String, val text: String, val url: String?)
private data class CompareBlocksUi(val common: List<CompareItemUi>, val different: List<CompareItemUi>)

private val SourceMetaInlineRegex = Regex("${Regex.escape(SummarySourceMeta.PREFIX)}[^\\n]*")
private val CompareBulletRegex = Regex("""^•\s*(.*?):\s*(.*?)\s*\((https?://[^)]+)\)\s*$""")

private fun parseCompareBlocks(raw: String): CompareBlocksUi? {
    val lines = raw.lines().map { it.trim() }
    val commonIndex = lines.indexOfFirst { it.equals("Спільне:", ignoreCase = true) }
    val differentIndex = lines.indexOfFirst {
        it.equals("Унікальне:", ignoreCase = true) || it.equals("Відмінне:", ignoreCase = true)
    }
    if (commonIndex == -1 || differentIndex == -1 || differentIndex <= commonIndex) return null

    fun parseRange(from: Int, toExclusive: Int): List<CompareItemUi> {
        return lines.subList(from, toExclusive)
            .filter { it.startsWith("•") }
            .mapNotNull { line ->
                val match = CompareBulletRegex.find(line)
                if (match != null) {
                    val source = match.groupValues[1].trim().ifBlank { "Джерело" }
                    val text = match.groupValues[2].trim()
                    val url = match.groupValues[3].trim().takeIf { it.isNotBlank() }
                    CompareItemUi(sourceName = source, text = text, url = url)
                } else {
                    val text = line.removePrefix("•").trim()
                    if (text.isBlank()) null else CompareItemUi("Джерело", text, null)
                }
            }
    }

    val common = parseRange(commonIndex + 1, differentIndex)
    val different = parseRange(differentIndex + 1, lines.size)
    if (common.isEmpty() && different.isEmpty()) return null
    return CompareBlocksUi(common = common, different = different)
}

@Composable
private fun CompareBlocksView(
    commonItems: List<CompareItemUi>,
    differentItems: List<CompareItemUi>,
    onOpenWebView: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CompareBlockCard(title = "Спільне", items = commonItems, onOpenWebView = onOpenWebView)
        CompareBlockCard(title = "Унікальне", items = differentItems, onOpenWebView = onOpenWebView)
    }
}

@Composable
private fun CompareBlockCard(
    title: String,
    items: List<CompareItemUi>,
    onOpenWebView: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            if (items.isEmpty()) {
                Text(
                    text = "Немає достатньо даних.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                items.forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "• ${item.text}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 24.sp
                        )
                        item.url?.let { url ->
                            AssistChip(
                                onClick = { onOpenWebView(normalizeForWebView(url)) },
                                shape = MaterialTheme.shapes.medium,
                                label = {
                                    Text(
                                        item.sourceName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Link,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun parseSummarySections(raw: String): List<SummarySection> {
    val normalizedRaw = raw.replace(Regex("\\s*${Regex.escape(SummarySourceMeta.PREFIX)}"), "\n${SummarySourceMeta.PREFIX}")
    return normalizedRaw
        .split(Regex("\\n\\s*\\n"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { block ->
            val lines = block.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
            if (lines.isEmpty()) return@mapNotNull null
            val source = lines.lastOrNull()?.let { parseSourceMeta(it.trim()) }
            val bodyLines = if (source != null) lines.dropLast(1) else lines
            val body = bodyLines.joinToString("\n").replace(SourceMetaInlineRegex, "").trim()
            if (body.isBlank()) return@mapNotNull null
            SummarySection(body = body, source = source)
        }
}

private fun parseSourceMeta(line: String): SourceMetaUi? {
    if (!line.startsWith(SummarySourceMeta.PREFIX)) return null
    val payload = line.removePrefix(SummarySourceMeta.PREFIX)
    val separator = payload.lastIndexOf('|')
    if (separator <= 0 || separator >= payload.lastIndex) return null
    val name = payload.substring(0, separator).trim()
    val url = payload.substring(separator + 1).trim()
    if (name.isBlank() || url.isBlank()) return null
    return SourceMetaUi(name, url)
}

private fun cleanSummaryText(raw: String): String {
    val normalizedRaw = raw.replace(Regex("\\s*${Regex.escape(SummarySourceMeta.PREFIX)}"), "\n${SummarySourceMeta.PREFIX}")
    return normalizedRaw
        .replace(SourceMetaInlineRegex, "")
        .lines()
        .filterNot { it.trim().startsWith(SummarySourceMeta.PREFIX) }
        .map { it.trimEnd() }
        .filter { it.isNotBlank() }
        .joinToString("\n")
}

private fun normalizeForWebView(url: String): String {
    val trimmed = url.trim()
    if (!trimmed.contains("t.me/")) return trimmed
    val marker = "t.me/"
    val idx = trimmed.indexOf(marker)
    if (idx == -1) return trimmed
    val prefix = trimmed.substring(0, idx + marker.length)
    var path = trimmed.substring(idx + marker.length).trimStart('/')
    if (path.startsWith("s/")) return "$prefix$path"
    if (path.startsWith("c/")) return "$prefix$path"
    val segments = path.split("/").filter { it.isNotBlank() }
    if (segments.size >= 2) {
        path = "s/${segments[0]}/${segments[1]}"
        return "$prefix$path"
    }
    if (segments.size == 1) {
        return "$prefix" + "s/${segments[0]}"
    }
    return trimmed
}

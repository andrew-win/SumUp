package com.andrewwin.sumup.ui.screen.feed

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                            Modifier.fillMaxWidth().height(150.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                        }
                    } else if (aiResult != null) {
                        val sections = parseSummarySections(aiResult)
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SelectionContainer {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    sections.forEach { section ->
                                        Text(
                                            text = section.body,
                                            style = MaterialTheme.typography.bodyMedium,
                                            lineHeight = 28.sp
                                        )
                                        section.source?.let { source ->
                                            AssistChip(
                                                onClick = { onOpenWebView(normalizeForWebView(source.url)) },
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
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
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

                if ((aiResult != null || !isAiLoading) && aiStrategy != AiStrategy.LOCAL) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = userQuestion,
                        onValueChange = onQuestionChange,
                        label = null,
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
private val SourceMetaInlineRegex = Regex("${Regex.escape(SummarySourceMeta.PREFIX)}[^\\n]*")

private fun parseSummarySections(raw: String): List<SummarySection> {
    val normalizedRaw = raw.replace(Regex("\\s*${Regex.escape(SummarySourceMeta.PREFIX)}"), "\n${SummarySourceMeta.PREFIX}")
    data class MutableSummarySection(var body: String, var source: SourceMetaUi?)
    val sections = mutableListOf<MutableSummarySection>()
    val lines = normalizedRaw.lines().map { it.trimEnd() }.filter { it.isNotBlank() }

    lines.forEach { line ->
        val trimmed = line.trim()
        if (trimmed.startsWith(SummarySourceMeta.PREFIX)) {
            val source = parseSourceMeta(trimmed) ?: return@forEach
            val lastIndex = sections.indexOfLast { it.source == null && it.body.isNotBlank() }
            if (lastIndex >= 0) {
                sections[lastIndex].source = source
            }
            return@forEach
        }

        val bodyLine = trimmed.replace(SourceMetaInlineRegex, "").trim()
        if (bodyLine.isBlank()) return@forEach

        val last = sections.lastOrNull()
        val shouldAppendToLastBullet = last != null &&
            last.source == null &&
            (last.body.startsWith("•") || last.body.startsWith("-")) &&
            !bodyLine.startsWith("•") &&
            !bodyLine.startsWith("-")

        if (shouldAppendToLastBullet) {
            last.body = "${last.body} $bodyLine".trim()
        } else {
            sections += MutableSummarySection(body = bodyLine, source = null)
        }
    }

    return sections
        .map { SummarySection(body = it.body, source = it.source) }
        .filter { it.body.isNotBlank() || it.source != null }
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








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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.DialogProperties
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.domain.support.SummarySourceMeta
import com.andrewwin.sumup.ui.components.AppAnimatedDialog
import com.andrewwin.sumup.ui.components.AppMotion
import com.andrewwin.sumup.ui.components.AppCardSurface
import com.andrewwin.sumup.ui.screen.feed.model.ArticleClusterUiModel
import com.andrewwin.sumup.ui.screen.feed.model.ArticleUiModel
import com.andrewwin.sumup.ui.theme.AppCardShape
import com.andrewwin.sumup.ui.theme.appCardBorder
import com.andrewwin.sumup.ui.util.SummaryBlockUi
import com.andrewwin.sumup.ui.util.SummarySourceLinkUi
import com.andrewwin.sumup.ui.util.ThemeItem
import com.andrewwin.sumup.ui.util.cleanSummaryTextForSharing
import com.andrewwin.sumup.ui.util.normalizeSummaryUrlForWebView
import com.andrewwin.sumup.ui.util.parseSummaryBlocks

@Composable
fun FeedAiDialog(
    isVisible: Boolean,
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
    AppAnimatedDialog(
        visible = isVisible,
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        enter = AppMotion.contentEnter(),
        exit = AppMotion.contentExit()
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
                        val blocks = parseSummaryBlocks(aiResult)
                        val sourceLabelMap = rememberSourceLabelMap(
                            summaryBlocks = blocks,
                            compareBlocks = compareBlocks
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (!summaryTitle.isNullOrBlank()) {
                                Text(
                                    text = summaryTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp)
                                )
                            }
                            if (compareBlocks != null) {
                                CompareBlocksView(
                                    commonItems = compareBlocks.common,
                                    differentItems = compareBlocks.different,
                                    sourceLabelMap = sourceLabelMap,
                                    onOpenWebView = onOpenWebView
                                )
                            } else {
                                SingleSummaryCard(
                                    blocks = blocks,
                                    sourceLabelMap = sourceLabelMap,
                                    onOpenWebView = onOpenWebView
                                )
                            }
                            SummaryMetaRow(
                                modelName = activeSummaryModelName,
                                aiStrategy = aiStrategy,
                                onCopy = {
                                    copyTextToClipboard(context, aiResult)
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.ai_result_copied),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onShare = {
                                    shareText(
                                        context = context,
                                        text = aiResult,
                                        chooserTitle = context.getString(R.string.summary_share_chooser_title)
                                    )
                                }
                            )
                        }
                    }
                }

                if (aiStrategy != AiStrategy.LOCAL) {
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
                            IconButton(
                                onClick = onAsk,
                                enabled = userQuestion.isNotBlank() && !isAiLoading
                            ) {
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
                        Icon(
                            painter = painterResource(R.drawable.ic_ask_ai),
                            contentDescription = null
                        )
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
    blocks: List<SummaryBlockUi>,
    sourceLabelMap: Map<String, String>,
    onOpenWebView: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        blocks.forEach { block ->
        when (block) {
            is SummaryBlockUi.Section -> LegacySummarySectionView(
                text = block.body,
                sources = block.sources,
                sourceLabelMap = sourceLabelMap,
                onOpenWebView = onOpenWebView
            )
            is SummaryBlockUi.PlainList -> PlainListSummarySectionView(
                items = block.items,
                sourceLabelMap = sourceLabelMap,
                onOpenWebView = onOpenWebView
            )
            is SummaryBlockUi.Theme -> ThemeSummarySectionView(
                heading = block.heading,
                summary = block.summary,
                items = block.items,
                sourceLabelMap = sourceLabelMap,
                onOpenWebView = onOpenWebView
            )
            }
        }
    }
}

private fun copyTextToClipboard(context: Context, text: String) {
    val cleaned = cleanSummaryTextForSharing(text)
    if (cleaned.isBlank()) return
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboardManager.setPrimaryClip(ClipData.newPlainText("ai_result_text", cleaned))
}

private fun shareText(
    context: Context,
    text: String,
    chooserTitle: String
) {
    val cleaned = cleanSummaryTextForSharing(text)
    if (cleaned.isBlank()) return
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, cleaned)
    }
    context.startActivity(Intent.createChooser(shareIntent, chooserTitle))
}

private data class CompareItemUi(val text: String, val sources: List<SummarySourceLinkUi>)
private data class CompareBlocksUi(val common: List<CompareItemUi>, val different: List<CompareItemUi>)
private val CompareBulletRegex = Regex("""^[•—-]\s*(.*?):\s*(.*?)\s*\((https?://[^)]+)\)\s*$""")
private const val InlineSourceAnnotationTag = "summary_source"

private enum class FeedSummarySourceStyle {
    TextLink,
    InlineChip
}

@Composable
private fun rememberSourceLabelMap(
    summaryBlocks: List<SummaryBlockUi>,
    compareBlocks: CompareBlocksUi?
): Map<String, String> {
    val summarySources = buildList {
        summaryBlocks.forEach { block ->
            when (block) {
                is SummaryBlockUi.Section -> addAll(block.sources)
                is SummaryBlockUi.PlainList -> block.items.forEach { addAll(it.sources) }
                is SummaryBlockUi.Theme -> block.items.forEach { addAll(it.sources) }
            }
        }
    }
    val compareSources = buildList {
        compareBlocks?.common?.forEach { addAll(it.sources) }
        compareBlocks?.different?.forEach { addAll(it.sources) }
    }
    return buildSourceLabelMap(summarySources + compareSources)
}

private fun buildSourceLabelMap(sources: List<SummarySourceLinkUi>): Map<String, String> {
    val orderedDistinct = buildList {
        val seen = mutableSetOf<String>()
        sources.forEach { source ->
            val key = source.key()
            if (seen.add(key)) add(source)
        }
    }
    val countsByName = orderedDistinct.groupingBy { it.name }.eachCount()
    val nextIndexByName = mutableMapOf<String, Int>()
    return orderedDistinct.associate { source ->
        val index = nextIndexByName.getOrElse(source.name) { 0 } + 1
        nextIndexByName[source.name] = index
        val label = if ((countsByName[source.name] ?: 0) > 1) {
            "${source.name} ($index)"
        } else {
            source.name
        }
        source.key() to label
    }
}

private fun SummarySourceLinkUi.key(): String = "$name|$url"

@Composable
private fun SummaryMetaRow(
    modelName: String?,
    aiStrategy: AiStrategy,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    val compactModel = modelName
        ?.substringAfter('/', modelName)
        ?.takeIf { it.isNotBlank() }
    val metaText = buildString {
        append(
            when (aiStrategy) {
                AiStrategy.CLOUD -> context.getString(R.string.ai_strategy_cloud)
                AiStrategy.LOCAL -> context.getString(R.string.ai_strategy_local)
                AiStrategy.ADAPTIVE -> context.getString(R.string.ai_strategy_adaptive)
            }
        )
        if (aiStrategy != AiStrategy.LOCAL && compactModel != null) {
            append(", ")
            append(compactModel)
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(6.dp))
        IconButton(
            onClick = onCopy,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onShare,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = null,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

private fun parseCompareBlocks(raw: String): CompareBlocksUi? {
    val lines = raw.lines().map { it.trim() }
    val commonIndex = lines.indexOfFirst { it.equals("Спільне:", ignoreCase = true) }
    val differentIndex = lines.indexOfFirst {
        it.equals("Унікальне:", ignoreCase = true) || it.equals("Відмінне:", ignoreCase = true)
    }
    if (commonIndex == -1 || differentIndex == -1 || differentIndex <= commonIndex) return null

    fun parseRange(from: Int, toExclusive: Int): List<CompareItemUi> {
        val parsed = mutableListOf<CompareItemUi>()
        var index = from
        while (index < toExclusive) {
            val line = lines[index]
            if (!(line.startsWith("•") || line.startsWith("—") || line.startsWith("-"))) {
                index++
                continue
            }

            val match = CompareBulletRegex.find(line)
            if (match != null) {
                val text = match.groupValues[2].trim()
                val url = match.groupValues[3].trim().takeIf { it.isNotBlank() }
                val sources = if (!url.isNullOrBlank()) {
                    listOf(SummarySourceLinkUi(name = match.groupValues[1].trim().ifBlank { "Джерело" }, url = url))
                } else {
                    emptyList()
                }
                parsed += CompareItemUi(text = text, sources = sources)
                index++
                continue
            }

            val text = line.removePrefix("•").removePrefix("—").removePrefix("-").trim()
            if (text.isBlank()) {
                index++
                continue
            }

            val sources = mutableListOf<SummarySourceLinkUi>()
            while (index + 1 < toExclusive) {
                val nextLine = lines.getOrNull(index + 1).orEmpty()
                if (!nextLine.startsWith(SummarySourceMeta.PREFIX)) break
                val payload = nextLine.removePrefix(SummarySourceMeta.PREFIX)
                val separator = payload.lastIndexOf('|')
                if (separator <= 0 || separator >= payload.lastIndex) break
                val sourceName = payload.substring(0, separator).trim()
                val sourceUrl = payload.substring(separator + 1).trim().ifBlank { null }
                if (!sourceName.isBlank() && !sourceUrl.isNullOrBlank()) {
                    sources += SummarySourceLinkUi(name = sourceName, url = sourceUrl)
                }
                index++
            }

            parsed += CompareItemUi(text = text, sources = sources)
            index++
        }
        return parsed
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
    sourceLabelMap: Map<String, String>,
    onOpenWebView: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CompareBlockCard(
            title = stringResource(R.string.summary_compare_common),
            items = commonItems,
            sourceLabelMap = sourceLabelMap,
            onOpenWebView = onOpenWebView
        )
        CompareBlockCard(
            title = stringResource(R.string.summary_compare_unique),
            items = differentItems,
            sourceLabelMap = sourceLabelMap,
            onOpenWebView = onOpenWebView
        )
    }
}

@Composable
private fun CompareBlockCard(
    title: String,
    items: List<CompareItemUi>,
    sourceLabelMap: Map<String, String>,
    onOpenWebView: (String) -> Unit
) {
    AppCardSurface(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
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
                    text = stringResource(R.string.summary_not_enough_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                items.forEach { item ->
                    InlineSummaryRow(
                        text = "— ${item.text}",
                        sources = item.sources,
                        sourceLabelMap = sourceLabelMap,
                        onOpenWebView = onOpenWebView,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        lineHeight = 26.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun LegacySummarySectionView(
    text: String,
    sources: List<SummarySourceLinkUi>,
    sourceLabelMap: Map<String, String>,
    onOpenWebView: (String) -> Unit
) {
    AppCardSurface(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            InlineSummaryRow(
                text = text,
                sources = sources,
                sourceLabelMap = sourceLabelMap,
                onOpenWebView = onOpenWebView,
                textStyle = MaterialTheme.typography.bodyLarge,
                lineHeight = 26.sp
            )
        }
    }
}

@Composable
private fun PlainListSummarySectionView(
    items: List<ThemeItem>,
    sourceLabelMap: Map<String, String>,
    onOpenWebView: (String) -> Unit
) {
    AppCardSurface(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEach { item ->
                InlineSummaryRow(
                    text = "${item.marker} ${item.text}",
                    sources = item.sources,
                    sourceLabelMap = sourceLabelMap,
                    onOpenWebView = onOpenWebView,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    lineHeight = 26.sp
                )
            }
        }
    }
}

@Composable
private fun ThemeSummarySectionView(
    heading: String,
    summary: String?,
    items: List<ThemeItem>,
    sourceLabelMap: Map<String, String>,
    onOpenWebView: (String) -> Unit
) {
    AppCardSurface(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = heading,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            summary?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items.forEach { item ->
                InlineSummaryRow(
                    text = "${item.marker} ${item.text}",
                    sources = item.sources,
                    sourceLabelMap = sourceLabelMap,
                    onOpenWebView = onOpenWebView,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    lineHeight = 26.sp
                )
            }
        }
    }
}

@Composable
private fun InlineSummaryRow(
    text: String,
    sources: List<SummarySourceLinkUi>,
    sourceLabelMap: Map<String, String>,
    onOpenWebView: (String) -> Unit,
    textStyle: TextStyle,
    lineHeight: TextUnit,
    sourceStyle: FeedSummarySourceStyle = FeedSummarySourceStyle.InlineChip
) {
    val effectiveStyle = textStyle.copy(lineHeight = lineHeight)
    val distinctSources = sources.distinctBy { it.key() }
    val chipTextStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp)
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    if (sourceStyle == FeedSummarySourceStyle.InlineChip && distinctSources.isNotEmpty()) {
        val bodyFontSize = if (effectiveStyle.fontSize.isSpecified) {
            effectiveStyle.fontSize
        } else {
            MaterialTheme.typography.bodyLarge.fontSize
        }
        val bodyFontPx = with(density) { bodyFontSize.toPx().coerceAtLeast(1f) }
        val chipHorizontalPx = with(density) { (10.dp * 2 + 13.dp + 6.dp).toPx() }
        val inlineContent = linkedMapOf<String, InlineTextContent>()
        val annotated = buildAnnotatedString {
            append(text)
            distinctSources.forEachIndexed { index, source ->
                val sourceName = sourceLabelMap[source.key()] ?: source.name
                val inlineId = "feed_summary_inline_chip_$index"
                val labelWidthPx = textMeasurer.measure(
                    text = sourceName,
                    style = chipTextStyle,
                    maxLines = 1
                ).size.width.toFloat()
                val chipWidthEm = ((labelWidthPx + chipHorizontalPx) / bodyFontPx).em
                inlineContent[inlineId] = InlineTextContent(
                    Placeholder(
                        width = chipWidthEm,
                        height = 1.48.em,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                    )
                ) {
                    Surface(
                        onClick = { onOpenWebView(normalizeSummaryUrlForWebView(source.url)) },
                        shape = RoundedCornerShape(11.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = sourceName,
                                style = chipTextStyle,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                append(" ")
                appendInlineContent(inlineId, "[source]")
            }
        }
        BasicText(
            text = annotated,
            style = effectiveStyle.copy(color = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.fillMaxWidth(),
            inlineContent = inlineContent
        )
    } else {
        val annotated = buildAnnotatedString {
            append(text)
            distinctSources.forEach { source ->
                val sourceName = sourceLabelMap[source.key()] ?: source.name
                append(" ")
                pushStringAnnotation(tag = InlineSourceAnnotationTag, annotation = source.url)
                pushStyle(
                    SpanStyle(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.86f),
                        fontSize = 12.sp
                    )
                )
                append("[")
                append(sourceName)
                append("]")
                pop()
                pop()
            }
        }
        ClickableText(
            text = annotated,
            style = effectiveStyle.copy(color = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.fillMaxWidth(),
            onClick = { offset ->
                annotated
                    .getStringAnnotations(tag = InlineSourceAnnotationTag, start = offset, end = offset)
                    .firstOrNull()
                    ?.let { onOpenWebView(normalizeSummaryUrlForWebView(it.item)) }
            }
        )
    }
}

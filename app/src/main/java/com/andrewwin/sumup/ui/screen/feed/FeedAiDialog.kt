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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.andrewwin.sumup.domain.usecase.ai.DigestTheme
import com.andrewwin.sumup.domain.usecase.ai.SummaryItem
import com.andrewwin.sumup.domain.usecase.ai.SummaryResult
import com.andrewwin.sumup.domain.usecase.ai.SummarySourceRef
import com.andrewwin.sumup.domain.support.SummarySourceMeta
import com.andrewwin.sumup.ui.components.AppAnimatedDialog
import com.andrewwin.sumup.ui.components.AppMotion
import com.andrewwin.sumup.ui.components.AppCardSurface
import com.andrewwin.sumup.ui.screen.feed.model.ArticleClusterUiModel
import com.andrewwin.sumup.ui.screen.feed.model.ArticleUiModel
import com.andrewwin.sumup.ui.theme.AppCardShape
import com.andrewwin.sumup.ui.theme.appCardBorder
import com.andrewwin.sumup.ui.util.*

@Composable
fun FeedAiDialog(
    isVisible: Boolean,
    context: Context,
    isFeedAiActive: Boolean,
    articleForAi: ArticleUiModel?,
    articleClusters: List<ArticleClusterUiModel>,
    aiResult: AiPresentationResult?,
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
    val isFeedEmpty = isFeedAiActive && articleClusters.isEmpty()
    val emptyFeedMessage = context.getString(R.string.feed_ai_empty_message)
    val rawText = aiResult?.rawText
    val summaryResult = aiResult?.result

    AppAnimatedDialog(
        visible = isVisible,
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        enter = AppMotion.contentEnter(),
        exit = AppMotion.contentExit()
    ) {
        FeedAiSummaryContent(
            context = context,
            isFeedAiActive = isFeedAiActive,
            summaryTitle = articleForAi?.displayTitle,
            isFeedEmpty = isFeedEmpty,
            emptyFeedMessage = emptyFeedMessage,
            aiResult = aiResult,
            isAiLoading = isAiLoading,
            aiStrategy = aiStrategy,
            activeSummaryModelName = activeSummaryModelName,
            userQuestion = userQuestion,
            onQuestionChange = onQuestionChange,
            onClose = onDismiss,
            onAsk = onAsk,
            onRegenerate = onRegenerate,
            onOpenWebView = onOpenWebView
        )
    }
}

@Composable
internal fun FeedAiSummaryContent(
    context: Context,
    isFeedAiActive: Boolean,
    summaryTitle: String?,
    isFeedEmpty: Boolean,
    emptyFeedMessage: String,
    aiResult: AiPresentationResult?,
    isAiLoading: Boolean,
    aiStrategy: AiStrategy,
    activeSummaryModelName: String?,
    userQuestion: String,
    onQuestionChange: (String) -> Unit,
    onClose: () -> Unit,
    onAsk: () -> Unit,
    onRegenerate: () -> Unit,
    onOpenWebView: (String) -> Unit
) {
    val rawText = aiResult?.rawText
    val summaryResult = aiResult?.result

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 8.dp)
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
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
            ) {
                var compareTabIndex by rememberSaveable { mutableIntStateOf(0) }
                if (isAiLoading) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                    }
                } else if (summaryResult != null) {
                    val compareBlocks = remember(summaryResult) { summaryResult.asCompareBlocksUi() }
                    val digestBlocks = remember(summaryResult) { summaryResult.asSummaryBlocksUi() }
                    val sourceLabelMap = rememberSourceLabelMap(
                        summaryBlocks = digestBlocks,
                        compareBlocks = compareBlocks,
                        summaryResult = summaryResult
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (compareBlocks != null && !summaryTitle.isNullOrBlank()) {
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
                                selectedTabIndex = compareTabIndex,
                                onSelectedTabIndexChange = { compareTabIndex = it },
                                sourceLabelMap = sourceLabelMap,
                                onOpenWebView = onOpenWebView
                            )
                            SummaryMetaRow(
                                modelName = activeSummaryModelName,
                                aiStrategy = aiStrategy,
                                onCopy = {
                                    copyTextToClipboard(context, rawText.orEmpty())
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.ai_result_copied),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onShare = {
                                    shareText(
                                        context = context,
                                        text = rawText.orEmpty(),
                                        chooserTitle = context.getString(R.string.summary_share_chooser_title)
                                    )
                                }
                            )
                        } else if (summaryResult is SummaryResult.Digest || summaryResult is SummaryResult.Error) {
                            StandardSummaryView(
                                result = summaryResult,
                                blocks = digestBlocks,
                                onOpenWebView = onOpenWebView
                            )
                            SummaryMetaRow(
                                modelName = activeSummaryModelName,
                                aiStrategy = aiStrategy,
                                onCopy = {
                                    copyTextToClipboard(context, rawText.orEmpty())
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.ai_result_copied),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onShare = {
                                    shareText(
                                        context = context,
                                        text = rawText.orEmpty(),
                                        chooserTitle = context.getString(R.string.summary_share_chooser_title)
                                    )
                                }
                            )
                        } else {
                            SingleSummaryCard(
                                result = summaryResult,
                                onOpenWebView = onOpenWebView,
                                modelName = activeSummaryModelName,
                                aiStrategy = aiStrategy,
                                onCopy = {
                                    copyTextToClipboard(context, rawText.orEmpty())
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.ai_result_copied),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onShare = {
                                    shareText(
                                        context = context,
                                        text = rawText.orEmpty(),
                                        chooserTitle = context.getString(R.string.summary_share_chooser_title)
                                    )
                                }
                            )
                        }
                    }
                } else if (isFeedEmpty) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = emptyFeedMessage,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_ask_ai),
                                contentDescription = null,
                                modifier = Modifier.size(64.dp).alpha(0.3f),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = context.getString(R.string.ai_summary_start_prompt),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.alpha(0.7f)
                            )
                        }
                    }
                }
            }

            if (aiResult != null || !isAiLoading) {
                FeedAiSummaryBottomActions(
                    isFeedAiActive = isFeedAiActive,
                    isAiLoading = isAiLoading,
                    isFeedEmpty = isFeedEmpty,
                    aiStrategy = aiStrategy,
                    userQuestion = userQuestion,
                    onQuestionChange = onQuestionChange,
                    onAsk = onAsk,
                    onRegenerate = onRegenerate
                )
            }
        }
    }
}

@Composable
private fun SingleSummaryCard(
    result: SummaryResult,
    onOpenWebView: (String) -> Unit,
    modelName: String?,
    aiStrategy: AiStrategy,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    val digestBlocks = remember(result) { result.asSummaryBlocksUi() }
    val sourceLabelMap = rememberSourceLabelMap(
        summaryBlocks = digestBlocks,
        compareBlocks = result.asCompareBlocksUi(),
        summaryResult = result
    )
    val compactModel = modelName
        ?.substringAfter('/', modelName)
        ?.takeIf { it.isNotBlank() }
    val footerText = buildString {
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

    if (result is SummaryResult.QA) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            buildList {
                result.question?.takeIf { it.isNotBlank() }?.let { question ->
                    add(QaSectionUi("Питання", listOf(ThemeItem(marker = "—", text = question, sources = emptyList()))))
                }
                add(QaSectionUi("Коротка відповідь", listOf(ThemeItem(marker = "—", text = result.shortAnswer, sources = emptyList()))))
                if (result.details.isNotEmpty()) {
                    add(QaSectionUi("Детальніше", result.details.take(5).map { it.toThemeItem() }))
                }
            }.forEach { section ->
                AppCardSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = section.heading,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        section.items.forEach { item ->
                            InlineSummaryRow(
                                text = "— ${item.text}",
                                sources = item.sources,
                                sourceLabelMap = sourceLabelMap,
                                onOpenWebView = onOpenWebView,
                                textStyle = MaterialTheme.typography.bodyLarge,
                                lineHeight = 26.sp,
                                sourceStyle = SummarySourceStyle.InlineChip
                            )
                        }
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
                    text = footerText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onShare, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    } else {
        val content = when (result) {
            is SummaryResult.Single -> SingleSummaryContentUi(
                title = result.title?.takeIf { it.isNotBlank() } ?: "Підсумок",
                bullets = result.points.map { it.text.trim() }.filter { it.isNotBlank() }.take(5),
                sources = result.sources.map { it.toLinkUi() }
            )
            is SummaryResult.Error -> SingleSummaryContentUi(
                title = "Підсумок",
                bullets = listOf(result.message),
                sources = emptyList()
            )
            else -> SingleSummaryContentUi(
                title = "Підсумок",
                bullets = listOf("Немає достатньо даних для відображення."),
                sources = emptyList()
            )
        }
        AppCardSurface(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = content.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                content.bullets.forEach { bullet ->
                    InlineSummaryRow(
                        text = "— $bullet",
                        sources = content.sources,
                        sourceLabelMap = sourceLabelMap,
                        onOpenWebView = onOpenWebView,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        lineHeight = 26.sp,
                        sourceStyle = SummarySourceStyle.InlineChip
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = footerText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onShare, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StandardSummaryCard(
    result: SummaryResult,
    blocks: List<SummaryBlockUi>,
    onOpenWebView: (String) -> Unit
) {
    StandardSummaryView(result = result, blocks = blocks, onOpenWebView = onOpenWebView)
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
    compareBlocks: CompareBlocksUi?,
    summaryResult: SummaryResult?
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
    val resultSources = summaryResult?.collectSourceLinks().orEmpty()
    return buildSourceLabelMap(summarySources + compareSources + resultSources)
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

private data class SingleSummaryContentUi(
    val title: String,
    val bullets: List<String>,
    val sources: List<SummarySourceLinkUi> = emptyList()
)

private data class QaSectionUi(
    val heading: String,
    val items: List<ThemeItem>
)

private fun SummaryResult.asCompareBlocksUi(): CompareBlocksUi? {
    val compare = this as? SummaryResult.Compare ?: return null
    return CompareBlocksUi(
        common = compare.common.map { CompareItemUi(text = it.text, sources = it.sources.map(SummarySourceRef::toLinkUi)) },
        different = compare.unique.map { CompareItemUi(text = it.text, sources = it.sources.map(SummarySourceRef::toLinkUi)) }
    )
}

private fun SummaryResult.asSummaryBlocksUi(): List<SummaryBlockUi> = when (this) {
    is SummaryResult.Digest -> themes.map { theme ->
        SummaryBlockUi.Theme(
            heading = theme.title,
            summary = theme.summary,
            items = theme.items.map { it.toThemeItem() }
        )
    }
    is SummaryResult.Error -> listOf(
        SummaryBlockUi.Section(
            body = message,
            sources = emptyList()
        )
    )
    else -> emptyList()
}

private fun SummaryResult.collectSourceLinks(): List<SummarySourceLinkUi> = when (this) {
    is SummaryResult.Single -> (points.flatMap { it.sources } + sources).distinct().map { it.toLinkUi() }
    is SummaryResult.Compare -> (common + unique).flatMap { it.sources }.distinct().map { it.toLinkUi() }
    is SummaryResult.Digest -> themes.flatMap(DigestTheme::items).flatMap { it.sources }.distinct().map { it.toLinkUi() }
    is SummaryResult.QA -> (details.flatMap { it.sources } + sources).distinct().map { it.toLinkUi() }
    is SummaryResult.Error -> emptyList()
}

private fun SummaryItem.toThemeItem(): ThemeItem =
    ThemeItem(marker = "—", text = text, sources = sources.map { it.toLinkUi() })

private fun SummarySourceRef.toLinkUi(): SummarySourceLinkUi =
    SummarySourceLinkUi(name = name, url = url)

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

@Composable
private fun CompareBlocksView(
    commonItems: List<CompareItemUi>,
    differentItems: List<CompareItemUi>,
    selectedTabIndex: Int,
    onSelectedTabIndexChange: (Int) -> Unit,
    sourceLabelMap: Map<String, String>,
    onOpenWebView: (String) -> Unit
) {
    if (differentItems.isEmpty()) {
        AppCardSurface(modifier = Modifier.fillMaxWidth()) {
            CompareBlockCardContent(
                items = commonItems,
                emptyMessage = stringResource(R.string.summary_compare_common_empty_local),
                sourceLabelMap = sourceLabelMap,
                onOpenWebView = onOpenWebView
            )
        }
        return
    }

    val tabTitles = listOf(
        stringResource(R.string.summary_compare_common),
        stringResource(R.string.summary_compare_unique)
    )
    val currentTab = selectedTabIndex.coerceIn(0, 1)
    val tabItems = if (currentTab == 0) commonItems else differentItems
    val emptyMessage = if (currentTab == 0) {
        stringResource(R.string.summary_compare_common_empty_local)
    } else {
        stringResource(R.string.summary_compare_unique_empty_local)
    }

    AppCardSurface(modifier = Modifier.fillMaxWidth()) {
        Column {
            TabRow(selectedTabIndex = currentTab) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = currentTab == index,
                        onClick = { onSelectedTabIndexChange(index) },
                        text = { Text(text = title) }
                    )
                }
            }
            CompareBlockCardContent(
                items = tabItems,
                emptyMessage = emptyMessage,
                sourceLabelMap = sourceLabelMap,
                onOpenWebView = onOpenWebView
            )
        }
    }
}

@Composable
private fun CompareBlockCardContent(
    items: List<CompareItemUi>,
    emptyMessage: String,
    sourceLabelMap: Map<String, String>,
    onOpenWebView: (String) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (items.isEmpty()) {
            InlineSummaryRow(
                text = "— $emptyMessage",
                sources = emptyList(),
                sourceLabelMap = sourceLabelMap,
                onOpenWebView = onOpenWebView,
                textStyle = MaterialTheme.typography.bodyLarge,
                lineHeight = 26.sp
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
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

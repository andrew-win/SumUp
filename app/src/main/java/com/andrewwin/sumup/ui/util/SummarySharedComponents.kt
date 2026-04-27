package com.andrewwin.sumup.ui.util

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.domain.usecase.ai.DigestTheme
import com.andrewwin.sumup.domain.usecase.ai.SummaryItem
import com.andrewwin.sumup.domain.usecase.ai.SummaryResult
import com.andrewwin.sumup.domain.usecase.ai.SummarySourceRef
import com.andrewwin.sumup.ui.components.AppCardSurface
import com.andrewwin.sumup.ui.theme.appCardBorder

enum class SummarySourceStyle {
    TextLink,
    InlineChip
}

private const val InlineSourceAnnotationTag = "summary_source"

@Composable
fun StandardSummaryView(
    result: SummaryResult?,
    blocks: List<SummaryBlockUi>,
    onOpenWebView: (String) -> Unit
) {
    val sourceLabelMap = rememberSourceLabelMap(
        summaryBlocks = blocks,
        compareBlocks = result?.asCompareBlocksUi(),
        summaryResult = result
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (result is SummaryResult.Digest) {
            result.themes.forEach { theme ->
                ThemeSummarySectionView(
                    heading = theme.title,
                    summary = theme.summary,
                    items = theme.items.map { it.toThemeItem() },
                    sourceLabelMap = sourceLabelMap,
                    onOpenWebView = onOpenWebView
                )
            }
        } else if (result is SummaryResult.Error) {
            LegacySummarySectionView(
                text = result.message,
                sources = emptyList(),
                sourceLabelMap = sourceLabelMap,
                onOpenWebView = onOpenWebView
            )
        } else {
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
}

@Composable
fun ThemeSummarySectionView(
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
fun LegacySummarySectionView(
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
fun PlainListSummarySectionView(
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
fun InlineSummaryRow(
    text: String,
    sources: List<SummarySourceLinkUi>,
    sourceLabelMap: Map<String, String>,
    onOpenWebView: (String) -> Unit,
    textStyle: TextStyle,
    lineHeight: TextUnit,
    sourceStyle: SummarySourceStyle = SummarySourceStyle.InlineChip
) {
    val effectiveStyle = textStyle.copy(lineHeight = lineHeight)
    val distinctSources = sources.distinctBy { it.key() }
    val chipTextStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp)
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    if (sourceStyle == SummarySourceStyle.InlineChip && distinctSources.isNotEmpty()) {
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
                val inlineId = "summary_inline_chip_$index"
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
                        border = appCardBorder(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
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

@Composable
fun rememberSourceLabelMap(
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

fun buildSourceLabelMap(sources: List<SummarySourceLinkUi>): Map<String, String> {
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

fun SummarySourceLinkUi.key(): String = "$name|$url"

data class CompareItemUi(val text: String, val sources: List<SummarySourceLinkUi>)
data class CompareBlocksUi(val common: List<CompareItemUi>, val different: List<CompareItemUi>)

fun SummaryResult.asCompareBlocksUi(): CompareBlocksUi? {
    val compare = this as? SummaryResult.Compare ?: return null
    return CompareBlocksUi(
        common = compare.common.map { CompareItemUi(text = it.text, sources = it.sources.map(SummarySourceRef::toLinkUi)) },
        different = compare.unique.map { CompareItemUi(text = it.text, sources = it.sources.map(SummarySourceRef::toLinkUi)) }
    )
}

fun SummaryResult.collectSourceLinks(): List<SummarySourceLinkUi> = when (this) {
    is SummaryResult.Single -> (points.flatMap { it.sources } + sources).distinct().map { it.toLinkUi() }
    is SummaryResult.Compare -> (common + unique).flatMap { it.sources }.distinct().map { it.toLinkUi() }
    is SummaryResult.Digest -> themes.flatMap(DigestTheme::items).flatMap { it.sources }.distinct().map { it.toLinkUi() }
    is SummaryResult.QA -> (details.flatMap { it.sources } + sources).distinct().map { it.toLinkUi() }
    is SummaryResult.Error -> emptyList()
}

fun SummaryItem.toThemeItem(): ThemeItem =
    ThemeItem(marker = "—", text = text, sources = sources.map { it.toLinkUi() })

fun SummarySourceRef.toLinkUi(): SummarySourceLinkUi =
    SummarySourceLinkUi(name = name, url = url)

fun SummaryResult.asSummaryBlocksUi(): List<SummaryBlockUi> = when (this) {
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

package com.andrewwin.sumup.ui.screen.summary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.andrewwin.sumup.ui.components.AppCardSurface
import com.andrewwin.sumup.ui.theme.appCardBorder
import com.andrewwin.sumup.ui.util.ThemeItem
import com.andrewwin.sumup.ui.util.normalizeSummaryUrlForWebView

internal enum class SummarySourceStyle {
    TextLink,
    InlineChip
}

internal const val InlineSourceAnnotationTag = "summary_source"

@Composable
internal fun SummaryLegacyBlock(
    text: String,
    sourceName: String?,
    sourceUrl: String?,
    onOpenWebView: (String) -> Unit,
    sourceStyle: SummarySourceStyle = SummarySourceStyle.InlineChip,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    lineHeight: TextUnit = 26.sp
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
                sourceName = sourceName,
                sourceUrl = sourceUrl,
                onOpenWebView = onOpenWebView,
                textStyle = textStyle,
                lineHeight = lineHeight,
                sourceStyle = sourceStyle
            )
        }
    }
}

@Composable
internal fun SummaryThemeBlock(
    heading: String,
    items: List<ThemeItem>,
    onOpenWebView: (String) -> Unit,
    sourceStyle: SummarySourceStyle = SummarySourceStyle.InlineChip,
    itemTextStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    itemLineHeight: TextUnit = 26.sp
) {
    AppCardSurface(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = heading,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            items.forEach { item ->
                InlineSummaryRow(
                    text = "${item.marker} ${item.text}",
                    sourceName = item.source?.name,
                    sourceUrl = item.source?.url,
                    onOpenWebView = onOpenWebView,
                    textStyle = itemTextStyle,
                    lineHeight = itemLineHeight,
                    sourceStyle = sourceStyle
                )
            }
        }
    }
}

@Composable
internal fun SummaryPlainListBlock(
    items: List<ThemeItem>,
    onOpenWebView: (String) -> Unit,
    sourceStyle: SummarySourceStyle = SummarySourceStyle.InlineChip,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    lineHeight: TextUnit = 26.sp
) {
    AppCardSurface(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { item ->
                InlineSummaryRow(
                    text = "${item.marker} ${item.text}",
                    sourceName = item.source?.name,
                    sourceUrl = item.source?.url,
                    onOpenWebView = onOpenWebView,
                    textStyle = textStyle,
                    lineHeight = lineHeight,
                    sourceStyle = sourceStyle
                )
            }
        }
    }
}

@Composable
private fun InlineSummaryRow(
    text: String,
    sourceName: String?,
    sourceUrl: String?,
    onOpenWebView: (String) -> Unit,
    textStyle: TextStyle,
    lineHeight: TextUnit,
    sourceStyle: SummarySourceStyle = SummarySourceStyle.InlineChip
) {
    val effectiveStyle = textStyle.copy(lineHeight = lineHeight)
    if (
        sourceStyle == SummarySourceStyle.InlineChip &&
        !sourceName.isNullOrBlank() &&
        !sourceUrl.isNullOrBlank()
    ) {
        val inlineId = "summary_inline_chip"
        val annotated = buildAnnotatedString {
            append(text)
            append(" ")
            appendInlineContent(inlineId, "[source]")
        }
        val chipWidthEm = ((sourceName.length.coerceAtMost(20) + 6) * 0.62f).em
        BasicText(
            text = annotated,
            style = effectiveStyle.copy(color = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.fillMaxWidth(),
            inlineContent = mapOf(
                inlineId to InlineTextContent(
                    Placeholder(
                        width = chipWidthEm,
                        height = 1.72.em,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                    )
                ) {
                    Surface(
                        onClick = { onOpenWebView(normalizeSummaryUrlForWebView(sourceUrl)) },
                        shape = RoundedCornerShape(11.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        border = appCardBorder(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
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
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            )
        )
    } else {
        val annotated = buildAnnotatedString {
            append(text)
            if (!sourceName.isNullOrBlank() && !sourceUrl.isNullOrBlank()) {
                append(" ")
                pushStringAnnotation(tag = InlineSourceAnnotationTag, annotation = sourceUrl)
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

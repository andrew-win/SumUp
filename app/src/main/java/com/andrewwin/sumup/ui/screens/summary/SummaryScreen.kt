package com.andrewwin.sumup.ui.screens.summary

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.Summary
import com.andrewwin.sumup.domain.summary.SummarySourceMeta
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    viewModel: SummaryViewModel = hiltViewModel(),
    onOpenWebView: (String) -> Unit = {}
) {
    val summaries by viewModel.summaries.collectAsState()
    val userPreferences by viewModel.userPreferences.collectAsState()
    val chartData by viewModel.chartData.collectAsState()
    val chartType by viewModel.chartType.collectAsState()
    val isVectorizationEnabled by viewModel.isVectorizationEnabled.collectAsState()

    val todaySummary = remember(summaries) {
        summaries.firstOrNull { isSameDay(it.createdAt, System.currentTimeMillis()) }
    }
    val olderSummaries = remember(summaries, todaySummary, userPreferences.showLastSummariesCount) {
        summaries
            .filter { it.id != todaySummary?.id }
            .take(userPreferences.showLastSummariesCount.coerceAtLeast(1))
    }
    val lastSummary = remember(summaries) { summaries.firstOrNull() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_summary)) },
                actions = {
                    FilledIconButton(
                        onClick = {},
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Блок 1: Список новин (графік)
            item {
                SummaryChart(
                    items = chartData,
                    currentType = chartType,
                    onTypeChange = viewModel::setChartType,
                    isModelEnabled = isVectorizationEnabled
                )
            }

            // Блок 2: Статус-рядок "Попереднє / Наступне" — місток між списком і зведенням
            item {
                PrevNextStatusRow(
                    previousSummaryAt = lastSummary?.createdAt,
                    isScheduledEnabled = userPreferences.isScheduledSummaryEnabled,
                    nextScheduledAt = if (userPreferences.isScheduledSummaryEnabled) {
                        getNextScheduledTimeMillis(
                            userPreferences.scheduledHour,
                            userPreferences.scheduledMinute
                        )
                    } else null
                )
            }

            // Блок 3: Поточне зведення (якщо є)
            if (todaySummary != null) {
                item {
                    SummaryCard(summary = todaySummary, onOpenWebView = onOpenWebView)
                }
            }

            // Блок 4: Історія
            if (olderSummaries.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.summary_history_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                items(olderSummaries, key = { it.id }) { summary ->
                    SummaryCard(summary = summary, onOpenWebView = onOpenWebView)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Графік — суцільна картка з роздільниками
// ─────────────────────────────────────────────

@Composable
fun SummaryChart(
    items: List<SummaryChartItem>,
    currentType: SummaryChartType,
    onTypeChange: (SummaryChartType) -> Unit,
    isModelEnabled: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChartTypeChip(
                selected = currentType == SummaryChartType.VIEWS,
                onClick = { onTypeChange(SummaryChartType.VIEWS) },
                label = stringResource(R.string.chart_views)
            )
            if (isModelEnabled) {
                ChartTypeChip(
                    selected = currentType == SummaryChartType.MENTIONS,
                    onClick = { onTypeChange(SummaryChartType.MENTIONS) },
                    label = stringResource(R.string.chart_mentions)
                )
            }
            ChartTypeChip(
                selected = currentType == SummaryChartType.FACTUALITY,
                onClick = { onTypeChange(SummaryChartType.FACTUALITY) },
                label = stringResource(R.string.chart_factuality)
            )
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.no_articles_prefix),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val maxValue = items.maxOfOrNull { it.value }?.takeIf { it > 0 } ?: 1f
                Column {
                    items.forEachIndexed { index, item ->
                        ChartBar(
                            item = item,
                            maxValue = maxValue,
                            isLast = index == items.lastIndex
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChartTypeChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = null
    )
}

@Composable
fun ChartBar(
    item: SummaryChartItem,
    maxValue: Float,
    isLast: Boolean
) {
    val fraction = (item.value / maxValue).coerceIn(0.05f, 1f)

    // Вертикальна смуга: інтенсивність кольору залежить від відносного значення
    val accentColor = lerp(
        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
        MaterialTheme.colorScheme.primary,
        fraction
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accentColor)
        )

        Spacer(Modifier.width(14.dp))

        Text(
            text = item.headline,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )

        Text(
            text = item.displayValue,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }

    if (!isLast) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    }
}

// ─────────────────────────────────────────────
// Статус-рядок: Попереднє | Наступне
// Місток між списком новин і картою зведення
// ─────────────────────────────────────────────

@Composable
fun PrevNextStatusRow(
    previousSummaryAt: Long?,
    isScheduledEnabled: Boolean,
    nextScheduledAt: Long?
) {
    val previousText = previousSummaryAt?.let { formatShortStatusDate(it) }
        ?: stringResource(R.string.summary_not_ready)
    val nextText = if (!isScheduledEnabled || nextScheduledAt == null) {
        stringResource(R.string.summary_scheduling_disabled)
    } else {
        formatShortStatusDate(nextScheduledAt)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.summary_previous_short),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = previousText,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.summary_next_short),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = nextText,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// Карта зведення
// ─────────────────────────────────────────────

@Composable
fun SummaryCard(summary: Summary, onOpenWebView: (String) -> Unit) {
    val dateFormat = remember { SimpleDateFormat("HH:mm, dd MMMM", Locale("uk", "UA")) }
    val isError = summary.content.startsWith(stringResource(R.string.error_prefix)) ||
            summary.content.startsWith(stringResource(R.string.no_articles_prefix))
    val context = LocalContext.current

    val sections = remember(summary.content) { parseSummarySections(summary.content) }
    var isExpanded by rememberSaveable(summary.id) { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = dateFormat.format(Date(summary.createdAt)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = if (isError)
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                else
                    MaterialTheme.colorScheme.surfaceContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    if (isExpanded && !isError) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                                .animateContentSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            sections.forEachIndexed { index, section ->
                                Text(
                                    text = section.body,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 24.sp
                                )
                                section.source?.let { source ->
                                    AssistChip(
                                        onClick = { onOpenWebView(normalizeForWebView(source.url)) },
                                        shape = RoundedCornerShape(14.dp),
                                        label = {
                                            Text(
                                                text = source.name,
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
                                if (index != sections.lastIndex) {
                                    Spacer(Modifier.height(6.dp))
                                }
                            }
                        }
                    } else {
                        Text(
                            text = sections.joinToString("\n\n") { it.body },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                                .animateContentSize(),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 24.sp
                        )
                    }

                    FilledIconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier
                            .size(30.dp)
                            .offset(x = 6.dp, y = (-6).dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            if (isExpanded) Icons.Default.KeyboardArrowUp
                            else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                copySummaryText(context, summary.content)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.summary_copied),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.size(30.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = { shareSummaryText(context, summary.content) },
                            modifier = Modifier.size(30.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    val strategyLabel = when (summary.strategy) {
                        AiStrategy.CLOUD -> stringResource(R.string.ai_strategy_cloud)
                        AiStrategy.LOCAL -> stringResource(R.string.ai_strategy_local)
                        AiStrategy.ADAPTIVE -> stringResource(R.string.ai_strategy_adaptive)
                    }
                    Text(
                        text = if (isError) stringResource(R.string.summary_system_notice)
                        else strategyLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Утиліти
// ─────────────────────────────────────────────

private fun isSameDay(t1: Long, t2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = t1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = t2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

private fun getNextScheduledTimeMillis(hour: Int, minute: Int): Long {
    val now = Calendar.getInstance()
    val next = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (next.timeInMillis <= now.timeInMillis) {
        next.add(Calendar.DAY_OF_YEAR, 1)
    }
    return next.timeInMillis
}

// We use a private lazy formatter for non-composable utility if needed, 
// or just create it when formatting a single value since it's not a hot loop for status.
private fun formatShortStatusDate(timestamp: Long): String {
    val format = SimpleDateFormat("HH:mm, dd MMM", Locale("uk", "UA"))
    return format.format(Date(timestamp))
}

private fun shareSummaryText(
    context: Context,
    summaryText: String
) {
    val cleaned = cleanSummaryText(summaryText)
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
    val clip = ClipData.newPlainText("summary_text", cleanSummaryText(summaryText))
    clipboardManager.setPrimaryClip(clip)
}

private fun cleanSummaryText(raw: String): String {
    return raw
        .lines()
        .filterNot { it.trim().startsWith(SummarySourceMeta.PREFIX) }
        .joinToString("\n")
}

private data class SourceMetaUi(val name: String, val url: String)
private data class SummarySection(val body: String, val source: SourceMetaUi?)

private fun parseSummarySections(raw: String): List<SummarySection> {
    val blocks = raw
        .split(Regex("\\n\\s*\\n"))
        .map { it.trim() }
        .filter { it.isNotBlank() }

    return blocks.map { block ->
        val lines = block.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
        val source = lines.lastOrNull()?.let { parseSourceMeta(it.trim()) }
        val bodyLines = if (source != null) lines.dropLast(1) else lines
        SummarySection(body = bodyLines.joinToString("\n"), source = source)
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

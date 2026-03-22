package com.andrewwin.sumup.ui.screens.summary

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.Summary
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    viewModel: SummaryViewModel = hiltViewModel()
) {
    val summaries by viewModel.summaries.collectAsState()
    val userPreferences by viewModel.userPreferences.collectAsState()
    val chartData by viewModel.chartData.collectAsState()
    val chartType by viewModel.chartType.collectAsState()
    
    val todaySummary = remember(summaries) {
        summaries.firstOrNull { isSameDay(it.createdAt, System.currentTimeMillis()) }
    }
    val olderSummaries = remember(summaries, todaySummary) {
        summaries.filter { it.id != todaySummary?.id }
    }

    val isModelEnabled = userPreferences.modelPath != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        stringResource(R.string.nav_summary)
                    ) 
                },
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
            item {
                SummaryChart(
                    items = chartData,
                    currentType = chartType,
                    onTypeChange = viewModel::setChartType,
                    isModelEnabled = isModelEnabled
                )
            }

            item {
                val statusText = if (userPreferences.isScheduledSummaryEnabled) {
                    val nextMillis = getNextScheduledTimeMillis(
                        userPreferences.scheduledHour,
                        userPreferences.scheduledMinute
                    )
                    val format = SimpleDateFormat("HH:mm, dd MMMM", Locale("uk", "UA"))
                    stringResource(R.string.summary_next_at, format.format(Date(nextMillis)))
                } else {
                    stringResource(R.string.summary_scheduling_disabled)
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    )
                ) {
                    Text(
                        text = statusText,
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (todaySummary != null) {
                item {
                    SummaryCard(summary = todaySummary)
                }
            }

            if (olderSummaries.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.summary_history_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(olderSummaries, key = { it.id }) { summary ->
                    SummaryCard(summary = summary)
                }
            }
        }
    }
}

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
            .padding(vertical = 8.dp)
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

        Spacer(Modifier.height(16.dp))

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
            items.forEach { item ->
                ChartBar(item, maxValue)
                Spacer(Modifier.height(12.dp))
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
fun ChartBar(item: SummaryChartItem, maxValue: Float) {
    val fraction = (item.value / maxValue).coerceIn(0.05f, 1f)
    val animatedWidth by animateFloatAsState(targetValue = fraction, label = "width")

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.headline,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
            Text(
                text = item.displayValue,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedWidth)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
fun SummaryCard(summary: Summary) {
    val dateFormat = remember { SimpleDateFormat("HH:mm, dd MMMM", Locale("uk", "UA")) }
    val isError = summary.content.startsWith(stringResource(R.string.error_prefix)) || 
                  summary.content.startsWith(stringResource(R.string.no_articles_prefix))
    
    var isExpanded by rememberSaveable(summary.id) { mutableStateOf(false) }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = dateFormat.format(Date(summary.createdAt)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
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
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = summary.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                            .animateContentSize(),
                        maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 26.sp
                    )
                    
                    FilledIconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(32.dp).offset(x = 8.dp, y = (-8).dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null
                        )
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    val strategyLabel = when (summary.strategy) {
                        AiStrategy.CLOUD -> stringResource(R.string.ai_strategy_cloud)
                        AiStrategy.EXTRACTIVE -> stringResource(R.string.ai_strategy_extractive)
                        AiStrategy.ADAPTIVE -> stringResource(R.string.ai_strategy_adaptive)
                    }
                    Text(
                        text = if (isError) stringResource(R.string.summary_system_notice) else strategyLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}

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

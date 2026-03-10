package com.andrewwin.sumup.ui.screens.summary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.andrewwin.sumup.R
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
    
    val todaySummary = remember(summaries) {
        summaries.firstOrNull { isSameDay(it.createdAt, System.currentTimeMillis()) }
    }
    val olderSummaries = remember(summaries) {
        summaries.filter { !isSameDay(it.createdAt, System.currentTimeMillis()) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        stringResource(R.string.nav_summary),
                        style = MaterialTheme.typography.titleLarge
                    ) 
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null)
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            item {
                StatusCard(
                    isEnabled = userPreferences.isScheduledSummaryEnabled,
                    hour = userPreferences.scheduledHour,
                    minute = userPreferences.scheduledMinute,
                    hasTodaySummary = todaySummary != null
                )
            }

            if (todaySummary != null) {
                item {
                    SummaryCard(summary = todaySummary)
                }
            } else if (userPreferences.isScheduledSummaryEnabled) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.summary_not_ready),
                            modifier = Modifier.padding(24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (olderSummaries.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.summary_history_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Normal
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
fun StatusCard(isEnabled: Boolean, hour: Int, minute: Int, hasTodaySummary: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
                val timeText = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                Text(
                    text = if (isEnabled)
                        stringResource(R.string.summary_next_at, timeText)
                    else
                        stringResource(R.string.summary_scheduling_disabled),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = if (hasTodaySummary)
                        stringResource(R.string.summary_status_ready)
                    else
                        stringResource(R.string.summary_status_waiting),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun SummaryCard(summary: Summary) {
    val dateFormat = remember { SimpleDateFormat("HH:mm, dd MMMM", Locale("uk", "UA")) }
    val isError = summary.content.startsWith(stringResource(R.string.error_prefix)) || 
                  summary.content.startsWith(stringResource(R.string.no_articles_prefix))
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) 
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) 
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = dateFormat.format(Date(summary.createdAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = summary.content,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (isError) stringResource(R.string.summary_system_notice) else stringResource(R.string.summary_cloud_type),
                style = MaterialTheme.typography.labelSmall,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

private fun isSameDay(t1: Long, t2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = t1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = t2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

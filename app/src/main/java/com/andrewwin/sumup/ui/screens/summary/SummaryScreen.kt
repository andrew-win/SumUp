package com.andrewwin.sumup.ui.screens.summary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.WorkInfo
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.Summary
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    viewModel: SummaryViewModel = viewModel()
) {
    val summaries by viewModel.summaries.collectAsState()
    val userPreferences by viewModel.userPreferences.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val workInfos by viewModel.workInfo.collectAsState()
    
    val todaySummary = remember(summaries) {
        summaries.firstOrNull { isSameDay(it.createdAt, System.currentTimeMillis()) }
    }
    val olderSummaries = remember(summaries) {
        summaries.filter { !isSameDay(it.createdAt, System.currentTimeMillis()) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_summary)) },
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
                Text(
                    text = "Зведення",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                val workState = workInfos.firstOrNull()?.state?.name ?: "Немає в черзі"
                val lastRun = if (userPreferences.lastWorkRunTimestamp > 0) {
                    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(userPreferences.lastWorkRunTimestamp))
                } else "Ще не запускався"

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                val timeText = String.format(Locale.getDefault(), "%02d:%02d", userPreferences.scheduledHour, userPreferences.scheduledMinute)
                                Text(
                                    text = if (userPreferences.isScheduledSummaryEnabled) 
                                        "Наступне о $timeText" 
                                    else "Планування вимкнено",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Статус системи: $workState • Старт: $lastRun",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.generateSummaryNow() },
                                modifier = Modifier.weight(1f),
                                enabled = !isGenerating,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                if (isGenerating) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                } else {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Згенерувати")
                                }
                            }
                            
                            OutlinedButton(
                                onClick = { viewModel.testWorkerNow() },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(Icons.Default.BugReport, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Тест фону")
                            }
                        }
                    }
                }
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
                            text = "Сьогодні зведення ще не було. Очікуємо на виконання задачі...",
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
                        text = "Згенеровано раніше:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
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
fun SummaryCard(summary: Summary) {
    val dateFormat = remember { SimpleDateFormat("HH:mm, dd MMMM", Locale("uk", "UA")) }
    val isError = summary.content.startsWith("Помилка") || summary.content.startsWith("Немає")
    
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
                text = if (isError) "Системне сповіщення" else "Хмарна сумаризація",
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

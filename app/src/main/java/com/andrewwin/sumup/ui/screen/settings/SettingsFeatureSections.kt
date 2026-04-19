package com.andrewwin.sumup.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.UserPreferences
import java.util.Locale

@Composable
fun ScheduledSummarySettingsSection(
    showTitle: Boolean = true,
    userPreferences: UserPreferences,
    showLastSummariesCount: Float,
    onShowLastSummariesCountChange: (Float) -> Unit,
    onShowLastSummariesCountCommitted: () -> Unit,
    showInfographicNewsCount: Float,
    onShowInfographicNewsCountChange: (Float) -> Unit,
    onShowInfographicNewsCountCommitted: () -> Unit,
    onScheduledSummaryToggle: (Boolean) -> Unit,
    onScheduledPushToggle: (Boolean) -> Unit,
    onPickTime: () -> Unit
) {
    SettingsSection(
        title = if (showTitle) stringResource(R.string.settings_scheduled_summary) else "",
        boxed = true
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.settings_scheduled_summary),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = userPreferences.isScheduledSummaryEnabled,
                    onCheckedChange = onScheduledSummaryToggle,
                    modifier = Modifier.scale(SETTINGS_SWITCH_SCALE)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.settings_scheduled_push_notifications),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = userPreferences.isScheduledSummaryPushEnabled,
                    onCheckedChange = onScheduledPushToggle,
                    modifier = Modifier.scale(SETTINGS_SWITCH_SCALE)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = userPreferences.isScheduledSummaryEnabled) { onPickTime() }
                    .padding(vertical = 8.dp)
            ) {
                Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                val timeText = String.format(
                    Locale.getDefault(),
                    "%02d:%02d",
                    userPreferences.scheduledHour,
                    userPreferences.scheduledMinute
                )
                Text(
                    text = stringResource(R.string.settings_time_label, timeText),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Column {
                Text(
                    stringResource(
                        R.string.settings_show_last_summaries_count,
                        showLastSummariesCount.toInt()
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
                Slider(
                    value = showLastSummariesCount,
                    onValueChange = onShowLastSummariesCountChange,
                    onValueChangeFinished = onShowLastSummariesCountCommitted,
                    valueRange = 1f..20f,
                    steps = 18
                )
            }

            Column {
                Text(
                    stringResource(
                        R.string.settings_show_infographic_news_count,
                        showInfographicNewsCount.toInt()
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
                Slider(
                    value = showInfographicNewsCount,
                    onValueChange = onShowInfographicNewsCountChange,
                    onValueChangeFinished = onShowInfographicNewsCountCommitted,
                    valueRange = 1f..10f,
                    steps = 8
                )
            }
        }
    }
}

@Composable
fun SourcesSettingsSection(
    showTitle: Boolean = true,
    isRecommendationsEnabled: Boolean,
    onRecommendationsToggle: (Boolean) -> Unit
) {
    SettingsSection(
        title = if (showTitle) stringResource(R.string.settings_sources) else "",
        boxed = true
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.settings_show_recommendations),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = isRecommendationsEnabled,
                onCheckedChange = onRecommendationsToggle,
                modifier = Modifier.scale(SETTINGS_SWITCH_SCALE)
            )
        }
    }
}

@Composable
fun MemorySettingsSection(
    showTitle: Boolean = true,
    articleAutoCleanupDays: Int,
    onArticleAutoCleanupDaysChange: (Int) -> Unit,
    onClearArticles: () -> Unit,
    onClearEmbeddings: () -> Unit,
    onClearScheduledSummaries: () -> Unit,
    onResetSettings: () -> Unit
) {
    SettingsSection(
        title = if (showTitle) stringResource(R.string.settings_memory) else "",
        boxed = true
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.settings_article_auto_cleanup_interval),
                style = MaterialTheme.typography.bodyLarge
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AutoCleanupChip(
                        days = 1,
                        articleAutoCleanupDays = articleAutoCleanupDays,
                        onArticleAutoCleanupDaysChange = onArticleAutoCleanupDaysChange,
                        modifier = Modifier.weight(1f)
                    )
                    AutoCleanupChip(
                        days = 3,
                        articleAutoCleanupDays = articleAutoCleanupDays,
                        onArticleAutoCleanupDaysChange = onArticleAutoCleanupDaysChange,
                        modifier = Modifier.weight(1f)
                    )
                    AutoCleanupChip(
                        days = 5,
                        articleAutoCleanupDays = articleAutoCleanupDays,
                        onArticleAutoCleanupDaysChange = onArticleAutoCleanupDaysChange,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AutoCleanupChip(
                        days = 7,
                        articleAutoCleanupDays = articleAutoCleanupDays,
                        onArticleAutoCleanupDaysChange = onArticleAutoCleanupDaysChange,
                        modifier = Modifier.weight(1f)
                    )
                    AutoCleanupChip(
                        days = 10,
                        articleAutoCleanupDays = articleAutoCleanupDays,
                        onArticleAutoCleanupDaysChange = onArticleAutoCleanupDaysChange,
                        modifier = Modifier.weight(1f)
                    )
                    AutoCleanupChip(
                        days = 30,
                        articleAutoCleanupDays = articleAutoCleanupDays,
                        onArticleAutoCleanupDaysChange = onArticleAutoCleanupDaysChange,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Button(
                onClick = onClearArticles,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            ) {
                Text(
                    text = stringResource(R.string.settings_clear_articles),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Button(
                onClick = onClearEmbeddings,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            ) {
                Text(
                    text = stringResource(R.string.settings_clear_embeddings),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Button(
                onClick = onClearScheduledSummaries,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            ) {
                Text(
                    text = stringResource(R.string.settings_clear_scheduled_summaries),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Button(
                onClick = onResetSettings,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            ) {
                Text(
                    text = stringResource(R.string.settings_reset_settings),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun AutoCleanupChip(
    days: Int,
    articleAutoCleanupDays: Int,
    onArticleAutoCleanupDaysChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = articleAutoCleanupDays == days,
        onClick = { onArticleAutoCleanupDaysChange(days) },
        label = {
            Text(
                text = stringResource(
                    when (days) {
                        1 -> R.string.settings_article_auto_cleanup_1_day
                        3 -> R.string.settings_article_auto_cleanup_3_days
                        5 -> R.string.settings_article_auto_cleanup_5_days
                        7 -> R.string.settings_article_auto_cleanup_7_days
                        10 -> R.string.settings_article_auto_cleanup_10_days
                        else -> R.string.settings_article_auto_cleanup_30_days
                    }
                )
            )
        },
        modifier = modifier
    )
}


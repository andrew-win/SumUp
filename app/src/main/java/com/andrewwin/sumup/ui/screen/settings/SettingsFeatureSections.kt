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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.andrewwin.sumup.R
import com.andrewwin.sumup.domain.settings.model.UserSettings
import java.util.Locale

@Composable
fun ScheduledSummarySettingsSection(
    showTitle: Boolean = true,
    isHelpMode: Boolean = false,
    userPreferences: UserSettings,
    showInfographicNewsCount: Float,
    onShowInfographicNewsCountChange: (Float) -> Unit,
    onShowInfographicNewsCountCommitted: () -> Unit,
    onScheduledSummaryToggle: (Boolean) -> Unit,
    onScheduledPushToggle: (Boolean) -> Unit,
    onAddTime: () -> Unit,
    onEditTime: (Int) -> Unit,
    onRemoveTime: (Int) -> Unit,
    onHelpRequest: (String) -> Unit = {}
) {
    val addScheduledTimeContentDescription = stringResource(R.string.settings_add_scheduled_time)
    SettingsSection(
        title = if (showTitle) stringResource(R.string.settings_scheduled_summary) else "",
        boxed = true,
        isHelpMode = isHelpMode
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingsToggleRow(
                label = stringResource(R.string.settings_scheduled_summary),
                checked = userPreferences.isScheduledSummaryEnabled,
                onCheckedChange = onScheduledSummaryToggle,
                isHelpMode = isHelpMode,
                helpDescription = stringResource(R.string.settings_help_scheduled_enabled),
                onHelpRequest = onHelpRequest
            )

            SettingsToggleRow(
                label = stringResource(R.string.settings_scheduled_push_notifications),
                checked = userPreferences.isScheduledSummaryPushEnabled,
                onCheckedChange = onScheduledPushToggle,
                isHelpMode = isHelpMode,
                helpDescription = stringResource(R.string.settings_help_scheduled_push),
                onHelpRequest = onHelpRequest
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_times_label),
                    style = MaterialTheme.typography.titleSmall
                )

                userPreferences.scheduledSummaryTimeList.forEachIndexed { index, time ->
                    ScheduledSummaryTimeRow(
                        isHelpMode = isHelpMode,
                        timeText = String.format(Locale.getDefault(), "%02d:%02d", time.hour, time.minute),
                        enabled = userPreferences.isScheduledSummaryEnabled,
                        canRemove = userPreferences.scheduledSummaryTimeList.size > 1,
                        onClick = { onEditTime(index) },
                        onRemove = { onRemoveTime(index) },
                        onHelpRequest = onHelpRequest
                    )
                }

                SettingsHelpTarget(
                    isHelpMode = isHelpMode,
                    helpDescription = stringResource(R.string.settings_help_scheduled_add_time),
                    onHelpRequest = onHelpRequest,
                    contentDescription = addScheduledTimeContentDescription
                ) {
                    Button(
                        onClick = onAddTime,
                        enabled = userPreferences.isScheduledSummaryEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = addScheduledTimeContentDescription
                            },
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = addScheduledTimeContentDescription,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(addScheduledTimeContentDescription)
                    }
                }
            }

            SettingsHelpTarget(
                isHelpMode = isHelpMode,
                helpDescription = stringResource(R.string.settings_help_scheduled_background_note),
                onHelpRequest = onHelpRequest,
                contentDescription = stringResource(R.string.settings_scheduled_background_recommendation)
            ) {
                Text(
                    text = stringResource(R.string.settings_scheduled_background_recommendation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SettingsIntSliderItem(
                label = stringResource(
                    R.string.settings_show_infographic_news_count,
                    showInfographicNewsCount.toInt()
                ),
                value = showInfographicNewsCount,
                onValueChange = onShowInfographicNewsCountChange,
                onValueChangeFinished = onShowInfographicNewsCountCommitted,
                valueRange = 1f..20f,
                steps = 18,
                isHelpMode = isHelpMode,
                helpDescription = stringResource(R.string.settings_help_scheduled_infographic_count),
                onHelpRequest = onHelpRequest
            )
        }
    }
}

@Composable
private fun ScheduledSummaryTimeRow(
    isHelpMode: Boolean,
    timeText: String,
    enabled: Boolean,
    canRemove: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onHelpRequest: (String) -> Unit
) {
    SettingsHelpTarget(
        isHelpMode = isHelpMode,
        helpDescription = stringResource(R.string.settings_help_scheduled_time_row),
        onHelpRequest = onHelpRequest,
        contentDescription = stringResource(R.string.settings_time_label, timeText)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(vertical = 8.dp)
        ) {
            Icon(
                Icons.Default.AccessTime,
                contentDescription = stringResource(R.string.settings_time_label, timeText),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.settings_time_label, timeText),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onRemove,
                enabled = enabled && canRemove
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.settings_remove_scheduled_time)
                )
            }
        }
    }
}

@Composable
fun SourcesSettingsSection(
    showTitle: Boolean = true,
    isRecommendationsEnabled: Boolean,
    onRecommendationsToggle: (Boolean) -> Unit,
    isHelpMode: Boolean = false,
    onHelpRequest: (String) -> Unit = {}
) {
    SettingsSection(
        title = if (showTitle) stringResource(R.string.settings_sources) else "",
        boxed = true,
        isHelpMode = isHelpMode
    ) {
        SettingsToggleRow(
            label = stringResource(R.string.settings_show_recommendations),
            checked = isRecommendationsEnabled,
            onCheckedChange = onRecommendationsToggle,
            isHelpMode = isHelpMode,
            helpDescription = stringResource(R.string.settings_help_recommendations_toggle),
            onHelpRequest = onHelpRequest
        )
    }
}

@Composable
fun MemorySettingsSection(
    showTitle: Boolean = true,
    isHelpMode: Boolean = false,
    articleAutoCleanupHours: Int,
    onArticleAutoCleanupHoursChange: (Int) -> Unit,
    onClearArticles: () -> Unit,
    onClearEmbeddings: () -> Unit,
    onClearScheduledSummaries: () -> Unit,
    onResetSettings: () -> Unit,
    onHelpRequest: (String) -> Unit = {}
) {
    SettingsSection(
        title = if (showTitle) stringResource(R.string.settings_memory) else "",
        boxed = true,
        isHelpMode = isHelpMode
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsIntSliderItem(
                label = stringResource(
                    R.string.settings_article_auto_cleanup_interval_hours,
                    articleAutoCleanupHours
                ),
                value = articleAutoCleanupHours.toFloat(),
                onValueChange = { onArticleAutoCleanupHoursChange(it.toInt()) },
                onValueChangeFinished = {},
                valueRange = UserSettings.MIN_ARTICLE_AUTO_CLEANUP_HOURS.toFloat()..
                    UserSettings.MAX_ARTICLE_AUTO_CLEANUP_HOURS.toFloat(),
                steps = UserSettings.MAX_ARTICLE_AUTO_CLEANUP_HOURS -
                    UserSettings.MIN_ARTICLE_AUTO_CLEANUP_HOURS - 1,
                isHelpMode = isHelpMode,
                helpDescription = stringResource(R.string.settings_help_memory_auto_cleanup),
                onHelpRequest = onHelpRequest
            )

            MemoryActionButton(
                label = stringResource(R.string.settings_clear_articles),
                helpDescription = stringResource(R.string.settings_help_memory_clear_articles),
                isHelpMode = isHelpMode,
                onHelpRequest = onHelpRequest,
                onClick = onClearArticles
            )

            MemoryActionButton(
                label = stringResource(R.string.settings_clear_embeddings),
                helpDescription = stringResource(R.string.settings_help_memory_clear_embeddings),
                isHelpMode = isHelpMode,
                onHelpRequest = onHelpRequest,
                onClick = onClearEmbeddings
            )

            MemoryActionButton(
                label = stringResource(R.string.settings_clear_scheduled_summaries),
                helpDescription = stringResource(R.string.settings_help_memory_clear_summaries),
                isHelpMode = isHelpMode,
                onHelpRequest = onHelpRequest,
                onClick = onClearScheduledSummaries
            )

            MemoryActionButton(
                label = stringResource(R.string.settings_reset_settings),
                helpDescription = stringResource(R.string.settings_help_memory_reset_settings),
                isHelpMode = isHelpMode,
                onHelpRequest = onHelpRequest,
                onClick = onResetSettings
            )
        }
    }
}

@Composable
private fun MemoryActionButton(
    label: String,
    helpDescription: String,
    isHelpMode: Boolean,
    onHelpRequest: (String) -> Unit,
    onClick: () -> Unit
) {
    SettingsHelpTarget(
        isHelpMode = isHelpMode,
        helpDescription = helpDescription,
        onHelpRequest = onHelpRequest,
        contentDescription = label
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .semantics {
                    contentDescription = label
                },
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

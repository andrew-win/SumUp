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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.UserPreferences
import java.util.Locale

@Composable
fun ScheduledSummarySettingsSection(
    showTitle: Boolean = true,
    isHelpMode: Boolean = false,
    userPreferences: UserPreferences,
    showInfographicNewsCount: Float,
    onShowInfographicNewsCountChange: (Float) -> Unit,
    onShowInfographicNewsCountCommitted: () -> Unit,
    onScheduledSummaryToggle: (Boolean) -> Unit,
    onScheduledPushToggle: (Boolean) -> Unit,
    onPickTime: () -> Unit,
    onHelpRequest: (String) -> Unit = {}
) {
    SettingsSection(
        title = if (showTitle) stringResource(R.string.settings_scheduled_summary) else "",
        boxed = true,
        isHelpMode = isHelpMode,
        helpDescription = stringResource(R.string.settings_help_section_scheduled_summary),
        onHelpRequest = onHelpRequest
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingsToggleRow(
                label = stringResource(R.string.settings_scheduled_summary),
                checked = userPreferences.isScheduledSummaryEnabled,
                onCheckedChange = onScheduledSummaryToggle
            )

            SettingsToggleRow(
                label = stringResource(R.string.settings_scheduled_push_notifications),
                checked = userPreferences.isScheduledSummaryPushEnabled,
                onCheckedChange = onScheduledPushToggle
            )

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

            Text(
                text = stringResource(R.string.settings_scheduled_background_recommendation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SettingsIntSliderItem(
                label = stringResource(
                    R.string.settings_show_infographic_news_count,
                    showInfographicNewsCount.toInt()
                ),
                value = showInfographicNewsCount,
                onValueChange = onShowInfographicNewsCountChange,
                onValueChangeFinished = onShowInfographicNewsCountCommitted,
                valueRange = 1f..10f,
                steps = 8
            )
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
        isHelpMode = isHelpMode,
        helpDescription = stringResource(R.string.settings_help_section_recommendations),
        onHelpRequest = onHelpRequest
    ) {
        SettingsToggleRow(
            label = stringResource(R.string.settings_show_recommendations),
            checked = isRecommendationsEnabled,
            onCheckedChange = onRecommendationsToggle
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
        isHelpMode = isHelpMode,
        helpDescription = stringResource(R.string.settings_help_section_memory),
        onHelpRequest = onHelpRequest
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
                valueRange = UserPreferences.MIN_ARTICLE_AUTO_CLEANUP_HOURS.toFloat()..
                    UserPreferences.MAX_ARTICLE_AUTO_CLEANUP_HOURS.toFloat(),
                steps = UserPreferences.MAX_ARTICLE_AUTO_CLEANUP_HOURS -
                    UserPreferences.MIN_ARTICLE_AUTO_CLEANUP_HOURS - 1
            )

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

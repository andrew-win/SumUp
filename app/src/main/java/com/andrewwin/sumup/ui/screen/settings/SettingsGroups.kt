package com.andrewwin.sumup.ui.screen.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andrewwin.sumup.R

internal sealed class SettingsGroupIcon {
    data class Vector(val imageVector: ImageVector) : SettingsGroupIcon()
    data class Drawable(@DrawableRes val resId: Int) : SettingsGroupIcon()
}

internal enum class SettingsGroup(
    val titleRes: Int,
    val descriptionRes: Int,
    val icon: SettingsGroupIcon
) {
    ACCOUNT(
        R.string.settings_group_account,
        R.string.settings_group_account_desc,
        SettingsGroupIcon.Vector(Icons.Default.AccountCircle)
    ),
    GENERAL(
        R.string.settings_group_general,
        R.string.settings_group_general_desc,
        SettingsGroupIcon.Vector(Icons.Default.Language)
    ),
    API_KEYS(
        R.string.settings_group_api_keys,
        R.string.settings_group_api_keys_desc,
        SettingsGroupIcon.Vector(Icons.Default.VpnKey)
    ),
    AI_PROCESSING(
        R.string.settings_group_ai_processing,
        R.string.settings_group_ai_processing_desc,
        SettingsGroupIcon.Drawable(R.drawable.ic_ask_ai)
    ),
    FEED(
        R.string.settings_group_feed,
        R.string.settings_group_feed_desc,
        SettingsGroupIcon.Drawable(R.drawable.ic_feed_page)
    ),
    SCHEDULED_SUMMARY(
        R.string.settings_group_scheduled,
        R.string.settings_group_scheduled_desc,
        SettingsGroupIcon.Vector(Icons.Default.Schedule)
    ),
    RECOMMENDATIONS(
        R.string.settings_group_recommendations,
        R.string.settings_group_recommendations_desc,
        SettingsGroupIcon.Drawable(R.drawable.ic_recommend)
    ),
    MEMORY(
        R.string.settings_group_memory,
        R.string.settings_group_memory_desc,
        SettingsGroupIcon.Vector(Icons.Default.Storage)
    )
}

@Composable
internal fun SettingsGroupsPanel(
    groups: List<SettingsGroup>,
    onGroupClick: (SettingsGroup) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column {
            groups.forEachIndexed { index, group ->
                SettingsGroupRow(
                    group = group,
                    onClick = { onGroupClick(group) }
                )
                if (index < groups.lastIndex) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsGroupRow(
    group: SettingsGroup,
    onClick: () -> Unit
) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        when (val icon = group.icon) {
            is SettingsGroupIcon.Vector -> Icon(
                imageVector = icon.imageVector,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            is SettingsGroupIcon.Drawable -> Icon(
                painter = painterResource(icon.resId),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(group.titleRes),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(group.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

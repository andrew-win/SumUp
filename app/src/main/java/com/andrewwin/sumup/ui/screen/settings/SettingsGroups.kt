package com.andrewwin.sumup.ui.screen.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VpnKey
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
import com.andrewwin.sumup.ui.theme.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.ChevronRight

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
    isHelpMode: Boolean = false,
    onGroupClick: (SettingsGroup) -> Unit,
    onHelpRequest: (SettingsGroup) -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            groups.forEachIndexed { index, group ->
                SettingsGroupRow(
                    group = group,
                    onClick = {
                        if (isHelpMode) onHelpRequest(group) else onGroupClick(group)
                    }
                )
                if (index < groups.size - 1) {
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.padding(start = 70.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun getIconColors(group: SettingsGroup): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    return when (group) {
        SettingsGroup.ACCOUNT -> if (isDark) IconBlueDark to IconBgBlueDark else IconBlueLight to IconBgBlueLight
        SettingsGroup.AI_PROCESSING -> if (isDark) IconOrangeDark to IconBgOrangeDark else IconOrangeLight to IconBgOrangeLight
        SettingsGroup.API_KEYS -> if (isDark) IconGreenDark to IconBgGreenDark else IconGreenLight to IconBgGreenLight
        SettingsGroup.RECOMMENDATIONS -> if (isDark) IconPurpleDark to IconBgPurpleDark else IconPurpleLight to IconBgPurpleLight
        else -> if (isDark) IconGreyDark to IconBgGreyDark else IconGreyLight to IconBgGreyLight
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
        val (iconTint, iconBg) = getIconColors(group)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            when (val icon = group.icon) {
                is SettingsGroupIcon.Vector -> Icon(
                    imageVector = icon.imageVector,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = iconTint
                )
                is SettingsGroupIcon.Drawable -> Icon(
                    painter = painterResource(icon.resId),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = iconTint
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(group.titleRes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(group.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

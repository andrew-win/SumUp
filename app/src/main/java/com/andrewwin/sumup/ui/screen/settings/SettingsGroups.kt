package com.andrewwin.sumup.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andrewwin.sumup.ui.components.AppCardSurface
import com.andrewwin.sumup.ui.components.AppHelpOverlayTarget
import com.andrewwin.sumup.ui.screen.settings.model.SettingsGroup
import com.andrewwin.sumup.ui.screen.settings.model.SettingsGroupIcon
import com.andrewwin.sumup.ui.theme.appBorderColor

@Composable
internal fun SettingsGroupsPanel(
    groups: List<SettingsGroup>,
    isHelpMode: Boolean = false,
    onGroupClick: (SettingsGroup) -> Unit,
    onHelpRequest: (SettingsGroup) -> Unit = {},
    helpDescriptionForGroup: (SettingsGroup) -> String = { "" }
) {
    AppCardSurface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            groups.forEachIndexed { index, group ->
                AppHelpOverlayTarget(
                    isEnabled = isHelpMode,
                    description = helpDescriptionForGroup(group),
                    onShowDescription = { onHelpRequest(group) }
                ) {
                    SettingsGroupRow(
                        group = group,
                        onClick = {
                            if (isHelpMode) onHelpRequest(group) else onGroupClick(group)
                        }
                    )
                }
                if (index < groups.size - 1) {
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.padding(start = 70.dp),
                        thickness = 0.5.dp,
                        color = appBorderColor()
                    )
                }
            }
        }
    }
}

@Composable
private fun getIconColors(): Pair<Color, Color> {
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = colorScheme.background.luminance() < 0.5f
    return if (isDarkTheme) {
        colorScheme.primary.copy(alpha = 0.82f) to colorScheme.primary.copy(alpha = 0.12f)
    } else {
        colorScheme.onPrimaryContainer to colorScheme.primaryContainer
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
        val (iconTint, iconBg) = getIconColors()
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
    }
}

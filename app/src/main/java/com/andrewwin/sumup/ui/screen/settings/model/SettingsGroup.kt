package com.andrewwin.sumup.ui.screen.settings.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VpnKey
import com.andrewwin.sumup.R

enum class SettingsGroup(
    val titleRes: Int,
    val descriptionRes: Int,
    val icon: SettingsGroupIcon
) {
    ACCOUNT(
        R.string.settings_group_account,
        R.string.settings_group_account_desc,
        SettingsGroupIcon.Vector(Icons.Default.AccountCircle)
    ),
    TRANSFER(
        R.string.settings_group_transfer,
        R.string.settings_group_transfer_desc,
        SettingsGroupIcon.Vector(Icons.Default.Sync)
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

package com.andrewwin.sumup.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Feed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Source
import androidx.compose.ui.graphics.vector.ImageVector
import com.andrewwin.sumup.R

sealed class Screen(
    val route: String,
    @StringRes val resourceId: Int,
    val icon: ImageVector
) {
    data object Summary : Screen("summary", R.string.nav_summary, Icons.Default.AutoAwesome)
    data object Feed : Screen("feed", R.string.nav_feed, Icons.Default.Feed)
    data object Sources : Screen("sources", R.string.nav_sources, Icons.Default.Source)
    data object Settings : Screen("settings", R.string.nav_settings, Icons.Default.Settings)
}

val navItems = listOf(
    Screen.Summary,
    Screen.Feed,
    Screen.Sources,
    Screen.Settings
)

package com.andrewwin.sumup.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Feed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Source
import androidx.compose.ui.graphics.vector.ImageVector
import com.andrewwin.sumup.R
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class NavigationIcon {
    data class Vector(val imageVector: ImageVector) : NavigationIcon()
    data class Custom(@DrawableRes val resId: Int) : NavigationIcon()
}

sealed class Screen(
    val route: String,
    @StringRes val resourceId: Int,
    val icon: NavigationIcon
) {
    data object Summary : Screen(
        route = "summary",
        resourceId = R.string.nav_summary,
        icon = NavigationIcon.Custom(R.drawable.ic_summary_page)
    )

    data object Feed : Screen(
        route = "feed",
        resourceId = R.string.nav_feed,
        icon = NavigationIcon.Custom(R.drawable.ic_feed_page)
    )

    data object Sources : Screen(
        route = "sources",
        resourceId = R.string.nav_sources,
        icon = NavigationIcon.Vector(Icons.Default.Source)
    )

    data object Settings : Screen(
        route = "settings",
        resourceId = R.string.nav_settings,
        icon = NavigationIcon.Vector(Icons.Default.Settings)
    )

    data object WebView : Screen(
        route = "webview/{url}",
        resourceId = R.string.nav_webview,
        icon = NavigationIcon.Vector(Icons.Default.Feed)
    ) {
        fun createRoute(url: String): String {
            val encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
            return "webview/$encodedUrl"
        }
    }
}

val navItems = listOf(
    Screen.Summary,
    Screen.Feed,
    Screen.Sources,
    Screen.Settings
)

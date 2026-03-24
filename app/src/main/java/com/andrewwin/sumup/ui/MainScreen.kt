package com.andrewwin.sumup.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.andrewwin.sumup.ui.screens.feed.FeedScreen
import com.andrewwin.sumup.ui.screens.settings.SettingsScreen
import com.andrewwin.sumup.ui.screens.sources.SourcesScreen
import com.andrewwin.sumup.ui.screens.summary.SummaryScreen
import com.andrewwin.sumup.ui.screens.webview.WebViewScreen
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            navItems.forEach { screen ->
                val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                item(
                    icon = {
                        when (val icon = screen.icon) {
                            is NavigationIcon.Vector -> Icon(
                                imageVector = icon.imageVector,
                                contentDescription = null
                            )
                            is NavigationIcon.Custom -> Icon(
                                painter = painterResource(icon.resId),
                                contentDescription = null
                            )
                        }
                    },
                    label = { 
                        Text(
                            text = stringResource(screen.resourceId),
                            style = MaterialTheme.typography.labelMedium
                        ) 
                    },
                    selected = selected,
                    onClick = {
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        navigationSuiteColors = androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults.colors(
            navigationBarContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            navigationBarContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Box {
            NavHost(
                navController = navController,
                startDestination = Screen.Summary.route,
            ) {
        composable(Screen.Summary.route) {
            SummaryScreen(
                onOpenWebView = { url ->
                    navController.navigate(Screen.WebView.createRoute(url))
                }
            )
        }
                composable(Screen.Feed.route) {
                    FeedScreen(
                        onOpenWebView = { url ->
                            navController.navigate(Screen.WebView.createRoute(url))
                        }
                    )
                }
                composable(Screen.Sources.route) {
                    SourcesScreen()
                }
                composable(Screen.Settings.route) {
                    SettingsScreen()
                }
                composable(
                    route = Screen.WebView.route,
                    arguments = listOf(navArgument("url") { type = NavType.StringType })
                ) { backStackEntry ->
                    val encodedUrl = backStackEntry.arguments?.getString("url") ?: ""
                    val url = URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.toString())
                    WebViewScreen(
                        url = url,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

package com.andrewwin.sumup.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.andrewwin.sumup.ui.screens.feed.FeedScreen
import com.andrewwin.sumup.ui.screens.settings.SettingsScreen
import com.andrewwin.sumup.ui.screens.sources.SourcesScreen
import com.andrewwin.sumup.ui.screens.summary.SummaryScreen

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
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
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
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Summary.route,
        ) {
            composable(Screen.Summary.route) {
                SummaryScreen()
            }
            composable(Screen.Feed.route) {
                FeedScreen()
            }
            composable(Screen.Sources.route) {
                SourcesScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}

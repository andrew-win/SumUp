package com.andrewwin.sumup.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.andrewwin.sumup.ui.screens.PlaceholderScreen

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            navItems.forEach { screen ->
                item(
                    icon = { Icon(screen.icon, contentDescription = null) },
                    label = { Text(stringResource(screen.resourceId)) },
                    selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
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
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Summary.route
        ) {
            composable(Screen.Summary.route) { PlaceholderScreen(stringResource(Screen.Summary.resourceId)) }
            composable(Screen.Feed.route) { PlaceholderScreen(stringResource(Screen.Feed.resourceId)) }
            composable(Screen.Sources.route) { PlaceholderScreen(stringResource(Screen.Sources.resourceId)) }
            composable(Screen.Settings.route) { PlaceholderScreen(stringResource(Screen.Settings.resourceId)) }
        }
    }
}

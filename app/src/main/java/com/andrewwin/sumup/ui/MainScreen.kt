package com.andrewwin.sumup.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Stable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import com.andrewwin.sumup.ui.screen.feed.FeedScreen
import com.andrewwin.sumup.ui.screen.settings.SettingsScreen
import com.andrewwin.sumup.ui.screen.sources.SourcesScreen
import com.andrewwin.sumup.ui.screen.summary.SummaryHistoryScreen
import com.andrewwin.sumup.ui.screen.summary.SummaryScreen
import com.andrewwin.sumup.ui.screen.webview.WebViewScreen
import com.andrewwin.sumup.ui.components.AppMotion
import com.andrewwin.sumup.ui.screen.feed.FeedAiSummaryMode
import com.andrewwin.sumup.ui.screen.feed.FeedAiSummaryScreen
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val mainRoutes = remember(navItems) { navItems.map { it.route }.toSet() }
    val currentRoute = currentDestination?.route
    val shouldShowNavigationSuite = currentRoute in mainRoutes
    val navigationActions = remember(navController) {
        FeedNavigationActions(
            onOpenWebView = { url ->
                navController.navigate(Screen.WebView.createRoute(url))
            },
            onOpenAiArticleSummary = { articleId ->
                navController.navigate(
                    Screen.FeedAiSummary.createRoute(
                        mode = FeedAiSummaryMode.ARTICLE,
                        articleIds = listOf(articleId)
                    )
                )
            },
            onOpenAiClusterSummary = { articleIds ->
                navController.navigate(
                    Screen.FeedAiSummary.createRoute(
                        mode = FeedAiSummaryMode.CLUSTER,
                        articleIds = articleIds
                    )
                )
            },
            onOpenAiFeedSummary = { articleIds ->
                navController.navigate(
                    Screen.FeedAiSummary.createRoute(
                        mode = FeedAiSummaryMode.FEED,
                        articleIds = articleIds
                    )
                )
            },
            onOpenSummaryHistory = {
                navController.navigate(Screen.SummaryHistory.route)
            },
            onNavigateBack = { navController.popBackStack() }
        )
    }

    if (shouldShowNavigationSuite) {
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
            MainNavHost(
                navController = navController,
                mainRoutes = mainRoutes,
                navigationActions = navigationActions
            )
        }
    } else {
        MainNavHost(
            navController = navController,
            mainRoutes = mainRoutes,
            navigationActions = navigationActions
        )
    }
}

@Stable
private data class FeedNavigationActions(
    val onOpenWebView: (String) -> Unit,
    val onOpenAiArticleSummary: (Long) -> Unit,
    val onOpenAiClusterSummary: (List<Long>) -> Unit,
    val onOpenAiFeedSummary: (List<Long>) -> Unit,
    val onOpenSummaryHistory: () -> Unit,
    val onNavigateBack: () -> Unit
)

@Composable
private fun MainNavHost(
    navController: androidx.navigation.NavHostController,
    mainRoutes: Set<String>,
    navigationActions: FeedNavigationActions
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Summary.route,
        enterTransition = {
            val fromRoute = initialState.destination.route
            val toRoute = targetState.destination.route
            if (fromRoute in mainRoutes && toRoute in mainRoutes) {
                EnterTransition.None
            } else {
                AppMotion.screenEnter()
            }
        },
        exitTransition = {
            val fromRoute = initialState.destination.route
            val toRoute = targetState.destination.route
            if (fromRoute in mainRoutes && toRoute in mainRoutes) {
                ExitTransition.None
            } else {
                AppMotion.screenExit()
            }
        },
        popEnterTransition = {
            val fromRoute = initialState.destination.route
            val toRoute = targetState.destination.route
            if (fromRoute in mainRoutes && toRoute in mainRoutes) {
                EnterTransition.None
            } else {
                AppMotion.screenPopEnter()
            }
        },
        popExitTransition = {
            val fromRoute = initialState.destination.route
            val toRoute = targetState.destination.route
            if (fromRoute in mainRoutes && toRoute in mainRoutes) {
                ExitTransition.None
            } else {
                AppMotion.screenPopExit()
            }
        }
    ) {
        composable(Screen.Summary.route) {
            SummaryScreen(
                onOpenWebView = navigationActions.onOpenWebView,
                onOpenSummaryHistory = navigationActions.onOpenSummaryHistory
            )
        }
        composable(Screen.Feed.route) {
            FeedScreen(
                onOpenWebView = navigationActions.onOpenWebView,
                onOpenAiArticleSummary = navigationActions.onOpenAiArticleSummary,
                onOpenAiClusterSummary = navigationActions.onOpenAiClusterSummary,
                onOpenAiFeedSummary = navigationActions.onOpenAiFeedSummary
            )
        }
        composable(Screen.Sources.route) {
            SourcesScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
        composable(Screen.SummaryHistory.route) {
            SummaryHistoryScreen(
                onNavigateBack = navigationActions.onNavigateBack,
                onOpenWebView = navigationActions.onOpenWebView
            )
        }
        composable(
            route = Screen.FeedAiSummary.route,
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType },
                navArgument("ids") { type = NavType.StringType }
            )
        ) {
            FeedAiSummaryScreen(
                onNavigateBack = navigationActions.onNavigateBack,
                onOpenWebView = navigationActions.onOpenWebView
            )
        }
        composable(
            route = Screen.WebView.route,
            arguments = listOf(navArgument("url") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedUrl = backStackEntry.arguments?.getString("url") ?: ""
            val url = URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.toString())
            WebViewScreen(
                url = url,
                onNavigateBack = navigationActions.onNavigateBack
            )
        }
    }
}

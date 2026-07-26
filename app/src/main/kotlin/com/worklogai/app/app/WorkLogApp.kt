package com.worklogai.app.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.worklogai.app.R
import com.worklogai.app.app.navigation.DATA_MANAGEMENT_ROUTE
import com.worklogai.app.app.navigation.ENTRY_DATE_ARGUMENT
import com.worklogai.app.app.navigation.ENTRY_EDITOR_ROUTE
import com.worklogai.app.app.navigation.SETTINGS_ROUTE
import com.worklogai.app.app.navigation.SUMMARY_PERIOD_ROUTE
import com.worklogai.app.app.navigation.SummaryNavigationTarget
import com.worklogai.app.app.navigation.TopLevelDestination
import com.worklogai.app.app.navigation.entryEditorRoute
import com.worklogai.app.app.navigation.summaryPeriodRoute
import com.worklogai.app.feature.datamanagement.DataManagementScreen
import com.worklogai.app.feature.editor.EntryEditorScreen
import com.worklogai.app.feature.editor.TodayScreen
import com.worklogai.app.feature.history.HistoryScreen
import com.worklogai.app.feature.settings.SettingsScreen
import com.worklogai.app.feature.summary.SummaryScreen
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkLogApp(
    summaryNavigationTarget: SummaryNavigationTarget? = null,
    onSummaryNavigationConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route
    val topLevelDestination = TopLevelDestination.entries.firstOrNull { it.route == currentRoute }
    val isSettings = currentRoute == SETTINGS_ROUTE
    val isDataManagement = currentRoute == DATA_MANAGEMENT_ROUTE
    val isEntryEditor = currentRoute == ENTRY_EDITOR_ROUTE
    val isSummaryPeriod = currentRoute == SUMMARY_PERIOD_ROUTE

    LaunchedEffect(summaryNavigationTarget) {
        summaryNavigationTarget?.let { target ->
            navController.currentBackStackEntryFlow.first()
            navController.navigate(summaryPeriodRoute(target))
            onSummaryNavigationConsumed()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            WorkLogTopAppBar(
                state =
                    WorkLogTopAppBarState(
                        isSettings = isSettings,
                        isDataManagement = isDataManagement,
                        isEntryEditor = isEntryEditor,
                        isSummaryPeriod = isSummaryPeriod,
                        topLevelDestination = topLevelDestination,
                    ),
                onNavigateUp = navController::navigateUp,
            )
        },
        bottomBar = {
            WorkLogBottomBar(
                visible = topLevelDestination != null,
                currentDestination = currentDestination,
                onDestinationSelected = { destination ->
                    navController.navigateToTopLevelDestination(destination)
                },
            )
        },
    ) { innerPadding ->
        WorkLogNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkLogTopAppBar(
    state: WorkLogTopAppBarState,
    onNavigateUp: () -> Unit,
) {
    val isEntryEditor = state.isEntryEditor
    TopAppBar(
        title = {
            Text(
                text =
                    when {
                        state.isSettings -> stringResource(R.string.settings)
                        state.isDataManagement -> "数据管理"
                        isEntryEditor -> "工作记录"
                        state.isSummaryPeriod -> stringResource(R.string.nav_summary)
                        state.topLevelDestination != null -> stringResource(state.topLevelDestination.labelResourceId)
                        else -> stringResource(R.string.app_name)
                    },
            )
        },
        navigationIcon = {
            val showNavigateUp =
                when {
                    state.isSettings -> false
                    state.isDataManagement -> true
                    state.isEntryEditor -> true
                    else -> state.isSummaryPeriod
                }
            if (showNavigateUp) {
                IconButton(onClick = onNavigateUp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
            }
        },
    )
}

private data class WorkLogTopAppBarState(
    val isSettings: Boolean,
    val isDataManagement: Boolean,
    val isEntryEditor: Boolean,
    val isSummaryPeriod: Boolean,
    val topLevelDestination: TopLevelDestination?,
)

@Composable
private fun WorkLogBottomBar(
    visible: Boolean,
    currentDestination: NavDestination?,
    onDestinationSelected: (TopLevelDestination) -> Unit,
) {
    if (!visible) return

    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            val selected =
                currentDestination
                    ?.hierarchy
                    ?.any { it.route == destination.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = { onDestinationSelected(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(destination.labelResourceId)) },
            )
        }
    }
}

@Composable
private fun WorkLogNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.TODAY.route,
        modifier = modifier,
    ) {
        composable(TopLevelDestination.TODAY.route) {
            TodayScreen(
                onOpenEntry = { date -> navController.navigate(entryEditorRoute(date.toString())) },
            )
        }
        composable(TopLevelDestination.HISTORY.route) {
            HistoryScreen(onOpenEntry = { date -> navController.navigate(entryEditorRoute(date.toString())) })
        }
        composable(ENTRY_EDITOR_ROUTE) { entry ->
            EntryEditorScreen(
                entryDate = entry.arguments?.getString(ENTRY_DATE_ARGUMENT),
                onInvalidDate = navController::navigateUp,
                onOpenEntry = { date -> navController.navigate(entryEditorRoute(date.toString())) },
            )
        }
        composable(TopLevelDestination.SUMMARY.route) {
            SummaryScreen(onOpenSettings = { navController.navigate(SETTINGS_ROUTE) })
        }
        composable(SUMMARY_PERIOD_ROUTE) {
            SummaryScreen(onOpenSettings = { navController.navigate(SETTINGS_ROUTE) })
        }
        composable(SETTINGS_ROUTE) {
            SettingsScreen(onOpenDataManagement = { navController.navigate(DATA_MANAGEMENT_ROUTE) })
        }
        composable(DATA_MANAGEMENT_ROUTE) {
            DataManagementScreen(
                onRestoreCompleted = {
                    navController.navigate(
                        TopLevelDestination.TODAY.route,
                        restoreCompletionNavOptions(),
                    )
                },
            )
        }
    }
}

internal fun restoreCompletionNavOptions(): NavOptions =
    navOptions {
        popUpTo(TopLevelDestination.TODAY.route) { inclusive = true }
        launchSingleTop = true
    }

private fun NavHostController.navigateToTopLevelDestination(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

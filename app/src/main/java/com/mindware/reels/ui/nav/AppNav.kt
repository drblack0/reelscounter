package com.mindware.reels.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mindware.reels.data.AppContainer
import com.mindware.reels.ui.debug.DebugScreen
import com.mindware.reels.ui.history.HistoryScreen
import com.mindware.reels.ui.today.TodayScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Today("today", "Today", Icons.Filled.Home),
    History("history", "History", Icons.Filled.DateRange),
    Debug("debug", "Debug", Icons.Filled.Settings), // becomes Settings in M6
}

@Composable
fun AppNav(container: AppContainer) {
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = current?.destination?.route == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { inner ->
        NavHost(nav, startDestination = Tab.Today.route, modifier = Modifier.padding(inner)) {
            composable(Tab.Today.route) { TodayScreen(container.repository, container.live) }
            composable(Tab.History.route) { HistoryScreen(container.repository) }
            composable(Tab.Debug.route) { DebugScreen() }
        }
    }
}

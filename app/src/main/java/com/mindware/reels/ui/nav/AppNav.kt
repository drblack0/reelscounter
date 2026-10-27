package com.mindware.reels.ui.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mindware.reels.data.AppContainer
import com.mindware.reels.detection.DebugLog
import com.mindware.reels.ui.debug.DebugScreen
import com.mindware.reels.ui.history.HistoryScreen
import com.mindware.reels.ui.onboarding.BatteryContent
import com.mindware.reels.ui.onboarding.OnboardingScreen
import com.mindware.reels.ui.onboarding.rememberServiceEnabled
import com.mindware.reels.ui.privacy.PrivacyScreen
import com.mindware.reels.ui.settings.SettingsScreen
import com.mindware.reels.ui.today.TodayScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Today("today", "Today", Icons.Filled.Home),
    History("history", "History", Icons.Filled.DateRange),
    Settings("settings", "Settings", Icons.Filled.Settings),
}

@Composable
fun AppNav(container: AppContainer) {
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route
    val debugOn by DebugLog.enabled.collectAsState()
    val start = if (container.settings.state.value.onboarded) Tab.Today.route else "onboarding"

    Scaffold(
        bottomBar = {
            if (Tab.entries.any { it.route == route }) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
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
            }
        },
    ) { inner ->
        Column(Modifier.padding(inner)) {
            if (debugOn) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text("Debug mode is on (structure-only logging)", Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
            NavHost(nav, startDestination = start, modifier = Modifier.weight(1f)) {
                composable("onboarding") {
                    OnboardingScreen(onDone = {
                        container.settings.update { it.copy(onboarded = true) }
                        nav.navigate(Tab.Today.route) { popUpTo("onboarding") { inclusive = true } }
                    })
                }
                composable(Tab.Today.route) {
                    Column {
                        if (!rememberServiceEnabled()) {
                            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "The accessibility service is off, so reels aren't being counted. Turn it on in Settings.",
                                    Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        TodayScreen(container.repository, container.live)
                    }
                }
                composable(Tab.History.route) { HistoryScreen(container.repository) }
                composable(Tab.Settings.route) { SettingsScreen(container, onOpen = { nav.navigate(it) }) }
                composable("privacy") { PrivacyScreen() }
                composable("debug") { DebugScreen() }
                composable("battery") {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                        Text("Keep it running", style = MaterialTheme.typography.headlineMedium)
                        BatteryContent()
                    }
                }
            }
        }
    }
}
